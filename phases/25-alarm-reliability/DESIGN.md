# Phase 25 — 알람 신뢰성 계측 설계

## 결정: 발화 식별은 등록 시도의 고유 토큰

Phase 24 핸드오프 §17의 `alarmKey → expectedAt` 단일 슬롯 원장은 §18의 콜드 스타트 순서를 견디지 못한다. `Application.onCreate()`에서 다음 리마인더 B를 재등록한 뒤 이전 A의 리시버가 실행되면 원장은 이미 B다. 또한 같은 PendingIntent에 `FLAG_UPDATE_CURRENT`를 사용하면 `set(B)` 실패 전에 A의 extras가 B로 바뀔 수 있다.

각 OS 등록 **시도**마다 새 무작위 토큰(UUID)을 만든다. 토큰을 `Intent.data`의 경로에 넣어 PendingIntent의 identity를 분리한다. 기존 `requestCode`를 유지하더라도 URI가 달라야 한다. 수신 시 extras의 시각/제목, 현재 슬롯, 과거 `registered`를 발화 식별에 사용하지 않는다. 리시버는 `data`에서 토큰만 읽고 `alarm_delivery_token` 행으로 `(alarmKey, expectedAt)`을 찾는다. 알 수 없는 토큰과 미리보기에는 `FIRED`를 기록하지 않는다.

스케줄러의 key별 직렬 경로는 다음 순서를 지킨다.

현재 `scheduleForEvent/Todo`, `scheduleNext`는 동기 API이고 메인 스레드, `Application.onCreate`, 리시버에서도 호출된다. 연결 시 앱 시작 보조 재등록은 key만 캡처해 `AlarmCommandQueue.submit`으로 명령을 보낸 뒤 즉시 반환한다. 단일 application-scoped IO 소비자가 명령을 순서대로 수행한다. 명령 내부에서 최신 Room 엔티티/설정을 다시 읽고 아래 1~4를 완료한다. 호출 시점의 엔티티 객체를 큐에 보관하지 않는다. `runBlocking`이나 메인 스레드 Room 접근은 사용하지 않는다. 명령 실패는 로그와 완료형 API의 예외로 보고한다. 큐 소비자 내부에서 완료형 API를 재귀 호출하지 않는다.

리시버의 필수 재무장은 `goAsync()`가 살아 있는 동안 `submitAndAwait`로 완료까지 기다린다. `ReminderReceiver.scheduleNext`는 현재 `goAsync()` 이전에 실행되므로 수신 처리 코루틴 안으로 옮긴다. `AlarmReceiver`의 반복 일정 재무장도 같은 완료형 API를 사용한다. 로그아웃의 `SyncRepository.clearLocalUserData`는 **suspend 취소 명령이 완료된 뒤** Room을 비운다. 완료형 API가 실패하면 데이터를 지우지 않고 실패를 호출자에게 전달한다. 앱 시작 enqueue가 프로세스 종료로 유실되더라도 기존 OS 알람은 유지하고, 다음 시작/복원에서 다시 대사한다.

1. 최신 Room 엔티티로 48시간 기대 집합을 계산하고 `alarm_log` 행을 **OS 호출 전** 보존한다. 같은 key의 이전 계산 결과가 최신 집합을 덮지 않게 한다.
2. IO 소비자가 새 토큰을 `PREPARED`로 Room에 저장하고 suspend 호출의 완료를 기다린다. 실패하면 OS 호출을 하지 않는다. 현재 방식과 다른 고유 URI로 PendingIntent를 만든다.
3. `setAlarmCompat` 성공 시 토큰을 `ARMED`로 기록하고 해당 기대 행의 `registered`를 1로 둔다. 그 다음 이전 토큰의 PendingIntent를 취소하고 `RETIRED`로 기록한다. 실패 시 새 토큰을 `FAILED`로 기록하고 이전 PendingIntent를 유지한다.
4. 취소/교체 중에 OS가 이미 발행한 이전 브로드캐스트가 도착할 수 있다. `RETIRED` 토큰도 수신을 인정한다. `PREPARED` 토큰이 도착했다면 그 고유 PendingIntent를 OS가 발행했다는 증거이므로 인정한다. 같은 토큰 재전달은 `deliveredAt` 원자 갱신으로 한 번만 센다. 여러 토큰이 같은 기대 행을 가리켜도 `FIRED`는 흡수 상태다.

토큰은 무작위로 추측하기 어렵게 만들고 수신 리시버의 `exported=false`를 유지한다. 실패한 토큰은 수신 대상이 아니다. 과거 토큰은 늦은 브로드캐스트 식별에 필요하므로 임의의 짧은 TTL로 제거하지 않는다. 장기 보존/정리는 실제 최대 지연과 계측 보존 정책을 확인한 뒤 정한다.

