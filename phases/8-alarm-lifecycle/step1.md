# Step 1: repository-wiring

## 배경

이 task는 "알람 라이프사이클 연결"이다. 현재 L-Sync는 일정/Todo를 만들 때 `hasAlarm=true`로 Room에 저장만 하고 AlarmManager에 등록하지 않아, **재부팅 전에는 알람이 한 번도 울리지 않는다.** 또한 일정 삭제·Todo 완료 시 이미 걸린 알람을 취소하지도 않는다.

**이전 Step(Step 0)에서** `AlarmScheduler`에 엔티티 단위 메서드 `scheduleForEvent(event: EventEntity)`와 `scheduleForTodo(todo: TodoEntity)`를 추가했다. 이 메서드들은 내부에서 트리거 시각(시간지정=시작시각 / 종일·Todo=마감일 09:00)을 계산하고, `hasAlarm=false`·완료·`dueDate==null`이면 `cancel`하며, 과거 시각이면 등록을 스킵한다.

이 Step은 그 메서드들을 **Repository의 생성/수정/삭제/완료 경로에 연결**한다.

## 읽어야 할 파일

먼저 아래 파일을 읽고 설계 의도를 파악하라:

- `docs/ARCHITECTURE.md` — 레이어 구조, Offline-First, Todo↔Finance 생명주기
- `CLAUDE.md` — CRITICAL 규칙 (특히 Todo 삭제/미완료 시 Finance 보존, Hilt `@Inject` 패턴)
- `app/src/main/java/com/lsync/app/notification/AlarmScheduler.kt` — **Step 0에서 추가된 `scheduleForEvent`/`scheduleForTodo`/`cancel`를 사용한다. 먼저 이 파일을 읽어 정확한 시그니처를 확인하라.**
- `app/src/main/java/com/lsync/app/data/repository/EventRepository.kt` — **수정 대상**
- `app/src/main/java/com/lsync/app/data/repository/TodoRepository.kt` — **수정 대상**
- `app/src/main/java/com/lsync/app/di/AppModule.kt` — `AlarmScheduler` 제공 방식 확인

이전 Step에서 만들어진 `AlarmScheduler`의 새 메서드를 꼼꼼히 읽고, 그 계약(과거 스킵·cancel 처리 등)을 이해한 뒤 호출부를 연결하라.

## 작업

`AlarmScheduler`는 `@Singleton` + `@Inject constructor`이므로 Hilt가 자동 제공한다. **AppModule에 `@Provides`를 추가할 필요 없다.** 두 Repository 생성자에 `private val alarmScheduler: AlarmScheduler`를 주입 파라미터로 추가하라.

### EventRepository.kt

생성자에 `AlarmScheduler` 주입 추가 후:

- `save(event: EventEntity)`: Room upsert + Firestore 동기화 **이후** `alarmScheduler.scheduleForEvent(event)` 호출.
  - 이유: `create()`가 내부에서 `save()`를 호출하므로 생성 시 자동 등록된다. 또한 `save()`는 upsert라 향후 수정 경로에서도 재등록이 보장된다. `scheduleForEvent`는 `hasAlarm=false`면 내부에서 `cancel`하므로 멱등하고 안전하다.
- `delete(id: String)`: 기존 삭제 처리 **이후** `alarmScheduler.cancel(id)` 호출.

### TodoRepository.kt

생성자에 `AlarmScheduler` 주입 추가 후:

- `create(...)`: 생성된 `entity`를 반환하기 **전에** `alarmScheduler.scheduleForTodo(entity)` 호출.
- `complete(todo, amount)`: 완료 처리 성공 경로에서 `alarmScheduler.cancel(todo.id)` 호출. 이유: 완료된 Todo는 리마인더가 필요 없다.
- `uncheck(todo)`: 미완료로 되돌린 `unchecked` 엔티티로 `alarmScheduler.scheduleForTodo(unchecked)` 호출. 이유: 다시 미완료가 됐으니 알람을 복구한다(마감일이 이미 지났으면 Step 0 로직이 자동 스킵).
- `delete(todo)`: 삭제 처리 **이후** `alarmScheduler.cancel(todo.id)` 호출.

## 핵심 규칙 (반드시 지킬 것)

- **데이터 영속성 정책을 깨지 마라.** Todo `delete`/`uncheck`/`complete`의 기존 Finance 처리 로직(삭제 금지, Soft Delete, `sourceTodoId` 해제, Batch Write)은 **절대 변경하지 마라.** 이 Step은 그 로직 뒤/앞에 알람 호출 한 줄을 더하는 것뿐이다.
- **Offline-First 순서 유지.** 알람 등록/취소는 Room 저장(로컬) 이후에 호출하라. 알람 실패가 데이터 저장을 막아선 안 된다.
- `complete()`는 `runCatching`(`Result`)를 반환한다. 알람 `cancel` 호출 위치가 기존 `runCatching` 블록의 성공 흐름 안인지 확인하고, 알람 호출 때문에 반환 타입이 바뀌지 않게 하라.
- userId 하드코딩 금지 등 기존 규칙 유지. 이 Step에서 새 userId 사용 없음.

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음 (Hilt 그래프 포함)
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다. 특히 Hilt가 `AlarmScheduler` 주입을 문제없이 해결하는지(컴파일 통과) 확인한다.
2. 아키텍처 체크리스트:
   - 두 Repository가 `AlarmScheduler`를 생성자 주입으로 받는가?
   - Event: `save`에서 `scheduleForEvent`, `delete`에서 `cancel`이 호출되는가?
   - Todo: `create`→schedule, `complete`→cancel, `uncheck`→schedule, `delete`→cancel이 연결됐는가?
   - 기존 Finance 생명주기 로직이 그대로인가? (Room이 SSOT, Firestore 직접 구독 없음)
3. 결과에 따라 `phases/8-alarm-lifecycle/index.json`의 step 1을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "EventRepository/TodoRepository에 AlarmScheduler 주입 — save·create·uncheck에서 등록, delete·complete에서 취소. 생성 시점부터 알람 실제 발화"`
   - 수정 3회 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 중단

## 금지사항

- `AlarmScheduler.kt`를 수정하지 마라. 이유: Step 0에서 완성됐고, 이 Step은 호출만 한다. 시그니처가 안 맞으면 Step 0 산출물을 다시 읽고 그에 맞춰 호출하라(임의로 메서드를 바꾸지 마라).
- `AlarmRestoreWorker.kt`를 수정하지 마라. 이유: 복원 worker 리팩터는 Step 2의 범위다.
- ViewModel(`ScheduleViewModel` 등)을 수정하지 마라. 이유: Repository가 단일 진입점이며 ViewModel은 이미 Repository를 호출한다. 알람은 Repository 레이어에서만 연결한다.
- 기존 Finance 연동/동기화 로직을 리팩토링하지 마라. 이 step의 범위(알람 호출 연결)만 작업하라.
