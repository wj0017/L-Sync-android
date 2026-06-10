# Step 0: rrule-builder

## 배경

이 task(`9-repeat-todo`)는 "반복 Todo 템플릿 UI"다. L-Sync에는 반복 Todo의 백엔드가 **이미 완성**돼 있다:

- `TodoTemplateEntity`(todo_templates 테이블): `id, userId, title, rrule, financeIsLinked, financeType, financeCategory, financeAmount, isActive, createdAt, updatedAt`.
- `TodoTemplateDao`, `TodoRepository`(upsertTemplate/getActiveTemplates/deactivateTemplate/observeActiveTemplates).
- `TodoMaterializerWorker`: `org.dmfs.rfc5545`(lib-recur) `RecurrenceRule`로 템플릿의 `rrule`을 파싱해 오늘~+14일치 인스턴스를 생성(일 1회 등록됨).

그러나 **사용자가 반복 규칙(rrule)을 만들 UI가 전혀 없다.** 이 Step은 그 첫 조각으로, UI 입력(빈도·간격·요일)을 RRULE 문자열로 변환하고 역으로 사람이 읽을 요약 문자열을 만드는 **순수 Kotlin 헬퍼**를 작성한다. Android 의존이 없으므로 단위 테스트로 검증한다.

이후 Step들이 이 헬퍼를 사용한다: Step 4(UI)가 사용자 입력 → `buildRrule()`로 rrule을 만들고, 템플릿 목록 표시에 `describeRrule()`을 쓴다.

## 읽어야 할 파일

먼저 아래 파일을 읽고 프로젝트의 아키텍처·컨벤션을 파악하라:

- `docs/ARCHITECTURE.md` — 디렉토리 구조
- `docs/PRD.md` — 2.3 반복 Todo 정책(Client-side Materialization)
- `CLAUDE.md` — CRITICAL 규칙(Floating Date, 디렉토리 규칙)
- `app/src/main/java/com/lsync/app/data/local/entity/TodoEntity.kt` — `TodoTemplateEntity`의 `rrule` 필드(예: `FREQ=MONTHLY`) 확인
- `app/src/main/java/com/lsync/app/worker/TodoMaterializerWorker.kt` — rrule이 어떻게 소비되는지 확인. 특히 `RecurrenceRule(template.rrule.removePrefix("RRULE:"))`로 파싱한다 → **이 헬퍼가 만드는 rrule은 그 파서가 받아들이는 형식이어야 한다.**
- `app/src/test/java/com/lsync/app/notification/PaymentNotificationParserTest.kt` — 이 프로젝트의 단위 테스트 작성 패턴(JUnit) 참고

## 작업

새 파일 `app/src/main/java/com/lsync/app/ui/schedule/RecurrenceOptions.kt`를 만든다. 순수 Kotlin(안드로이드 import 금지)으로 작성한다.

### 모델

```kotlin
enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

// Weekday는 RRULE BYDAY 토큰과 매핑 (MO, TU, WE, TH, FR, SA, SU)
enum class Weekday(val token: String) { MON("MO"), TUE("TU"), WED("WE"), THU("TH"), FRI("FR"), SAT("SA"), SUN("SU") }

data class RecurrenceOption(
    val frequency: Frequency,
    val interval: Int = 1,                 // 1 이상. "N마다"
    val weekdays: Set<Weekday> = emptySet() // WEEKLY일 때만 의미. 비어있으면 BYDAY 생략
)
```

### 함수

```kotlin
fun buildRrule(option: RecurrenceOption): String
fun describeRrule(rrule: String): String   // 한국어 요약. 파싱 실패 시 "반복" 등 안전한 기본값
```

### buildRrule 규칙

- `FREQ=` 는 항상 포함. 예: `FREQ=WEEKLY`.
- `interval >= 2` 일 때만 `;INTERVAL=n` 추가. (interval=1이면 생략)
- `frequency == WEEKLY` 이고 `weekdays`가 비어있지 않으면 `;BYDAY=` + 요일 토큰을 **MO,TU,WE,TH,FR,SA,SU 순서로 정렬**해 콤마로 연결. (입력 Set 순서에 의존하지 마라 — 결정론적 출력)
- WEEKLY가 아니면 BYDAY를 넣지 마라.
- `RRULE:` 접두사는 붙이지 마라. Materializer가 `removePrefix("RRULE:")`로 제거하므로 접두사 없이 순수 규칙만 반환하면 일관적이다.
- 예시:
  - `RecurrenceOption(DAILY)` → `FREQ=DAILY`
  - `RecurrenceOption(WEEKLY, 2, setOf(MON, WED))` → `FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE`
  - `RecurrenceOption(MONTHLY, 1)` → `FREQ=MONTHLY`