리시버는 `goAsync()` 후 `Dispatchers.IO`에서 `recordDelivery` 트랜잭션을 실행하고 `finally`에서 `PendingResult.finish()`를 호출한다. 이 기록을 등록 큐 뒤에 막연히 대기시키지 않는다. 고유 토큰이 과거 발화를 현재 재등록과 분리한다. Android 브로드캐스트 시간 제한 안에 완료되어야 하며, 오래 걸리는 게시·대사는 WorkManager로 넘긴다.

### 실패와 콜드 스타트 순서

- A→B→A→B, 마지막 B의 `set` 실패: 마지막 B 토큰은 `FAILED`; 이전 A 토큰은 살아 있다. A 수신은 A 행만 `FIRED`로 만든다. A/B 시각의 선후를 바꿔도 같다.
- A 브로드캐스트로 프로세스 시작 → `Application.onCreate`가 B 재등록 → A 리시버 실행: 새 B 토큰은 A의 토큰을 덮지 않는다. A는 A로 기록되고 B 토큰은 다음 수신을 위해 남는다.
- `set` 성공 직후 프로세스 종료: `PREPARED` 행이 남는다. 토큰이 실제 배달되면 발화 증거로 받아들인다. 배달이 없으면 성공 여부는 알 수 없다. 기한 후 기대 행의 미수신으로 분류하며, OS 등록 성공 여부 진단은 미확정으로 표시해야 한다.
- 새 등록 성공 후 이전 취소 전에 종료: 두 토큰이 남을 수 있다. 둘 중 어느 쪽이 실제로 도착했는지 각각 식별된다. 같은 기대 발생의 중복은 한 번만 집계한다. 다른 기대 발생 두 개가 울리면 실제 두 발화를 보존한다.

이 방식은 **OS가 PendingIntent의 고유 identity를 브로드캐스트까지 보존한다**는 Android 계약과 리시버의 토큰 파싱 구현에 의존한다. 실제 기기 테스트가 필요하다. 기존 버전의 토큰 없는 PendingIntent는 미확인 발화로 표시하고 게이트에 귀속하지 않는다.

## 데이터와 판정

`AlarmLogEntity`, `AlarmDeliveryTokenEntity`, `AlarmCoverageGapEntity`, `AlarmCoverageStateEntity`, `AlarmLogDao`, `AlarmReliabilityPolicy`는 독립 기반으로 추가했다. `alarm_log`는 `(alarmKey, expectedAt)` 유일 기대 발생이다. `registered`는 성공 이력이며 발화 증거가 아니다. `markFired`는 `MISSED`를 늦은 `FIRED`로 바로잡는다. `recordDelivery`는 토큰 조회·중복 claim·발화 기록을 Room 트랜잭션 하나에서 처리한다. 기대 행이 없으면 수신을 집계하지 않는다.

48시간 기대 창은 저장 비용과 관측 범위의 선택이다. 단, OS에 실제로 거는 다음 트리거 1건은 48시간 밖이어도 기대 행을 만든다. `recordDelivery`는 기대 행이 없는 토큰을 집계하지 않으므로, 이 행이 없으면 그 알람이 울려도 기록되지 않는다. 이 행은 누락 판정에서 관측 범위(`coveredThroughAt`) 안에 들어올 때만 대상이 된다. 12시간 주기 복원이 범위를 앞으로 밀어 기대 시각 전에 포함되고, 복원이 멈추면 공백으로 `UNOBSERVED` 처리된다. 이미 `FIRED`인 행은 재등록돼도 `registered`·`exact`를 다시 쓰지 않는다(시계를 뒤로 돌린 경우). 마지막 커버리지 끝 시각이 집계 끝보다 이르거나 집계 구간에 `alarm_coverage_gap`이 있으면 전체 5% 판정은 `UNDETERMINED`다. 관측된 일정·Todo의 `MISSED`와 게시 차단 `FIRED`만 분자에 넣고, `FIRED + MISSED`를 분모로 둔다. 통독·소비 리마인더는 분리한다. 기기에서 정확/비정확 알람의 15분/2시간 유예와 48시간 창을 실측하기 전까지는 `REFERENCE_*`만 출력한다. 알림 표시 자체는 OS가 확인해 주지 않는다.

## 통합 차단점

`AppDatabase` v8, `MIGRATION_7_8`, `AppModule` 마이그레이션/DAO provider를 함께 반영했다. 기존 6→7 테스트는 6→8로 확장하고 7→8 직접 업그레이드 테스트를 추가했다. 네 데이터 테이블과 기존 데이터 보존은 계측 테스트에서 확인한다.

`notification/`, `worker/`, 저장소, `LSyncApplication`과 상태 화면을 연결했다. 일정·할 일은 토큰 수신 시각과 게시 호출 결과를 별도로 기록한다. 복원 워커는 스케줄러 명령 완료 후 48시간 관측 범위를 갱신하고, 12시간마다 반복 실행한다. 7→8 및 6→8 마이그레이션은 에뮬레이터 계측 테스트로 확인했다. 5% 판정은 실제 기기에서 48시간 창과 유예 시간, Doze·재부팅·권한 변경 순서를 실측하기 전까지 참고치다.
