# L-Sync Architecture

## 레이어 구조

```
UI Layer (Compose)
    ↓ collectAsState()
ViewModel (Hilt)
    ↓
Repository (Room Flow + Firestore 동기화)
    ↓                    ↓
Room (SSOT)         FirestoreDataSource
```

성경·통독 데이터는 Firestore 미사용. SQLite asset 번들(오프라인 전용).

## 디렉토리 구조

```
app/src/main/java/com/lsync/app/
├── LSyncApplication.kt
├── MainActivity.kt
├── di/
│   └── AppModule.kt                # Hilt SingletonComponent
│                                   # AppDatabase(v4), BibleDatabase, EsvDatabase,
│                                   # 모든 DAO, Repository, Firestore 포함
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt          # v4 — Event, Todo, Finance, TodoTemplate, ReadingPlan, Memo
│   │   ├── BibleDatabase.kt        # 읽기 전용, assets/bible.db 번들
│   │   ├── EsvDatabase.kt          # 읽기 전용, assets/esv.db 번들
│   │   ├── FinanceCategory.kt
│   │   ├── entity/
│   │   │   ├── EventEntity.kt
│   │   │   ├── TodoEntity.kt
│   │   │   ├── TodoTemplateEntity.kt
│   │   │   ├── FinanceEntity.kt
│   │   │   ├── ReadingPlanEntity.kt  # reading_plan 테이블 (date, book, chapter, isRead)
│   │   │   ├── MemoEntity.kt          # memos 테이블 (book, chapter, verse, text, date)
│   │   │   ├── BibleVerseEntity.kt
│   │   │   └── EsvVerseEntity.kt
│   │   └── dao/
│   │       ├── EventDao.kt
│   │       ├── TodoDao.kt
│   │       ├── TodoTemplateDao.kt
│   │       ├── FinanceDao.kt          # 정산: observeAllSettlementItems, getBySettlementGroup
│   │       ├── ReadingPlanDao.kt     # observeForDate, markRead, deleteFromDate, getReadDates
│   │       ├── MemoDao.kt             # observeForChapter, insert, deleteById
│   │       ├── BibleDao.kt
│   │       └── EsvDao.kt
│   ├── remote/
│   │   └── FirestoreDataSource.kt    # push(upsert/batch) + fetchEvents/fetchTodos/fetchFinance(복원 pull)
│   └── repository/
│       ├── EventRepository.kt
│       ├── TodoRepository.kt          # createTemplate, upsertMaterialized(로컬+알람+동기화), create/complete/uncheck/delete
│       ├── FinanceRepository.kt
│       ├── ReadingPlanRepository.kt  # 1년 통독 시퀀스 계산, 설정(SharedPreferences), getStreak/getWeeklyHeatmap
│       ├── SyncRepository.kt         # pullAll(userId) — Firestore→Room last-write-wins 복원(upsert-only)
│       ├── AuthRepository.kt         # Firebase Auth 래퍼. currentUserId, authState, signInWithGoogle, signOut. 로그인·자동로그인 시 pullAll 트리거
│       └── AuthMigrationHelper.kt    # "local_user" → Firebase UID Room 일괄 마이그레이션 (멱등)
├── ui/
│   ├── theme/
│   │   ├── Color.kt
│   │   ├── Type.kt
│   │   └── Theme.kt
│   ├── auth/
│   │   ├── LoginScreen.kt          # Google 로그인 버튼·로딩·에러 UI
│   │   └── AuthViewModel.kt        # AuthUiState(isSignedIn/isLoading/error/userId), signInWithGoogle, signOut
│   ├── navigation/
│   │   └── NavGraph.kt             # 4-tab: Home / Schedule / Finance / Bible. 미로그인 시 LoginScreen early return
│   ├── home/
│   │   ├── HomeScreen.kt           # 오늘 날짜·통독·일정 미리보기·가계부 요약. 연속 읽기 streak + 주간 히트맵. 헤더 로그아웃 메뉴 포함
│   │   └── HomeViewModel.kt        # EventRepo + TodoRepo + FinanceRepo + ReadingPlanRepo 조합 (streak/heatmap 포함)
│   ├── schedule/
│   │   ├── ScheduleScreen.kt       # 단일 LazyColumn 통스크롤(헤더·캘린더·구분선·목록) + ExpandableFab
│   │   │                           # 위로 스크롤하면 캘린더가 밀려 사라지고 목록만 남음
│   │   │                           # LSyncDialog, CreateEventDialog, CreateTodoDialog, CreateRepeatTodoDialog(반복 템플릿), 활성 템플릿 목록 포함
│   │   ├── RecurrenceOptions.kt    # RecurrenceOption 모델 + buildRrule(FREQ/INTERVAL/BYDAY)·describeRrule(한국어 요약)
│   │   └── ScheduleViewModel.kt    # CalendarViewModel + TodoViewModel 통합. templates 상태·createTemplate·deleteTemplate
│   ├── finance/
│   │   ├── FinanceScreen.kt
│   │   ├── FinanceViewModel.kt
│   │   └── TransactionFormSheet.kt
│   └── bible/
│       ├── BibleScreen.kt          # HorizontalPager, ESV/개역개정 교차, 목차, 검색, 절 묵상 메모, PlanChapterBanner(오늘 통독 장 읽음 토글)
│       └── BibleViewModel.kt       # 개역개정 보조 텍스트 기본 표시(showKorean=true), ReadingPlanRepository 주입 → isPlanChapter/isPlanChapterRead
├── notification/
│   ├── AlarmScheduler.kt              # scheduleForEvent/scheduleForTodo(트리거 계산 단일화), 정확알람 권한 폴백
│   ├── AlarmReceiver.kt
│   ├── BootReceiver.kt
│   ├── PaymentNotificationParser.kt  # title 정규식 단독 게이트 (바디 폴백 제거)
│   └── PaymentNotificationService.kt
├── worker/
│   ├── AlarmRestoreWorker.kt
│   ├── TodoMaterializerWorker.kt     # 반복 인스턴스 생성 → TodoRepository.upsertMaterialized(동기화·알람 포함)
│   ├── MaterializationTrigger.kt     # 템플릿 생성 직후 1회 즉시 materialization (OneTimeWork)
│   └── WidgetRefreshWorker.kt        # 30분 주기 위젯 갱신 (비-Hilt CoroutineWorker)
└── ui/widget/
    ├── LSyncWidget.kt                # GlanceAppWidget — 4개 섹션 UI + loadWidgetState()
    ├── LSyncWidgetReceiver.kt        # GlanceAppWidgetReceiver
    ├── WidgetEntryPoint.kt           # Hilt @EntryPoint (TodoDao, EventDao, FinanceDao, ReadingPlanDao, AuthRepository)
    ├── WidgetRefreshHelper.kt        # @Singleton — 데이터 변경 시 LSyncWidget().update() 즉시 호출 (Home/Finance/Schedule VM에서 사용)
    └── WidgetState.kt                # todos, events, monthExpense, monthIncome, readingPlan + empty()
```

