# Step 1: widget-data

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `CLAUDE.md`
- `docs/ARCHITECTURE.md`
- `app/src/main/java/com/lsync/app/ui/widget/LSyncWidget.kt`
- `app/src/main/java/com/lsync/app/ui/widget/LSyncWidgetReceiver.kt`
- `app/src/main/java/com/lsync/app/data/local/dao/TodoDao.kt`
- `app/src/main/java/com/lsync/app/data/local/dao/EventDao.kt`
- `app/src/main/java/com/lsync/app/data/local/dao/FinanceDao.kt`
- `app/src/main/java/com/lsync/app/data/local/dao/ReadingPlanDao.kt`
- `app/src/main/java/com/lsync/app/data/local/entity/FinanceEntity.kt`
- `app/src/main/java/com/lsync/app/di/AppModule.kt`

## 작업

위젯에서 Room DB를 읽어 오늘 할 일·일정·이달 가계부·성경 통독 데이터를 하나의 `WidgetState`로 조립하는 레이어를 구현한다.

### 1. DAO에 위젯용 suspend 쿼리 추가

`GlanceAppWidget.provideGlance()`는 코루틴 컨텍스트에서 실행되므로 Flow가 아닌 suspend 함수가 필요하다. **기존 메서드는 수정하지 않고** 아래 메서드만 추가하라.

**TodoDao.kt**에 추가:

```kotlin
@Query("""
    SELECT * FROM todos
    WHERE dueDate = :date
      AND isCompleted = 0
      AND deletedAt IS NULL
    ORDER BY createdAt ASC
""")
suspend fun getIncompleteByDate(date: String): List<TodoEntity>
```

**EventDao.kt**에 추가:

```kotlin
@Query("""
    SELECT * FROM events
    WHERE deletedAt IS NULL
      AND startDate LIKE :datePrefix || '%'
    ORDER BY startDate ASC
""")
suspend fun getByDate(datePrefix: String): List<EventEntity>
```

- `startDate LIKE :datePrefix || '%'` — 종일 일정(`"2024-05-01"`)과 시간 지정 일정(`"2024-05-01T09:00:00+09:00"`) 모두 커버한다.

### 2. WidgetState 데이터 클래스 생성

`app/src/main/java/com/lsync/app/ui/widget/WidgetState.kt`를 생성하라:

```kotlin
package com.lsync.app.ui.widget

import com.lsync.app.data.local.entity.EventEntity
import com.lsync.app.data.local.entity.ReadingPlanEntity
import com.lsync.app.data.local.entity.TodoEntity

data class WidgetState(
    val todos: List<TodoEntity>,
    val events: List<EventEntity>,
    val monthExpense: Long,
    val monthIncome: Long,
    val todayReadingPlan: List<ReadingPlanEntity>,
    val readCount: Int,
    val totalCount: Int,
)
```

### 3. Hilt EntryPoint 생성

위젯은 Android 시스템이 직접 인스턴스화하므로 Hilt 자동 주입이 불가하다. `EntryPointAccessors`를 사용한다.

`app/src/main/java/com/lsync/app/ui/widget/WidgetEntryPoint.kt`를 생성하라:

```kotlin
package com.lsync.app.ui.widget

import com.lsync.app.data.local.dao.EventDao
import com.lsync.app.data.local.dao.FinanceDao
import com.lsync.app.data.local.dao.ReadingPlanDao
import com.lsync.app.data.local.dao.TodoDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun todoDao(): TodoDao
    fun eventDao(): EventDao
    fun financeDao(): FinanceDao
    fun readingPlanDao(): ReadingPlanDao
}
```

### 4. LSyncWidget에 데이터 로딩 로직 구현

`app/src/main/java/com/lsync/app/ui/widget/LSyncWidget.kt`를 수정하라.

`provideGlance()` 구현 지침:

- `EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)`로 DAO를 획득한다.
- `today`는 `java.time.LocalDate.now().toString()` (기기 로컬 날짜, `YYYY-MM-DD`).
- 이달 범위: `monthStart = today.substring(0, 7) + "-01"`, `monthEnd = today.substring(0, 7) + "-31"`. Room의 날짜 비교는 사전식 문자열 정렬이므로 `-31`은 실제로 존재하지 않는 날짜여도 안전하다.
- Finance 집계 시 `settlementGroupId != null`인 항목은 수입·지출 집계에서 제외한다 (PRD 2.4 정산 정책). `FinanceEntity.type` 필드를 읽어 `"EXPENSE"` / `"INCOME"`으로 분기하라.
- `userId`는 `"local_user"` 하드코딩 (CLAUDE.md CRITICAL 규칙).
- 데이터 로딩 후 `provideContent { /* step 2에서 구현할 UI */ }` 블록 안에 `WidgetState`를 넘겨라.

데이터 로딩 결과로 `WidgetState`를 만드는 것까지가 이 step의 범위다. `provideContent` 블록 안의 UI는 임시 `Text("로딩 완료")`로 두어도 된다.

시그니처 예시 (구현체는 에이전트 재량):

```kotlin
class LSyncWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = loadWidgetState(context)
        provideContent {
            // step 2에서 교체
            androidx.glance.text.Text(text = "L-Sync (${state.todos.size}개 할 일)")
        }
    }

    private suspend fun loadWidgetState(context: Context): WidgetState { ... }
}
```

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 체크리스트를 확인한다:
   - `WidgetState.kt`가 `ui/widget/` 패키지에 생성됐는가?
   - `WidgetEntryPoint.kt`가 `ui/widget/` 패키지에 생성됐는가?
   - `TodoDao`에 `getIncompleteByDate`, `EventDao`에 `getByDate`가 추가됐는가?
   - `LSyncWidget.provideGlance()`가 Room에서 데이터를 읽어 `WidgetState`를 조립하는가?
   - `userId = "local_user"` 하드코딩을 사용하는가?
   - CLAUDE.md CRITICAL 규칙을 위반하지 않았는가?
3. 결과에 따라 `phases/6-home-widget/index.json`의 step 1을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "산출물 한 줄 요약"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- Firestore를 호출하지 마라. 이유: 위젯은 Room 전용이다. 네트워크 없이도 동작해야 한다.
- Flow를 구독하지 마라. 이유: `provideGlance()`는 one-shot suspend 함수이므로 suspend 쿼리만 사용한다.
- AppModule.kt를 수정하지 마라. 이유: `@EntryPoint`는 Hilt가 자동 처리하므로 `@Provides` 등록이 불필요하다.
- 기존 DAO 메서드를 수정하지 마라. 이유: 새 메서드 추가만 허용된다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
