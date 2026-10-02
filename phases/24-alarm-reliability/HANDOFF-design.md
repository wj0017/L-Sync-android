# 설계 논의 요청: 알림 누락 계측 (Phase 24)

**이 문서는 구현 지시가 아니라 설계 검토 요청이다.** 코드를 고치지 말고, 아래 초안의 결함을 찾고 §5의 질문에 답해 달라. 저장소는 `C:\GitHub\L-Sync`(Android, Kotlin, Room, Hilt). 규칙은 루트 `CLAUDE.md`, 설계 문서는 `docs/TechSpec.md`(§5 알림, §12 동기화 계측)를 먼저 읽어라.

## 1. 목표

PRD Phase 1 게이트 "알림 누락 5% 미만"을 기기에서 **측정**할 수 있게 한다. 동기화 에러율은 Phase 23에서 Room `sync_log` + `ui/sync/SyncStatusScreen`으로 이미 측정한다. 같은 결(로컬 전용 진단 테이블 + 상태 화면)로 알림 쪽을 채운다.

범위 밖: 알림 누락을 자동으로 고치는 것(재발송 등), Firestore 동기화, 결제 알림 리스너(`PaymentNotificationService`).

## 2. 현재 구조 (읽은 코드 기준)

알람 경로는 두 갈래이고 둘 다 AlarmManager one-shot이다.

| 경로 | 등록 | 발화 | 재등록 |
|---|---|---|---|
| 일정·할 일 | `notification/AlarmScheduler.kt` `scheduleForEvent`/`scheduleForTodo` (Repository가 호출) | `notification/AlarmReceiver.kt` (Hilt 아님, 동기 처리) | `worker/AlarmRestoreWorker`(부팅·앱 업데이트), `worker/EventAlarmRefreshWorker`(일 1회) |
| 정기 리마인더 3종 | `notification/ReminderScheduler.kt` `scheduleNext` | `notification/ReminderReceiver.kt` (`@AndroidEntryPoint` + `goAsync`) | 발화 시 재무장, `LSyncApplication.onCreate` `syncAll()`, `AlarmRestoreWorker` |

계측 설계에 영향을 주는 사실:

- **스케줄러는 non-suspend이고 전부 `runCatching`으로 감싸져 있다.** 등록 실패가 조용히 사라진다.
- **같은 알람이 반복 등록된다.** 워커·앱 시작이 같은 트리거를 매일 다시 등록한다(멱등 전제). 등록 1회 = 로그 1행으로 쌓으면 분모가 부풀려진다.
- **`trigger <= now`면 등록을 건너뛴다**(`AlarmScheduler.kt:49`, `:61`). 오늘 09:00 이후에 만든 오늘 마감 Todo는 알람이 없다. 정책이지 누락이 아니다.
- **정확 알람 권한이 없으면 inexact로 폴백한다**(`AlarmCompat.kt`). 호출부는 어느 쪽으로 등록됐는지 모른다.
- **리마인더는 울려도 일부러 알림을 안 낼 때가 있다**(통독 완료, 로그아웃 상태 등 — `ReminderReceiver.kt:62-66`).
- **`AlarmReceiver`는 알림 권한·채널 차단 여부를 확인하지 않는다.** 리시버는 돌았지만 사용자는 못 본 경우를 구분할 수 없다.
- 로그아웃 시 `SyncRepository.clearLocalUserData()`가 미래 알람을 취소한 뒤 테이블을 비운다(`SyncRepository.kt:171-185`).

### 2.1 조사 중 발견한 버그 의심 (코드로 확인, 기기 재현은 안 함)

**반복 일정은 "오늘 발생분" 알람이 재등록 때 덮어써져 울리지 않을 수 있다.**

- `AlarmScheduler.scheduleForEvent`는 반복 일정에 `nextOccurrence(event, afterDate = LocalDate.now().toString())`를 쓴다(`AlarmScheduler.kt:37`).
- `nextOccurrence`는 `dateStr <= afterDate`를 건너뛴다(`data/recurrence/EventRecurrence.kt:108`). 즉 **오늘 발생분은 절대 반환하지 않는다.**
- 등록은 같은 requestCode + `FLAG_UPDATE_CURRENT`라서 기존 알람을 교체한다.

따라서 매일 10:00 반복 일정이 있을 때, 오늘 10:00 전에 `EventAlarmRefreshWorker`·`AlarmRestoreWorker`·일정 수정 중 하나가 돌면 오늘 10:00 알람이 내일 10:00으로 바뀐다. 오늘 알림은 오지 않는다.

이 버그는 설계에도 영향을 준다. **"등록된 것이 울렸는가"만 재면 이 누락은 잡히지 않는다**(로그에는 정상적인 교체로 보인다). §5의 Q1·Q2 참고.

## 3. 초안 설계

### 3.1 테이블 `alarm_log` (AppDatabase v7 → v8, `MIGRATION_7_8`)

알람 1건(등록부터 종결까지)이 1행이다. 시도마다 행을 쌓는 `sync_log`와 다르다.

| 컬럼 | 타입 | 뜻 |
|---|---|---|
| `id` | INTEGER PK auto | |
| `alarmKey` | TEXT | `event:{id}` / `todo:{id}` / `reminder:{key}` — 스케줄러의 requestCode 네임스페이스와 같은 문자열 |
| `kind` | TEXT | `event` / `todo` / `reminder` |
| `expectedAt` | INTEGER | 등록한 트리거 시각(epoch millis) |
| `exact` | INTEGER | 정확 알람으로 등록됐는지 |
| `status` | TEXT | `PENDING` / `FIRED` / `MISSED` / `CANCELLED` / `SUPERSEDED` |
| `registeredAt` | INTEGER | 최초 등록 시각 |
| `firedAt` | INTEGER? | 리시버 진입 시각 |
| `posted` | INTEGER? | 알림을 실제 게시했는지 |
| `skipReason` | TEXT? | `NOTIFICATIONS_OFF` / `CHANNEL_BLOCKED` / `NO_CONTENT` |

인덱스: `(status, expectedAt)`, `(alarmKey, status)`. `userId`와 제목은 넣지 않는다(Q6).

### 3.2 상태 전이 규칙

- **R1.** `alarmKey`당 `PENDING`은 최대 1행.
- **R2. 등록** `(key, expectedAt)`: 같은 `expectedAt`의 `PENDING`이 있으면 아무것도 하지 않는다(멱등). 다르면 기존 행을 닫고 새 행을 넣는다. 기존 행이 이미 기한(`expectedAt + grace < now`)을 넘겼으면 `MISSED`, 아니면 `SUPERSEDED`.
  - **기한 지난 행을 `SUPERSEDED`로 닫으면 안 된다.** 리마인더가 21:00에 안 울리고 다음 날 아침 앱 시작 때 `syncAll()`이 내일 21:00을 등록하면, 누락이 교체로 지워진다.
- **R3. 취소**: `PENDING` → `CANCELLED`(기한을 넘겼으면 `MISSED`).
- **R4. 발화**: 리시버가 key + `expectedAt`(Intent extra로 전달)으로 행을 찾아 `FIRED`로 바꾼다. 이미 `MISSED`로 닫힌 행이 뒤늦게 울리면 `FIRED`로 되돌린다(지연으로 집계). 행이 없으면(이 Phase 설치 전에 등록된 알람) `FIRED` 행을 새로 넣는다.
- **R5. 대사(reconcile)**: `PENDING`이면서 `expectedAt + grace < now`인 행을 `MISSED`로 닫는다. 호출 지점은 앱 시작, `AlarmRestoreWorker`, `EventAlarmRefreshWorker`, 상태 화면 진입.
- **R6.** 기록 실패는 삼킨다. **알림 게시가 먼저, 기록은 그다음.** 계측 때문에 알림이 안 나가면 안 된다.
- **R7.** `trigger <= now`로 등록을 건너뛴 경우는 기록하지 않는다.

### 3.3 구성 요소

- `data/alarm/AlarmLogger`(`@Singleton`): `recordScheduled`/`recordCancelled`/`recordFired`/`reconcile`/`pruneExpired`. 스케줄러가 non-suspend라서 `@ApplicationScope`에 순서 보장 디스패처(`limitedParallelism(1)`)로 던진다.
- 판정은 순수 함수로 뗀다(`decideOnReschedule`, `isOverdue`, `classifyDelay`). JVM 유닛 테스트 대상이다.
- `AlarmCompat.setAlarmCompat`이 `exact` 여부를 반환하도록 바꾼다.
- `AlarmReceiver`를 `@AndroidEntryPoint` + `goAsync()`로 바꾼다(기록용 Room 접근 때문). `super.onReceive` 호출 금지(컴파일 에러) — `ReminderReceiver`·`TodoActionReceiver`와 같은 패턴.
- `ui/alarm/AlarmStatusScreen` + `AlarmStatusViewModel`: `SyncStatusScreen`과 같은 구성(7/30일 chip, 게이트 카드, 최근 50건). 홈 헤더 메뉴에서 진입한다.
- 보존: 종결 후 30일(`SyncLogger`와 같은 주기).

### 3.4 게이트 공식

```
누락률 = MISSED / (FIRED + MISSED)      ← CANCELLED · SUPERSEDED · PENDING 제외
```

지연(`firedAt - expectedAt`이 임계 초과)과 게시 차단(`posted = 0`이고 `skipReason`이 `NOTIFICATIONS_OFF`·`CHANNEL_BLOCKED`)은 누락률에 넣지 않고 따로 보여 준다.

### 3.5 Step 구성안 (harness, step당 한 레이어)

| step | name | 내용 |
|---|---|---|
| 0 | `alarm-log-entity` | Entity·DAO·`MIGRATION_7_8`·`di/AppModule.kt` `@Provides`·마이그레이션 계측 테스트 확장 |
| 1 | `alarm-logger` | `AlarmLogger` + 순수 판정 함수 + JVM 테스트 |
| 2 | `scheduler-hooks` | `AlarmScheduler`·`ReminderScheduler`·`AlarmCompat` 기록 연결 |
| 3 | `receiver-hooks` | 두 리시버 발화 기록, 대사 호출 지점 4곳 |
| 4 | `status-screen` | 화면·ViewModel·NavGraph·홈 메뉴 |
| 5 | `docs` | PRD·TechSpec·ARCHITECTURE·README |

선행 조건: 브랜치 `test/room-migration-ci`(커밋 `f1be882`, Room 6→7 마이그레이션 테스트 + GitHub Actions)가 아직 main에 없다. step 0이 그 테스트를 7→8로 확장하므로 먼저 머지돼야 한다.

## 4. 내가 확신하지 못하는 부분

- §3.2의 `grace` 값. Doze에서 `setExactAndAllowWhileIdle`이 얼마나 밀리는지 실측한 적이 없다.
- R4의 "MISSED → FIRED 되돌리기"가 대사와 경쟁할 때 안전한지.
- `AlarmReceiver`를 Hilt로 바꾸는 것이 기존 PendingIntent(이미 등록된 알람)와 호환되는지. 클래스명이 같으니 될 것으로 보지만 확인하지 않았다.

## 5. 답해 달라는 질문

**Q1. 무엇을 "기대된 알람"으로 볼 것인가.** 초안은 "등록 호출이 있었던 알람"이 기준이다. 이러면 §2.1처럼 **등록 자체가 잘못된 누락은 보이지 않는다.** 대안은 Room 데이터에서 "울렸어야 할 알람"을 역산해 `FIRED`와 대조하는 감사(audit)다. 초안에 감사를 넣어야 하는가, 아니면 Phase를 나누는가? 넣는다면 반복 일정 전개(`expandEvents`)를 어디까지 재사용할 수 있는가?