## 핵심 설계 결정

### Offline-First
Room이 SSOT. 모든 쓰기는 Room에 먼저 저장하고, 이후 Firestore에 비동기 동기화(push).
UI는 Room Flow를 구독하므로 네트워크 없이도 즉각 반응. UI는 Firestore를 직접 구독하지 않는다.

### Firestore 복원 동기화 (pull)
- **목적:** 재설치·기기 변경 시 원격 데이터를 Room으로 되돌린다(기존엔 push 전용이라 복원 경로가 없었음).
- **`SyncRepository.pullAll(userId)`:** `FirestoreDataSource.fetchEvents/fetchTodos/fetchFinance`로 원격 문서를 받아 **last-write-wins**로 머지 — 항목별 `updatedAt`이 로컬보다 클 때만 upsert(로컬이 같거나 최신이면 보존). **upsert-only**라 로컬 전용 데이터를 삭제하지 않음.
- **삭제 반영:** Event/Finance는 원격 hard delete라 fetch에 없고, Todo는 `deletedAt`이 채워진 채 와서 upsert로 soft delete가 복원된다.
- **트리거 시점:** `AuthRepository`가 로그인(백그라운드)·자동로그인(앱 재실행) 시 `AuthMigrationHelper.migrate` **직후** `pullAll` 실행. UI는 Room Flow로 자동 반영. 실패는 `syncSafe`로 Crashlytics(`sync_failed`)에 기록하고 흐름을 막지 않음. (Auth↔Sync 순환 의존 없음 — Sync는 Auth를 모름)

