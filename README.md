# L-Sync

**일정 · 할 일 · 재정 · 신앙을 하나의 흐름으로 동기화하는 개인 맞춤형 안드로이드 생산성 허브**

---

## 기능

| 탭 | 주요 기능 |
|----|----------|
| **홈** | 오늘 날짜, 통독 진행·연속 읽기 streak·주간 히트맵, 일정·할일 미리보기, 가계부 요약 |
| **일정** | 캘린더 그리드, 일정/할일 통합 관리, RRULE 반복 일정, 반복 할 일 템플릿 |
| **가계부** | 수입·지출 기록, Todo 완료 시 자동 생성, 통계, 정산 추적 |
| **성경** | 개역개정·ESV 병렬 뷰어, 1년 통독 계획, 검색, 절 묵상 메모 |

### 핵심 동작

- **Offline-First** — Room이 SSOT. 네트워크 없이도 모든 기능 동작. Firestore는 백그라운드 동기화 전용.
- **Todo ↔ 가계부 연동** — Todo 완료 시 가계부 항목 자동 생성. 미완료 시 통계 제외(soft delete). 삭제 시 가계부 유지.
- **반복 할 일 템플릿** — 빈도·간격(N주마다)·요일을 지정해 템플릿을 만들면 앱이 개별 Todo를 client-side materialization으로 생성. 생성된 인스턴스도 Firestore 동기화·마감일 알람을 동일하게 거친다.
- **정산 추적** — 공동 결제 후 돌려받는 흐름을 추적. 정산 입금은 수입에 섞지 않고 별도 집계해 잔액만 정확히 반영.
- **결제 알림 자동 파싱** — 알림 리스너가 결제 알림 제목을 파싱하여 가계부에 자동 입력.
- **성경 데이터 완전 오프라인** — 개역개정 31,024절 / ESV 31,086절 SQLite asset 번들.

---

## 기술 스택

- **언어:** Kotlin
- **UI:** Jetpack Compose + Material3
- **DI:** Hilt
- **로컬 DB:** Room 2.6.1
- **백엔드:** Firebase Firestore / Auth / Crashlytics / Analytics
- **백그라운드:** WorkManager + AlarmManager
- **폰트:** Pretendard (UI), Instrument Serif Italic (금액 기호·절 번호)
- **최소 SDK:** 26 / 타깃 SDK: 34

---

## 아키텍처

```
UI Layer (Compose)
    ↓ collectAsState()
ViewModel (Hilt)
    ↓
Repository
    ↓                    ↓
Room (SSOT)         FirestoreDataSource
```

```
app/src/main/java/com/lsync/app/
├── ui/{screen}/          # Composable 화면 + ViewModel
├── data/local/entity/    # Room Entity
├── data/local/dao/       # Room DAO
├── data/remote/          # FirestoreDataSource
├── data/repository/      # Repository (Room + Firestore 조합)
├── di/                   # AppModule.kt (Hilt)
├── notification/         # AlarmScheduler, 결제 알림 파서
└── worker/               # AlarmRestoreWorker, TodoMaterializerWorker
```

---

## 개발 명령어

```bash
# 디버그 빌드
./gradlew assembleDebug

# 린트
./gradlew lintDebug

# 유닛 테스트
./gradlew testDebugUnitTest

# Firebase App Distribution 배포
./gradlew assembleDebug appDistributionUploadDebug --no-configuration-cache
```

---

## 개발 로드맵

- [x] **Phase 1** — 일정 + 할 일 MVP (캘린더 CRUD, Todo, 알림, Firebase 연동)
- [x] **Phase 2** — 가계부 자동화 (Finance CRUD, Todo 연동, 통계, CSV Export, 결제 알림 파싱, 정산 추적)
- [x] **Phase 3** — 성경 뷰어 + 1년 통독 계획 + 절 묵상 메모
- [x] **Phase 4** — 홈 위젯 (Jetpack Glance, 5×2, 4개 섹션)
- [x] **Phase 5** — Firebase Auth Google 로그인 + userId 마이그레이션
- [x] **Phase 6** — 통독 고급화 (BibleScreen 연동, 연속 읽기 streak, 주간 히트맵)
- [x] **Phase 7** — 알람 라이프사이클 (생성·수정·삭제 시 AlarmManager 연동, 마감일 09:00 리마인더, 정확 알람 폴백)
- [x] **Phase 8** — 반복 할 일 템플릿 (RRULE 빌더, 인스턴스 동기화·알람 일원화, 즉시 materialization 트리거)
