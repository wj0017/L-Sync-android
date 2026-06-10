# Step 0: range-source

## 배경

이 task(`12-finance-dashboard`)는 "가계부 통계 대시보드"다. 현재 가계부는 **월 단위**(`FinanceRepository.observeByMonth`)로만 데이터를 노출하고, 여러 달에 걸친 추세나 카테고리별 집계를 위한 데이터 소스가 없다. 대시보드는 최근 N개월 범위 데이터가 필요하다.

이 Step은 **날짜 범위(여러 달) 거래를 관찰하는 Flow**를 데이터 레이어에 추가한다. 집계 계산 자체는 Step 1(ViewModel)에서 한다 — 이 Step은 "원천 Flow"만 제공한다.

핵심 사실: `FinanceDao`에는 이미 `getAllByDateRange(userId, from, to)`(suspend)가 있다. 대시보드는 반응형이어야 하므로 **Flow 버전**이 필요하다.

## 읽어야 할 파일

먼저 아래 파일을 읽고 설계 의도를 파악하라:

- `docs/ARCHITECTURE.md` — Offline-First(Room Flow 구독), 정산 집계 규칙(`net = income + reimbursed − expense`, 정산 항목은 수입·지출 미합산)
- `docs/PRD.md` — 2.4 정산 정책
- `CLAUDE.md` — Room SSOT, Hilt
- `app/src/main/java/com/lsync/app/data/local/dao/FinanceDao.kt` — **수정 대상.** 기존 `getAllByDateRange`, `observeByMonth`(있다면) 쿼리 패턴을 본보기로 Flow 쿼리 추가.
- `app/src/main/java/com/lsync/app/data/repository/FinanceRepository.kt` — **수정 대상.** `observeByMonth` 등 기존 Flow 노출 패턴을 따른다.
- `app/src/main/java/com/lsync/app/data/local/entity/FinanceEntity.kt` — 필드(`type, amount, category, date, isExcluded, settlementGroupId`)

## 작업

### FinanceDao

날짜 범위로 거래를 관찰하는 Flow 쿼리를 추가한다(기존 `getAllByDateRange`와 동일한 WHERE, 반환만 `Flow`):

```kotlin
@Query("SELECT * FROM finance WHERE userId = :userId AND date >= :from AND date <= :to ORDER BY date")
fun observeByDateRange(userId: String, from: String, to: String): Flow<List<FinanceEntity>>
```

(기존 `getAllByDateRange`의 정확한 WHERE 절을 읽고 그대로 맞춰라. `isExcluded`/`deletedAt` 필터가 기존 쿼리에 있으면 동일하게 적용.)

### FinanceRepository

```kotlin
fun observeByDateRange(from: String, to: String): Flow<List<FinanceEntity>>
```

- `dao.observeByDateRange(userId, from, to)`를 그대로 노출. userId는 기존 Repository가 쓰는 방식(생성자 주입된 `AuthRepository.currentUserId` 또는 호출자 전달)을 **기존 `observeByMonth` 구현과 동일하게** 따른다. (먼저 `observeByMonth`가 userId를 어떻게 얻는지 읽고 같은 방식 사용.)

## 핵심 규칙 (반드시 지킬 것)

- **집계하지 마라.** 이 Step은 원천 Flow만 제공한다. 추세/카테고리 합산은 Step 1(ViewModel)의 책임이다. 이유: 레이어 분리(scope 최소화).
- **기존 쿼리 일관성.** `observeByDateRange`의 필터(userId, isExcluded 등)는 기존 `getAllByDateRange`/`observeByMonth`와 어긋나면 안 된다. 통계 왜곡 방지.
- Room이 SSOT. Firestore를 읽지 마라.

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 아키텍처 체크리스트:
   - `FinanceDao.observeByDateRange`가 Flow를 반환하고 기존 범위 쿼리와 필터가 일치하는가?
   - `FinanceRepository.observeByDateRange`가 기존 `observeByMonth`와 동일한 userId 처리 방식인가?
   - 이 Step에서 집계 로직을 넣지 않았는가?
3. 결과에 따라 `phases/12-finance-dashboard/index.json`의 step 0을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "FinanceDao/FinanceRepository.observeByDateRange(from,to) Flow 추가 — 다월 범위 거래 원천. Step1 VM이 집계"`
   - 수정 3회 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 중단

## 금지사항

- ViewModel/Compose를 수정하지 마라. 이유: Step 1·2의 범위다.
- 집계/통계 계산 코드를 추가하지 마라. 이유: 이 Step은 원천 Flow만 제공한다.
- 기존 `observeByMonth`/`getAllByDateRange`/`create`/`update` 동작을 바꾸지 마라.
