# Step 1: signout-clear-local

로그아웃 시 동기화 대상 로컬 Room 데이터를 비워, 다른 Google 계정으로 전환했을 때 이전 사용자의 일정·할일·가계부·예산이 노출되는 데이터 격리 버그를 고친다. 함께, 로그아웃 후에도 이전 계정의 일정/할일 알람이 발화하는 2차 누수를 차단한다.

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `docs/ARCHITECTURE.md` (특히 "Offline-First", "Firestore 복원 동기화(pull)", "userId 마이그레이션", "알람 라이프사이클" 섹션)
- `CLAUDE.md` (CRITICAL: Hilt `@Provides` 누락 시 런타임 크래시 / Offline-First)
- `app/src/main/java/com/lsync/app/data/repository/AuthRepository.kt` ← **수정 대상** (`signOut`)
- `app/src/main/java/com/lsync/app/data/repository/SyncRepository.kt` ← **수정 대상** (clear 메서드 추가 + 생성자 의존성 추가)
- `app/src/main/java/com/lsync/app/di/AppModule.kt` ← **수정 대상** (`provideSyncRepository` 갱신)
- `app/src/main/java/com/lsync/app/data/local/dao/EventDao.kt` ← **수정 대상** (clear 메서드 추가)
- `app/src/main/java/com/lsync/app/data/local/dao/TodoDao.kt` ← **수정 대상**
- `app/src/main/java/com/lsync/app/data/local/dao/FinanceDao.kt` ← **수정 대상**
- `app/src/main/java/com/lsync/app/data/local/dao/BudgetDao.kt` ← **수정 대상**
- `app/src/main/java/com/lsync/app/data/local/dao/TodoTemplateDao.kt` ← **수정 대상**
- `app/src/main/java/com/lsync/app/notification/AlarmScheduler.kt` (읽기 전용 — `cancel(id)` 시그니처 확인)
- `app/src/main/java/com/lsync/app/ui/auth/AuthViewModel.kt` (읽기 전용 — `signOut()` 이 코루틴에서 suspend 호출됨을 확인)

이전에 만들어진 코드를 꼼꼼히 읽고, 설계 의도를 이해한 뒤 작업하라.

## 작업

### 1) 각 동기화 DAO에 전체 삭제 메서드 추가

다음 5개 DAO에 테이블 전체를 비우는 suspend 메서드를 추가하라(테이블명은 각 엔티티의 실제 테이블명을 사용):

- `EventDao` → `@Query("DELETE FROM events") suspend fun clearAll()`
- `TodoDao` → `@Query("DELETE FROM todos") suspend fun clearAll()`
- `FinanceDao` → `@Query("DELETE FROM finance") suspend fun clearAll()`
- `BudgetDao` → `@Query("DELETE FROM budgets") suspend fun clearAll()`
- `TodoTemplateDao` → `@Query("DELETE FROM todo_templates") suspend fun clearAll()`

### 2) `SyncRepository` 에 `clearLocalUserData()` 추가

- 생성자에 `todoTemplateDao: TodoTemplateDao` 와 `alarmScheduler: AlarmScheduler` 를 **추가**한다 (현재는 remote, eventDao, todoDao, financeDao, budgetDao 만 받음).
- 새 suspend 함수 `clearLocalUserData()` 를 추가한다. 동작:
  1. **삭제 전에** 예약된 알람을 먼저 취소한다(삭제 후에는 ID를 조회할 수 없으므로 순서 중요):
     - `eventDao.getFutureAlarmedEvents(today)` 결과 각 항목에 대해 `alarmScheduler.cancel(it.id)`
     - `todoDao.getFutureAlarmedTodos(today)` 결과 각 항목에 대해 `alarmScheduler.cancel(it.id)`
     - `today` 는 `LocalDate.now().toString()` (Floating Time, 타임존 변환 금지). 이 알람 취소 블록은 `runCatching` 으로 감싸 한 항목 실패가 전체 정리를 막지 않게 한다.
  2. 이후 5개 테이블을 모두 비운다: `eventDao.clearAll()`, `todoDao.clearAll()`, `financeDao.clearAll()`, `budgetDao.clearAll()`, `todoTemplateDao.clearAll()`.

**핵심 규칙 (이탈 금지):**
- **`reading_plan` 과 `memos` 테이블은 절대 비우지 마라.** 이유: 둘 다 로컬 전용(Firestore 미동기화)이라 비우면 통독 진행도·묵상 메모가 영구 손실되며 복원 경로가 없다. 동기화로 복원 가능한 테이블(events/todos/finance/budgets/todo_templates)만 비운다.
- 알람 취소를 테이블 삭제보다 **먼저** 수행하라. 순서를 바꾸면 cancel에 넘길 ID를 잃는다.

### 3) `AuthRepository.signOut()` 에서 clear 호출

`signOut()` 을 다음 순서로 바꾼다:
1. `syncRepository.clearLocalUserData()` (로컬 정리 — Firebase 사용자 컨텍스트가 아직 유효할 때)
2. `firebaseAuth.signOut()`

`signOut()` 은 이미 suspend이고 `AuthViewModel` 이 `viewModelScope.launch` 안에서 호출하므로 시그니처는 유지한다.

**근거:** Offline-First라 재로그인 시 `AuthRepository` 가 `pullAll(uid)` 로 해당 사용자의 데이터를 Firestore에서 복원한다. 따라서 로그아웃 시 동기화 대상 테이블을 비워도 데이터 손실이 없고, 계정 전환 시 이전 사용자 데이터 노출을 차단한다. (DAO 전수 userId 필터링은 범위가 크고 회귀 위험이 높아 채택하지 않는다.)

### 4) `AppModule.provideSyncRepository` 갱신

`provideSyncRepository` 의 파라미터와 생성자 호출에 `todoTemplateDao: TodoTemplateDao` 와 `alarmScheduler: AlarmScheduler` 를 추가하라. 두 의존성 모두 이미 Hilt가 제공한다(`TodoTemplateDao` 는 `provideTodoTemplateDao`, `AlarmScheduler` 는 `@Inject constructor` + `@Singleton`). **누락 시 Hilt 그래프가 깨져 런타임 크래시가 난다 (CLAUDE.md CRITICAL).**

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 아키텍처 체크리스트를 확인한다:
   - `clearLocalUserData()` 가 `reading_plan`/`memos` 를 건드리지 않는가?
   - 알람 취소가 테이블 삭제보다 먼저 수행되는가?
   - `AppModule.provideSyncRepository` 가 새 생성자 시그니처와 일치하는가? (Hilt 그래프 무결성)
   - `signOut()` 이 clear 후 `firebaseAuth.signOut()` 순서인가?
   - Room이 SSOT인가? (UI가 직접 Firestore를 구독하지 않는가?)
3. 결과에 따라 `phases/18-stabilization/index.json` 의 step 1을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "산출물 한 줄 요약"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- `reading_plan`/`memos` 테이블 또는 그 DAO에 clear 메서드를 추가하거나 호출하지 마라. 이유: 로컬 전용·복원 불가 데이터를 영구 손실시킨다.
- `AppDatabase.clearAllTables()` 를 사용하지 마라. 이유: reading_plan/memos까지 함께 지워진다. 반드시 테이블별 `clearAll()` 로 선택적으로 비운다.
- DAO 쿼리에 userId 필터를 전수 추가하는 식으로 접근하지 마라. 이유: 범위가 크고 회귀 위험이 높다. 이 step의 해법은 로그아웃 시 clear다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