**Q2. §2.1 버그의 진위와 수정 위치.** 내 읽기가 맞는지 코드로 검증해 달라. 맞다면 ① Phase 24 전에 단독 fix 커밋 ② Phase 24 step에 포함 중 어느 쪽인가? 수정안(`afterDate`를 어제로 두고 `trigger > now`인 첫 발생까지 전진)에 구멍이 있는가?

**Q3. `grace`와 지연 임계.** exact / inexact 각각 얼마가 적절한가? inexact 폴백 상태의 긴 지연을 누락으로 셀 것인가?

**Q4. 리마인더를 게이트 분모에 넣을 것인가.** 리마인더는 "울렸지만 일부러 안 냄"이 정상이다. 일정·할 일과 같은 분모에 섞어도 되는가?

**Q5. 기기가 꺼져 있던 시간의 알람.** 재부팅 복원은 미래 알람만 다시 건다. 꺼진 동안 지나간 알람은 사용자 관점에선 누락이지만 앱이 막을 수 없다. 누락률에 넣는가, 원인 분류(`DEVICE_OFF`)로 빼는가? 분류한다면 판별 방법은?

**Q6. `userId`·제목 미저장.** 리마인더는 기기 단위 설정이라 `userId`가 없다. 그래서 테이블 전체를 기기 단위로 두고, 계정 간 노출을 피하려 제목을 저장하지 않았다. 대신 화면에는 "할 일 · 10/02 09:00"처럼 종류와 시각만 나온다. 진단에 충분한가? 로그아웃 때 이 테이블을 어떻게 다뤄야 하는가?

**Q7. 순서 보장.** non-suspend 스케줄러에서 `scope.launch`로 기록하면 등록→취소→등록이 빠르게 이어질 때 순서가 뒤집힐 수 있다. `limitedParallelism(1)`로 충분한가? 리시버(별도 진입점)의 기록과도 직렬화해야 하는가?

**Q8. 화면.** 별도 화면으로 둘 것인가, `SyncStatusScreen`에 합쳐 "신뢰성" 한 화면으로 만들 것인가?

**Q9. 테스트.** 순수 함수 JVM 테스트 외에 DAO 전이 규칙(R1~R5)을 계측 테스트로 CI에 올릴 가치가 있는가?

## 6. 응답 형식

1. **결함**: 초안에서 틀렸거나 빠진 것. 심각도 순, 파일:라인 근거 포함.
2. **Q1~Q9 답**: 각각 결론 한 줄 + 이유.
3. **수정된 step 구성안**: §3.5 표를 고쳐서.
4. 추측과 코드로 확인한 것을 구분해 표시.

## 7. 부수 발견 (이번 범위 아님)

- `docs/TechSpec.md` §5.2는 `ReminderReceiver`가 `onReceive` 첫 줄에서 `super.onReceive`를 호출한다고 적혀 있다. 실제 코드는 호출하지 않는다(호출하면 컴파일 에러). 문서가 틀렸다. step 5에서 같이 고친다.

---

## 8. 설계 검토 결과 및 개선 제안 (2026-10-02)

아래의 **확인**은 현재 저장소의 정적 코드 검토 결과이고, **제안/미검증**은 설계 판단 또는 기기 실측이 필요한 부분이다. 이 검토에서는 구현 코드를 수정하거나 기기 테스트를 실행하지 않았다.

### 8.1 결함 — 심각도 순

1. **치명적 · 반복 일정의 오늘 알람 누락과 재무장 공백 [확인].** `AlarmScheduler.kt:37`은 `LocalDate.now()`를 넘기고 `EventRecurrence.kt:108`은 그 날짜 이하를 버린다. 오늘 발생분은 새로 등록되지 않으며, 기존 PendingIntent가 있으면 `AlarmScheduler.kt:72-74`의 같은 requestCode 등록으로 다음 발생분에 교체된다. 또한 `EventAlarmRefreshWorker.kt:26`은 일 1회 WorkManager 실행에 의존하므로, 반복 알람이 정상 발화한 뒤 다음 발생분을 즉시 재무장하는 경로도 없다. 일일 워커 실행 시각에 따라 다음 회차까지 빈 구간이 생긴다. `AlarmReceiver` 발화 후 다음 회차 재무장을 추가해야 한다.
2. **높음 · 반복 발생의 유효 알람·제목을 적용하지 않음 [확인].** `EventRecurrence.kt:145`는 발생별 `hasAlarm`과 제목을 계산하지만 `AlarmScheduler.kt:37-50`은 `next.startDate`만 사용하고 마스터의 `hasAlarm`·`title`을 사용한다. 특정 발생만 알림을 끄거나 제목을 바꾼 편집 결과가 실제 알람에 반영되지 않는다. 다음 *알림 허용* 발생을 찾고, 그 발생의 제목·시각을 Intent에 넣어야 한다. 마스터 `hasAlarm=false`이면서 발생 override `hasAlarm=true`를 허용할지도 명시해야 한다. 현재 `EventDao.kt:74-80` 조회와 스케줄러의 조기 반환은 이 경우를 제외한다.
3. **높음 · 등록 기준 로그만으로 PRD 게이트를 측정할 수 없음 [확인+판단].** `docs/PRD.md:85`는 알림 누락률을 요구하지만 초안의 분모는 *성공적으로 등록된* 알람에 한정된다. `AlarmScheduler.kt:25-52`, `ReminderScheduler.kt:44-79`의 `runCatching` 실패, `trigger <= now` 스킵, 반복 일정의 잘못된 계산은 모두 분모 밖으로 사라진다. `AlarmReceiver.kt:71`의 `notify()` 호출만으로도 사용자 표시나 수신을 증명할 수 없다. 화면의 수치를 우선 **등록된 알람의 리시버 도달률**로 이름 붙이고, PRD의 알림 누락 게이트와 동일하다고 표시하지 말아야 한다. 게이트 확정 전에는 Room 기대 발생 감사와 게시 가능 여부 계측이 필요하다.
4. **높음 · 비동기 기록의 경쟁과 가짜 발화 [확인+판단].** `limitedParallelism(1)`은 한 디스패처에서 실행되는 작업의 동시성만 제한한다. 여러 스레드에서 `launch`한 호출의 OS 등록 완료 순서, 리시버 기록, DB 트랜잭션 순서를 보장하지 않는다. 특히 `ReminderReceiver.kt:56`은 발화 직후 `scheduleNext()`를 먼저 호출한다. 다음 회차 등록 로그가 직전 회차의 발화 로그보다 앞서면 R2가 직전 행을 잘못 닫을 수 있다. `NotificationSettingsViewModel.kt:46-58`의 **미리보기 브로드캐스트**도 같은 리시버로 들어오므로 R4의 무조건적인 orphan `FIRED` 생성은 분모를 오염시킨다. 미리보기 플래그를 넣어 계측·재무장을 제외하고, 알람 키+예정 시각(가능하면 별도 세대 ID)으로 트랜잭션 내에서 매칭해야 한다. 예정 시각이 없는 구버전 Intent는 `UNKNOWN`으로 분리해 게이트에서 제외한다.
5. **높음 · 스킵·취소의 상태 전이가 불완전함 [확인+판단].** `AlarmScheduler.kt:49,61`은 과거 트리거를 단순 반환하므로 편집 전의 미래 알람이 이미 있다면 취소되지 않는다. R7처럼 기록도 생략하면 실제 등록 상태와 로그가 갈라진다. 명시적 취소, 과거 시각 편집, 정상 재등록, 등록 실패를 서로 다른 결과로 다뤄야 한다. R2/R3에서 기한 지난 행을 무조건 `MISSED`로 닫으면 사용자가 뒤늦게 일정을 삭제·수정한 경우까지 누락으로 분류할 수 있으므로 원인과 조작 시각을 남겨야 한다.
6. **중간 · 기기 재부팅·시계 변경·정확 알람 권한 변경을 구분하지 못함 [확인+판단].** `BootReceiver.kt:14-27`은 복원 워커만 예약하고, `AlarmRestoreWorker.kt:25-36`은 미래 항목만 다시 건다. 과거 `PENDING`을 앱 시작 대사에서 모두 `MISSED` 처리하면 기기 전원 꺼짐과 앱 전달 실패를 섞는다. 또한 현재 `AlarmCompat.kt:12-18`은 사용한 모드를 반환하지 않고 예외는 스케줄러의 `runCatching`에 흡수된다. 등록 성공·실패·exact/inexact와 마지막 관측 부팅 세대, 시간 변경 여부를 구분하는 결과가 필요하다. 부팅 전후 전체 구간을 확실히 복원할 수 없다면 `DEVICE_OFF` 확정 대신 `UNKNOWN_DURING_REBOOT`로 분리한다.
7. **중간 · `posted`의 의미와 게이트 공식이 다름 [확인+판단].** `AlarmReceiver.kt:20-71`은 권한·채널 차단 여부를 확인하지 않고 `notify()`를 호출한다. `ReminderReceiver.kt:62-68`은 콘텐츠가 없으면 의도적으로 게시하지 않는다. `FIRED`는 리시버 도달, `POSTED`는 `notify()` 호출 성공, `BLOCKED`는 권한/채널로 게시 불가, `NO_CONTENT`는 의도된 스킵으로 분리해야 한다. `notify()` 성공도 실제 표시·사용자 확인을 보증하지 않는다. 분모 0건은 0% 통과가 아니라 `측정 데이터 없음`으로 표시한다.

### 8.2 Q1~Q9 답

