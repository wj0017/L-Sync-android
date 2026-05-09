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

## 디렉토리 구조

```
app/src/main/java/com/lsync/app/
├── LSyncApplication.kt         # @HiltAndroidApp, WorkManager 설정
├── MainActivity.kt             # @AndroidEntryPoint, NavGraph 진입점
├── di/
│   └── AppModule.kt            # Hilt SingletonComponent 모든 의존성 제공
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt      # @Database (EventEntity, TodoEntity, FinanceEntity, TodoTemplateEntity)
│   │   ├── entity/             # Room @Entity 클래스들
│   │   └── dao/
│   │       ├── EventDao.kt
│   │       ├── TodoDao.kt
│   │       ├── FinanceDao.kt
│   │       └── (TodoTemplateDao.kt 미존재 — TodoMaterializerWorker 구현 시 생성 필요)
│   ├── remote/
│   │   └── FirestoreDataSource.kt  # Firestore Batch Write 등 원격 작업
│   └── repository/
│       ├── EventRepository.kt
│       ├── TodoRepository.kt
│       └── FinanceRepository.kt
├── ui/
│   ├── theme/
│   │   ├── Color.kt            # 다크 미니멀 색상 시스템
│   │   ├── Type.kt             # Pretendard + Instrument Serif
│   │   └── Theme.kt            # 다크 전용 MaterialTheme
│   ├── navigation/
│   │   └── NavGraph.kt         # 4-tab BottomNav (Calendar/Todo/Finance/Bible)
│   ├── calendar/               # CalendarScreen, CalendarViewModel, 공유 컴포넌트
│   ├── todo/                   # TodoScreen, TodoViewModel
│   ├── finance/                # FinanceScreen, FinanceViewModel
│   └── bible/                  # BibleScreen (Phase 3 플레이스홀더)
├── notification/
│   ├── AlarmScheduler.kt       # AlarmManager.setExactAndAllowWhileIdle
│   ├── AlarmReceiver.kt        # 알람 수신 BroadcastReceiver
│   └── BootReceiver.kt         # 재부팅 후 알람 복구 트리거
└── worker/
    ├── AlarmRestoreWorker.kt   # 재부팅 시 알람 복구
    └── TodoMaterializerWorker.kt  # 반복 Todo 생성
```

## 핵심 설계 결정

### Offline-First
Room이 SSOT. 모든 쓰기는 Room에 먼저 저장하고, 이후 Firestore에 비동기 동기화.
UI는 Room Flow를 구독하므로 네트워크 없이도 즉각 반응.

### Todo ↔ Finance 연동
- 완료: `TodoRepository.complete()` → `FinanceEntity` 생성 → Room 저장 → Firestore Batch Write
- 미완료: `FinanceEntity.isExcluded = true` (삭제 금지)
- 삭제: `FinanceEntity.sourceTodoId = null` (데이터 유지)

### 공유 Dialog 컴포넌트 (CalendarScreen.kt에 정의)
- `LSyncDialog` — 확인/취소 다이얼로그
- `LSyncInputDialog` — 입력 필드 포함 다이얼로그
- `LSyncField` — 단일 라인 입력 필드
- `LSyncCheckbox` — 커스텀 체크박스
