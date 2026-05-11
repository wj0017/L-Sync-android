# Step 2: alarm-restore-worker

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `CLAUDE.md`
- `docs/ARCHITECTURE.md`
- `docs/TechSpec.md`
- `app/src/main/java/com/lsync/app/data/local/dao/EventDao.kt`
- `app/src/main/java/com/lsync/app/data/local/dao/TodoDao.kt`
- `app/src/main/java/com/lsync/app/data/local/entity/EventEntity.kt`
- `app/src/main/java/com/lsync/app/data/local/entity/TodoEntity.kt`
- `app/src/main/java/com/lsync/app/notification/AlarmScheduler.kt`
- `app/src/main/java/com/lsync/app/worker/AlarmRestoreWorker.kt`
- `app/src/main/java/com/lsync/app/di/AppModule.kt`

이전 step에서 만들어진 코드를 꼼꼼히 읽고, 설계 의도를 이해한 뒤 작업하라.

## 작업

`AlarmRestoreWorker.kt`의 `doWork()`를 구현하라. 디바이스 재부팅 시 AlarmManager 알람이 초기화되므로, BootReceiver가 이 Worker를 실행하여 알람을 재등록한다.

### DAO 쿼리 추가

**EventDao.kt**에 다음 메서드를 추가하라:

```kotlin
@Query("""
    SELECT * FROM events 
    WHERE hasAlarm = 1 
      AND deletedAt IS NULL 
      AND startDate >= :fromDate
""")
suspend fun getFutureAlarmedEvents(fromDate: String): List<EventEntity>
```

`fromDate`는 `"YYYY-MM-DD"` 형식 문자열이다.

**TodoDao.kt**에 다음 메서드를 추가하라:

```kotlin
@Query("""
    SELECT * FROM todos 
    WHERE hasAlarm = 1 
      AND isCompleted = 0 
      AND deletedAt IS NULL 
      AND dueDate >= :fromDate
""")
suspend fun getFutureAlarmedTodos(fromDate: String): List<TodoEntity>
```

`TodoEntity`에 `hasAlarm` 필드가 없는 경우: Entity를 먼저 확인한 뒤, 없으면 이 쿼리에서 `hasAlarm` 조건을 제외하고 `dueDate IS NOT NULL AND dueDate >= :fromDate`로만 조회하라.

### Worker 구현

```kotlin
@HiltWorker
class AlarmRestoreWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val eventDao: EventDao,
    private val todoDao: TodoDao,
    private val alarmScheduler: AlarmScheduler,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result
}
```

### 구현 규칙

**실행 흐름:**
1. 오늘 날짜(YYYY-MM-DD)를 `fromDate`로 사용하여 미래 알람 대상 이벤트·투두를 조회한다.
2. 각 이벤트에 대해 `alarmScheduler.scheduleEventAlarm(event)`을 호출한다.
3. 각 투두에 대해 `alarmScheduler.scheduleTodoAlarm(todo)`를 호출한다.
4. 모든 처리가 완료되면 `Result.success()`를 반환한다.

**AlarmScheduler 시그니처 확인:**
AlarmScheduler의 기존 함수 시그니처를 반드시 읽고 그대로 사용하라. 시그니처를 바꾸지 마라.

**에러 처리:**
- 개별 알람 스케줄링 실패 시 해당 항목을 건너뛰고 계속 진행한다.
- 전체 Worker가 실패하면 BootReceiver가 재시도 로직을 갖고 있지 않으므로 치명적 에러가 아니면 `Result.failure()`를 반환하지 않는다.

**날짜 계산:**
- 종일 이벤트/투두의 날짜는 `"YYYY-MM-DD"` Floating Time 문자열이다. 타임존 변환을 하지 마라.
- `LocalDate.now().toString()`으로 오늘 날짜 문자열을 얻어 쿼리에 사용하라.

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 아키텍처 체크리스트를 확인한다:
   - `AlarmRestoreWorker`가 `@HiltWorker` + `@AssistedInject`를 사용하는가?
   - `EventDao`에 `getFutureAlarmedEvents()` 쿼리가 추가됐는가?
   - `TodoDao`에 `getFutureAlarmedTodos()` 쿼리가 추가됐는가?
   - 날짜를 타임존 변환 없이 `"YYYY-MM-DD"` 문자열로 다루는가?
   - CLAUDE.md CRITICAL 규칙을 위반하지 않았는가?
3. 결과에 따라 `phases/1-phase1-completion/index.json`의 step 2를 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "산출물 한 줄 요약"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- AlarmScheduler의 기존 메서드 시그니처를 변경하지 마라. 이유: 다른 곳에서 이미 사용 중이며 이 step 범위가 아니다.
- EventEntity, TodoEntity의 필드를 추가·수정하지 마라. 이유: Room 마이그레이션이 필요해지므로 이 step 범위를 벗어난다.
- 타임존 변환을 하지 마라. 이유: CLAUDE.md CRITICAL 규칙 — 날짜는 Floating Time.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