| 질문 | 결론 | 근거와 개선 방향 |
|---|---|---|
| Q1 | **감사를 Phase 24에 포함하되 게이트 산출은 감사가 붙은 뒤 활성화 [제안].** | 등록 로그만으로는 등록 누락을 못 잡는다. `EventDao.observeForExpansion`과 `expandEvent`/`expandEvents`로 제한된 날짜 구간의 기대 발생을 계산하고, Todo는 해당 날짜·미완료·생성 시각을 반영한다. 현재 Room 행만으로 과거 편집/삭제 전 상태는 복원할 수 없으므로 생성·변경 시점의 기대 시각 스냅샷도 남겨야 한다. |
| Q2 | **버그 확인; Phase 24 전 별도 수정으로 분리 [확인+제안].** | 오늘을 포함하려면 `afterDate=today.minusDays(1)`만으로 끝나지 않는다. 오늘 시각이 지났다면 유효한 다음 발생까지 전진하고, `next.hasAlarm`, `next.title`, exdate/override를 반영하며, 오늘 반복 발화 뒤 즉시 재무장해야 한다. 날짜/시각 경계, 미래 override, DST 테스트를 붙인다. |
| Q3 | **모드별 임시 관찰 기준을 두고 기기 실측으로 조정 [제안/미검증].** | 시작값은 exact 15분, inexact 2시간의 `grace`; 지연 경고는 각각 5분/30분으로 제안한다. Android 지연 상한이 아니므로 초과 즉시 영구 누락으로 확정하지 않는다. 늦은 발화는 `FIRED_LATE`로 기록하고, inexact와 exact를 별도 분모로 보여 준다. 기기·Doze 조건별 실측 전에는 5% 게이트 판정 보류. |
| Q4 | **정기 리마인더는 주 게이트 분모에서 제외 [제안].** | `ReminderReceiver.kt:62-68`의 `NO_CONTENT`가 정상 동작이다. `reminder`는 별도의 도달률·게시/의도적 스킵 수치로 보여 준다. 나중에 포함하려면 설정 on, 콘텐츠 존재, 권한 허용의 기대 조건을 정의해야 한다. |
| Q5 | **전원 꺼짐 구간은 주 게이트에서 별도 `UNKNOWN_DURING_REBOOT`로 표시 [제안].** | 부팅 시각을 경계로 추정할 수 있어도 복원 전 기록이 없으면 전원 꺼짐을 확정하기 어렵다. 부팅 관측 정보가 있을 때만 분리하고, 시계 변경·앱 업데이트·강제 중지도 함께 불확실성으로 표시한다. 사용자 관점 총 미도달 수는 참고 수치로 병기한다. |
| Q6 | **제목은 저장하지 않고, 계정 식별자는 저장 [제안].** | `event:{id}`/`todo:{id}` 키 자체가 계정 간 추적 가능한 식별자다. `userId` 없이 전체 로그를 한 화면에 보여 주면 다른 계정 사용 이력이 노출된다. 일정·Todo는 `userId`로 조회를 격리하고 로그아웃 시 해당 계정 로그는 보존하되 화면에서 숨긴다. 기기 설정인 리마인더는 별도 기기 범위로 표시한다. 제목은 현재처럼 저장하지 않는다. |
| Q7 | **`limitedParallelism(1)`만으로는 부족 [확인+제안].** | OS 등록/취소 결과와 DB 전이를 key+예정 시각 기준으로 직렬화하고 DAO 트랜잭션·유니크 제약으로 R1을 보장한다. 리시버는 같은 전이 함수를 사용하되, 발화와 차기 등록이 역순 도착해도 둘 다 올바르게 반영되는 멱등 규칙이 필요하다. 앱 종료 전 미기록 가능성은 별도 `UNKNOWN`으로 다룬다. |
| Q8 | **알람 상태는 별도 화면으로 시작 [제안].** | 동기화는 원격 전송 시도, 알람은 기대 발생·기기 권한·지연을 다룬다. 각 화면에 명확한 게이트 정의와 `측정 데이터 없음` 상태를 두고, 홈 메뉴에서 각각 진입한다. |
| Q9 | **DAO 전이 계측 테스트와 마이그레이션 테스트가 필요 [제안].** | R1 중복 등록, R2/R3 기한 경계, R4 늦은 발화·orphan·미리보기, 재부팅 불확실성, 가지치기, 계정 격리는 순수 함수만으로 검증되지 않는다. `AppDatabaseMigrationTest.kt:19`의 6→7 경로를 7→8 연쇄로 확장한다. |

### 8.3 수정된 step 구성안

| step | name | 내용 |
|---|---|---|
| 선행 | `recurring-alarm-fix` | 오늘 발생분 선택, 발생별 알람/제목 적용, 과거 시각 처리, 리시버 발화 직후 재무장, 회귀 테스트. Phase 24 계측 전에 별도 수정. |
| 0 | `alarm-log-entity` | v8 Entity·DAO·마이그레이션·Hilt 제공. 계정 범위, 세대 식별자, 등록 결과/불확실 상태, key별 PENDING 유니크 제약과 전이 계측 테스트. 선행 Room 6→7 테스트 브랜치 반영 후 작업. |
| 1 | `alarm-policy` | 등록/취소/발화·대사 판정, exact/inexact 임시 기준, 0건 표기와 게이트 집계 테스트. |
| 2 | `scheduler-hooks` | OS 등록 성공/실패·모드 반환, 과거 트리거·명시적 취소 결과, 순서 보장된 기록 연결. |
| 3 | `receiver-hooks` | 예정 시각/세대 매칭, 실제 리시버 도달·게시 결과, 미리보기 제외, 발화/다음 회차 등록의 순서 안전성. |
| 4 | `expected-occurrence-audit` | 일정 전개·Todo 기대 발생 스냅샷, 제한 구간 감사, 재부팅/시간 변경 불확실성 분리, 대사 진입점. **이 step 전까지 PRD 5% 게이트 판정 비활성.** |
| 5 | `status-screen` | 알람 전용 화면. 일정·Todo 주 게이트와 리마인더 참고 지표, 권한 차단·지연·미확인 구간, 측정 데이터 없음 표시. |
| 6 | `docs` | PRD·TechSpec·ARCHITECTURE·README의 측정 정의와 한계, TechSpec §5.2의 잘못된 `super.onReceive` 설명 정정. |

**남은 검증(§8 작성자):** Hilt 리시버 전환 시 기존 PendingIntent의 실제 발화 호환성, Doze에서 exact/inexact 지연 분포, 재부팅·시계 변경 분류 정확도는 기기 테스트가 필요하다. 클래스와 Intent identity가 유지되면 기존 PendingIntent가 같은 리시버로 전달될 것으로 예상하지만, 이는 정적 코드만으로 확증할 수 없다.

---

## 9. §8에 대한 재검토와 반론 (초안 작성자, 2026-10-02)

§8의 **[확인]** 표시 항목을 전부 코드와 대조했다. 기기 테스트는 하지 않았다.

### 9.1 사실 확인 결과

| §8 주장 | 대조 결과 |
|---|---|
| 8.1-1 오늘 발생분 누락 | 맞다. `AlarmScheduler.kt:37` + `EventRecurrence.kt:108`. |
| 8.1-1 발화 후 재무장 경로 없음 | 맞다. `AlarmReceiver.kt`는 스케줄러를 부르지 않고, 반복 일정 재등록은 `EventAlarmRefreshWorker`(일 1회)뿐이다. |
| 8.1-2 발생별 `hasAlarm`·제목 미적용 | 맞다. `EventRecurrence.kt:143-145`가 계산하지만 `AlarmScheduler.kt:27,50`은 마스터 값을 쓴다. `EventRepository.editOccurrence`(`:158-181`)가 발생별 `hasAlarm`을 실제로 저장하므로 도달 가능한 경로다. |
| 8.1-2 마스터 off + 발생 on 제외 | 맞다. `EventDao.kt:76`(`hasAlarm = 1`)과 `AlarmScheduler.kt:27`이 둘 다 걸러낸다. |
| 8.1-4 미리보기가 같은 리시버로 진입 | 맞다. `NotificationSettingsViewModel.kt:49-61`. |
| 8.1-5 과거 트리거에서 기존 알람 미취소 | 맞다. `AlarmScheduler.kt:49`, `:61`이 취소 없이 반환한다. 내일 일정을 과거로 옮기면 내일 알람이 그대로 울린다. |
| 8.1-6 복원은 미래 항목만 | 맞다. `AlarmRestoreWorker.kt:27-30`. |

**§8이 놓친 것 1건 [확인].** pull은 알람을 전혀 건드리지 않는다. `SyncRepository.mergeEvents`/`mergeTodos`(`:49-65`)는 DAO `upsert`만 하고 `AlarmScheduler`를 부르지 않는다(이 파일의 스케줄러 호출은 `:176-177` 로그아웃 취소뿐). 결과:

- 웹에서 만들거나 재로그인으로 복원된 **Todo는 재부팅·앱 업데이트 전까지 알람이 없다**(Todo를 다시 거는 워커는 `AlarmRestoreWorker`뿐이다).
- 복원된 일정은 다음 `EventAlarmRefreshWorker` 실행까지 알람이 없다.
- 웹에서 삭제·완료한 항목은 이 기기의 알람이 취소되지 않아 그대로 울린다.

재로그인하면 모든 Todo 알람이 사라지는 셈이라 §2.1보다 영향 범위가 넓다.

### 9.2 받아들이는 것

- 선행 수정 phase 분리(Q2), 리마인더는 주 게이트에서 제외(Q4), 별도 화면(Q8), 분모 0건은 "측정 데이터 없음"(8.1-7).
- `userId` 저장(Q6). nullable로 두고 리마인더는 null. 제목은 저장하지 않는다.
- `grace` 시작값 exact 15분 / inexact 2시간(Q3). 상수로 두고 실측 후 조정한다.
- DAO 전이 계측 테스트(Q9). `.github/workflows/android.yml:32-37`이 이미 에뮬레이터에서 `connectedDebugAndroidTest`를 돌리므로 추가 비용이 작다.
- 과거 트리거는 반환 대신 **취소**(8.1-5 전반부). 선행 수정에 넣는다.

### 9.3 동의하지 않는 것

**A. 세대 ID·직렬화 대신 `UNIQUE(alarmKey, expectedAt)` + "FIRED는 흡수 상태" (8.1-4, Q7).**
행의 정체를 (key, 예정 시각)으로 고정하면 순서 문제가 대부분 사라진다.

- 발화 기록은 `(key, expectedAt)` 행을 **현재 상태와 무관하게** `FIRED`로 만든다(`SUPERSEDED`·`MISSED`·`CANCELLED` 위에도 덮는다. 울린 것은 사실이다).
- 등록은 한 트랜잭션에서 ① 같은 key의 다른 `PENDING` 닫기 ② `(key, expectedAt)`을 `PENDING`으로 upsert(`FIRED`면 그대로 둔다).
- `ReminderReceiver.kt:56`처럼 "다음 회차 등록"이 "이번 회차 발화"보다 먼저 기록돼도, 둘은 다른 행을 건드리고 발화가 나중에 이전 행을 `FIRED`로 덮으므로 최종 상태가 같다.

세대 ID는 Intent·테이블·매칭에 필드를 하나 더 만들지만 (key, expectedAt)이 이미 그 역할을 한다. 반론이 있으면 **이 규칙으로 최종 상태가 틀어지는 구체적 순서**를 제시해 달라.

**B. 미리보기·구버전 Intent는 플래그·`UNKNOWN` 행 없이 "기록 안 함" (8.1-4).**
발화 기록 조건을 "Intent에 `expectedAt` extra가 있을 때만"으로 둔다. 미리보기(`NotificationSettingsViewModel.kt:57-59`)와 이 Phase 이전에 등록된 알람은 둘 다 extra가 없어 자동으로 빠진다. 초안 R4의 orphan `FIRED` 삽입은 철회한다. 구버전 알람은 다음 앱 시작·워커의 재등록(`FLAG_UPDATE_CURRENT`)으로 extra가 채워진다.

**C. 기대 발생 스냅샷·과거 구간 감사는 과하다 (Q1, step 4).**
과거 구간을 Room 현재 행으로 역산하면 그 사이의 수정·삭제 때문에 틀린다. §8도 이를 인정하고 "변경 시점 스냅샷"을 추가하자고 했는데, **그 스냅샷이 곧 `PENDING` 행이다.** 대신 전방 불변식 검사를 제안한다.

> 대사 시점에 알람 대상 항목(미완료·마감일 있는 Todo, 알람 켜진 일정) 각각에 대해 "지금 기준 다음 트리거"를 계산하고, 그 `(key, expectedAt)`의 `PENDING` 행이 없으면 `UNREGISTERED`로 기록한다.

이것으로 등록 누락(§9.1의 pull 공백, `runCatching`에 삼켜진 실패)이 잡힌다. 계산은 선행 수정에서 뽑아낼 순수 함수(예: `nextAlarmFor(event, now)`) 하나를 스케줄러와 공유한다. 같은 함수를 쓰므로 **그 함수 자체의 버그는 못 잡는다** — 그건 유닛 테스트 몫이다(§2.1은 테스트가 "오늘 발생분" 경계를 안 찔러서 생겼다). 감사를 독립 구현으로 이중화하자는 것이라면 그 비용의 근거를 듣고 싶다.

**D. 기한 지난 행을 사용자가 늦게 삭제·수정해도 `MISSED`가 맞다 (8.1-5 후반부).**
`expectedAt + grace`가 지났는데 `PENDING`이면 그 시점에 이미 안 울린 것이다. 이후의 사용자 조작은 그 사실을 바꾸지 않는다. "원인·조작 시각" 컬럼은 추가하지 않는다.

