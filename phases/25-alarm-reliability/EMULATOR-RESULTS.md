# Phase 25 — 에뮬레이터 검증 결과 (2026-10-12)

환경: Pixel_9 AVD, Android API 37 (`sdk_gphone16k_x86_64`, Play 이미지, root 불가), RAM 4GB(`-memory 4096`, 2GB에서는 LMK가 런처까지 종료해 테스트 불가), 디버그 빌드, 실제 계정 로그인.
시각 조작: `adb shell cmd alarm set-time <ms>` (+ `settings put global auto_time 0`). DB 확인: `run-as com.lsync.app cat databases/lsync.db*`.
테스트 일정은 모두 `TEST_*` 제목, 09:00 비종일·알림 켬. 종료 후 전부 삭제(tombstone)했다.

## 시나리오 결과

| # | 시나리오 | 결과 | 근거 |
|---|---|---|---|
| 1 | 기본 발화 (비정확 알람, 정확 알람 권한 없음) | 통과 | 09:00 기대 → 09:00:47 발화, `FIRED`·`posted=1`, 알림 게시 확인. 토큰 `ARMED`→`deliveredAt`→`RETIRED` |
| 2 | Doze (`deviceidle force-idle`, 화면 꺼짐, 배터리 분리) | 통과 | 깊은 Doze `IDLE` 상태에서 09:00:00.042 발화, `posted=1` (정확 알람) |
| 3 | 재부팅 | 부분 통과 | 부팅 후 `AlarmRestoreWorker` SUCCESS, 남은 알람을 새 토큰으로 재등록. **꺼진 동안 기대 시각이 지나는 경우(공백 기록)는 미검증** — 에뮬레이터 콜드 부팅이 시계를 실제 시각으로 되돌려 재현 불가 |
| 4 | 알림 권한 철회 (`pm revoke POST_NOTIFICATIONS`) | 통과 | `FIRED`·`posted=0`·`skipReason=NOTIFICATIONS_OFF` — 게시 차단 분자로 분리 기록 |
| 5 | 콜드 스타트 (프로세스 종료 후 발화) | 통과 | 09:00:00.026 `Start proc ... for broadcast AlarmReceiver`, 09:00:01.013 `FIRED`·`posted=1`. 시작 중 재등록이 수신 기록을 덮지 않음 |

## 관찰 사항

1. **일정 추가 UI에 시각 선택이 없다.** `ScheduleScreen.kt` `CreateEventDialog`는 비종일 일정의 시각을 항상 `09:00:00+09:00`으로 저장한다(편집 시에만 기존 시각 보존). 일정 알람 실사용의 선결 과제. → **해결:** 시작 시각 칩 + 공용 `ui/components/TimePickerDialog.kt` 추가. 에뮬레이터에서 생성 15:30·편집 16:45 저장과 알람 재등록(이전 기대 행 `SUPERSEDED`, 토큰 교체)을 확인.
2. **앱 시작마다 토큰이 반복 생성된다.** 시계 변경으로 앱이 시작될 때 같은 key가 1초 안에 3~4회 재등록됐다(매번 이전 토큰 `RETIRED`, 마지막 하나만 `ARMED` — 정합성은 유지). 알람 수 × 앱 시작 횟수만큼 `alarm_delivery_token`이 쌓이고 30일 가지치기에 의존한다. 같은 트리거면 재등록을 건너뛰는 최적화 여지. → **해결:** `AlarmRegistrar.reusableToken`(유닛 테스트 7케이스) — 같은 시각·모드로 `ARMED`이고 PendingIntent가 살아 있으면 재사용. 에뮬레이터에서 프로세스 재시작 3회 후에도 토큰 1개 유지, `force-stop` 후에는 복원 워커가 새 토큰으로 재등록함을 확인.
3. **시계를 뒤로 돌리면** 이미 `FIRED`된 기대 발생이 다시 미래가 되어 재등록·재발화(알림 2회)된다. `FIRED`는 흡수 상태라 집계는 1회지만, `markRegistered`가 FIRED 행의 `exact`를 덮어썼다(0→1). 관측 범위 끝(`coveredThroughAt`)도 실제 시각보다 앞에 남는다. 수동 시계 변경 분류는 미검증. → **`exact` 덮어쓰기 해결:** `markRegistered`가 `FIRED` 행을 건드리지 않음(계측 테스트 `reArmAfterDeliveryKeepsTheFiredMode`).
4. 기대 행이 48시간 창 밖(약 61시간 뒤 다음 트리거)에도 생성됐다 — 다음 트리거 1건은 창과 무관하게 기록하는 동작으로 보인다. → **의도된 동작으로 확인:** 기대 행이 없으면 `recordDelivery`가 수신을 집계하지 않는다. 누락 판정은 관측 범위 안에서만 하므로 게이트를 왜곡하지 않는다. DESIGN.md·TechSpec에 명시.

## 실기기에서 남은 것

- 제조사 배터리 최적화·Doze 실지연 분포 → 15분/2시간 유예와 48시간 창 확정, `REFERENCE_*` 해제 판단
- 꺼진 동안 기대 시각이 지나는 재부팅 공백 분류
- 수동 시계/시간대 변경 분류
- 레거시(토큰 없는) PendingIntent 발화 호환