### describeRrule 규칙

- rrule 문자열을 파싱해 한국어 요약 반환. 예:
  - `FREQ=DAILY` → "매일"
  - `FREQ=WEEKLY` → "매주"
  - `FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE` → "2주마다 월,수"
  - `FREQ=MONTHLY` → "매월", `FREQ=YEARLY` → "매년"
- interval>=2면 "N일/주/개월/년마다" 형식. BYDAY가 있으면 요일을 한국어(월화수목금토일)로 덧붙임.
- 알 수 없는/깨진 입력은 예외를 던지지 말고 "반복" 같은 안전한 문자열을 반환하라. 이유: 잘못된 rrule이 UI 렌더링을 크래시시키면 안 된다.

### 단위 테스트

`app/src/test/java/com/lsync/app/ui/schedule/RecurrenceOptionsTest.kt`를 만들어 `PaymentNotificationParserTest.kt`와 같은 JUnit 스타일로 작성한다. 최소 케이스:

- DAILY → `FREQ=DAILY`
- WEEKLY + interval=2 + {MON,WED} → `FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE` (요일 순서 정렬 확인: 입력을 `setOf(WED, MON)`으로 줘도 `BYDAY=MO,WE`)
- MONTHLY interval=1 → `FREQ=MONTHLY` (INTERVAL 생략 확인)
- `describeRrule("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE")` → "2주마다 월,수"
- `describeRrule("쓰레기값")` → 예외 없이 문자열 반환

## 핵심 규칙 (반드시 지킬 것)

- **출력 결정론.** 같은 입력은 항상 같은 rrule 문자열을 내야 한다(요일 정렬 필수). 이유: 비결정적 출력은 멱등성·테스트를 깨뜨린다.
- **Materializer 호환.** 생성한 rrule은 `org.dmfs.rfc5545.recur.RecurrenceRule(rrule)` 생성자가 파싱 가능한 형식이어야 한다(표준 RFC 5545 RRULE 본문). 새 의존성을 추가하지 마라 — 문자열 조립만으로 충분하다.
- 안드로이드 프레임워크 import 금지(`android.*`, Compose 등). 이유: 순수 로직이어야 JVM 단위 테스트가 가능하다.

## Acceptance Criteria

```bash
./gradlew assembleDebug        # 컴파일 에러 없음
./gradlew testDebugUnitTest    # 새 RecurrenceOptionsTest 포함 전체 단위 테스트 통과
./gradlew lintDebug            # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 아키텍처 체크리스트:
   - `RecurrenceOptions.kt`가 `ui/schedule/`에 있고 안드로이드 의존이 없는가?
   - `buildRrule`이 INTERVAL=1 생략·요일 정렬·WEEKLY 외 BYDAY 미포함 규칙을 지키는가?
   - 테스트가 실제로 통과하는가?
3. 결과에 따라 `phases/9-repeat-todo/index.json`의 step 0을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "RecurrenceOptions.kt 추가 — RecurrenceOption 모델, buildRrule(FREQ/INTERVAL/BYDAY 정렬)·describeRrule(한국어 요약), 단위테스트 통과. UI(step4)와 템플릿 표시에 사용"`
   - 수정 3회 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 중단

## 금지사항

- Repository / Worker / ViewModel / Compose 파일을 수정하지 마라. 이유: 이 Step은 순수 헬퍼 + 테스트 단일 모듈이다. 소비는 이후 step의 몫이다.
- 새 라이브러리 의존성을 `build.gradle`에 추가하지 마라. 이유: rrule은 문자열 조립으로 충분하고, 파싱은 이미 있는 lib-recur가 Materializer에서 담당한다.
- `RRULE:` 접두사를 붙이지 마라. 이유: Materializer가 접두사를 제거하므로 접두사 없는 순수 본문으로 통일한다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