### 성경 데이터 — 완전 오프라인
- `BibleDatabase` (bible_v3.db): 개역개정 4판 31,024절
- `EsvDatabase` (esv.db): ESV 31,086절
- 모두 SQLite asset 번들. Firestore 미사용, 읽기 전용.
- DB 콘텐츠 버전(`bible_prefs`) 관리로 앱 업데이트 시 자동 갱신.
- `esvOnTop` 상태로 ESV/개역개정 primary 순서 전환, BOOK_NAMES_EN 매핑으로 영문 책명 연동.

### 성경 통독 계획 (ReadingPlan)
- `ReadingPlanRepository`: 66권 챕터 수 배열 기반으로 시작 위치·순서·일일 챕터 수 설정 가능.
- `ReadingOrder.CANONICAL` (창~계) / `ReadingOrder.NT_FIRST` (신약→구약) 지원.
- 설정은 SharedPreferences (`reading_plan_prefs`)에 저장, 진행 상황은 Room `reading_plan` 테이블에 저장.
- 설정 변경 시 오늘 이후 row 삭제 후 재계산 (`resetFromToday`).
- AppDatabase v2 마이그레이션으로 추가 (MIGRATION_1_2).

### Todo ↔ Finance 연동
- 완료: `TodoRepository.complete()` → `FinanceEntity` 생성 → Room → Firestore Batch Write
- 미완료: `FinanceEntity.isExcluded = true` (삭제 금지)
- 삭제: `FinanceEntity.sourceTodoId = null` (데이터 유지)

### 정산 추적 (Finance Settlement)
- 별도 테이블 없이 `FinanceEntity.settlementGroupId` 단일 컬럼으로 그룹화. EXPENSE가 리더, INCOME이 정산 입금.
- `FinanceViewModel`이 `observeAllSettlementItems()`(전 기간)를 구독해 그룹별 `SettlementSummary`(받은 금액/잔액/완료 여부)를 실시간 계산 → 교차월 정산도 추적.
- 정산 입금은 수입·지출에 합산하지 않고 별도 집계(`reimbursed`). `net = income + reimbursed − expense`.
- **지출 표시는 순지출**(`expense − reimbursed`, 음수 방지)로 노출 — 정산으로 돌려받은 금액을 미리 차감해 체감 지출과 일치(FinanceScreen·위젯 공통).
- 반자동 연결: 미완료 정산이 있고 `잔액 ≥ 수입액 AND 지출일 ≤ 수입일`인 INCOME에만 "정산에 연결" 노출.
- 월별 거래는 Room Flow(`observeByMonth`) 단일 구독. 저장/연결 후 재조회하지 않고 Flow 자동 재방출에 의존(`monthJob`으로 이전 구독 취소).

### 성경 묵상 메모
- `MemoDao.observeForChapter(book, chapter)`를 구독해 현재 장의 절별 메모를 표시. 로컬 전용(Firestore 미사용).

### 통독 고급화 (BibleScreen 연동 · streak · 히트맵)
- **BibleScreen 연동:** `BibleViewModel`이 `ReadingPlanRepository`를 주입받아 현재 펼친 (book, chapter)가 오늘 통독 분량인지(`isPlanChapter`)와 읽음 여부(`isPlanChapterRead`)를 계산. 통독 장이면 `PlanChapterBanner`를 노출하고 탭으로 읽음 토글.
- **연속 읽기 streak:** `ReadingPlanRepository.getStreak()` — 오늘부터 거꾸로 읽은 날짜가 연속된 일수. `ReadingPlanDao.getReadDates()`(읽은 날짜 distinct)를 HashSet으로 조회.
- **주간 히트맵:** `getWeeklyHeatmap()` — 최근 7일(6일 전 → 오늘) 각 날짜의 읽음 여부 `List<Boolean>`. 홈 화면 도트로 렌더링.

