# Step 2: restore-refactor

## 배경

이 task는 "알람 라이프사이클 연결"이다.

- **Step 0**에서 `AlarmScheduler`에 엔티티 단위 메서드 `scheduleForEvent(event: EventEntity)`와 `scheduleForTodo(todo: TodoEntity)`를 추가했다. 이 메서드들이 트리거 시각(시간지정=시작시각 / 종일·Todo=마감일 09:00), 과거 시각 스킵, `hasAlarm=false`·완료·`dueDate==null` 시 cancel을 **모두 내부에서 처리**한다.
- **Step 1**에서 두 Repository(생성/수정/삭제/완료)에 그 메서드를 연결했다.

현재 `AlarmRestoreWorker`(재부팅 후 Room에서 미래 알람을 읽어 AlarmManager에 재등록)는 **트리거 시각을 자체적으로 인라인 계산**한다. 그런데 그 인라인 로직은 종일 일정·Todo를 **자정(00:00)** 으로 등록하고 과거 시각 스킵도 하지 않아, Step 0이 정한 **09:00 + 과거 스킵** 규칙과 **불일치**한다. 같은 항목이 라이브 경로(09:00)와 복원 경로(00:00)에서 다른 시각에 걸리는 드리프트가 생긴다.

이 Step은 복원 worker가 인라인 계산을 버리고 **Step 0의 메서드를 그대로 재사용**하게 하여 단일 진실 원천으로 통일한다.

## 읽어야 할 파일

먼저 아래 파일을 읽고 설계 의도를 파악하라:

- `docs/ARCHITECTURE.md` — 디렉토리 구조, 워커 위치
- `docs/TechSpec.md` — 5.1(일정/할 일 알람), 백그라운드 엔진
- `CLAUDE.md` — Floating Time, Hilt 패턴
- `app/src/main/java/com/lsync/app/notification/AlarmScheduler.kt` — **Step 0에서 추가된 `scheduleForEvent`/`scheduleForTodo` 시그니처를 먼저 확인하라.**
- `app/src/main/java/com/lsync/app/worker/AlarmRestoreWorker.kt` — **수정 대상.** 현재 인라인 트리거 계산 방식을 확인하라.
- `app/src/main/java/com/lsync/app/data/local/dao/EventDao.kt` — `getFutureAlarmedEvents(fromDate)` 쿼리
- `app/src/main/java/com/lsync/app/data/local/dao/TodoDao.kt` — `getFutureAlarmedTodos(fromDate)` 쿼리

이전 Step들에서 만들어진 `AlarmScheduler`의 새 메서드가 트리거 계산·과거 스킵·cancel을 모두 책임진다는 점을 이해한 뒤 작업하라.

## 작업

`AlarmRestoreWorker.doWork()`를 리팩터한다.

- DAO 조회는 그대로 둔다: `eventDao.getFutureAlarmedEvents(fromDate)`, `todoDao.getFutureAlarmedTodos(fromDate)` (`fromDate = LocalDate.now().toString()`).
- 각 `event`에 대한 **인라인 트리거 시각 계산(`OffsetDateTime.parse`/`atStartOfDay` 등)을 제거**하고 `alarmScheduler.scheduleForEvent(event)`로 대체한다.
- 각 `todo`에 대해서도 인라인 계산을 제거하고 `alarmScheduler.scheduleForTodo(todo)`로 대체한다.
- 더 이상 쓰지 않게 된 import(`OffsetDateTime`, `ZoneId` 등)는 정리한다.
- `Result.success()` 반환 유지.

리팩터 후 `doWork()`는 대략 다음 형태가 된다 (참고용 골격, 세부는 재량):

```kotlin
override suspend fun doWork(): Result {
    val fromDate = LocalDate.now().toString()
    eventDao.getFutureAlarmedEvents(fromDate).forEach { alarmScheduler.scheduleForEvent(it) }
    todoDao.getFutureAlarmedTodos(fromDate).forEach { alarmScheduler.scheduleForTodo(it) }
    return Result.success()
}
```

## 핵심 규칙 (반드시 지킬 것)

- **트리거 시각 계산을 worker 안에서 하지 마라.** 반드시 `scheduleForEvent`/`scheduleForTodo`에 위임하라. 이유: 이 task의 목적이 라이브 경로와 복원 경로의 시각 로직을 단일화하는 것이다. worker가 다시 계산하면 09:00/과거-스킵 규칙이 갈라진다.
- `@HiltWorker` + `@AssistedInject` 구조와 기존 주입 필드(`eventDao`, `todoDao`, `alarmScheduler`)를 유지하라. 이미 `alarmScheduler`가 주입돼 있으므로 새 의존성 추가는 불필요하다.
- 과거 시각 항목을 worker에서 별도로 거르지 마라. `scheduleForEvent`/`scheduleForTodo`가 내부에서 스킵한다(중복 처리 방지).

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음 (미사용 import 없음)
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 아키텍처 체크리스트:
   - worker에 트리거 시각 인라인 계산이 **남아있지 않은가?** (`OffsetDateTime.parse`, `atStartOfDay`, `.toEpochMilli()` 등이 worker에서 사라졌는가)
   - `scheduleForEvent`/`scheduleForTodo`만 호출하는가?
   - `@HiltWorker`/`@AssistedInject` 구조가 유지됐는가?
   - 미사용 import가 정리됐는가? (lint 경고 0)
3. 결과에 따라 `phases/8-alarm-lifecycle/index.json`의 step 2를 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "AlarmRestoreWorker가 인라인 트리거 계산을 버리고 scheduleForEvent/scheduleForTodo 재사용 — 복원·라이브 경로 09:00/과거-스킵 규칙 단일화 완료"`
   - 수정 3회 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 중단

## 머지 후 수동 확인 (참고 — execute.py 자동 실행 범위 밖, 사람이 확인)

이 task 전체(Step 0~2)는 빌드(AC)로는 알람의 실제 발화를 검증할 수 없다. 머지 후 실기기/에뮬레이터에서 아래를 한 번 확인하면 좋다. **이 절은 자동 세션이 수행하지 않는다 — 기기 설치·실행을 시도하지 마라.**

- 알람 켠 일정/마감일 Todo를 1~2분 뒤 시각으로 만들고 알림이 뜨는지.
- 일정 삭제·Todo 완료 시 알림이 더는 오지 않는지(취소 동작).
- 정확 알람 권한을 끈 상태에서도 알림이(다소 지연돼도) 오는지(inexact 폴백).

## 금지사항

- `AlarmScheduler.kt`를 수정하지 마라. 이유: Step 0에서 완성됐다. 시그니처가 안 맞으면 그 파일을 읽고 맞춰 호출하라.
- Repository를 수정하지 마라. 이유: Step 1에서 완료됐다.
- DAO 쿼리(`getFutureAlarmedEvents`/`getFutureAlarmedTodos`)를 바꾸지 마라. 이유: 이 Step의 범위는 worker의 호출부 리팩터뿐이다.
- 기존 코드를 추가로 리팩토링하지 마라. 이 step의 범위만 작업하라.
