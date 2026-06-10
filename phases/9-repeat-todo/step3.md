# Step 3: viewmodel-layer

## 배경

이 task(`9-repeat-todo`)는 "반복 Todo 템플릿 UI"다. 백엔드와 Repository 메서드는 준비됐고, 이제 화면(Step 4)이 쓸 ViewModel API를 만든다.

이전 Step들에서:
- **Step 0**: `ui/schedule/RecurrenceOptions.kt` — `RecurrenceOption`, `buildRrule(option): String`, `describeRrule(rrule): String`.
- **Step 1**: `TodoRepository.createTemplate(userId, title, rrule, finance...)` 와 `upsertMaterialized(todos)`.
- **Step 2**: `TodoMaterializerWorker`가 인스턴스를 동기화·알람과 함께 저장(일 1회 주기 실행).

문제: 템플릿을 만든 직후 사용자는 인스턴스가 **바로** 보이길 기대하는데, Materializer는 일 1회만 돈다. 그래서 템플릿 생성 직후 **즉시 1회 materialization**을 트리거해야 한다. 또 화면이 활성 템플릿 목록을 보고 삭제(비활성화)할 수 있어야 한다.

## 읽어야 할 파일

먼저 아래 파일을 읽고 설계 의도를 파악하라:

- `docs/ARCHITECTURE.md` — `collectAsState`만 사용, Offline-First, 위젯 즉시 갱신 패턴(`WidgetRefreshHelper`)
- `CLAUDE.md` — CRITICAL 규칙(`@HiltViewModel`+`@Inject`, `collectAsStateWithLifecycle` 금지, userId는 `AuthRepository.currentUserId`)
- `app/src/main/java/com/lsync/app/ui/schedule/ScheduleViewModel.kt` — **수정 대상.** 기존 `createTodo`, `deleteTodo`, `uiState`(`ScheduleUiState`), `currentUserId`, `widgetRefreshHelper` 사용 패턴을 그대로 따른다.
- `app/src/main/java/com/lsync/app/ui/widget/WidgetRefreshHelper.kt` — **새로 만들 `MaterializationTrigger`의 본보기.** `@Singleton` + `@Inject constructor(@ApplicationContext context, @ApplicationScope scope)` + WorkManager/Glance 호출 패턴.
- `app/src/main/java/com/lsync/app/worker/TodoMaterializerWorker.kt` — `enqueuePeriodicWork(context)` companion 패턴, 워커 클래스 참조
- `app/src/main/java/com/lsync/app/data/repository/TodoRepository.kt` — `createTemplate`, `observeActiveTemplates(userId)`, `deactivateTemplate(id)`

## 작업

### 1) MaterializationTrigger (새 파일)

`app/src/main/java/com/lsync/app/worker/MaterializationTrigger.kt`를 만든다. `WidgetRefreshHelper`와 동일한 구조:

```kotlin
@Singleton
class MaterializationTrigger @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun runNow() {
        val request = OneTimeWorkRequestBuilder<TodoMaterializerWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "todo_materializer_now",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
```

- `TodoMaterializerWorker`는 `@HiltWorker`라 OneTimeWorkRequest로도 정상 동작(HiltWorkerFactory가 처리).
- `@Singleton` + `@Inject constructor`이므로 Hilt가 자동 제공한다. **AppModule에 `@Provides` 추가는 불필요**하다(생성자 주입이므로). 단, 빌드가 주입 실패를 내면 `WidgetRefreshHelper`가 어떻게 제공되는지 확인해 동일하게 맞춰라.

### 2) ScheduleViewModel 확장

- 생성자에 `private val materializationTrigger: MaterializationTrigger` 주입 추가.
- `ScheduleUiState`에 `val templates: List<TodoTemplateEntity> = emptyList()` 추가.
- `init`(또는 기존 관찰 패턴)에서 `todoRepository.observeActiveTemplates(currentUserId)`를 구독해 `uiState.templates`를 갱신한다(기존 `viewModelScope.launch { ... collect { ... } }` 패턴, `collectAsState` 대응).
- 함수 추가:

```kotlin
fun createTemplate(
    title: String,
    rrule: String,
    financeIsLinked: Boolean = false,
    financeType: String? = null,
    financeCategory: String? = null,
    financeAmount: Long? = null,
)

fun deleteTemplate(id: String)
```

- `createTemplate`: `todoRepository.createTemplate(userId = currentUserId, ...)` 호출 → 성공 시 `materializationTrigger.runNow()`(즉시 인스턴스 생성) + `widgetRefreshHelper.requestUpdate()`. 실패 시 기존 패턴대로 `_uiState.update { it.copy(error = ...) }`.
- `deleteTemplate`: `todoRepository.deactivateTemplate(id)` 호출(hard delete 아님). 이후 `widgetRefreshHelper.requestUpdate()`.
- rrule 문자열은 **호출자(Step 4 UI)가 `buildRrule`로 만들어 넘긴다.** ViewModel은 rrule을 다시 만들지 않는다(이 Step에서 `RecurrenceOptions`를 import할 필요 없음).

## 핵심 규칙 (반드시 지킬 것)

- **`@HiltViewModel` + `@Inject constructor` 유지.** 누락 시 런타임 크래시.
- **`collectAsStateWithLifecycle` 금지**, ViewModel 내부는 `viewModelScope.launch { flow.collect { } }` 패턴. (UI는 `collectAsState()`)
- **userId는 `currentUserId`(=`authRepository.currentUserId`)만.** `"local_user"` 하드코딩 금지.
- **데이터 영속성.** `deleteTemplate`은 반드시 `deactivateTemplate`(isActive=0)로 매핑. 템플릿이나 이미 생성된 인스턴스를 hard delete하지 마라.
- 즉시 트리거는 `MaterializationTrigger.runNow()`만 사용. ViewModel에서 직접 `Context`/WorkManager를 만지지 마라(ViewModel은 Context를 보유하지 않는다 — 그래서 트리거를 별도 `@Singleton`으로 분리한다).

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음 (Hilt 주입 포함)
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 아키텍처 체크리스트:
   - `MaterializationTrigger`가 `WidgetRefreshHelper`와 동일한 주입 패턴인가?
   - `ScheduleViewModel`이 `@HiltViewModel`/`@Inject`이고 `currentUserId`만 쓰는가?
   - `uiState.templates`가 `observeActiveTemplates` Flow 구독으로 채워지는가?
   - `createTemplate` 성공 후 `materializationTrigger.runNow()`가 호출되는가?
   - `deleteTemplate`이 `deactivateTemplate`(soft)인가?
3. 결과에 따라 `phases/9-repeat-todo/index.json`의 step 3을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "MaterializationTrigger(@Singleton, OneTimeWork) 추가. ScheduleViewModel에 templates 상태·createTemplate(생성 후 즉시 materialize)·deleteTemplate(deactivate). UI(step4)가 buildRrule로 rrule 만들어 createTemplate 호출"`
   - 수정 3회 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 중단

## 금지사항

- Compose(`ScheduleScreen.kt`)를 수정하지 마라. 이유: UI는 Step 4의 범위다.
- `TodoRepository`/`TodoMaterializerWorker`를 수정하지 마라. 이유: Step 1·2에서 완료됐다. 이 Step은 호출만 한다.
- `RecurrenceOptions.kt`(Step 0)를 수정하지 마라. ViewModel은 rrule 문자열을 받기만 한다.
- 기존 `createTodo`/`toggleComplete`/`deleteTodo` 등의 동작을 바꾸지 마라. 이 step은 추가만 한다.
