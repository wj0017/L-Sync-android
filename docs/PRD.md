# L-Sync PRD (제품 요구사항 정의서)

**목적:** 프로젝트의 비전, 정책, 마일스톤을 정의하여 개발 중 흔들리지 않는 의사결정 기준을 제공.

---

## 1. 프로젝트 비전 및 원칙

- **제품명:** L-Sync (Life-Synchronization)
- **비전:** 일정, 할 일, 재정, 신앙을 하나의 흐름으로 동기화하는 개인 맞춤형 안드로이드 생산성 허브.
- **핵심 원칙:**
  - **Offline-First:** 모든 핵심 기능은 네트워크 단절 시에도 100% 동작해야 한다.
  - **지속 가능한 페이스:** 번아웃 방지를 위해 측정 가능하고 합리적인 단계별 검증(Dogfooding)을 거친다.
  - **데이터 영속성 보장:** 사용자 편의를 위한 삭제 기능(Todo 삭제 등)이 과거의 자산 기록(가계부)을 훼손해서는 안 된다.

---

## 2. 핵심 도메인 정책

### 2.1. 시간대(Timezone) 정책

- **종일 일정 & 마감일 Todo:** Floating Time 적용. 기기 시간대와 무관하게 저장된 '날짜 문자열' 그 자체를 기준으로 렌더링.
- **시간 지정 일정:** 한국 시간(UTC+9) 기준으로 저장하되, 해외 출장 시를 대비해 '타임존 고정(Timezone Lock)' 옵션을 제공.

### 2.2. Todo ↔ 가계부 생명주기 정책

- Todo 완료 시 가계부 데이터 자동 생성. (금액 미정 시 UI 팝업 강제)
- Todo 미완료(Uncheck) 시 연결된 가계부 데이터는 통계에서만 제외(Soft Delete).
- Todo 삭제 시 가계부 데이터는 유지되며 연결 고리(`sourceTodoId`)만 해제.

### 2.3. 반복 Todo 정책 (가계부 연동용)

- 서버 의존도를 낮추기 위해 **'Client-side Materialization'** 적용.
- 사용자가 템플릿을 만들면, 앱이 백그라운드에서 주기적으로 개별 Todo 문서들을 생성.
- **템플릿 생성 UI:** 일정 화면의 반복 할 일 FAB → 빈도(매일/매주/매월)·간격(N주마다 등)·요일·가계부 연동을 선택하면 `buildRrule`이 RRULE 문자열을 만들고 템플릿을 저장. 활성 템플릿은 목록에서 확인·중지(deactivate) 가능.
- **인스턴스 생명주기 일원화:** Materializer가 만든 개별 인스턴스도 단발성 Todo와 동일하게 Room 저장 + Firestore 동기화 + 마감일 알람 등록을 거친다(`TodoRepository.upsertMaterialized`). 템플릿 생성 직후 `MaterializationTrigger`로 1회 즉시 생성한다.

### 2.4. 정산(Settlement) 정책

- 공동 결제 후 일부 금액을 돌려받는 흐름을 추적. 별도 테이블 없이 `settlementGroupId` 단일 컬럼으로 그룹화(EXPENSE 리더 + INCOME 정산 입금).
- 정산 입금은 **실제 수입이 아니므로 수입·지출 통계에 합산하지 않고** 별도 집계한다. 잔액(`net`)에는 정확히 반영(`net = income + reimbursed − expense`).
- 정산 리더(EXPENSE) 삭제 시 그룹 전체 정리. 단 데이터 영속성 정책상 Todo 연동 입금은 삭제하지 않고 연결만 해제.

---

## 3. 마일스톤 및 검증 게이트

### ✅ Phase 1: 일정 + 할 일 (MVP) — 완료

- **구현:** 캘린더 CRUD (RRULE 지원), 투두 CRUD, 로컬 마감 알림, Firebase 연동.
- **게이트:** 2주간 직접 사용. Crashlytics 기준 **알림 누락 및 동기화 에러율 5% 미만**.

### ✅ Phase 2: 가계부 자동화 시스템 — 완료

- **구현:** 가계부 CRUD, 로컬 통계 산출, Todo ↔ Finance 자동 연동, CSV 내보내기, 결제 알림 자동 파싱, 정산 추적(2.4).
- **게이트:** 1개월간 직접 사용. **실제 카드 결제액과 앱 내 통계가 오차 없이 일치**할 때 통과.

### ✅ Phase 3: 성경 뷰어 및 고도화 — 완료

- **구현:** 성경 통독 뷰어(개역개정·ESV **로컬 SQLite asset 번들**, Firestore 미사용), 1년 통독 계획, 절 묵상 메모.

### ✅ Phase 4: 홈 위젯 — 완료

- **구현:** Jetpack Glance 기반 안드로이드 홈 위젯. 할 일·일정·이달 가계부·성경 통독 4개 섹션. 2열 카드 레이아웃, 가계부 막대 차트, 통독은 오늘 챕터 목록 표시.
- **크기:** 5×2 cells (minWidth 250dp, minHeight 110dp).
- **갱신:** 데이터 변경 시 `WidgetRefreshHelper`로 즉시 갱신. WorkManager 30분 주기 + 시스템 `updatePeriodMillis`(fallback).

### ✅ Phase 5: Firebase Auth Google 로그인 — 완료

- **구현:** Google 로그인 화면, Firebase Auth 연동, `userId = "local_user"` 하드코딩 전면 교체, 기존 Room 데이터 자동 마이그레이션, 홈 화면 로그아웃 메뉴.
- **마이그레이션 정책:** 최초 로그인 시 `AuthMigrationHelper`가 Room 내 `"local_user"` userId를 실제 Firebase UID로 일괄 업데이트. SharedPreferences 플래그로 멱등성 보장.
- **Firestore 보안 규칙:** `request.auth.uid == resource.data.userId` 적용. 기존 `"local_user"` Firestore 데이터는 폐기 후 마이그레이션된 UID로 재동기화.

### ✅ Phase 6: 통독 고급화 — 완료

- **구현:** 성경 화면과 통독 계획 연동. 연속 읽기(streak)·주간 히트맵 동기 부여 요소 추가.
  - **BibleScreen:** 현재 펼친 장이 오늘 통독 분량이면 `PlanChapterBanner` 노출, 탭 한 번으로 읽음 토글.
  - **홈 화면:** 연속 읽기 streak 수치 + 최근 7일 주간 히트맵 도트.
- **순지출 표시 정책:** 가계부·위젯의 이달 지출은 **정산 받은 금액(`reimbursed`)을 차감한 순지출**(`expense − reimbursed`, 음수 방지)로 표시한다.