### 일정·할 일 알람 라이프사이클
- **단일 진실 원천:** 트리거 시각 계산은 `AlarmScheduler.scheduleForEvent(event)` / `scheduleForTodo(todo)` 안에만 존재. 라이브 등록(Repository)과 재부팅 복원(`AlarmRestoreWorker`)이 같은 메서드를 공유해 시각 드리프트를 방지.
- **트리거 규칙:** 시간 지정 일정 = 시작 시각 그대로 / 종일 일정·마감일 Todo = 그 날 **09:00**(`REMINDER_HOUR`, `ZoneId.systemDefault()`). `trigger ≤ now`면 등록 스킵(과거 알람 즉시 발화 방지).
- **라이브 연결:** Event는 `save()`에서 등록·`delete()`에서 취소. Todo는 `create`/`uncheck`에서 등록, `complete`/`delete`에서 취소. → 생성 시점부터 알람이 실제 발화(재부팅 불필요).
- **게이팅:** Event는 `hasAlarm` 플래그(false면 cancel). Todo는 별도 플래그 없이 *마감일 있는 모든 미완료 Todo = 자동 리마인더*(완료·`dueDate==null`이면 cancel).
- **정확 알람 권한 폴백:** Android 12+에서 정확 알람 권한이 없으면 `setExactAndAllowWhileIdle` 대신 `setAndAllowWhileIdle`(inexact)로 폴백 — 권한 미허용 시 알람이 조용히 사라지지 않게. 트리거 계산·등록은 `runCatching`으로 감싸 한 항목 실패가 복원 루프를 막지 않음.

### 결제 알림 자동 가계부
- `PaymentNotificationService` → `PaymentNotificationParser` → `FinanceRepository.create()`
- Parser: **title에서만** `{금액}원 결제/입금` 패턴 매칭 (바디 폴백 제거 — 알림성 메시지 false positive 방지).

### 홈 위젯 (Jetpack Glance)
- `LSyncWidgetReceiver`(GlanceAppWidgetReceiver) → `LSyncWidget`(GlanceAppWidget) → `loadWidgetState()` → `WidgetContent()`
- 위젯은 Hilt 자동 주입 불가 → `WidgetEntryPoint`(@EntryPoint)로 `EntryPointAccessors.fromApplication()` 패턴 사용.
- 데이터 소스: Room 전용. Firestore 미사용 (네트워크 없이도 동작).
- 크기: 5×2 cells (minWidth 250dp, minHeight 110dp). 2열 카드 레이아웃, 가계부 막대 차트, 통독은 오늘 챕터 목록 표시.
- 갱신: ① 데이터 변경 시 `WidgetRefreshHelper.requestUpdate()`로 즉시 갱신(Home/Finance/Schedule ViewModel에서 호출), ② `WidgetRefreshWorker`(30분 주기, `ExistingPeriodicWorkPolicy.KEEP`), ③ 시스템 `updatePeriodMillis`(fallback).
- Finance 집계: 이달 지출은 **순지출**(`expense − reimbursed`, 음수 방지)로 표시. `reimbursed`(`INCOME` & `settlementGroupId != null`)와 정산 입금은 수입·지출에 합산하지 않음 (PRD 2.4 정산 정책 동일 적용).

### Firebase Auth (Google 로그인)
- `AuthRepository`: `callbackFlow` + `stateIn(Eagerly)`로 `FirebaseAuth.AuthStateListener`를 `StateFlow<FirebaseUser?>`로 래핑.
- `AuthViewModel`: 초기값을 `authRepository.currentUserId != null`로 즉시 계산 → 앱 재실행 시 LoginScreen 깜빡임 방지.
- `NavGraph`: `!authState.isSignedIn`이면 `LoginScreen` early return — NavHost 진입 전 차단하여 BottomBar 노출 방지.
- `di/Qualifiers.kt`: `@ApplicationScope` Qualifier로 `CoroutineScope(SupervisorJob() + Dispatchers.Default)` 주입.

### userId 마이그레이션
- `AuthMigrationHelper`: 첫 로그인 시 Room의 `"local_user"` userId를 실제 Firebase UID로 일괄 UPDATE. SharedPreferences 플래그로 멱등성 보장.
- 신규 로그인: `signInWithGoogle()` 내부에서 마이그레이션 동기 await 후 반환 → NavGraph 전환 시점에 데이터 준비 완료.
- 앱 재실행(자동 로그인): `AuthRepository.init`에서 `scope.launch` 비동기 실행 (이미 마이그레이션됐으면 즉시 종료).

### 공유 Dialog 컴포넌트 (ScheduleScreen.kt에 정의)
- `LSyncDialog` — 확인/취소
- `LSyncInputDialog` — 입력 필드 포함
- `LSyncField` — 단일 라인 입력
- `LSyncCheckbox` — 커스텀 체크박스
