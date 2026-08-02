# L-Sync

**일정 · 할 일 · 재정 · 신앙을 하나의 흐름으로 동기화하는 개인 맞춤형 안드로이드 생산성 허브**

---

## 기능

| 탭 | 주요 기능 |
|----|----------|
| **홈** | 오늘 날짜, 통독 진행·연속 읽기 streak·주간 히트맵, 일정·할일 미리보기, 가계부 요약, 통합 검색·월간 리포트 진입점 |
| **일정** | 캘린더 그리드, 일정/할일 통합 관리(롱프레스 수정), RRULE 반복 일정, 반복 할 일 템플릿 |
| **가계부** | 수입·지출 기록, Todo 완료 시 자동 생성, 통계 대시보드(월별 추세·카테고리), 카테고리별·전체 월 예산, 정산 추적 |
| **성경** | 개역개정·ESV 병렬 뷰어, 1년 통독 계획, 검색, 절 묵상 메모 |
| **검색** | 일정·할일·가계부 횡단 통합 검색(홈 헤더 진입, 결과 탭→해당 화면 이동) |
| **리포트** | 월간 횡단 집계 — 할일 완료율, 일정 발생 수, 순지출·수입·상위 카테고리, 통독 장·일수(홈 헤더 진입, 월 이동) |

### 핵심 동작

- **Offline-First** — Room이 SSOT. 네트워크 없이도 모든 기능 동작. Firestore는 백그라운드 동기화 전용(push). 로그인 시 원격 데이터를 last-write-wins로 Room에 복원(pull).
- **Todo ↔ 가계부 연동** — Todo 완료 시 가계부 항목 자동 생성. 미완료 시 통계 제외(soft delete). 삭제 시 가계부 유지.
- **반복 할 일 템플릿** — 빈도·간격(N주마다)·요일을 지정해 템플릿을 만들면 앱이 개별 Todo를 client-side materialization으로 생성. 생성된 인스턴스도 Firestore 동기화·마감일 알람을 동일하게 거친다.
- **반복 일정 (iCal 읽기-전개)** — RRULE을 순수 Kotlin 엔진으로 전개해 캘린더·홈·위젯에 표시. 단건/이후/전체 범위 삭제·수정, exdate·override 지원.
- **월 예산** — 카테고리별·전체 월 한도를 설정하면 대시보드에서 진행률과 초과 경고를 표시. 매월 반복되며 Firestore 동기화.
- **정산 추적** — 공동 결제 후 돌려받는 흐름을 추적. 정산 입금은 수입에 섞지 않고 별도 집계해 잔액만 정확히 반영.
- **월간 리포트** — 네 도메인을 한 달 단위로 묶어 보여준다. 반복 일정은 전개한 발생 수로 세고, 가계부는 대시보드와 동일한 정산 정책(순지출·정산입금 제외)을 적용해 화면 간 수치가 어긋나지 않는다.
- **알림·위젯 딥링크** — 알림·위젯을 탭하면 해당 탭으로 이동(콜드스타트 보존). Todo 알림에서 바로 완료 처리.
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
- [x] **Phase 9** — Firestore 복원 동기화 (pull) (로그인 시 last-write-wins upsert, 재설치·기기 변경 복원)
- [x] **Phase 10** — 일정·할 일 수정 (카드 롱프레스 편집, 완료·연동 필드 보존, 다이얼로그 prefill 재사용)
- [x] **Phase 11** — 가계부 통계 대시보드 (최근 6개월 추세·카테고리별 지출 Canvas 차트, 정산 규칙 일관)
- [x] **Phase 12** — 권한 재진입 (홈 메뉴에서 미허용 알림·정확알람·결제알림 접근 권한 바로가기, 경량)
- [x] **Phase 13** — 알림·위젯 딥링크 + 알림 완료 액션 (singleTop·onNewIntent 탭 이동, 콜드스타트 보존, Todo 알림 완료 처리)
- [x] **Phase 14** — 반복 일정 (RRULE iCal 읽기-전개 엔진, 단건/이후/전체 범위 삭제·수정, exdate·override, 알람·홈·위젯 전개)
- [x] **Phase 15** — 가계부 예산 (카테고리별 + 전체 월 한도, 매월 반복, 초과 경고, AppDatabase v5 마이그레이션, Firestore 동기화)
- [x] **Phase 16** — 통합 검색 (일정·할일·가계부 횡단 LIKE 검색, 홈 헤더 진입, 결과 탭→해당 화면 이동)
- [x] **Phase 17** — 안정화 (홈 가계부 집계 정산정책 통일, 통독 Flow 컬렉터 누수 제거, 로그아웃 시 동기화 테이블 정리·계정 전환 데이터 격리)
- [x] **Phase 18** — 월간 리포트 (일정·할일·가계부·통독 횡단 월간 집계, 월 이동, Canvas 완료율 링·카테고리 막대, 홈 헤더 진입)
