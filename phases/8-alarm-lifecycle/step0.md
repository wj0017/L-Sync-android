# Step 0: alarm-scheduler-core

## 배경

이 task는 "알람 라이프사이클 연결"이다. 현재 L-Sync에는 알람 인프라(`AlarmScheduler`, `AlarmReceiver`, `BootReceiver` → `AlarmRestoreWorker`)가 모두 존재하지만, **알람을 실제로 등록/취소하는 `AlarmScheduler` 메서드가 오직 `AlarmRestoreWorker`(재부팅 복원)에서만 호출된다.** 일정/Todo를 만들 때는 `hasAlarm=true`로 Room에 저장만 될 뿐 AlarmManager에 등록되지 않아, **재부팅 전에는 알람이 한 번도 울리지 않는다.**

이 Step은 그 문제를 풀기 위한 **첫 단추**다. 알람의 "트리거 시각 계산" 로직(09:00 리마인더, 시작 시각, 과거 시각 스킵)을 `AlarmScheduler`의 **엔티티 단위 메서드**로 단일화한다. 이후 Step 1(Repository 연결)과 Step 2(복원 worker 리팩터)가 이 메서드를 공유해 로직 드리프트를 방지한다.

## 읽어야 할 파일

먼저 아래 파일을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `docs/ARCHITECTURE.md` — 레이어 구조, 디렉토리 규칙
- `docs/TechSpec.md` — 5장(안드로이드 알림 & 권한)
- `CLAUDE.md` — CRITICAL 규칙 (특히 Floating Time, Hilt 패턴)
- `app/src/main/java/com/lsync/app/notification/AlarmScheduler.kt` — **이 Step에서 수정할 대상**
- `app/src/main/java/com/lsync/app/notification/AlarmReceiver.kt` — 알람 수신 시 알림 표시 (참고)
- `app/src/main/java/com/lsync/app/worker/AlarmRestoreWorker.kt` — 현재 트리거 시각을 인라인 계산하는 방식 (참고: 이 로직을 메서드로 흡수)
- `app/src/main/java/com/lsync/app/data/local/entity/EventEntity.kt` — `id, title, isAllDay, startDate, hasAlarm` 등 필드 확인
- `app/src/main/java/com/lsync/app/data/local/entity/TodoEntity.kt` — `id, title, dueDate, isCompleted` 등 필드 확인 (※ `hasAlarm` 필드 **없음**)

## 작업

`AlarmScheduler.kt`에 **엔티티를 받아 트리거 시각을 스스로 계산하는** 메서드 2개를 추가한다. 기존 low-level 메서드(`scheduleEventAlarm`, `scheduleTodoAlarm`, `cancel`)와 companion 상수는 **그대로 유지**하고, 새 메서드가 내부에서 그것들을 호출하게 한다.

### 추가할 메서드 시그니처

```kotlin
fun scheduleForEvent(event: EventEntity)
fun scheduleForTodo(todo: TodoEntity)
```

### scheduleForEvent(event) 규칙

1. `event.hasAlarm == false` 이면 → `cancel(event.id)` 호출 후 즉시 return. (알람이 꺼진 항목은 기존 등록을 제거해 일관성 유지)
2. 트리거 시각 계산:
   - **시간 지정 일정** (`isAllDay == false`): `OffsetDateTime.parse(event.startDate).toInstant().toEpochMilli()` — 시작 시각 그대로.
   - **종일 일정** (`isAllDay == true`): `LocalDate.parse(event.startDate)`의 그 날 **09:00**(시스템 타임존). 아래 `REMINDER_HOUR` 사용.
3. 계산된 `triggerAtMillis <= System.currentTimeMillis()` 이면 → 등록하지 않고 return. (과거 시각 알람이 즉시 발화하는 것 방지)
4. 그 외 → 기존 `scheduleEventAlarm(event.id, event.title, triggerAtMillis)` 호출.

### scheduleForTodo(todo) 규칙

1. `todo.isCompleted == true` 또는 `todo.dueDate == null` 이면 → `cancel(todo.id)` 호출 후 즉시 return.
2. 트리거 시각: `LocalDate.parse(todo.dueDate)`의 그 날 **09:00**(시스템 타임존).
3. `triggerAtMillis <= System.currentTimeMillis()` 이면 → 등록하지 않고 return.
4. 그 외 → 기존 `scheduleTodoAlarm(todo.id, todo.title, triggerAtMillis)` 호출.

### 공통

- `companion object`에 `const val REMINDER_HOUR = 9` 추가.
- 날짜 → 09:00 epoch millis 변환은 다음 패턴을 사용한다 (시스템 타임존 기준, 한 곳에 private helper로 빼도 좋다):