**E. 시계 변경 추적·`FIRED_LATE` 상태는 넣지 않는다 (8.1-6, Q3).**
지연은 `firedAt - expectedAt`으로 조회 시 계산하면 된다. 시계 변경은 개인 Dogfooding에서 드물고 판별 비용이 크다. 재부팅만 다룬다: 대사 때 `bootTime = currentTimeMillis - elapsedRealtime`을 구해 `expectedAt < bootTime`인 `MISSED`에 `missReason = BEFORE_BOOT`를 붙이고 주 게이트에서 빼 따로 보여 준다(꺼져 있었는지, 켜져 있었는데 안 울리고 나중에 재부팅됐는지는 구분하지 못한다는 한계를 화면에 적는다).

**F. 화면 명칭.** C의 불변식 검사가 들어가면 `누락률 = (MISSED + UNREGISTERED) / (FIRED + MISSED + UNREGISTERED)`로 PRD 게이트 수치라 부를 수 있다고 본다. "알림이 실제로 화면에 떴는가"는 앱이 관측할 수 없으므로 한계로 문서화하고, 게시 차단(`NOTIFICATIONS_OFF`·`CHANNEL_BLOCKED`)만 별도 표시한다.

### 9.4 재수정 step 구성안

**선행 phase `24-alarm-fixes`** (계측 없이 버그만. 계측을 먼저 넣으면 알려진 버그를 세는 데 2주를 쓴다)

| step | name | 내용 |
|---|---|---|
| 0 | `next-alarm` | 순수 함수 `nextAlarmFor(event, now)`: 오늘 발생분 포함, `trigger > now`인 첫 **알람 허용** 발생까지 전진, 발생별 제목·시각. JVM 테스트(오늘 전/후 경계, exdate, override 시각·알람 off, 마스터 off + 발생 on, UNTIL 종료). |
| 1 | `scheduler-fix` | `AlarmScheduler`가 위 함수 사용, 과거 트리거는 취소. `EventDao.getFutureAlarmedEvents`가 override 알람도 포함. |
| 2 | `rearm-and-pull` | `AlarmReceiver` Hilt 전환 + 반복 일정 발화 직후 재무장. pull 머지 후 변경된 행의 알람 등록·취소. |

**본 phase `25-alarm-reliability`**

| step | name | 내용 |
|---|---|---|
| 0 | `alarm-log-entity` | v8, `UNIQUE(alarmKey, expectedAt)`, nullable `userId`, `missReason`. 마이그레이션 테스트 7→8. |
| 1 | `alarm-logger` | 전이 트랜잭션(§9.3-A), 순수 판정 함수 JVM 테스트 + DAO 전이 계측 테스트. |
| 2 | `scheduler-hooks` | 등록·취소 기록, `setAlarmCompat`의 exact 여부 반환, Intent에 `expectedAt` extra. |
| 3 | `receiver-hooks` | 발화·게시 결과 기록(extra 있을 때만), 대사 + 불변식 검사 호출 지점. |
| 4 | `status-screen` | 알람 전용 화면. 주 게이트(일정·Todo), 리마인더·재부팅 구간·게시 차단·지연은 참고 수치. |
| 5 | `docs` | PRD·TechSpec·ARCHITECTURE·README. |

### 9.5 다시 묻는 것

1. §9.3-A 규칙이 깨지는 순서가 있는가?
2. §9.3-C의 전방 불변식 검사로 부족한 누락 유형이 있는가? (있다면 독립 감사가 그것을 어떻게 잡는지까지)
3. §9.1의 pull 공백: 머지 루프 안에서 행마다 `scheduleForEvent`/`scheduleForTodo`를 부르는 것과, pull 종료 후 `AlarmRestoreWorker`를 1회 예약하는 것 중 어느 쪽인가? 후자는 취소(웹에서 삭제·완료)를 못 다룬다.
4. 마스터 `hasAlarm=false` + 발생 override `hasAlarm=true`를 지원하는 쪽으로 정했다. 반대 근거가 있는가?

---

## 10. §9에 대한 재답변 (§8 작성자, 2026-10-02)

§9.1의 **pull 이후 알람 미조정**은 내가 놓친 결함이다. `SyncRepository.kt:49-65`에서 Room만 upsert하고 `AlarmScheduler`를 호출하지 않는다는 지적에 동의한다. 아래는 §9.5의 네 질문에 대한 답이다. 코드를 다시 대조했으며 기기에서 실행하지는 않았다.

### 10.1 A: `(alarmKey, expectedAt)` 유니크만으로는 순서 문제가 끝나지 않는다

**구체적인 반례 [설계 규칙으로 도출].** 같은 일정 key의 트리거를 A=내일 09:00에서 B=내일 10:00으로 연달아 변경한다고 하자. AlarmManager 호출은 `set(A)` → `set(B)` 순서로 성공해 실제 OS 슬롯은 B다. 두 기록을 `scope.launch`로 따로 던진 뒤 DB 실행 순서가 `recordScheduled(B)` → `recordScheduled(A)`가 되면, §9.3-A의 트랜잭션 규칙은 B를 `SUPERSEDED`로 닫고 A를 `PENDING`으로 남긴다. **최종 DB=A, OS=B**다. `UNIQUE(key, expectedAt)`과 `FIRED` 흡수 상태는 이 순서를 막지 못한다. `AlarmScheduler.kt:66-86`은 같은 key의 `PendingIntent`를 재사용하므로 실제 덮어쓰기도 가능하다. 취소와 재등록의 기록이 역순인 경우에도 DB가 취소 상태, OS는 예약 상태가 된다.

§9.3-A가 해결하는 **직전 발화와 다음 회차 등록의 서로 다른 시각** 경쟁은 인정한다. 따라서 세대 ID를 모든 경우에 추가할 필요는 없다. 대신 key별 OS 작업과 기록의 순서를 보존해야 한다. 최소안은 스케줄러 호출 시점에 key별 증가 `revision`을 발급해 DB 전이가 **낡은 revision의 등록/취소 기록을 거부**하게 하는 것이다. 이 값은 프로세스 재시작 후에도 비교 가능하도록 영속화하거나, 더 단순하게 key별 직렬화된 작업 큐에서 OS 작업과 DB 기록을 함께 수행해야 한다. `limitedParallelism(1)`만으로 호출 순서가 보장된다고 가정하지 말고 위 A→B 역순 로그 테스트로 검증하자. OS 등록 성공 후 DB 실패·프로세스 종료의 틈은 어느 안에서도 남으므로, 이 경우는 미확인 상태/후속 대사로 다뤄야 한다.

**같은 시각의 재등록 [설계 주의].** `(key, expectedAt)`은 *기대 발생*의 식별자로는 충분하다. 그러나 같은 시각을 유지하면서 제목·알람 설정·계정을 바꾼 등록까지 별개의 OS 시도로 구분하지는 않는다. 로그 목적이 기대 발생의 도달 여부라면 재등록 시도별 행은 필요 없지만, 상태 화면에서 이를 등록 성공 횟수나 OS 상태 확인으로 해석하면 안 된다.

### 10.2 B: extra만으로 발화를 제외하는 방안은 수용, 미리보기 재무장은 별도 차단

`expectedAt`이 없는 Intent를 게이트에서 제외하는 간소화에 동의한다. 구버전 알람과 미리보기의 발화 행을 억지로 만들 필요는 없다. 다만 `ReminderReceiver.kt:53-56`은 **extra 검사 전** `scheduleNext`를 호출한다. `NotificationSettingsViewModel.kt:49-61`의 미리보기에는 `expectedAt`이 없으므로, 예를 들어 21:01에 미리보기를 누르면 오늘 21:00 알람이 실제로 늦게 발화하기 전인데도 내일 21:00으로 OS 슬롯을 교체할 수 있다. §3.2 R2는 grace 안의 오늘 행을 `SUPERSEDED`로 닫아 실제 누락을 감춘다.

미리보기 Intent에 명시적 `EXTRA_PREVIEW`를 넣고 리시버에서 **발화 기록뿐 아니라 자동 재무장도 건너뛰자**. 기존 구버전 알람은 preview가 아니므로 발화 기록만 제외하고 차기 회차는 재무장한다. 이 구분이 없으면 extra 없는 구버전 발화와 미리보기를 같은 동작으로 처리해야 한다.

### 10.3 C: 전방 불변식 검사는 유용하지만, 과거 누락은 복원하지 못한다

**구체적인 반례 [설계 규칙으로 도출].** 오늘 09:00 반복 일정 등록이 실패해 `PENDING` 행이 없고, 앱·워커가 내일 08:00까지 다시 돌지 않았다고 하자. 이때 `nextAlarmFor(event, now)`는 내일 09:00을 반환한다. 전방 검사로 내일의 `UNREGISTERED`는 만들 수 있지만 **오늘 09:00 기대 발생은 이미 계산 범위 밖**이므로 오늘의 누락을 기록할 방법이 없다. 마감 Todo도 오늘 09:00을 지나면 `nextAlarmFor(todo, now)`가 null이 되어 같은 문제가 생긴다. 특히 `EventAlarmRefreshWorker.kt:34-41`은 주기 작업이라 각 기대 시각 전에 반드시 실행된다는 보장이 없다.

`PENDING`은 *등록 기록*이지 독립적인 기대 발생 스냅샷이 아니다. 스케줄러가 `runCatching`으로 실패를 삼키거나 잘못된 시각을 계산해 등록하지 않은 경우, 바로 그 `PENDING`이 없다(`AlarmScheduler.kt:25-52`). 이 점 때문에 §8에서 별도 기대 기록을 제안했다. 다만 전체 과거 Room 역산이나 별개 RRULE 엔진은 필요 없다. **최소안 [제안]:** 생성·수정·pull 시 Room에 기대 발생의 `key + expectedAt + userId + 활성 구간`을 OS 등록 결과와 독립적으로 저장하고, 반복은 짧은 전방 구간만 채운다. 등록 성공 여부는 별도 상태로 둔다. 다음 대사가 늦어도 이미 저장한 오늘 기대분을 판정할 수 있다. 스케줄러와 기대 계산이 같은 순수 함수를 공유하므로 **계산 함수 자체의 버그는 못 잡는다**는 §9의 지적에 동의하며, 날짜 경계 테스트로 보완한다. 이 기록 비용이 Phase 24 범위를 넘는다면 전방 검사만 먼저 구현하되 수치 이름은 `관측된 등록·도달 누락률`로 제한하자.

### 10.4 pull 처리: 변경된 각 행을 upsert 직후 조정한다

`SyncRepository.mergeEvents`/`mergeTodos`가 **실제로 로컬에 반영한 행에 한해** 같은 key의 알람을 등록·취소하는 안을 택한다. 웹에서 완료·삭제한 Todo와 `hasAlarm`을 끈 일정의 기존 PendingIntent까지 취소해야 하므로 미래 행만 조회하는 `AlarmRestoreWorker` 1회 예약은 대체안이 아니다. 원격 문서가 로컬보다 오래되어 upsert하지 않은 경우에는 알람도 건드리지 않는다. 다수 변경 시에는 대상 key를 모아 머지 후 처리해도 되지만, 그 작업을 워커로 넘길 경우 삭제·완료된 ID도 빠짐없이 전달해야 한다. `AuthRepository.kt:38-59`의 자동 로그인·로그인 경로가 모두 `pullAll`을 호출하므로 이 경로가 재로그인 복원도 해결한다. 알람 조정 실패를 또 조용히 삼키지 말고 진단에 남겨야 한다.

### 10.5 마스터 off + 발생 on: 지원에 동의

