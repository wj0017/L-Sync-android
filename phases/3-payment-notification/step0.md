# Step 0: payment-parser

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `CLAUDE.md`
- `docs/ARCHITECTURE.md`
- `app/src/main/java/com/lsync/app/data/local/FinanceCategory.kt`

## 작업

`app/src/main/java/com/lsync/app/notification/PaymentNotificationParser.kt`를 신규 생성하라.

### 데이터 클래스

```kotlin
data class ParsedPayment(
    val amount: Long,
    val note: String,
    val category: String,
)
```

### PaymentNotificationParser object

```kotlin
object PaymentNotificationParser {
    val PAYMENT_PACKAGES: Set<String>

    fun parse(packageName: String, title: String, text: String): ParsedPayment?

    internal fun extractAmount(text: String): Long?
    internal fun inferCategory(text: String): String
    internal fun extractNote(text: String): String
}
```

### 구현 규칙

**PAYMENT_PACKAGES — 정확히 아래 5개:**
- `viva.republica.toss` (토스 앱)
- `com.viva.finance` (토스뱅크)
- `com.shinhan.smartsalary` (신한카드)
- `com.kbcard.kbcardclient` (KB국민카드)
- `com.kakaobank.channel` (카카오뱅크)

**parse() 로직 (순서대로 실행):**
1. `packageName`이 `PAYMENT_PACKAGES`에 없으면 즉시 `null` 반환.
2. `title + " " + text`에 `결제`, `승인`, `출금` 중 하나도 없으면 `null` 반환. (마케팅·잔액 알림 차단)
3. `extractAmount(text)` 호출. `null`이면 `null` 반환.
4. `inferCategory(text)` 호출.
5. `extractNote(text)` 호출.
6. `ParsedPayment(amount, note, category)` 반환.

**extractAmount(text):**
- regex: `(\d{1,3}(?:,\d{3})*)원`
- 첫 번째 매치의 숫자 부분에서 쉼표 제거 후 `toLong()`.
- 매치 없거나 변환 실패 시 `null`.

**inferCategory(text):**
키워드 우선순위 순서로 매칭 (text에 포함 여부로 판단):
1. `스타벅스`, `이디야`, `카페`, `커피`, `베이커리`, `빵` → `FinanceCategory.CAFE`
2. `GS25`, `CU`, `세븐일레븐`, `편의점`, `마트`, `쿠팡`, `배달`, `식당`, `음식` → `FinanceCategory.FOOD`
3. `지하철`, `버스`, `Tmap`, `택시`, `카카오T`, `주유`, `전철` → `FinanceCategory.TRANSPORT`
4. `병원`, `약국`, `의원`, `치과`, `한의원` → `FinanceCategory.MEDICAL`
5. `넷플릭스`, `유튜브`, `스포티파이`, `구독`, `Apple`, `Google` → `FinanceCategory.SUBSCRIPTION`
6. 해당 없음 → `FinanceCategory.ETC`

**extractNote(text):**
- `text`를 최대 100자로 trim하여 반환.
- 가맹점명이 자연스럽게 포함됨 (e.g., "스타벅스 강남점에서 5,500원을 결제했어요").

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 체크리스트:
   - `PaymentNotificationParser.kt`가 `notification/` 하위에 생성됐는가?
   - `android.*` import가 전혀 없는가?
   - `PAYMENT_PACKAGES`에 5개 패키지가 정확히 포함됐는가?
   - `FinanceCategory` 상수를 직접 참조하는가? (한국어 문자열 하드코딩 금지)
3. 결과에 따라 `phases/3-payment-notification/index.json`의 step 0을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "산출물 한 줄 요약"`
   - 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`

## 금지사항

- `android.*`, `androidx.*`, `com.google.*`를 import하지 마라. 이유: 이 파일은 순수 Kotlin 파싱 로직으로, Android 의존성 없이 단독으로 테스트 가능해야 한다.
- `FinanceCategory`의 한국어 값을 직접 문자열로 하드코딩하지 마라. 이유: `FinanceCategory.FOOD` 등 상수를 사용해야 카테고리 이름 변경 시 일관성이 유지된다.
- suspend 함수를 추가하지 마라. 이유: 파싱은 동기 순수 함수여야 한다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