```kotlin
LocalDate.parse(dateStr)
    .atTime(REMINDER_HOUR, 0)
    .atZone(ZoneId.systemDefault())
    .toInstant()
    .toEpochMilli()
```

- 파싱 실패(잘못된 날짜 문자열)나 `SecurityException`(정확 알람 권한 미허용) 가능성에 대비해 각 메서드 본문을 `runCatching { ... }`으로 감싸 예외를 삼켜라. 이유: 한 항목의 실패가 다른 항목 등록(Step 2의 복원 루프)을 막으면 안 된다.

### 정확 알람 권한 graceful fallback

Android 12+(API 31)에서는 사용자가 정확 알람 권한을 거부할 수 있고, 그 상태에서 `setExactAndAllowWhileIdle`를 호출하면 `SecurityException`이 발생한다. 현재 low-level 메서드는 이를 무방비로 호출하므로, 권한이 없을 때 **알람이 조용히 사라지는** 문제가 있다. 이를 막기 위해 등록 시 아래처럼 분기하라:

- `Build.VERSION.SDK_INT < 31` 또는 `alarmManager.canScheduleExactAlarms() == true` → 기존대로 `setExactAndAllowWhileIdle(...)`.
- 그 외(권한 없음) → `setAndAllowWhileIdle(...)`로 **부정확(inexact) 알람 폴백**. 시각이 다소 밀려도 알람 자체는 발화되게 한다.

이 분기는 `scheduleEventAlarm`/`scheduleTodoAlarm` 두 low-level 메서드에 공통이므로, private helper(예: `setAlarm(triggerAtMillis, pendingIntent)`)로 추출해 양쪽이 쓰게 하라. **메서드 시그니처(파라미터)는 바꾸지 마라** — Step 2가 그대로 의존한다.

## 핵심 규칙 (반드시 지킬 것)

- **트리거 계산 로직은 이 메서드들 안에만 존재해야 한다.** Step 1·2가 이 메서드를 호출만 하도록, 시각 계산을 밖으로 노출하거나 중복하지 마라. 이유: 라이브 등록과 재부팅 복원이 서로 다른 시각을 쓰면 알람이 이중/누락 발생.
- **Floating Time 위반 금지.** 종일 일정·Todo 마감일은 `YYYY-MM-DD` 문자열이다. UTC 등으로 타임존 변환하지 말고 `ZoneId.systemDefault()` 기준 09:00으로만 변환하라.
- low-level 메서드(`scheduleEventAlarm`/`scheduleTodoAlarm`/`cancel`)의 **시그니처를 바꾸지 마라**(Step 2가 의존). 내부 등록 방식에 위 fallback 분기를 더하는 것은 허용·요구된다.

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 아키텍처 체크리스트:
   - `scheduleForEvent`/`scheduleForTodo`가 추가되고 기존 low-level 메서드·companion 상수가 보존됐는가?
   - 트리거 시각 계산이 이 두 메서드 안에만 있는가?
   - `REMINDER_HOUR = 9` 상수가 정의됐는가?
   - 종일/Todo 변환에 `ZoneId.systemDefault()`만 쓰고 임의 UTC 변환이 없는가?
   - 정확 알람 권한이 없을 때 `setAndAllowWhileIdle`로 폴백하는 분기가 있는가? (권한 미허용 시 알람이 사라지지 않는가)
3. 결과에 따라 `phases/8-alarm-lifecycle/index.json`의 step 0을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "AlarmScheduler에 scheduleForEvent/scheduleForTodo 추가 — 트리거 시각(시작시각/마감일 09:00/과거 스킵) 단일화, REMINDER_HOUR=9, 정확 알람 권한 없으면 inexact 폴백, low-level 시그니처 보존"`
   - 수정 3회 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 중단

## 금지사항

- Repository / Worker / ViewModel을 수정하지 마라. 이유: 이 Step의 범위는 `AlarmScheduler.kt` 단일 파일이다. 호출부 연결은 Step 1·2의 몫이다.
- 기존 `scheduleEventAlarm`/`scheduleTodoAlarm`/`cancel`의 **시그니처**(이름·파라미터·반환)를 변경하지 마라. 이유: Step 2가 그대로 의존한다. (내부 등록 방식에 정확 알람 폴백 분기를 더하는 것은 위 "graceful fallback" 지시대로 허용된다.)
- `TodoEntity`에 `hasAlarm` 필드를 추가하지 마라. 이유: 이번 task의 결정은 "마감일 있는 모든 미완료 Todo = 자동 리마인더"이며 스키마/마이그레이션 변경은 범위 밖이다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