`EventOverride.hasAlarm`은 개별 발생의 유효 플래그로 설계되어 있다(`EventRecurrence.kt:143-145`). 마스터 off에서도 개별 발생 on을 허용하면 동작이 일관된다. `EventDao.kt:74-80`의 조회와 `AlarmScheduler.kt:27-29`의 조기 반환을 함께 고치고, 마스터 off/발생 on, 마스터 on/발생 off 모두 테스트해야 한다. **조회에서 `rrule IS NOT NULL`을 무조건 모두 가져오는 간단한 안은 정확성은 지키지만 비용이 늘 수 있으므로**, 실제 데이터량을 본 뒤 최적화하자.

### 10.6 §9.3-D~F에 대한 정리

- **D — 늦은 삭제/수정:** `expectedAt + grace` 이전에 발화하지 않았다면 그 뒤 사용자가 삭제해도 당시 미도달이었다는 결론에 동의한다. 다만 DB에 발화 기록이 늦게 도착하는 경쟁은 별도로 해결해야 한다.
- **E — 재부팅:** `BEFORE_BOOT`는 `DEVICE_OFF`의 증거가 아니라 *현재 부팅보다 오래된 기대 시각*이라는 관찰 라벨로 쓰면 된다. 부팅 시각과 복원 워커 실행 사이에 지나간 알람(`expectedAt > bootTime`)도 OS 재등록 전이어서 일반 `MISSED`와 구분해야 한다. 벽시계 변경 시 `currentTimeMillis - elapsedRealtime` 추정치는 흔들릴 수 있다는 한계를 화면에 적는다.
- **F — 5% 게이트:** `UNREGISTERED`를 미래 시각에 발견한 즉시 누락 분자에 넣으면 예정 시각 전에 등록·복구해도 실패로 남는다. **`expectedAt + grace`가 지난 기대 발생만** 분모·분자에 포함하고, 해소된 `UNREGISTERED`는 누락에서 빼야 한다. 또한 알림 권한이 꺼져 모든 리시버가 발화했어도 §9의 공식은 0% 누락이 된다. 이 수치는 앱의 **기대 알람 대비 리시버 미도달률**로는 유효하지만, PRD의 사용자 관점 `알림 누락률`과 같다고 표기하려면 게시 차단을 실패로 포함할지 별도 게이트로 볼지 먼저 결정해야 한다. `notify()` 성공은 실제 화면 표시나 사용자 열람의 증거가 아니라는 한계도 유지한다.

**수정 권고:** §9.4의 선행 버그 수정·pull 조정·DAO 테스트 구성은 수용한다. 신뢰성 phase의 `alarm-logger`에는 A의 순서 반례와 미리보기 재무장 테스트를, `receiver-hooks`에는 preview 구분을, `status-screen`에는 성숙한 기대 발생만 집계하는 규칙과 게이트 명칭을, 필요하면 별도 `expected-occurrence-journal` step을 추가하자. 위 반례를 해결한 뒤에도 §9.4처럼 다음 phase로 번호를 옮길지는 기존 `phases/index.json`과 PRD의 Phase 24 명칭을 함께 맞춰 결정하면 된다.

---

## 11. §10에 대한 답과 합의안 (초안 작성자, 2026-10-02)

§10의 반례 두 개(A→B 역순 기록, 전방 검사의 과거 공백)는 둘 다 성립한다. 받아들이고 설계를 고친다. 아래 [확인]은 코드 대조, [설계]는 규칙에서 도출한 것이다.

### 11.1 순서 (§10.1) — 반례 수용, 해법은 revision 대신 "OS 호출과 기록 투입을 한 락 안에서"

A→B 역순은 맞다. 다만 원인을 더 좁힐 수 있다. 같은 스레드에서 연달아 부르면 `launch` 순서가 곧 큐 순서라 뒤집히지 않는다. 뒤집히는 것은 **두 스레드가 같은 key를 동시에 등록할 때**이고(예: 사용자 수정 `EventRepository.save` ↔ 오래된 엔티티를 들고 있는 `EventAlarmRefreshWorker.kt:26`), 이때는 기록뿐 아니라 **OS 슬롯 자체가 이미 경쟁 상태**다. 지금 코드에도 있는 문제다 [확인: `AlarmScheduler`에 동기화 없음].

해법 [설계]:

- 스케줄러의 "OS 등록/취소 + 기록 작업 투입"을 하나의 `synchronized` 블록에 넣는다. 투입은 `Channel(UNLIMITED).trySend`, 소비자는 코루틴 1개.
- 그러면 **OS 적용 순서 = 채널 투입 순서 = DB 적용 순서**가 된다. 영속 revision은 필요 없다.
- 리시버의 발화 기록은 채널을 거치지 않아도 된다(`FIRED` 흡수 규칙으로 순서 무관).
- OS 성공 후 DB 기록 전에 프로세스가 죽는 틈은 §11.3의 기대 행 동기화가 다음 대사 때 메운다.

오래된 엔티티로 OS 슬롯을 되돌리는 경쟁(워커가 읽은 뒤 사용자가 수정)은 락으로도 남는다. 워커 쪽이 등록 직전에 행을 다시 읽는 것으로 줄일 수 있지만 선행 phase 범위에는 넣지 않고 한계로 적는다. 다음 일일 워커가 바로잡는다.

### 11.2 미리보기 (§10.2) — `EXTRA_PREVIEW` 수용, 그리고 같은 구멍이 앱 시작에도 있다

미리보기가 재무장을 건너뛰어야 한다는 데 동의한다. 그런데 §10.2의 시나리오(21:00 알람이 지연 중인데 21:01에 `scheduleNext`가 내일로 교체)는 **미리보기만의 문제가 아니다.** `LSyncApplication.kt:38`의 `syncAll()`과 `AlarmRestoreWorker.kt:33`도 같은 교체를 한다 [확인]. 플래그로는 이쪽을 못 막는다.

그래서 규칙을 고친다 [설계]. **재등록이 닫는 행의 `expectedAt <= now`이면 grace와 무관하게 `MISSED`다.** 우리가 그 알람의 OS 슬롯을 지웠으므로 더는 울릴 수 없다. 직전에 실제로 울렸다면 `FIRED` 흡수 규칙이 덮는다. grace는 대사(R5)와 사용자 취소(R3)에만 적용한다.

### 11.3 기대 기록 (§10.3) — 수용. "다음 1개 검사"를 "48시간 기대 행 동기화"로 넓힌다

과거 공백 반례는 맞다. §10.3의 최소안(등록 결과와 독립된 기대 기록 + 짧은 전방 구간)을 받되, 별도 저널 테이블 없이 `alarm_log` 한 테이블로 합친다.

- 핵심 연산은 `syncExpected(key, userId, expectedSet)` 하나다. 한 트랜잭션에서 ① `expectedSet`에 없는 그 key의 미종결 행을 닫고(§11.2 규칙) ② `expectedSet`의 각 시각을 upsert한다(`FIRED`는 그대로).
- `expectedSet` = now ~ now+48h 안의 알람 대상 발생 전부. 일정은 기존 `expandEvent`로 전개한다. Todo는 `{마감일 09:00}` 또는 공집합, 리마인더는 `{다음 트리거}`.
- 행에 `registered`(OS 등록 성공 여부)를 따로 둔다. 스케줄러가 OS 호출 결과로 채운다. **행 생성은 OS 호출 성공에 의존하지 않는다** — `runCatching`에 삼켜진 실패도 행이 남아 기한 후 `MISSED`가 된다.
- 호출 지점: 스케줄러의 등록·취소(해당 key), 대사(전 항목). 대사는 앱 시작·일일 워커·복원 워커·화면 진입에서 돈다.
- R1("key당 PENDING 1행")은 폐기한다. 48시간 안에 반복 발생이 여러 개면 미종결 행도 여러 개다. OS 슬롯이 하나라는 제약은 `registered`가 표현한다.

부수 효과: 기대 집합은 `expandEvent`, OS 등록 시각은 `nextAlarmFor`로 **서로 다른 코드 경로**에서 나온다. §2.1 같은 계산 버그가 다시 생기면 "기대 행은 있는데 `registered`가 안 붙는" 형태로 드러난다. §9.3-C에서 못 잡는다고 했던 유형의 일부가 잡힌다.

남는 공백: 48시간 넘게 아무 대사도 돌지 않으면 그 뒤 발생은 행이 없다. 일일 워커가 있으므로 수용한다.

### 11.4 pull (§10.4) — 동의, 구멍 하나 추가

upsert한 행마다 조정하는 안으로 간다. 단, 지금의 `scheduleForTodo`·`scheduleForEvent`는 `deletedAt`을 보지 않는다 [확인: `AlarmScheduler.kt:27`, `:56`]. pull로 들어온 tombstone을 그대로 넘기면 **삭제된 항목에 알람을 건다.** 두 함수가 `deletedAt != null`이면 취소하도록 선행 phase에서 같이 고친다.

### 11.5 게이트 (§10.6-F) — 수용, 게시 차단은 누락에 넣는다

- 기한(`expectedAt + grace`)이 지났거나 `FIRED`인 행만 집계한다. `UNREGISTERED` 상태는 없앤다(§11.3에서 `registered = 0`인 미종결 행으로 대체되고, 기한 전에 등록되면 자연히 해소된다).
- **게시 차단(`NOTIFICATIONS_OFF`·`CHANNEL_BLOCKED`)은 누락으로 센다.** 사용자 관점에서 알림이 안 뜬 것이고, 화면에 원인이 따로 보이므로 조치할 수 있다. 이렇게 해야 PRD의 "알림 누락률"이라 부를 수 있다.

```
누락률 = (MISSED + BLOCKED) / (FIRED + MISSED)     ← 일정·Todo만, BEFORE_BOOT 제외
        BLOCKED ⊂ FIRED (리시버는 돌았으나 게시 불가)
```

- `notify()` 호출 성공이 실제 표시를 보증하지 않는다는 한계는 문서와 화면에 적는다.
- §10.6-E의 "부팅~복원 워커 사이" 구분은 넣지 않는다. 창이 10초 남짓이고(`BootReceiver.kt:20`) 판별 근거가 없다. `BEFORE_BOOT`는 관찰 라벨이라는 표현은 수용한다.

### 11.6 합의된 구성

**`24-alarm-fixes`** (버그 수정)

| step | name | 내용 |
|---|---|---|
| 0 | `next-alarm` | 순수 함수 `nextAlarmFor(event, now)`: 오늘 포함, `trigger > now`인 첫 알람 허용 발생, 발생별 제목·시각. 경계 JVM 테스트(오늘 전/후, exdate, override 시각·알람 on/off 양방향, UNTIL). |
| 1 | `scheduler-fix` | 스케줄러가 위 함수 사용. 과거 트리거·`deletedAt` 행은 취소. OS 호출을 key 경쟁에서 보호하는 락. `getFutureAlarmedEvents`가 override 알람 포함. |
| 2 | `rearm-and-pull` | `AlarmReceiver` Hilt 전환 + 반복 일정 발화 직후 재무장. 미리보기 `EXTRA_PREVIEW`는 재무장 생략. pull이 upsert한 행의 알람 조정. |

**`25-alarm-reliability`** (계측)

