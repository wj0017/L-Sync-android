# Step 1: materializer-worker

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `CLAUDE.md`
- `docs/ARCHITECTURE.md`
- `docs/TechSpec.md`
- `app/src/main/java/com/lsync/app/data/local/entity/TodoTemplateEntity.kt`
- `app/src/main/java/com/lsync/app/data/local/entity/TodoEntity.kt`
- `app/src/main/java/com/lsync/app/data/local/dao/TodoTemplateDao.kt` (Step 0에서 생성됨)
- `app/src/main/java/com/lsync/app/data/repository/TodoRepository.kt`
- `app/src/main/java/com/lsync/app/worker/TodoMaterializerWorker.kt`
- `app/src/main/java/com/lsync/app/di/AppModule.kt`

이전 step에서 만들어진 코드를 꼼꼼히 읽고, 설계 의도를 이해한 뒤 작업하라.

## 작업

`TodoMaterializerWorker.kt`의 `doWork()`를 구현하라.

### 핵심 로직

```kotlin
@HiltWorker
class TodoMaterializerWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val todoTemplateDao: TodoTemplateDao,
    private val todoRepository: TodoRepository,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result

    companion object {
        fun enqueuePeriodicWork(context: Context)
    }
}
```

### 구현 규칙

**인스턴스 생성 정책:**
- 오늘부터 +14일 범위의 날짜에 대해 각 활성 템플릿의 RRULE을 파싱하여 해당 날짜가 발생하는지 확인한다.
- 결정론적 ID: `"${templateId}_${dueDate}"` (dueDate는 `"YYYY-MM-DD"` 형식). 이 ID 규칙을 반드시 지켜라. 이유: 멱등성 보장 — 같은 날 여러 번 실행해도 중복 생성되지 않는다.
- RRULE 파싱에는 Step 0에서 추가한 `org.dmfs:lib-recur` 라이브러리를 사용한다.

**기존 인스턴스 보존 정책:**
- 이미 `isCompleted = true`인 인스턴스는 upsert 시 덮어쓰지 않는다. 이유: 완료된 기록을 잃으면 안 된다.
- `getPendingFutureByTemplate(templateId, today)` (TodoDao)를 사용해 미완료 미래 인스턴스만 갱신한다.
- 과거 인스턴스(dueDate < today)는 절대 신규 생성하지 않는다.

**userId:**
- 반드시 `"local_user"`를 사용한다. Firebase Auth를 호출하지 마라.

**에러 처리:**
- RRULE 파싱 실패 시 해당 템플릿을 건너뛰고 로그를 남긴다. 전체 Worker를 실패시키지 않는다.
- doWork()는 성공 시 `Result.success()`, 치명적 에러 시만 `Result.failure()`를 반환한다.

**enqueuePeriodicWork:**
- `PeriodicWorkRequestBuilder`로 1일 간격으로 등록한다.
- `ExistingPeriodicWorkPolicy.KEEP`을 사용한다. 이유: 이미 예약된 작업을 중복 등록하지 않는다.
- WorkManager 태그: `"todo_materializer"`

### RRULE 파싱 예시

```kotlin
// lib-recur 사용 예시 (구현체는 에이전트 재량)
val rule = RecurrenceRule(rruleString)
val start = DateTime(LocalDate.parse(firstOccurrenceDate).toEpochDay() * 86400000L)
val it = rule.iterator(start)
```

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 아키텍처 체크리스트를 확인한다:
   - `TodoMaterializerWorker`가 `@HiltWorker` + `@AssistedInject`를 사용하는가?
   - 결정론적 ID `"${templateId}_${dueDate}"` 규칙을 따르는가?
   - 완료된 인스턴스를 덮어쓰지 않는가?
   - userId가 `"local_user"`인가?
   - CLAUDE.md CRITICAL 규칙을 위반하지 않았는가?
3. 결과에 따라 `phases/1-phase1-completion/index.json`의 step 1을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "산출물 한 줄 요약"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- TodoTemplateEntity, TodoEntity의 필드를 수정하지 마라. 이유: 이 step은 Worker 구현만 다룬다.
- Firebase Auth를 직접 호출하지 마라. 이유: CLAUDE.md CRITICAL 규칙. userId는 `"local_user"` 고정.
- `collectAsStateWithLifecycle`을 사용하지 마라. 이유: CLAUDE.md CRITICAL 규칙.
- Worker 내에서 UI 레이어 코드를 참조하지 마라. 이유: 레이어 분리 원칙.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
