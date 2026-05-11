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

성경 데이터는 Firestore 미사용. SQLite asset 번들(오프라인 전용).

## 디렉토리 구조

```
app/src/main/java/com/lsync/app/
├── LSyncApplication.kt             # @HiltAndroidApp, WorkManager 설정
├── MainActivity.kt                 # @AndroidEntryPoint, NavGraph 진입점
├── di/
│   └── AppModule.kt                # Hilt SingletonComponent — 전체 의존성 제공
│                                   # AppDatabase, BibleDatabase, EsvDatabase,
│                                   # 모든 DAO, Repository, Firestore 포함
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt          # 사용자 데이터 DB (Event, Todo, Finance, TodoTemplate)
│   │   ├── BibleDatabase.kt        # 성경 DB (읽기 전용, assets/bible.db 번들)
│   │   ├── EsvDatabase.kt          # ESV 영어 성경 DB (읽기 전용, assets/esv.db 번들)
│   │   ├── FinanceCategory.kt      # 가계부 카테고리 상수
│   │   ├── entity/
│   │   │   ├── EventEntity.kt
│   │   │   ├── TodoEntity.kt
│   │   │   ├── TodoTemplateEntity.kt
│   │   │   ├── FinanceEntity.kt
│   │   │   ├── BibleVerseEntity.kt # bible_verses 테이블 (idx, book, chapter, verse, text, ...)
│   │   │   └── EsvVerseEntity.kt   # esv_verses 테이블 (idx, book, chapter, verse, text)
│   │   └── dao/
│   │       ├── EventDao.kt
│   │       ├── TodoDao.kt
│   │       ├── TodoTemplateDao.kt
│   │       ├── FinanceDao.kt
│   │       ├── BibleDao.kt         # getBooks, getChapterCount(s), getVerses, search
│   │       └── EsvDao.kt           # getVerses(book, chapter)
│   ├── remote/
│   │   └── FirestoreDataSource.kt  # Firestore Batch Write 등 원격 작업
│   └── repository/
│       ├── EventRepository.kt
│       ├── TodoRepository.kt
│       └── FinanceRepository.kt
├── ui/
│   ├── theme/
│   │   ├── Color.kt                # 다크 미니멀 색상 시스템
│   │   ├── Type.kt                 # Pretendard + Instrument Serif
│   │   └── Theme.kt                # 다크 전용 MaterialTheme
│   ├── navigation/
│   │   └── NavGraph.kt             # 4-tab BottomNav (Calendar/Todo/Finance/Bible)
│   ├── calendar/                   # CalendarScreen, CalendarViewModel
│   ├── todo/                       # TodoScreen, TodoViewModel
│   ├── finance/                    # FinanceScreen, FinanceViewModel
│   └── bible/
│       ├── BibleScreen.kt          # HorizontalPager 기반 뷰어
│       │                           # 검색, ESV 병렬보기, 2단계 목차(책→장), 스와이프 이동
│       └── BibleViewModel.kt       # BibleDao + EsvDao 조합
│                                   # SharedPreferences로 마지막 위치 저장
├── notification/
│   ├── AlarmScheduler.kt           # AlarmManager.setExactAndAllowWhileIdle
│   ├── AlarmReceiver.kt            # 알람 수신 BroadcastReceiver
│   ├── BootReceiver.kt             # 재부팅 후 알람 복구 트리거
│   ├── PaymentNotificationParser.kt # 결제 알림 파싱 (순수 Kotlin)
│   │                               # title 우선 금액 추출, 잔액 필터링, INCOME/EXPENSE 분류
│   └── PaymentNotificationService.kt # NotificationListenerService
│                                   # 지원: 토스뱅크, 신한, KB, 카카오뱅크
└── worker/
    ├── AlarmRestoreWorker.kt       # 재부팅 시 알람 복구
    └── TodoMaterializerWorker.kt   # 반복 Todo 생성
```

## 핵심 설계 결정

### Offline-First
Room이 SSOT. 모든 쓰기는 Room에 먼저 저장하고, 이후 Firestore에 비동기 동기화.
UI는 Room Flow를 구독하므로 네트워크 없이도 즉각 반응.

### 성경 데이터 — 완전 오프라인
- `BibleDatabase` (bible_v3.db): 개역개정 4판 31,024절, SQLite asset 번들
- `EsvDatabase` (esv.db): ESV 31,086절, SQLite asset 번들
- Firestore 미사용. 읽기 전용이므로 동기화 불필요.
- DB 콘텐츠 버전(`bible_prefs`) 관리로 앱 업데이트 시 자동 갱신.

### Todo ↔ Finance 연동
- 완료: `TodoRepository.complete()` → `FinanceEntity` 생성 → Room 저장 → Firestore Batch Write
- 미완료: `FinanceEntity.isExcluded = true` (삭제 금지)
- 삭제: `FinanceEntity.sourceTodoId = null` (데이터 유지)

### 결제 알림 자동 가계부
- `PaymentNotificationService`: 지원 앱 알림 수신 → Parser → `FinanceRepository.create()`
- Parser: title에서 금액 우선 추출 (잔액 오인 방지), 입금/출금 키워드로 INCOME/EXPENSE 분류

### 공유 Dialog 컴포넌트 (CalendarScreen.kt에 정의)
- `LSyncDialog` — 확인/취소 다이얼로그
- `LSyncInputDialog` — 입력 필드 포함 다이얼로그
- `LSyncField` — 단일 라인 입력 필드
- `LSyncCheckbox` — 커스텀 체크박스