| step | name | 내용 |
|---|---|---|
| 0 | `alarm-log-entity` | v8. `UNIQUE(alarmKey, expectedAt)`, nullable `userId`, `registered`, `exact`, `missReason`, `posted`·`skipReason`. 마이그레이션 테스트 7→8. |
| 1 | `alarm-logger` | `syncExpected` 트랜잭션, 닫기 판정 순수 함수, 순서 채널. JVM 테스트 + DAO 계측 테스트(A→B 역순, 지연 중 재등록, 발화/차기 등록 역순). |
| 2 | `scheduler-hooks` | 락 안에서 OS 호출 + 기록 투입, `setAlarmCompat` exact 반환, Intent `expectedAt` extra. |
| 3 | `receiver-hooks` | 발화·게시 결과 기록(extra 있을 때만), 대사(48시간 기대 행 동기화 + 기한 경과 닫기 + `BEFORE_BOOT`) 호출 지점. |
| 4 | `status-screen` | 알람 전용 화면. 주 게이트(§11.5), 참고 수치: 리마인더·재부팅 구간·지연·차단 원인. 0건은 "측정 데이터 없음". |
| 5 | `docs` | PRD·TechSpec·ARCHITECTURE·README, TechSpec §5.2 `super.onReceive` 정정. |

### 11.7 남은 이견과 미검증

- 이견은 §11.1의 순서 보장 수단(락+채널 vs 영속 revision) 하나다. 락+채널이 깨지는 순서가 있으면 그것만 답해 달라. 없으면 합의로 본다.
- 기기 검증이 필요한 것: Hilt 전환 후 기존 PendingIntent 발화, Doze에서의 exact/inexact 지연 분포(grace 15분/2시간의 타당성).

---

## 12. §11 재확인 (§8·§10 작성자, 2026-10-02)

**순서 수단에 관한 이견은 해소한다.** 단일 프로세스에서 같은 key의 모든 OS 등록·취소가 *같은 락*을 지나고, 락 안에서 성공 결과를 `Channel`에 넣으며, 소비자가 하나이고 전이 트랜잭션이 성공한다는 조건이면 OS 적용 순서와 DB 적용 순서가 같다. 이 조건 아래에서는 §10.1의 A→B 역순 기록 반례가 성립하지 않는다. 영속 revision은 필수가 아니다. `trySend` 실패를 확인하고, 소비자 예외로 채널이 멈추지 않도록 운영 계약을 명시하면 된다. **이 결론은 정적 설계 판단**이며 동시 실행 테스트는 아직 없다.

다만 §11의 합의안을 구현 지시로 쓰기 전에 다음 네 가지를 명시해야 한다.

1. **락은 오래된 입력을 새 입력으로 만들지 않는다 [코드 확인+설계].** `EventAlarmRefreshWorker.kt:25-26`은 DAO에서 읽은 `EventEntity`를 나중에 스케줄러에 넘긴다. 사용자가 그 사이에 일정을 수정해 새 시각 B를 등록한 뒤 워커가 오래된 시각 A를 등록하면, 락과 채널은 **정확히 A를 최종 상태로 기록**한다. OS·DB 일치는 지키지만 실제 Room의 최신 일정과는 불일치하고, 다음 주기 워커까지 잘못된 알람이 남을 수 있다. 이 문제를 선행 수정 범위에서 미루면 알려진 알림 누락/오발송을 남긴다. 해당 key 작업 시 DAO의 현재 행을 재조회해 유효 시각을 계산하거나, 전달받은 엔티티의 `updatedAt`이 최신인지 확인한 후 적용하자. Todo·pull 경로도 같은 기준이 필요하다. `syncExpected` 대사 역시 오래된 스냅샷이 새 기대 집합을 덮지 않도록 해야 한다.
2. **락+메모리 채널은 프로세스 종료를 견디는 기록이 아니다 [설계].** `set(B)` 성공 → `trySend(record B)` 성공 → 소비 전 프로세스 종료 순서에서는 OS=B지만 DB의 `registered`는 이전 값이다. §11.3의 다음 대사는 *기대 행*을 만들 수 있으나 AlarmManager에 B가 실제 등록돼 있는지는 그 행만으로 증명하지 못한다. 재시작 대사에서 미래 알람을 실제로 다시 등록하고 그 성공 결과를 기록하거나, `registered`를 `UNKNOWN`으로 표시해 미확인 구간을 수치에서 분리해야 한다. `revision`도 이 내구성 문제를 단독으로 해결하지 못한다.
3. **기대 행과 등록 결과의 갱신 순서를 고정해야 한다 [설계].** §11.3의 `syncExpected`와 §11.1의 채널 기록이 서로 다른 경로에서 같은 key를 쓰면, `recordScheduled(B)` 이후 오래된 기대 집합 `{A}`의 `syncExpected`가 실행돼 B를 닫을 수 있다. `UNIQUE(key, expectedAt)`으로도 방지되지 않는다. 기대 집합 계산에 사용한 Room 행의 버전(`updatedAt`)을 확인하거나, key별 동일 직렬 경로 안에서 최신 행 조회 → 기대 집합 동기화 → OS 등록/취소 → 결과 기록을 하나의 논리 작업으로 처리하자. OS 호출과 Room 트랜잭션은 원자적으로 묶을 수 없으므로 중간 실패 상태의 표시도 필요하다.
4. **48시간 창의 미관측 구간을 게이트와 분리해야 한다 [설계].** `EventAlarmRefreshWorker.kt:34-43`의 `PeriodicWorkRequest`는 정확히 매일 실행한다는 약속이 아니다. 기기가 오래 꺼져 있거나 작업이 지연되어 48시간 넘게 대사가 없으면 그 뒤의 기대 발생은 기록되지 않는다. `BEFORE_BOOT`로 분류되는 일부는 제외할 수 있지만, 나머지 빈 구간은 성공 건수로도 실패 건수로도 세지 않는다. 화면에 `마지막 기대 집합 갱신 시각`과 **측정 가능 구간/미관측 구간**을 표시하고, 2주 게이트는 필요한 구간이 충분히 관측됐을 때만 판정하자. 48시간이라는 길이는 정확성 보장이 아니라 저장·계산 비용과 커버리지의 선택이다.

**§11.2의 재등록 규칙은 조건을 하나 붙여 수용한다.** `expectedAt <= now`인 지연 중 알람을 *성공적으로* 교체·취소했다면 기존 행은 grace 안이어도 `MISSED`로 닫는 것이 맞다. `AlarmCompat.kt:12-18`의 OS 호출이 예외로 실패했다면 기존 슬롯이 어떻게 되었는지 확정하지 못하므로, 성공과 실패를 구분한 뒤 전이해야 한다. 미리보기의 `EXTRA_PREVIEW`는 여전히 필요하다.

**§11.5의 공식은 조건부로 수용한다.** `BLOCKED ⊂ FIRED`라서 중복 분모 없이 `(MISSED + BLOCKED)/(FIRED + MISSED)`로 계산할 수 있다. 다만 게시 차단과 수신 누락이 다른 원인임을 화면에서 나눠 보여 주고, `notify()` 성공이 실제 표시를 보증하지 않는다는 한계를 제목/설명에 적는다. 예정 시각+grace 이전의 미래 기대 행은 물론, 위 4번의 미관측 구간도 5% 판정에 끼워 넣지 않는다.

**권장 수정:** §11.6의 선행 `scheduler-fix`에 최신 Room 행 검증을 추가한다. 계측의 `alarm-logger`/`scheduler-hooks` 계약에는 `syncExpected`와 등록 결과의 key별 순서, 채널 실패·프로세스 종료 후 복구를 명시한다. `status-screen`에는 미관측 구간과 게이트 판정 가능 여부를 추가한다. 이 세 조건을 반영하면 §11.6의 단계 구조와 락+채널 방식에 동의한다.

---

## 13. §12 수용과 최종 합의 (초안 작성자, 2026-10-02)

§12의 네 조건을 모두 받아들인다. 1~3번은 따로 풀지 않고 **"알람 쓰기 경로를 하나로 만든다"**는 한 가지 결정으로 같이 해결한다.

### 13.1 단일 경로 (§12-1·2·3)

모든 알람 변경은 `scheduleForEvent(entity)` / `scheduleForTodo(entity)` / `scheduleNext(type)`만 지난다. id만 받는 `cancelEvent(id)`·`cancelTodo(id)`를 외부에서 직접 부르는 경로(삭제·완료·로그아웃)는 엔티티를 넘기는 호출로 바꾼다. 스케줄러가 `deletedAt`·`isCompleted`·`hasAlarm`을 보고 스스로 취소한다.

락 안에서 하는 일은 순서대로 다음과 같다.

1. **신선도 검사.** key별 "마지막으로 적용한 `updatedAt`"을 메모리 맵에 둔다. 넘어온 엔티티의 `updatedAt`이 그보다 작으면 버린다. 워커가 오래된 스냅샷을 들고 와도 최신 등록을 되돌리지 못한다(§12-1). 스케줄러가 non-suspend라 DAO 재조회 대신 이 방식을 쓴다. 맵은 프로세스 수명이면 충분하다 — 경쟁하는 두 호출은 같은 프로세스 안에 있다.
2. 엔티티에서 48시간 기대 집합과 다음 트리거를 계산한다(순수 함수).
3. OS 등록 또는 취소. 성공·실패·exact 여부를 얻는다.
4. `(key, userId, 기대 집합, 등록 시각, OS 결과)`를 **메시지 1개**로 채널에 넣는다.

기대 집합과 등록 결과가 한 메시지라서 §12-3의 "기록 뒤에 오래된 기대 집합이 덮는" 순서가 생기지 않는다. `syncExpected`를 따로 부르는 경로는 없다.

**대사는 관찰이 아니라 재등록이다(§12-2).** 대사는 알람 대상 항목을 Room에서 읽어 위 경로로 다시 흘린다. 프로세스 종료로 기록이 빠졌거나 강제 종료로 OS 알람이 지워진 경우 모두, 다음 대사에서 실제로 다시 등록되고 그 결과가 기록된다. `registered = UNKNOWN` 상태는 두지 않는다. 지금의 `AlarmRestoreWorker` 로직을 앱 시작·일일 워커에서도 쓰는 것이다(현재 Todo는 재부팅 때만 복원된다).

**OS 호출 실패 시(§12의 §11.2 조건).** 등록이 예외로 실패하면 기존 행을 닫지 않는다. 기대 행은 `registered = 0`으로 남기고, 이전 슬롯의 행은 그대로 둔다.

채널 계약: `trySend` 실패는 Crashlytics에 남긴다. 소비자는 메시지마다 `runCatching`으로 감싸 한 건의 예외가 루프를 멈추지 않게 한다.

### 13.2 미관측 구간 (§12-4)

- 대사가 끝날 때 "기대 행이 채워진 끝 시각"(now + 48h)을 저장한다. 다음 대사 시작 시 now가 그 시각을 넘었으면 `[저장된 끝 시각, now]`를 미관측 구간으로 기록한다. 저장 위치는 v8 마이그레이션에 같이 넣는 작은 테이블 `alarm_coverage_gap(id, fromAt, toAt)`.
- 화면: 마지막 대사 시각, 집계 구간 내 미관측 시간 합계를 보여 준다. 미관측 구간이 있으면 통과/초과 문구 옆에 "미관측 구간 있음"을 붙인다. 판정 자체를 숨기지는 않는다.
- 48시간은 비용과 커버리지의 선택이라는 점을 TechSpec에 적는다.

### 13.3 최종 구성 (§11.6에서 바뀐 부분만)

| phase · step | 추가된 내용 |
|---|---|
| `24-alarm-fixes` 1 `scheduler-fix` | 락 + key별 `updatedAt` 신선도 검사. 외부 `cancel*(id)` 호출을 엔티티 경로로 일원화. |
| `24-alarm-fixes` 2 `rearm-and-pull` | 앱 시작에서도 전체 재등록(현 `AlarmRestoreWorker` 로직 공유). |
| `25-alarm-reliability` 0 `alarm-log-entity` | `alarm_coverage_gap` 테이블. |
| `25-alarm-reliability` 1 `alarm-logger` | 메시지 1개 = 기대 집합 + OS 결과. 채널 실패·소비자 예외 계약. OS 실패 시 기존 행 유지 테스트. |
| `25-alarm-reliability` 3 `receiver-hooks` | 대사 = 재등록 + 기한 경과 닫기 + 미관측 구간 기록. |
| `25-alarm-reliability` 4 `status-screen` | 마지막 대사 시각, 미관측 시간, 차단과 미도달을 원인별로 분리 표시. |

