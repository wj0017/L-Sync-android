# Step 3: widget-refresh

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `CLAUDE.md`
- `docs/ARCHITECTURE.md`
- `app/src/main/java/com/lsync/app/ui/widget/LSyncWidget.kt`
- `app/src/main/java/com/lsync/app/ui/widget/LSyncWidgetReceiver.kt`
- `app/src/main/java/com/lsync/app/worker/TodoMaterializerWorker.kt`
- `app/src/main/java/com/lsync/app/LSyncApplication.kt`

## 작업

위젯 데이터를 주기적으로 갱신하는 WorkManager 워커를 구현하고, 앱 시작 시 등록한다.

### 1. WidgetRefreshWorker 생성

`app/src/main/java/com/lsync/app/worker/WidgetRefreshWorker.kt`를 생성하라:

```kotlin
class WidgetRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        LSyncWidget().updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "widget_refresh"

        fun enqueuePeriodicWork(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
```

- `@HiltWorker`를 붙이지 마라. 이 워커는 Hilt 주입이 필요 없으며, `LSyncWidget().updateAll()`이 내부적으로 `EntryPointAccessors`를 통해 DAO에 접근한다.
- `ExistingPeriodicWorkPolicy.KEEP` — 이미 등록된 워커가 있으면 그대로 유지한다. 앱 재시작마다 중복 등록되지 않는다.

### 2. LSyncApplication에 워커 등록

`app/src/main/java/com/lsync/app/LSyncApplication.kt`의 `onCreate()` 안에 추가하라:

```kotlin
WidgetRefreshWorker.enqueuePeriodicWork(this)
```

기존 `TodoMaterializerWorker.enqueuePeriodicWork(this)` 다음 줄에 추가한다.

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 체크리스트를 확인한다:
   - `WidgetRefreshWorker.kt`가 `worker/` 패키지에 생성됐는가?
   - `@HiltWorker` 어노테이션이 없는가?
   - `LSyncApplication.onCreate()`에서 `WidgetRefreshWorker.enqueuePeriodicWork(this)` 호출이 추가됐는가?
   - CLAUDE.md CRITICAL 규칙을 위반하지 않았는가?
3. 결과에 따라 `phases/6-home-widget/index.json`의 step 3을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "산출물 한 줄 요약"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- `@HiltWorker`를 추가하지 마라. 이유: Hilt 워커로 등록하면 `HiltWorkerFactory`가 필요한 생성자를 요구하는데, 이 워커는 주입 의존성이 없어 불필요하게 복잡해진다.
- Repository나 DAO를 이 워커에 직접 주입하지 마라. 이유: 데이터 조회는 `LSyncWidget.loadWidgetState()`에 캡슐화되어 있다.
- `ExistingPeriodicWorkPolicy.REPLACE`를 사용하지 마라. 이유: 앱 재시작마다 실행 타이머가 리셋되어 30분 주기가 보장되지 않는다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
