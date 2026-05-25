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
│   │       ├── ReadingPlanDao.kt     # observeForDate, markRead, deleteFromDate
│   │       ├── MemoDao.kt             # observeForChapter, insert, deleteById
│   │       ├── BibleDao.kt
│   │       └── EsvDao.kt
│   ├── remote/
│   │   └── FirestoreDataSource.kt
│   └── repository/
│       ├── EventRepository.kt
│       ├── TodoRepository.kt
│       ├── FinanceRepository.kt
│       └── ReadingPlanRepository.kt  # 1년 통독 시퀀스 계산, 설정(SharedPreferences)
├── ui/
│   ├── theme/
│   │   ├── Color.kt
│   │   ├── Type.kt
│   │   └── Theme.kt
│   ├── navigation/
│   │   └── NavGraph.kt             # 4-tab: Home / Schedule / Finance / Bible
│   ├── home/
│   │   ├── HomeScreen.kt           # 오늘 날짜·통독·일정 미리보기·가계부 요약
│   │   └── HomeViewModel.kt        # EventRepo + TodoRepo + FinanceRepo + ReadingPlanRepo 조합
│   ├── schedule/
│   │   ├── ScheduleScreen.kt       # 단일 LazyColumn 통스크롤(헤더·캘린더·구분선·목록) + ExpandableFab
│   │   │                           # 위로 스크롤하면 캘린더가 밀려 사라지고 목록만 남음
│   │   │                           # LSyncDialog, CreateEventDialog, CreateTodoDialog 포함
│   │   └── ScheduleViewModel.kt    # CalendarViewModel + TodoViewModel 통합
│   ├── finance/
│   │   ├── FinanceScreen.kt
│   │   ├── FinanceViewModel.kt
│   │   └── TransactionFormSheet.kt
│   └── bible/
│       ├── BibleScreen.kt          # HorizontalPager, ESV/개역개정 교차, 목차, 검색, 절 묵상 메모
│       └── BibleViewModel.kt       # 개역개정 보조 텍스트 기본 표시(showKorean=true)
├── notification/
│   ├── AlarmScheduler.kt
│   ├── AlarmReceiver.kt
│   ├── BootReceiver.kt
│   ├── PaymentNotificationParser.kt  # title 정규식 단독 게이트 (바디 폴백 제거)
│   └── PaymentNotificationService.kt
└── worker/
    ├── AlarmRestoreWorker.kt
    └── TodoMaterializerWorker.kt
```

## 핵심 설계 결정

### Offline-First
Room이 SSOT. 모든 쓰기는 Room에 먼저 저장하고, 이후 Firestore에 비동기 동기화.
UI는 Room Flow를 구독하므로 네트워크 없이도 즉각 반응.

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
- 반자동 연결: 미완료 정산이 있고 `잔액 ≥ 수입액 AND 지출일 ≤ 수입일`인 INCOME에만 "정산에 연결" 노출.
- 월별 거래는 Room Flow(`observeByMonth`) 단일 구독. 저장/연결 후 재조회하지 않고 Flow 자동 재방출에 의존(`monthJob`으로 이전 구독 취소).

### 성경 묵상 메모
- `MemoDao.observeForChapter(book, chapter)`를 구독해 현재 장의 절별 메모를 표시. 로컬 전용(Firestore 미사용).

### 결제 알림 자동 가계부
- `PaymentNotificationService` → `PaymentNotificationParser` → `FinanceRepository.create()`
- Parser: **title에서만** `{금액}원 결제/입금` 패턴 매칭 (바디 폴백 제거 — 알림성 메시지 false positive 방지).

### 공유 Dialog 컴포넌트 (ScheduleScreen.kt에 정의)
- `LSyncDialog` — 확인/취소
- `LSyncInputDialog` — 입력 필드 포함
- `LSyncField` — 단일 라인 입력
- `LSyncCheckbox` — 커스텀 체크박스