### 13.4 상태

설계 이견은 없다. 남은 것은 기기에서만 확인할 수 있는 두 가지다: Hilt 전환 후 기존 PendingIntent 발화, Doze에서의 지연 분포(grace 15분 / 2시간). 동시 실행 테스트는 `25` step 1의 계측 테스트로 작성한다.

알려진 한계로 남기는 것: 지연 중인 알람(`expectedAt <= now`, 아직 안 울림)을 재등록이 교체하면 그 알림은 사라지고 `MISSED`로 기록된다. `24`에서는 발화 여부를 알 수 없어 막지 못한다.

---

## 14. 최종 합의안 확인: 구현 전에 수정할 계약 (§8·§10·§12 작성자, 2026-10-02)

§13의 **락 안에서 OS 작업과 단일 메시지 투입 → 단일 소비자**, 최신 엔티티 확인, 기대 집합과 등록 결과의 결합, pull 이후 조정, 게시 차단 집계에는 동의한다. 실제 구현 커밋은 아직 없다(`git status`에는 이 핸드오프 디렉터리만 미추적). 아래 세 항목은 §13.4의 '설계 이견 없음'이라고 닫기 전에 바로잡아야 한다.

1. **로그아웃은 활성 엔티티를 `scheduleFor*`에 넘겨서는 취소되지 않는다 [코드 확인].** `SyncRepository.kt:171-185`는 활성 일정·Todo를 조회해 알람을 취소한 후 Room을 비운다. §13.1대로 id 기반 `cancel*`을 없애고 조회한 엔티티를 `scheduleForEvent`/`scheduleForTodo`에 넘기면 `deletedAt=null`, `isCompleted=false`, `hasAlarm=true`인 활성 행이므로 **취소 대신 재등록**된다. 삭제·완료는 tombstone/완료 상태를 전달하면 해결되지만 로그아웃은 원격 데이터를 삭제하지 않으므로 그런 상태를 만들면 안 된다. 동일한 단일 경로 안에 `CancelForSignOut(key, userId)` 같은 **명시적 취소 명령**을 두고, 취소 성공 후 Room을 비우는 순서를 유지하자. key별 `updatedAt` 메모리 맵은 계정 정리 후 초기화해야 한다.
2. **`FLAG_UPDATE_CURRENT`는 등록 성공 이전에 기존 PendingIntent의 extras를 바꾼다 [플랫폼 문서+코드 확인].** 현재 `AlarmScheduler.kt:66-86`과 `ReminderScheduler.kt:70-77`은 `getBroadcast(... FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE)`를 먼저 호출하고, 그 뒤 `setAlarmCompat`을 호출한다. Android 문서는 이 플래그가 같은 PendingIntent의 extras를 즉시 교체하며 `FLAG_IMMUTABLE`이어도 작동한다고 명시한다([Android PendingIntent API](https://developer.android.com/reference/android/app/PendingIntent)). A 시각 알람이 살아 있을 때 B 시각 Intent의 `expectedAt=B`로 기존 PendingIntent를 갱신하고 OS `set(B)`가 실패하면, §13.1의 '기존 행을 그대로 둔다'와 달리 **A 시각 발화가 B의 extra로 도착할 수 있다**. 이 경우 A는 거짓 `MISSED`, B는 거짓 `FIRED`가 된다. 실패 경로에서 이전 extras를 복구할 수 있는지 기기 테스트로 확인하거나, 예정 시각/세대를 PendingIntent의 identity(data URI 등)에 포함해 새 후보를 기존 슬롯과 분리한 뒤 성공한 경우에만 이전 PendingIntent를 취소하는 등록 절차를 설계하자. 후자는 전환 중 두 PendingIntent가 공존할 수 있으므로 중복 발화 방지도 함께 정해야 한다.
3. **미관측 구간이 있어도 '통과'를 띄우면 5% 게이트를 판정했다고 오해한다 [설계].** §13.2는 미관측 구간을 기록하면서도 통과/초과 판정은 유지한다. 예를 들어 기대 발생 100건 중 90건이 창 밖에 있어 기록되지 않고, 관측된 10건이 모두 성공하면 화면은 0% '통과'다. 실제 전체 누락률은 알 수 없다. 미관측 시간이 있는 집계 구간은 `관측 구간 0%`와 함께 **전체 게이트는 `판정 불가`**로 표시하자. 필요하면 명시적인 커버리지 임계값을 정하되, 그 기준이 없는 상태에서 PRD 5% 통과로 표시하면 안 된다. WorkManager의 실행 시각은 정확하지 않으며 시작 지연 10초도 복원 완료 시각을 보장하지 않는다([Android WorkManager 안내](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work)).

**결론:** §13의 큰 방향에는 동의한다. 위 1~3은 설계 취향이 아니라 로그아웃 기능과 측정 결과의 정확성에 직접 영향을 주므로, `24-alarm-fixes`와 `25-alarm-reliability` 작업 계약에 반영한 뒤 구현하는 것이 안전하다. 기기에서만 확인 가능한 PendingIntent 전환/실패와 Doze 지연은 별도 실측으로 남긴다.

---

## 15. §14 반영 (초안 작성자, 2026-10-02)

세 항목 모두 맞다. 1번은 §13.1에서 내가 틀렸다. 계약을 아래로 고친다.

### 15.1 로그아웃 — 명시적 취소 명령을 둔다 (§14-1 수용)

`SyncRepository.kt:176-177`이 넘기는 것은 활성 행이므로 `scheduleFor*`에 넣으면 재등록된다 [코드 확인]. §13.1의 "id 기반 취소 전면 제거"를 철회하고 다음으로 바꾼다.

- 스케줄러의 진입점은 **`scheduleFor*(entity)`와 `cancelForSignOut(kind, id)` 두 종류**다. 둘 다 같은 락·같은 채널을 지난다.
- 삭제·완료는 계속 엔티티 경로(tombstone·완료 상태를 넘김)를 쓴다. `cancelForSignOut`은 `clearLocalUserData()`에서만 부른다.
- 순서는 지금과 같다: 취소 → Room 비우기. 취소 뒤 그 key의 `updatedAt` 맵 항목을 지운다(재로그인 pull이 같은 `updatedAt`의 행을 다시 등록할 수 있어야 한다).
- 로그: 그 key의 미종결 행은 `CANCELLED`. `expectedAt <= now`인 행은 §11.2 규칙대로 `MISSED`.

### 15.2 extras 선갱신 — extra를 "등록에 성공한 행"일 때만 믿는다 (§14-2 수용, 해법은 다르게)

`FLAG_UPDATE_CURRENT`가 `set()` 이전에 extras를 바꾼다는 지적은 맞다 [코드 확인: `AlarmScheduler.kt:68-74`, `ReminderScheduler.kt:71-77` 모두 `getBroadcast` → `setAlarmCompat` 순]. `set(B)`가 실패하면 A 시각 발화가 `expectedAt = B`를 달고 온다.

PendingIntent identity에 시각을 넣어 슬롯을 분리하는 안은 받지 않는다. 한 key에 OS 알람이 둘 공존하는 전환 구간이 생기고, 그 중복 발화를 막는 장치가 또 필요하다. 대신 **귀속 규칙**으로 푼다.

- `registered`를 "그 행의 시각으로 OS 등록에 성공한 적이 있다"는 **붙으면 안 떨어지는 값**으로 정의한다(현재 슬롯 여부가 아니다).
- 발화 기록 시 extra의 `(key, E)` 행이 `registered = 1`이면 그 행을 `FIRED`로 한다.
- `registered = 0`이거나 행이 없으면 extra가 오염된 것이다. 그 key에서 `registered = 1`이고 `expectedAt <= now`이며 아직 `FIRED`가 아닌 가장 최근 행에 귀속한다. 그런 행도 없으면 기록하지 않는다.

§14의 예에 적용하면: `set(B)` 실패 → B 행은 `registered = 0`, A 행은 그대로. A 시각에 extra B로 발화 → B는 자격이 없으므로 A가 `FIRED`. B는 기한 후 `MISSED`(실제로 B 시각에는 안 울렸으니 맞다). 거짓 `MISSED`·거짓 `FIRED`가 생기지 않는다.

§11.1의 순서 무관 성질도 유지된다. 리마인더가 다음 회차를 먼저 등록해도 직전 행의 `registered`는 1로 남아 있으므로 extra로 정확히 귀속된다.

남는 부작용은 알림 문구다. 이 경우 A 시각 알림에 B 등록 시점의 제목이 표시될 수 있다. `set()` 실패 자체가 드물어 수용하고 한계로 적는다. 이 규칙은 `25` step 1의 계측 테스트 항목에 넣는다(OS 실패는 주입).

### 15.3 미관측 구간 — 전체 게이트는 "판정 불가" (§14-3 수용)

집계 구간 안에 미관측 구간이 하나라도 있으면 통과/초과 문구를 내지 않는다.

- 표시: `관측 구간 누락률 N%` + `전체 게이트: 판정 불가 (미관측 X시간)`.
- 미관측 구간이 없을 때만 통과/초과를 낸다. 커버리지 임계값은 두지 않는다 — 기준 없이 숫자를 정하는 것보다 0을 요구하는 쪽이 단순하다. 미관측은 48시간 넘게 대사가 없을 때만 생기므로 매일 쓰는 기기에서는 드물고, 생기더라도 7일 구간에서는 일주일 뒤 빠진다.
- 분모 0건은 종전대로 "측정 데이터 없음".

### 15.4 구성 변경 (§11.6 + §13.3에 더함)

| phase · step | 변경 |
|---|---|
| `24-alarm-fixes` 1 `scheduler-fix` | 진입점 2종(`scheduleFor*`, `cancelForSignOut`). 로그아웃 후 `updatedAt` 맵 정리. |
| `25-alarm-reliability` 0 `alarm-log-entity` | `registered`는 sticky. |
| `25-alarm-reliability` 1 `alarm-logger` | 발화 귀속 규칙(§15.2)과 테스트: OS 실패 주입, extra 오염, 로그아웃 취소. |
| `25-alarm-reliability` 4 `status-screen` | 미관측 구간이 있으면 "판정 불가". |

### 15.5 상태

§14의 세 항목을 반영했다. §15.2만 상대 제안과 다른 해법을 택했으니, 귀속 규칙이 틀린 행을 `FIRED`로 만드는 경우가 있으면 지적해 달라. 없으면 이 문서(§11.6 → §13 → §15 순으로 덮어씀)를 `24-alarm-fixes` step 파일의 근거로 쓴다.

---

## 16. §15.2 발화 귀속 규칙의 반례 (§8·§10·§12·§14 작성자, 2026-10-02)

§15.1의 명시적 로그아웃 취소와 §15.3의 `판정 불가` 표시는 수용한다. §15.2의 **`registered=1` 이력만으로 extra를 신뢰하는 규칙에는 동의할 수 없다.** 아래는 플랫폼 API의 extras 갱신 계약과 §15.2 규칙을 적용한 구체적인 순서다. 기기 재현은 아직 하지 않았다.

1. 같은 key의 A=오늘 09:00을 성공적으로 등록한다. A 행의 `registered=1`, OS 슬롯=A.
2. 사용자가 B=오늘 10:00으로 바꿔 성공적으로 등록한다. B 행의 `registered=1`, OS 슬롯=B. A의 성공 이력은 §15.2에 따라 지워지지 않는다.
3. 사용자가 다시 A=09:00으로 바꿔 성공적으로 등록한다. A의 `registered=1`, OS 슬롯=A. B 행은 `SUPERSEDED`여도 **`registered=1` 이력은 남는다**.
4. 09:00 전에 B=10:00으로 다시 변경한다. `PendingIntent.getBroadcast(... FLAG_UPDATE_CURRENT)`가 기존 OS 슬롯의 extra를 `expectedAt=B`로 갱신한 뒤 `set(B)`가 예외로 실패한다. §15.2의 OS 실패 규칙대로 현재 OS 슬롯=A, A 행은 유지, B의 이번 시도는 실패다. 하지만 B 행의 `registered=1`은 2번의 과거 성공 이력 때문에 그대로다.
5. 09:00에 실제 A 알람이 도착하며 extra는 B다. §15.2의 첫 규칙은 B 행의 `registered=1`만 보고 **B를 `FIRED`로 기록한다.** A는 `MISSED`가 된다. 실제 발화와 집계가 모두 뒤집힌다. `SUPERSEDED` 행을 제외하도록 바꾸어도 4번의 B 재시도 때 B를 다시 미종결로 만들면 같은 문제가 남는다.

따라서 `registered=1`은 **그 시각이 한 번 등록된 적 있다는 이력**이지, 이번 브로드캐스트를 발생시킨 현재 OS 슬롯의 증거가 아니다. 09:00 시각에 B=10:00이 발화했다는 판단은 `expectedAt > firedAt` 경계 검사만 추가해도 이 예에서는 거를 수 있다. 그러나 B를 08:00, A를 10:00으로 바꾼 동일 순서에서는 그 검사만으로는 구분되지 않는다. 현재 OS 슬롯/등록 세대와 과거 성공 이력을 분리하거나, 동일 PendingIntent의 extras가 실패 전에 바뀌지 않도록 등록 방식을 바꿔야 한다. 어느 쪽도 보장할 수 없는 발화는 `UNKNOWN`으로 분리해 게이트에서 제외하는 편이 거짓 `FIRED`보다 낫다.

**요청하는 수정:** §15.2 규칙을 구현 확정안으로 쓰지 말고, 위 A→B→A→B(마지막 실패) 순서를 DAO 상태 전이 테스트에 넣자. 특히 `registered=1`이 과거 성공 이력으로 남는 조건을 그대로 재현해야 한다. 이 반례를 해결하는 발화 식별 규칙 또는 PendingIntent 교체 절차가 정해지면 나머지 합의안은 유지할 수 있다. Android의 `FLAG_UPDATE_CURRENT`는 기존 PendingIntent의 extras를 교체하며 `FLAG_IMMUTABLE`이어도 예외가 아니라는 점은 [Android PendingIntent API](https://developer.android.com/reference/android/app/PendingIntent)에 명시되어 있다.

---

## 17. §16 반례 수용 — 발화 식별을 extra에서 "슬롯 원장"으로 옮긴다 (초안 작성자, 2026-10-02)

§16의 A→B→A→B(마지막 실패) 순서는 성립한다. sticky `registered`는 과거 이력일 뿐이라 지금 OS가 들고 있는 알람을 가리키지 못한다. §15.2 규칙은 철회한다. B < A 변형까지 포함해, **Intent extra를 발화 식별에 쓰는 한 이 문제는 닫히지 않는다** — extra는 `set()` 성공 여부와 무관하게 먼저 바뀌기 때문이다.

### 17.1 규칙

발화 식별의 근거를 extra가 아니라 **스케줄러가 성공한 OS 호출만 적는 슬롯 원장**으로 바꾼다.

- 원장: SharedPreferences `alarm_slots`, `alarmKey → expectedAt`. key당 값 하나(OS 슬롯이 key당 하나인 것과 같다).
- 쓰기는 스케줄러 락 안에서, **OS 호출이 성공한 직후에만** 한다.
  - `set(E)` 성공 → `slots[key] = E`
  - `cancel` 성공 → `slots[key]` 삭제
  - `set` 실패 → 원장을 건드리지 않는다(OS 슬롯도 이전 그대로다)
- 리시버는 `onReceive` **첫 동작으로** `slots[key]`를 읽어 지역 변수에 잡고 원장에서 지운다. 그 뒤에 재무장(`scheduleNext` 등)과 알림 게시를 한다. 발화 기록은 잡아 둔 값 E로 `(key, E)` 행을 `FIRED`로 만든다(흡수 규칙은 유지).
- 원장에 key가 없으면 기록하지 않는다. 이 Phase 이전에 등록된 알람이 여기에 해당한다. 미리보기는 `EXTRA_PREVIEW`로 원장 조회·재무장 모두 건너뛴다.
- Intent의 `expectedAt` extra와 sticky `registered`는 **없앤다.** `registered`는 "이 행의 시각으로 `set`이 성공했다"는 단순 플래그로 되돌린다(게이트 판정에는 쓰지 않고 진단 표시용).

### 17.2 §16 순서에 적용

| 단계 | OS 슬롯 | 원장 |
|---|---|---|
| 1. A 등록 성공 | A | A |
| 2. B 등록 성공 | B | B |
| 3. A 등록 성공 | A | A |
| 4. B 등록 시도, `set` 실패 (extra는 B로 바뀜) | A | A (변경 없음) |
| 5. 09:00 발화, extra = B | — | A를 읽고 삭제 → **A가 `FIRED`** |

B 행은 등록에 실패했으므로 기한 후 `MISSED`가 된다. A를 10:00, B를 08:00으로 바꾼 변형에서도 원장은 A라서 결과가 같다. 리마인더가 발화 직후 다음 회차를 등록하는 순서(§11.1)도, 리시버가 원장을 먼저 읽으므로 영향이 없다.

### 17.3 남는 틈 [설계]

- **`set` 성공과 원장 쓰기 사이의 프로세스 종료.** 둘 다 락 안의 연속된 동기 호출이고 `apply()`는 메모리에 즉시 반영된다. 디스크 반영 전에 죽으면 원장이 한 단계 낡는다. 다음 대사(앱 시작·워커)가 전부 재등록하면서 원장을 다시 쓴다(§13.1). 그 사이에 울린 알람은 낡은 시각으로 귀속될 수 있다 — 한계로 적는다.
- **`cancel`의 예외.** 실질적으로 발생하지 않는 경로로 보지만 확인한 것은 아니다. 실패 시 원장을 그대로 둔다.
- **알림 문구.** §16 4번 상황에서 A 시각 알림에 B 등록 시점의 제목이 표시될 수 있다(extra의 제목은 여전히 선갱신된다). 계측 정확성과는 무관하다.

### 17.4 테스트와 구성 변경

- `25` step 1 계측 테스트에 §16의 A→B→A→B(마지막 실패) 순서와 B < A 변형을 그대로 넣는다. OS 실패는 주입한다.
- `25` step 0: `registered`는 단순 플래그. step 2 `scheduler-hooks`: Intent extra 대신 슬롯 원장 쓰기. step 3 `receiver-hooks`: 원장 선(先)읽기.
- `24-alarm-fixes`는 이 쟁점과 무관하다. 변경 없음.

### 17.5 상태

- `24-alarm-fixes`: 합의됨. 진행 가능.
- `25-alarm-reliability`: §17.1에 반례가 없으면 확정. 반례가 있으면 그것만 답해 달라.
- 기기 실측 대기: Hilt 전환 후 기존 PendingIntent 발화, Doze 지연과 grace(15분 / 2시간), 48시간 기대 창의 충분성. 실측 전까지 화면의 5% 판정은 참고치다.

---

## 18. §17.1 슬롯 원장의 콜드 스타트 반례 (§8·§10·§12·§14·§16 작성자, 2026-10-02)

§17.1의 '리시버 `onReceive` 첫 동작에서 원장을 읽는다'는 순서만으로는 발화 시각이 보존되지 않는다. **리시버가 실행되기 전에 앱의 `Application.onCreate()`가 실행되기 때문이다**([Android Application API](https://developer.android.com/reference/android/app/Application)). 현재 `LSyncApplication.kt:34-39`는 시작 중 `reminderScheduler.syncAll()`을 동기 호출하고, `ReminderScheduler.kt:38-79`는 현재 시각을 기준으로 다음 트리거를 등록한다. 아래는 리마인더 A가 꺼져 있던 앱 프로세스를 깨운 경우다. 플랫폼 라이프사이클과 현재 코드에서 도출한 시나리오이며 기기 재현은 아직 하지 않았다.

1. 오늘 21:00 리마인더 A가 OS에 등록되어 있고 슬롯 원장은 `reminder:bible → A`다. 앱 프로세스는 죽어 있다.
2. 21:00 이후 OS가 A의 브로드캐스트를 배달하려고 앱 프로세스를 시작한다. `ReminderReceiver.onReceive` 전에 `LSyncApplication.onCreate()`가 실행된다.
3. `onCreate()`의 `syncAll()`이 `nextDailyTrigger(now, ...)`로 **내일 21:00 B**를 등록한다(`ReminderScheduler.kt:122-127`). OS 등록 성공 직후 §17.1 원장은 `reminder:bible → B`로 바뀐다.
4. 이제 A의 `ReminderReceiver.onReceive`가 시작된다. 첫 동작으로 원장을 읽어도 **B를 얻는다.** A 브로드캐스트를 B의 `FIRED`로 기록하고 B 원장 값을 지운다. 실제 A는 `MISSED`로 남고, 내일 B가 울릴 때는 원장이 없어 기록되지 않는다.

이는 앱 시작 재등록이 있는 한 흔한 콜드 스타트 경로다. `AlarmReceiver`도 §13.1의 앱 시작 전체 재등록이 리시버보다 먼저 실행되면 같은 문제가 생긴다. 원장이 '현재 OS 슬롯'을 나타낸다는 점과 브로드캐스트가 '이미 발행된 이전 슬롯'일 수 있다는 점을 분리해야 한다. 리시버의 첫 줄을 바꾸는 것만으로는 해결되지 않는다.

**수정 방향:** 자동 재등록이 미배달 브로드캐스트보다 먼저 원장을 덮어쓸 수 없게 순서를 제어하거나, 발화 Intent 자체에 등록 시점의 안정적인 식별자를 담아 이전/새 슬롯을 구분해야 한다. 후자를 택한다면 `FLAG_UPDATE_CURRENT`로 기존 Intent를 먼저 바꾸는 문제를 함께 해결해야 한다. 적어도 콜드 스타트 A 발화 → `Application.onCreate` B 재등록 → A 리시버 진입 순서를 §17.1의 계측 테스트와 실제 기기 테스트에 넣어야 한다. 이 반례가 해소되기 전에는 `25-alarm-reliability`의 발화 식별 규칙을 확정하지 않는 것이 맞다.

**진행 판단:** `24-alarm-fixes`의 step 초안 작성은 지금 진행해도 된다. 이 발화 식별 쟁점과 독립적인 버그 수정이기 때문이다. 현재 체크아웃 `test/room-migration-ci`의 `f1be882`에는 6→7 마이그레이션 테스트가 이미 있으며, `24-alarm-fixes`는 DB v8 변경이 없어 이 커밋을 `main`에 먼저 머지할 기술적 필요가 없다. v8을 추가하는 `25-alarm-reliability` 시작 전에는 해당 테스트 기반을 반영하면 된다. `main` 머지는 별도 통합 작업으로 다루자.
