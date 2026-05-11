# Step 0: finance-dao

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `CLAUDE.md`
- `docs/ARCHITECTURE.md`
- `docs/TechSpec.md`
- `app/src/main/java/com/lsync/app/data/local/entity/FinanceEntity.kt`
- `app/src/main/java/com/lsync/app/data/local/dao/FinanceDao.kt`
- `app/src/main/java/com/lsync/app/di/AppModule.kt`

파일들을 꼼꼼히 읽고 현재 구조를 이해한 뒤 작업하라.

## 작업

### 1. FinanceCategory 정의

`app/src/main/java/com/lsync/app/data/local/FinanceCategory.kt`를 신규 생성하라.

카테고리는 아래 7개로 고정한다:

```kotlin
object FinanceCategory {
    const val FOOD = "식비"
    const val TRANSPORT = "교통"
    const val CAFE = "카페/간식"
    const val SHOPPING = "쇼핑"
    const val MEDICAL = "의료"
    const val SUBSCRIPTION = "구독"
    const val ETC = "기타"

    val all: List<String> = listOf(FOOD, TRANSPORT, CAFE, SHOPPING, MEDICAL, SUBSCRIPTION, ETC)
}
```

이 object는 FinanceEntity의 `category` 문자열 필드에 저장되는 값의 유효한 목록이다. DB enum 타입이 아니므로 마이그레이션이 필요 없다.

### 2. FinanceDao 메서드 추가

`app/src/main/java/com/lsync/app/data/local/dao/FinanceDao.kt`에 아래 두 메서드를 추가하라. 기존 메서드는 수정하지 마라.

```kotlin
@Query("DELETE FROM finances WHERE id = :id")
suspend fun deleteById(id: String)

@Query("""
    SELECT * FROM finances
    WHERE userId = :userId
      AND date >= :from
      AND date <= :to
      AND isExcluded = 0
    ORDER BY date ASC
""")
suspend fun getAllByDateRange(userId: String, from: String, to: String): List<FinanceEntity>
```

- `deleteById`: 수동 입력 가계부 항목의 영구 삭제. Todo에서 자동 생성된 항목에는 사용하지 않는다 (Todo 정책은 `excludeByTodoId` 사용).
- `getAllByDateRange`: CSV 내보내기용. `isExcluded = 0` 조건으로 소프트 삭제된 항목을 제외한다. `date`는 `"YYYY-MM-DD"` 문자열이므로 사전식 정렬이 날짜 오름차순과 동일하다.

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 아키텍처 체크리스트를 확인한다:
   - `FinanceCategory.kt`가 `data/local/` 하위에 생성됐는가?
   - `FinanceDao.kt`에 `deleteById`, `getAllByDateRange` 두 메서드가 추가됐는가?
   - 기존 FinanceDao 메서드가 변경되지 않았는가?
   - CLAUDE.md CRITICAL 규칙을 위반하지 않았는가?
3. 결과에 따라 `phases/2-finance-automation/index.json`의 step 0을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "산출물 한 줄 요약"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- FinanceEntity 필드를 추가·수정하지 마라. 이유: Room 마이그레이션이 필요해지며 이 step 범위를 벗어난다.
- AppDatabase 버전을 올리지 마라. 이유: 새 쿼리만 추가할 뿐 스키마 변경이 없다.
- AppModule.kt를 수정하지 마라. 이유: 이 step은 DAO 레이어만 다룬다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
