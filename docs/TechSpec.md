# L-Sync Technical Spec (v0.16)

**목적:** 데이터베이스 구조, 보안 규칙, 안드로이드 권한·알림, 백그라운드 엔진 등 구현에 직접 필요한 기술 명세.

---

## 1. 데이터베이스 스키마

> 공통 메타데이터: `id`, `userId`, `createdAt`, `updatedAt`, `deletedAt`(옵션)

### 1.1. `events`

`isAllDay` 플래그로 타임존 적용 여부 분리. 종일 일정은 `YYYY-MM-DD` Floating Time.

```json
{
  "title": "주간 회의",
  "isAllDay": false,
  "startDate": "2024-05-01T09:00:00+09:00",
  "timezone": "Asia/Seoul",
  "rrule": "FREQ=WEEKLY;BYDAY=MO",
  "exdatesJson": "[\"2024-05-08\"]",
  "overridesJson": "{...}",
  "hasAlarm": true
}
```

**반복 일정 (iCal 읽기-전개)**
- 마스터 한 행만 저장하고 발생(occurrence)은 런타임에 전개(`data/recurrence/EventRecurrence.kt` 순수 Kotlin 엔진). 시각 보존·종일 Floating·`MAX_ITERATIONS` 가드.
- `exdatesJson`(제외 날짜 JSON 배열)·`overridesJson`(발생별 수정 JSON)로 예외 처리.
- 범위 삭제: `deleteOccurrence`=exdate / `deleteFollowing`=UNTIL−1 절단 / `deleteSeries`=hard delete. 범위 수정: `editOccurrence`=override / `editFollowing`=원본 절단+새 마스터 / `editSeries`=update.
- 알람: `scheduleForEvent`가 `nextOccurrence`로 다음 발생 등록 + 일1회 `EventAlarmRefreshWorker`로 재계산.

### 1.2. `todos` & `todo_templates`

`dueDate`는 `YYYY-MM-DD` Floating Time.

```json
{
  "templateId": "template_789",
  "title": "넷플릭스 구독료",
  "isCompleted": false,
  "dueDate": "2024-05-15",
  "financeIsLinked": true,
  "financeType": "EXPENSE",
  "financeCategory": "구독",
  "financeAmount": 13500,
  "linkedFinanceId": "finance_abc"
}
```

### 1.3. `finance`

`date`는 `YYYY-MM-DD`. `isExcluded = true`인 항목은 통계 제외.

```json
{
  "type": "EXPENSE",
  "amount": 13500,
  "category": "구독",
  "date": "2024-05-15",
  "sourceTodoId": "template_789_2024-05-15",
  "isExcluded": false,
  "settlementGroupId": null
}
```

**정산 추적 (`settlementGroupId`)**
- 공동 결제 후 일부를 돌려받는 흐름을 별도 테이블 없이 단일 컬럼으로 추적.
- 같은 `settlementGroupId`를 공유하는 항목들이 하나의 정산 그룹. `EXPENSE`가 리더, `INCOME`이 정산 입금.
- 정산 입금은 **수입·지출 어디에도 합산하지 않고** 별도 집계(`reimbursed`)하여 수입 과대·지출 음수 표시를 방지. `net = income + reimbursed − expense`.
- **지출 표시값은 순지출**(`expense − reimbursed`, `coerceAtLeast(0)`) — 정산으로 돌려받은 금액을 차감해 표기(FinanceScreen·위젯 공통).
- 리더(EXPENSE) 삭제 시 그룹의 정산 입금도 함께 삭제. 단 Todo 연동 입금(`sourceTodoId != null`)은 삭제 금지 정책상 `settlementGroupId`만 해제.

### 1.4. `reading_plan` (AppDatabase v2 추가)

성경 통독 진행 추적. 날짜별로 읽어야 할 챕터를 Row로 저장.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | INTEGER PK (autoincrement) | |
| date | TEXT | YYYY-MM-DD |
| book | INTEGER | 책 번호 (1~66) |
| chapter | INTEGER | 장 번호 |
| isRead | INTEGER (Boolean) | 읽음 여부 |

**통독 고급화 집계 (Room 쿼리)**
- `ReadingPlanDao.getReadDates()` — `isRead = 1`인 distinct 날짜 목록.
- `ReadingPlanRepository.getStreak()` — 오늘부터 거꾸로 연속 읽은 일수.
- `ReadingPlanRepository.getWeeklyHeatmap()` — 최근 7일(6일 전 → 오늘) 읽음 여부 `List<Boolean>` (홈 히트맵).
- BibleScreen 연동: `BibleViewModel`이 현재 (book, chapter)가 오늘 통독 분량인지/읽음인지 판정해 `PlanChapterBanner` 노출·토글.

### 1.5. `memos` (AppDatabase v3 추가)

성경 절 묵상 메모. 절 단위로 메모를 저장하며 Firestore 미사용(로컬 전용).

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | INTEGER PK (autoincrement) | |
| book | INTEGER | 책 번호 (1~66) |
| chapter | INTEGER | 장 번호 |
| verse | INTEGER | 절 번호 |
| text | TEXT | 메모 본문 |
| date | TEXT | YYYY-MM-DD |

### 1.6. `budgets` (AppDatabase v5 추가)

가계부 월 예산. 카테고리별·전체 월 한도를 저장하며 매월 반복 적용. Firestore `budgets` 컬렉션 동기화.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | TEXT PK | `"${userId}_${category}"` 결정론적 — 카테고리당 1행, 멱등 upsert |
| userId | TEXT | |
| category | TEXT | FinanceCategory 값. 전체 한도는 sentinel `TOTAL_CATEGORY = "__TOTAL__"` |
| limitAmount | INTEGER | 매월 반복 한도(원) |
| createdAt / updatedAt | INTEGER | |
| deletedAt | INTEGER? | soft delete — pull(upsert-only) 삭제 전파용 |

**예산 집계·표시**
- `FinanceDashboardViewModel.budgets` — 이번 달만. 카테고리 예산 사용액 = 해당 카테고리 EXPENSE 원금, TOTAL 예산 사용액 = 순지출(`expense − reimbursed`). 0나눗셈 가드.
- 진행바: 정상 막대 `AccentBlue`, 초과 시에만 `AccentRed`(절제 = 디자인 의도).
- `BudgetRepository.setBudget`(멱등 upsert + `syncSafe`), `deleteBudget`(soft delete 원격 전파). `SyncRepository.pullAll`이 LWW upsert-only로 복원.

### 1.7. 성경 데이터 (로컬 SQLite — Firestore 미사용)

#### bible_verses (개역개정 4판)
- DB 파일: `assets/bible.db` → 내부 `bible_v3.db` (콘텐츠 버전 2)
- 31,024절, 66권

| 컬럼 | 타입 | 설명 |
|------|------|------|
| idx | INTEGER PK | |
| book | INTEGER | 1=창세기 … 66=요한계시록 |
| chapter | INTEGER | |
| verse | INTEGER | |
| text | TEXT | 본문 |
| testament | TEXT | "구" / "신" |
| book_name | TEXT | 한국어 책명 |
| book_short | TEXT | 약자 |

#### esv_verses (English Standard Version)
- DB 파일: `assets/esv.db`, 31,086절

| 컬럼 | 타입 | 설명 |
|------|------|------|
| idx | INTEGER PK | |
| book | INTEGER | bible_verses와 동일 체계 |
| chapter / verse / text | | |

### 1.8. `sync_log` (AppDatabase v7 추가)

원격 동기화 시도 1건 = 1행의 진단 기록. **로컬 전용 — Firestore 동기화 대상이 아니다**(Room에만 산다). 인덱스 `index_sync_log_userId_createdAt`(`userId`, `createdAt`). 사용처는 §12.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | INTEGER PK (autoincrement) | |
| userId | TEXT | 호출부가 엔티티의 `userId`를 넘긴다. 모든 조회가 이 컬럼으로 필터 |
| target | TEXT | `SyncTarget.key` — `"events"` / `"todos"` / `"finance"` / `"budgets"` / `"pull"` |
| entityId | TEXT? | 재전송 키(엔티티 id). `target = "pull"`이면 null |
| success | INTEGER (Boolean) | |
| errorType | TEXT? | 예외 클래스 `simpleName` |
| errorMessage | TEXT? | 예외 message 앞 200자 |
| createdAt | INTEGER | 기록 시각(epoch millis) |
| resolvedAt | INTEGER? | 재시도로 해소된 시각. `success = 0 AND resolvedAt IS NULL` = 미해결 실패 |

---

## 2. AppDatabase 마이그레이션

| 버전 | 변경 내용 |
|------|----------|
| 1 | 초기: events, todos, todo_templates, finance |
| 2 | reading_plan 테이블 추가 (MIGRATION_1_2) |
| 3 | memos 테이블 추가 (MIGRATION_2_3) |
| 4 | finance.settlementGroupId 컬럼 추가 (MIGRATION_3_4) |
| 5 | budgets 테이블 추가 (MIGRATION_4_5) |
| 6 | finance.deletedAt 컬럼 추가 (MIGRATION_5_6) |
| 7 | sync_log 테이블 추가 (MIGRATION_6_7) |

---

## 3. Firebase 보안 규칙

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /{collection}/{docId} {
      allow read, delete: if request.auth != null
                       && request.auth.uid == resource.data.userId;
      allow create: if request.auth != null
                 && request.auth.uid == request.resource.data.userId;
      allow update: if request.auth != null
                 && request.auth.uid == resource.data.userId
                 && request.auth.uid == request.resource.data.userId;
    }
  }
}
```

> Firebase Auth Google 로그인 연동 완료. `userId`는 `AuthRepository.currentUserId`(실제 Firebase UID)를 사용한다.

---

## 4. 백그라운드 엔진

- **Idempotency:** Todo ID = `${templateId}_${dueDate}`. Upsert로 중복 방지.
- **Materialization Window:** 현재일 기준 +14일치 인스턴스 미리 생성.
- **템플릿 Update 정책:** 미완료 미래 인스턴스 덮어쓰기, 완료된 과거 인스턴스 변경 무시.
- **RRULE 생성:** `ui/schedule/RecurrenceOptions.kt`의 `RecurrenceOption(frequency, interval, weekdays)` → `buildRrule`(`FREQ`/`INTERVAL`/`BYDAY` 정렬)로 RRULE 문자열 생성, `describeRrule`로 한국어 요약 표시.
- **인스턴스 동기화·알람:** `TodoMaterializerWorker`는 `todoDao.upsertAll`이 아닌 `TodoRepository.upsertMaterialized`를 호출 → 생성된 인스턴스가 Firestore 동기화·마감일 알람 등록까지 거친다(단발성 Todo와 동일 경로).
- **즉시 생성 트리거:** `MaterializationTrigger`(`@Singleton`)가 템플릿 생성 직후 `OneTimeWork`(`ExistingWorkPolicy.REPLACE`)로 Materializer를 1회 즉시 실행.

---

## 5. 안드로이드 알림 & 권한

### 5.1 일정/할 일 알람
- **등록 시점:** 생성/수정 시 Repository가 `AlarmScheduler.scheduleForEvent`/`scheduleForTodo`로 즉시 AlarmManager 등록. 삭제·완료·과거 시각은 같은 스케줄러 경로에서 기존 알람을 취소한다. 로그아웃은 원격 엔티티를 삭제하지 않고 별도 취소한다.
- **트리거 시각(단일 계산):** 시간 지정 일정 = 시작 시각 / 종일 일정·마감일 Todo = 그 날 **09:00**(`REMINDER_HOUR=9`, `ZoneId.systemDefault()`). 단발 일정·Todo의 `trigger ≤ now`면 기존 예약을 취소하고, 반복 일정은 다음 유효 발생을 찾는다.
- **게이팅:** Event = `hasAlarm` 플래그. Todo = 마감일 있는 모든 미완료 Todo 자동 리마인더(별도 플래그 없음).
- **재부팅 복원:** `RECEIVE_BOOT_COMPLETED` → `BootReceiver` → `AlarmRestoreWorker` → `getFutureAlarmedEvents`/`getFutureAlarmedTodos` 조회 → 동일한 `scheduleForEvent`/`scheduleForTodo` 재사용(라이브와 시각 로직 일치).
- **반복 일정:** 오늘 발생 시각이 아직 미래이면 오늘 알람을 유지한다. exdate와 발생별 제목·시각·`hasAlarm` override를 반영하고, 발화 뒤 `AlarmReceiver`가 Room 최신 마스터로 다음 1회를 즉시 재등록한다. 마스터 알람 off라도 개별 발생 on을 허용한다.
- **원격 복원:** Firestore pull로 Room에 반영한 일정·Todo는 곧바로 등록/취소한다. 앱 시작 때도 `AlarmRestoreWorker`를 예약해 기존 항목의 알람을 다시 맞춘다.
- **정확 알람 권한:** Android 12+(API 31)에서 `canScheduleExactAlarms()` false면 `setExactAndAllowWhileIdle` 대신 `setAndAllowWhileIdle`(inexact) 폴백. 권한 요청은 `PermissionHelper`.
- Android 13+: 첫 실행 시 `POST_NOTIFICATIONS` 요청.
- **딥링크·완료 액션:** `AlarmReceiver` contentIntent는 고유 data `lsync://nav/schedule/$id`(rc=`id.hashCode`) → `MainActivity`(singleTop + `onNewIntent`, `EXTRA_NAV_TARGET`) → `NavGraph`가 해당 탭으로 이동(콜드스타트 보존). `TYPE_TODO` 알림은 완료 액션(rc=+1, `lsync://complete/$id`) → `TodoActionReceiver`(@AndroidEntryPoint, `goAsync`)가 완료 처리(금액 미정 연동 Todo는 앱 유도로 ₩0 가드, 중복 완료 가드, 알림 cancel). 위젯 4카드도 `actionStartActivity`로 동일 딥링크 진입.

### 5.2 정기 리마인더 (통독 · 소비 요약)

- **구성:** `ReminderScheduler`(등록/취소·시각 계산) → AlarmManager → `ReminderReceiver`(내용 계산·게시·재무장).
- **설정:** `NotificationSettingsRepository` — SharedPreferences `notification_prefs`, `StateFlow<NotificationSettings>`. 로컬 전용(Firestore 미동기화). **기본값 전부 off.**

| 리마인더 | `ReminderType` | 기본 시각 | notify ID | 채널 | 딥링크 |
|---|---|---|---|---|---|
| 통독 | `BIBLE` | 21:00 | 991_001 | `lsync_bible_reminder` (DEFAULT) | `bible` 탭 |
| 소비 매일 | `SPENDING_DAILY` | 21:30 | 991_002 | `lsync_spending_digest` (LOW) | `finance` 탭 |
| 소비 주간 | `SPENDING_WEEKLY` | 일 20:00 | 991_003 | `lsync_spending_digest` (LOW) | `finance` 탭 |

- **트리거 계산(순수 함수):** `nextDailyTrigger(now, hour, minute)` / `nextWeeklyTrigger(now, dayOfWeek, hour, minute)`. 대상 시각이 `now`보다 **엄격히 미래**면 그대로, 같거나 과거면 +1일 / +7일. `dayOfWeek`는 `java.time.DayOfWeek.value`(1=월 … 7=일), 범위 밖 값은 1~7로 보정. 등록은 `ZoneId.systemDefault()` epoch millis(사용자가 고른 벽시계 시각이므로 Floating Time 규칙과 무관).
- **requestCode:** `"reminder:${type.key}".hashCode()` — `AlarmScheduler`의 `"$type:$id"` 네임스페이스 방식과 동일.
- **정확 알람 폴백:** `AlarmCompat.setAlarmCompat` 공유(`AlarmScheduler`와 단일화).
- **재무장 3중화:** ① `ReminderReceiver`가 발화 시 `scheduleNext`(알림 미게시여도 항상) ② `LSyncApplication.onCreate()` `syncAll()` ③ `AlarmRestoreWorker`(`BOOT_COMPLETED` / `LOCKED_BOOT_COMPLETED` / **`MY_PACKAGE_REPLACED`**).
- **통독 내용 규칙:** 시작 안 함 → 스킵 / `ensureReadingPlanForDate(today)` 선행 → 항목 없음이면 스킵 / **전부 읽음이면 스킵**. 문구 = `"오늘 통독 N장 남았어요"` + 챕터 목록, streak ≥ 2면 `getStreakAsOf(어제)` 사용(오늘 기준으로 세면 리마인더 시점엔 항상 0).
- **소비 내용 규칙:** `currentUserId == null`이면 스킵(재무장은 수행). 매일 = 오늘 순지출 + 이번 달 순지출 + 전체 예산(`"${userId}___TOTAL__"`) 사용률. 주간 = **최근 7일 vs 직전 7일** 증감 + 최다 카테고리(EXPENSE 원금) + 이번 달 누적. 집계는 **`data/report/MonthlyAggregate.kt` 단일 출처**(`financeTotals(...).expense` = 순지출, `topExpenseCategories(rows, limit = 1)` = 최다 카테고리)만 호출한다 — 리시버가 정산 공식을 복제하지 않는다.
- **Hilt 리시버:** `@AndroidEntryPoint` + `goAsync()`(Room 조회 동안 프로세스 유지). 생성된 부모의 `onReceive`는 abstract이므로 `super.onReceive`는 호출하지 않는다.
- **설정 UI:** `ui/settings/NotificationSettingsScreen.kt` — 토글 + 시각(`ui.window.Dialog` + material3 `TimePicker`; material3 1.2.x에 `TimePickerDialog` 컴포저블이 없어 직접 구성) + 요일 ghost chip + "지금 미리보기"(켜진 리마인더를 명시적 브로드캐스트로 즉시 1회 발송). 시각 다이얼로그는 `ui/components/TimePickerDialog.kt` 공용 컴포저블로, 일정 생성·수정 다이얼로그의 시작 시각 선택에도 쓴다(비종일 일정 기본 09:00, 신규는 선택 날짜 기준 기기 오프셋, 편집은 기존 오프셋 보존). `NavGraph` `composable("notifications")` + `HomeScreen` 헤더 메뉴 진입.

### 5.3 결제 알림 자동 가계부
- **서비스:** `PaymentNotificationService` (NotificationListenerService)
- **지원 앱:**

| 패키지 | 앱 |
|--------|-----|
| `viva.republica.toss` | 토스 |
| `com.viva.finance` | 토스뱅크 |
| `com.shinhan.smartsalary` | 신한 SOL |
| `com.kbcard.kbcardclient` | KB Pay |
| `com.kakaobank.channel` | 카카오뱅크 |

- **파싱 규칙 (`PaymentNotificationParser`):**
  1. title에서 `{금액}원 결제/승인/출금/입금/환급` 패턴 매칭 — title에 없으면 즉시 null 반환
  2. 바디 폴백 없음 (알림성 메시지 false positive 방지 — 예: "예상 환급액 안내", "통신비 도착")
  3. 입금 키워드(`입금, 환급, 반환, 수신`) → `INCOME`, 나머지 → `EXPENSE`
  4. `|` 뒤 가맹점명 추출 → 카테고리 자동 추론

---

## 6. 홈 위젯

### 6.1 위젯 스펙

| 항목 | 값 |
|------|----|
| 라이브러리 | Jetpack Glance 1.1.0 |
| 크기 | 5×2 cells (minWidth 250dp, minHeight 110dp) |
| 레이아웃 | 2열 카드, 가계부 막대 차트, 통독은 오늘 챕터 목록 |
| 시스템 갱신 주기 | 1800000ms (30분, fallback) |
| 갱신 주체 | ① `WidgetRefreshHelper`(데이터 변경 시 즉시), ② `WidgetRefreshWorker`(30분 주기) |
| 데이터 소스 | Room 전용 (Firestore 미사용) |

### 6.1.1 리포트 위젯 (4×2, Phase 20)

기존 5×2 위젯은 Row2가 106dp 고정에 4카드로 포화 상태라 **건드리지 않고 별도 위젯**을 추가했다.

| 항목 | 값 |
|------|----|
| 클래스 | `ui/widget/ReportWidget.kt` + `ReportWidgetReceiver` + `res/xml/report_widget_info.xml` |
| 크기 | 4×2 cells (minWidth 200dp, minHeight 110dp) |
| 지표 | 이번 달 할일 완료율 · 일정 발생 수 · 순지출 · 통독(장/일) — **앱 리포트 화면(§9)과 동일 정의** |
| 데이터 | `TodoDao.getByDueDateRange` / `EventDao.getForExpansion`+`expandEvents` / `FinanceDao.getAllByDateRange` / `ReadingPlanDao.getReadInRange` (모두 suspend 일회성 조회 — Glance는 Flow를 구독하지 않는다) |
| 진입 | 탭 → `EXTRA_NAV_TARGET="report"` → `NavGraph`가 하단 탭 로직 대신 `navigate("report")`로 분기 |
| 미로그인 | `currentUserId == null`이면 `ReportWidgetState.empty()` |

- **집계 단일 출처:** `data/report/MonthlyAggregate.kt`(`financeTotals`/`topExpenseCategories`, 순수 Kotlin·DI 없음)를 `ReportViewModel`과 위젯이 **함께 호출**한다. 정산 공식이 화면마다 복제되면 숫자가 갈라진다(Phase 17 이력) — 새 집계 지점은 반드시 이 파일을 쓴다. `HomeViewModel`·`FinanceDashboardViewModel`·`LSyncWidget`의 기존 사본은 검증된 코드라 의도적으로 두었다.
- **`WidgetRefreshHelper.requestUpdate()`가 두 위젯을 모두 갱신**한다. 호출부(Home/Finance/Schedule VM)는 변경 없음.
- `LSyncWidget.kt`의 색 상수·`navIntent`는 `private`이라 import할 수 없다. `ReportWidget.kt`에 동일 값으로 재선언한다 — 검증된 파일을 public으로 여는 대신 중복을 택했다.

### 6.2 위젯 섹션

| 섹션 | 데이터 | 표시 규칙 |
|------|--------|----------|
| 할 일 | `TodoDao.getIncompleteByDate(today)` | 최대 3개 + "+N개 더" |
| 일정 | `EventDao.getByDate(today)` | 최대 3개, 시간 지정 일정은 "HH:mm 제목" 형식 |
| 가계부 | `FinanceDao.getAllByDateRange(userId, monthStart, monthEnd)` | 이달 순지출(`expense − reimbursed`)·수입 집계, 정산 항목 제외 |
| 성경 통독 | `ReadingPlanDao.getForDate(today)` | "X/Y 완료" + 미읽은 챕터 최대 2개 |

### 6.3 Hilt 주입 패턴

위젯은 시스템이 직접 인스턴스화하므로 `@HiltAndroidApp` 자동 주입 불가. 아래 패턴 사용:

```kotlin
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint { /* DAO 반환 함수 */ }

// 사용 시
EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
```

### 6.4 갱신 정책

- **즉시 갱신:** `WidgetRefreshHelper`(@Singleton). `GlanceAppWidgetManager`로 위젯 ID를 조회해 `LSyncWidget().update()` 호출. Home/Finance/Schedule ViewModel이 데이터 변경 후 `requestUpdate()`를 부른다(`@ApplicationScope` 코루틴).
- **주기 갱신:** `WidgetRefreshWorker`(`@HiltWorker` 없는 순수 `CoroutineWorker`). `LSyncWidget().updateAll(context)` 호출. `ExistingPeriodicWorkPolicy.KEEP`으로 앱 재시작 시 타이머 리셋 방지. `LSyncApplication.onCreate()`에서 `enqueuePeriodicWork()`.
- **Fallback:** 시스템 `updatePeriodMillis`(30분).

---

## 7. 트랜잭션 및 모니터링

- **원자성:** Todo 완료 → Finance 생성은 Firestore Batch Write로 묶음.
- **Crashlytics:** 네트워크 연결 상태에서 Exception 발생 시 `sync_failed` 이벤트 기록.
- **로컬 계측:** 같은 `syncSafe`가 성공·실패를 Room `sync_log`에도 남긴다(Crashlytics와 병행). 재전송·에러율 화면은 §12.
- **복원 동기화(pull):** `SyncRepository.pullAll(userId)`가 `FirestoreDataSource.fetchEvents/fetchTodos/fetchFinance/fetchBudgets`로 원격을 받아 **last-write-wins upsert-only** 머지(항목별 `updatedAt > local.updatedAt`일 때만 덮어씀, 로컬 전용 데이터 미삭제). 비교용 Room 조회에는 tombstone도 포함해 오래된 원격 문서가 로컬 삭제를 되살리지 않게 한다. `AuthRepository`가 로그인·자동로그인 시 마이그레이션 직후 백그라운드 실행. 재설치·기기 변경 복원 경로. 엔티티 그룹별 `syncSafe`로 부분 실패 격리.
- **로그아웃 데이터 격리(계정 전환):** `AuthRepository.signOut()`은 `clear → firebaseAuth.signOut()` 순서. `SyncRepository.clearLocalUserData()`가 동기화 5테이블(events / todos / finance / budgets / todo_templates)을 `clearAll()`하되 **알람을 선취소한 뒤 삭제**. `reading_plan` / `memos`는 로컬 전용·복원 불가라 **보존**. `AppModule.provideSyncRepository`에 `todoTemplateDao`·`alarmScheduler` 주입.

---

## 8. 통합 검색 (Global Search)

- **횡단 LIKE 검색:** `EventDao.searchByTitle`·`TodoDao.searchByTitle`·`FinanceDao.search`(LIKE, Flow). `SearchRepository`가 `SearchResults{events, todos, finances}`를 3 Flow `combine`(blank 가드).
- **ViewModel:** `SearchViewModel`(@HiltViewModel) `debounce(200)` + `flatMapLatest`, `onQueryChange`/`clearQuery`.
- **진입·이동:** `NavGraph` `composable("search")`(하단탭 아님) + `HomeScreen` 헤더 검색 아이콘. 결과 탭 → 해당 탭으로 이동만(인라인 편집 없음).

---

## 9. 월간 리포트 (Monthly Report)

- **집계 소스:** `ReportViewModel`(@HiltViewModel)이 Room Flow 4개를 `combine`. 신규 DAO/Repository 클래스 없이 기존 클래스에 메서드만 추가 — 스키마 변경·마이그레이션·`AppModule` 등록 모두 불필요.

| 지표 | 소스 | 규칙 |
|------|------|------|
| 일정 건수 | `EventRepository.observeForExpansion(from, to)` | `expandEvents(events, from, to).size` — 반복 마스터를 전개한 **발생 수**(행 수 아님) |
| 할일 완료율 | `TodoRepository.observeByDueDateRange(from, to)` | `deletedAt IS NULL AND dueDate IS NOT NULL` — 마감일 없는 Todo는 분모에서 제외 |
| 순지출 | `FinanceRepository.observeByDateRange(from, to)` | `(Σ EXPENSE − Σ 정산입금).coerceAtLeast(0)` |
| 수입 | 〃 | `type == "INCOME" && settlementGroupId == null`만 합산(정산 입금 제외) |
| 상위 카테고리 | 〃 | EXPENSE **원금** 기준 groupBy → 내림차순 5개(정산 받음 차감 없음) |
| 통독 | `ReadingPlanRepository.observeReadInRange(from, to)` | `isRead = 1`. 장 수 = 행 수, 일 수 = distinct `date` |

- **정산 정책 일관성:** 가계부 집계는 `FinanceDashboardViewModel.aggregate`와 동일 공식(PRD 2.4). 어긋나면 홈·가계부 탭·위젯과 수치가 불일치한다.
- **월 범위:** Floating Date 문자열 `"%04d-%02d-01"` ~ `"%04d-%02d-{lengthOfMonth}"`. 타임존 변환 없음.
- **월 이동:** `previousMonth`/`nextMonth`(`YearMonth.now()` 초과 시 무시). 변경 시 `monthJob.cancel()` → 상태 초기화 → 새 범위 재구독.
- **UI:** `ui/report/ReportScreen.kt` — `collectAsStateWithLifecycle()`만 사용, `Canvas` 직접 드로잉(차트 라이브러리 미사용), 지출에 `AccentRed` 미사용. 진입은 `composable("report")` + `HomeScreen` 헤더 아이콘(하단 4-tab 불변).

### 9.1 리포트 내보내기 (Phase 21)

월간 리포트를 **이미지(PNG) / 텍스트** 두 형식으로 공유한다. 집계는 재계산하지 않고 `ReportUiState`를 그대로 소비한다(§9 표가 유일한 공식).

| 구성 | 파일 | 계약 |
|------|------|------|
| 텍스트 요약 | `ui/report/ReportSummaryText.kt` | `buildReportSummaryText(state): String`. Android·`Context`·로케일 의존 금지(월 이름 직접 조립, 금액은 `Locale.US` 고정 그룹 구분자) → JVM 단위 테스트 대상 |
| 카드 렌더 | `ui/report/ReportCardRenderer.kt` | `suspend renderReportCard(context, state, widthPx = 1080): Bitmap?`. `Dispatchers.Main` 고정(View measure/layout/draw), 실패 시 `null` |
| 공유 카드 | `ui/report/ReportScreen.kt` `ReportShareCard`(internal) | 화면의 4카드 Composable을 재사용하는 최상위 `Column` — 별도 레이아웃을 만들지 않는다 |
| 파일·Intent | `data/export/ShareFiles.kt` | `suspend savePngForShare(context, bitmap, fileName): Uri?` / `buildImageShareIntent(context, uri, subject)` / `buildTextShareIntent(text, subject)` |

**렌더 파이프라인** (Compose 1.6.7에 `rememberGraphicsLayer()`/`toImageBitmap()`이 없어 채택)

1. `ContextWrapper` 체인을 따라 `Activity`를 찾고 `android.R.id.content`에 `ComposeView`를 **1×1 INVISIBLE**로 붙인다(`DisposeOnDetachedFromWindow`).
2. `LocalDensity`를 `Density(density = 3f, fontScale = 1f)`로 고정 — 기기 밀도/글꼴 배율이 이미지에 새지 않게 한다(`1080px / 3f = 360dp` 폭).
3. `awaitFrame()` ×2 → `measure(EXACTLY widthPx, UNSPECIFIED)`. 높이가 0이면 한 프레임 더 기다렸다 재측정, 그래도 0이면 `null`.
4. `layout` → `Bitmap(ARGB_8888)` + `Canvas.drawColor(0xFF0A0A0A)`(BgPrimary) 후 `draw`.
5. `finally`에서 반드시 `removeView` — 누락 시 보이지 않는 뷰와 컴포지션이 Activity에 누적된다.

**FileProvider 3중 일치** — 하나라도 어긋나면 런타임 `IllegalArgumentException: Failed to find configured root`.

| 위치 | 값 |
|------|-----|
| `AndroidManifest.xml` | `android:authorities="${applicationId}.fileprovider"`, `exported="false"`, `grantUriPermissions="true"` |
| `res/xml/file_paths.xml` | `<cache-path name="shared" path="shared/" />` |
| `ShareFiles.kt` | `File(context.cacheDir, "shared")`, authority = `"${context.packageName}.fileprovider"` |

**주의 사항**

- **캐시 정리는 다음 저장 직전에만.** 공유 직후 삭제하면 수신 앱(카카오톡·Gmail 등)이 비동기로 읽기 전에 파일이 사라진다.
- `FLAG_GRANT_READ_URI_PERMISSION` 누락 → 수신 앱 `SecurityException`. `clipData` 누락 → Android 13+ Sharesheet 썸네일 미리보기 없음.
- 렌더/저장 실패는 예외를 삼키고 `null`을 반환하며, 화면이 **스낵바 액션으로 텍스트 공유를 대안 제시**한다.
- 공유 관련 상태(형식 시트·렌더 진행·미리보기 비트맵)는 전부 `ReportScreen`의 로컬 `remember` — `ReportViewModel`은 무변경이다.

---

## 10. Room Entity 현황

| Entity | DB | 주요 필드 |
|--------|-----|-----------|
| EventEntity | AppDatabase | id, userId, title, isAllDay, startDate, endDate, timezone, rrule, exdatesJson?, overridesJson?, hasAlarm, deletedAt? |
| TodoEntity | AppDatabase | id, userId, templateId?, title, isCompleted, dueDate?, financeIsLinked, financeType?, financeCategory?, financeAmount?, linkedFinanceId?, deletedAt? |
| FinanceEntity | AppDatabase | id, userId, type, amount, category, date, note?, sourceTodoId?, isExcluded, settlementGroupId? |
| TodoTemplateEntity | AppDatabase | id, userId, title, rrule, financeIsLinked, financeType?, financeCategory?, financeAmount?, isActive |
| ReadingPlanEntity | AppDatabase | id, date, book, chapter, isRead |
| MemoEntity | AppDatabase | id, book, chapter, verse, text, date |
| BudgetEntity | AppDatabase | id(`userId_category`), userId, category, limitAmount, createdAt, updatedAt, deletedAt? |
| SyncLogEntity | AppDatabase | id, userId, target, entityId?, success, errorType?, errorMessage?, createdAt, resolvedAt? (로컬 전용) |
| BibleVerseEntity | BibleDatabase | idx, book, chapter, verse, text, testament, book_name, book_short |
| EsvVerseEntity | EsvDatabase | idx, book, chapter, verse, text |

---

## 11. 웹 클라이언트 동기화 계약 (`web/`)

웹(Next.js)은 앱과 같은 Firestore 컬렉션을 직접 읽고 쓴다. **앱의 읽기 방식이 계약의 기준**이다.

`FirestoreDataSource`의 역매핑은 `runCatching { ... }.getOrNull()` + `mapNotNull` 조합이므로, 필드 타입이 하나라도 어긋나면 **예외 없이 그 문서가 통째로 버려진다.** 로그도 남지 않는다.

| 필드 | 앱이 읽는 방식 | 웹이 써야 하는 값 | 어기면 |
|------|---------------|-----------------|--------|
| 문서 ID | `document(entity.id)` — 엔티티 id가 곧 문서 ID | `setDoc(doc(col, id))`, id는 UUID | 앱이 `document(id)`로 갱신 시 별도 문서가 생겨 갈라짐 |
| `id` | `getString("id") ?: return null` | 문서 ID와 **동일한** UUID를 본문에도 | `null` 반환 → 드롭 (`addDoc` 자동 ID는 본문에 `id`가 없다) |
| `createdAt` / `updatedAt` | `getLong(...) ?: 0L` | `Date.now()` (epoch millis Number) | `serverTimestamp()` → Timestamp → 예외 → 드롭 |
| `deletedAt` / `completedAt` | `getLong(...)` (nullable) | `Date.now()` 또는 `null` | ISO 문자열 → 예외 → 드롭 |
| `date` / `dueDate` / `startDate` | `getString(...)` | `YYYY-MM-DD` **Floating Time 문자열 그대로** | millis로 바꾸면 드롭 + 타임존 의미 파괴 |
| `amount` | `getLong("amount")` | Number | 문자열이면 드롭 |

- **삭제는 tombstone만.** 앱의 pull(`SyncRepository.pullAll`)은 **upsert-only**라 원격에서 문서가 사라져도 Room 행은 남는다. hard delete하면 다음 로컬 변경 때 그 행이 다시 push되어 **부활**한다. `deletedAt`(millis) + `updatedAt` 갱신으로 전파한다.
- **읽기 측도 tombstone을 걸러야 한다** — `useEvents`/`useTodos`/`useFinance` 모두 `!deletedAt` 필터.
- **집계는 `web/lib/finance.ts` 단일 출처.** 앱 §9의 공식과 동일 — 순지출 `Math.max(0, Σ EXPENSE − Σ 정산입금)`, 수입은 `settlementGroupId == null`인 INCOME만, 상위 카테고리는 EXPENSE 원금 기준 내림차순 5개.
- **반복 일정은 `web/lib/recurrence.ts`** (`EventRecurrence.kt`의 웹 대응물). RRULE 파싱은 `rrule` 패키지에 위임. dtstart는 `new Date(Date.UTC(y, m-1, d))`, 결과는 `toISOString().slice(0,10)` — **UTC 왕복만 허용**(로컬 타임존 왕복 시 KST에서 하루 밀림). 전개 순서는 `범위 필터 → exdates 제외 → overrides 적용`, override는 시각만 바꾸고 발생 날짜는 옮기지 않는다.
- **통독은 웹에 없다** — `reading_plan`·`memos`는 Firestore 미동기화(로컬 전용). 웹 리포트는 일정·할일·가계부 3개 도메인만 집계한다.
- **`crypto.randomUUID()`는 secure context 전용** — https 또는 localhost에서만 동작한다. 평문 http(LAN IP)로 접속한 dev 서버에서는 생성이 실패한다.
- **미해결:** Phase 19 이전에 웹에서 만든 문서는 랜덤 문서 ID + Timestamp `createdAt`이라 여전히 앱이 읽지 못한다. 마이그레이션 스크립트는 없다 — Firebase 콘솔에서 수동 확인·정리.

### 11.1 웹 인증 (Google 로그인)

`1ae9917 feat(web): add Firebase Google authentication`으로 웹의 `local_user` 고정 UID가 제거됐다. 별도 Phase 번호 없이 Phase 19(웹 따라잡기)의 보강으로 취급한다.

| 구성 | 파일 | 내용 |
|------|------|------|
| Auth 인스턴스 | `web/lib/firebase.ts` | 같은 Firebase 앱에서 `getAuth(app)` → `auth` export |
| 인증 게이트 | `web/components/AuthProvider.tsx` | `onAuthStateChanged` 확인 중이면 "로그인 확인 중…", 미로그인이면 로그인 화면(`signInWithPopup` + `GoogleAuthProvider`). 로그인 후 `AuthenticatedApp`(key = `user.uid`)이 `uid: user.uid`를 컨텍스트로 내려준다. 상단에 이메일 + 로그아웃(`signOut`) |
| 쓰기 래퍼 | `AuthProvider`의 `runWrite(action)` | 실행 전후 `auth.currentUser?.uid === user.uid`를 확인하고, 실패 시 에러 배너를 띄운 뒤 `false` 반환. 일정·가계부 페이지의 추가·삭제·토글이 모두 이것을 거친다 |
| 읽기 에러 | `web/hooks/useEvents.ts`·`useTodos.ts`·`useFinance.ts` | `onSnapshot` 에러 콜백에서 목록을 비우고 `reportReadError(source, message)`로 배너 표시. 성공·언마운트 시 해제 |
| 에러 문구 | `errorMessage` | `permission-denied` / `auth/popup-blocked` / `auth/unauthorized-domain` / `auth/operation-not-allowed`를 한국어로 매핑. 팝업을 닫은 경우(`auth/popup-closed-by-user`·`auth/cancelled-popup-request`)는 표시하지 않는다 |

- **보안 규칙(§3)을 웹에서도 만족한다.** 웹이 Google 로그인으로 앱과 같은 실제 Firebase UID를 얻고, 쓰기는 그 UID를 `userId`로 넣는다. 따라서 `request.auth.uid == request.resource.data.userId`(create)·`resource.data.userId`(read/update/delete)가 앱과 동일하게 성립한다. 앱과 **같은 Google 계정**으로 로그인해야 같은 데이터가 보인다.
- **목록 조회는 `userId` 동등 필터를 유지해야 한다.** Firestore 규칙은 필터가 아니므로, `web/lib/db.ts`의 `eventsQuery`/`todosQuery`/`financeQuery`가 `where('userId', '==', uid)`를 빼면 결과 중 일부만 허용되는 쿼리로 판정돼 **목록 전체가 `permission-denied`로 거부**된다. 인증 도입으로 이 필터의 `uid`가 `local_user`에서 실제 UID로 바뀌었을 뿐 제약은 그대로다.
- **이 커밋에서 바뀌지 않은 것:** 쓰기 계약(표 위 — 문서 ID = 본문 `id`, epoch millis, tombstone)과 `!deletedAt` 읽기 필터. 추가로 `addFinance`가 `note`를 `undefined` 대신 `null`로 쓴다(`note: data.note ?? null`).
- **Firebase 콘솔 수동 설정:** Authentication에서 Google 제공자 활성화, Authorized domains에 `localhost`·배포 도메인 등록. 절차는 `web/README.md`.

---

## 12. 동기화 신뢰성 계측 (Phase 23)

동기화 실패가 Crashlytics로만 가서 사용자·개발자가 기기에서 확인할 수 없던 문제를 해결한다. 모든 원격 연산 결과를 Room `sync_log`(§1.8)에 남기고, 실패는 네트워크 복구 시 재전송하며, 에러율을 화면으로 보여준다.

```
Repository.syncSafe ─┬─ Crashlytics(sync_failed)          ← 원격 모니터링(기존 유지)
                     └─ SyncLogger ─→ Room sync_log ─┬─→ SyncStatusScreen (에러율·미해결·최근 기록)
                          │ (실패 시)                 └─→ SyncRepository.retryFailed
                          └─ SyncRetryTrigger ─→ SyncRetryWorker ─┘
```

### 12.1 기록 지점

| `syncSafe` 위치 | 실행 방식 | `SyncTarget` | `entityId` |
|-----------------|----------|--------------|------------|
| `EventRepository` | `appScope.launch` fire-and-forget | `EVENTS` | 이벤트 id |
| `TodoRepository` | 〃 | `TODOS` | Todo id (배치 연산도 Todo id 1행) |
| `FinanceRepository` | 〃 | `FINANCE` | 거래 id |
| `BudgetRepository` | 〃 | `BUDGETS` | `"${userId}_${category}"` |
| `SyncRepository.pullAll` | suspend (호출부가 대기) | `PULL` | null — 엔티티 그룹(events/todos/finance/budgets) fetch마다 1행. **게이트 분모에서 제외**(§12.6) |

- 시그니처: `syncSafe(target: SyncTarget, userId: String, entityId: String?, block: suspend () -> Unit)`. `block()` 성공 후 `recordSuccess`, 예외 시 Crashlytics 기록 **후** `recordFailure`.
- **원격 연산 1회 = 로그 1행.** `completeTodoWithFinance`·`uncheckTodoWithFinance`·`deleteTodoUnlinkFinance` 같은 배치 write도 Todo id로 1행만 쓴다. 정산 그룹 삭제처럼 사용자 작업 1회가 여러 원격 연산을 만들면 로그도 여러 행이다.
- **`SyncLogger`는 `AuthRepository`를 주입받지 않는다.** `AuthRepository → SyncRepository → SyncLogger → AuthRepository`로 Hilt 순환이 생긴다. `userId`는 호출부가 엔티티(`entity.userId`)나 `pullAll(userId)` 인자에서 넘긴다.
- **Crashlytics 기록은 그대로 유지한다.** 로컬 `sync_log` = 사용자가 기기에서 확인·재시도하는 용도, Crashlytics = 개발자 원격 모니터링 용도로 역할이 다르다.

### 12.2 `SyncLogger` (`data/sync/SyncLogger.kt`, `@Singleton`)

- `SyncTarget` enum(`EVENTS`/`TODOS`/`FINANCE`/`BUDGETS`/`PULL`, `key`가 `sync_log.target` 문자열의 단일 출처).
- `suspend recordSuccess(target, userId, entityId)` / `suspend recordFailure(target, userId, entityId, error)`. `errorType` = `error::class.java.simpleName`, `errorMessage` = `message.take(200)`.
- **기록 실패는 삼킨다**(`runCatching`) — 진단 기록 때문에 사용자 데이터 저장 흐름이 깨지면 안 된다.
- `recordFailure`는 기록 후 `SyncRetryTrigger.schedule()`을 호출한다(이것도 `runCatching`).
- **보존:** 30일(`RETENTION_MILLIS`) 초과 성공 로그·해소된 실패·`SourceMissing`을 `SyncLogDao.pruneOlderThan`으로 삭제한다. 그 밖의 미해결 실패는 기간과 무관하게 보존한다. 정리는 **삽입 50회(`PRUNE_EVERY`)마다 1회** 실행하고, 새 기록이 없어도 만료된 `SourceMissing`이 남지 않도록 앱 시작 때 `SyncLogger.pruneExpired()`를 한 번 실행한다. 삽입 카운터는 `AtomicInteger`다.

### 12.3 재전송 (`SyncRepository.retryFailed(userId): RetryResult`)

`RetryResult(attempted, resolved, failed, missing)`. `SyncLogDao.getUnresolvedFailures(userId)`(오래된 순)를 대상으로 한다.

- **재전송 전용 조건부 쓰기:** Room 현재 행을 `FirestoreDataSource.retryEvent`/`retryTodo`/`retryFinance`/`retryBudget`으로 보낸다. 일반 push의 오프라인 큐를 유지하려고 `upsert*`는 그대로 둔다. 트랜잭션 안에서 원격 문서를 읽고, 없거나 원격 `updatedAt <= local.updatedAt`일 때만 `set(merge)`한다.
  - `batch.update` 기반 메서드(`uncheckTodoWithFinance`·`deleteTodoUnlinkFinance`)를 재사용하지 않는 이유: `update`는 **문서가 없으면 실패**한다. 생성 push가 실패해 원격 문서가 아예 없는 상태에서 미완료·삭제를 재현하면 영원히 실패한다. Room 행 전체를 merge하면 문서 유무와 무관하게 최종 상태가 맞는다.
  - **`todos` 재전송은 `linkedFinanceId`의 Finance를 동반한다.** 두 문서를 같은 트랜잭션에서 모두 읽은 뒤, 쓰기 여부는 문서별로 판정한다. 한쪽 원격만 최신이면 그 문서만 건너뛰고 다른 문서는 쓴다. 문서가 없으면 생성하므로 dangling reference를 피한다. 두 문서 모두 쓰였거나 최신 원격 때문에 건너뛰었을 때 로그를 해소한다.
  - **`updatedAt`은 새로 찍지 않는다.** 그 자체는 LWW 보호가 아니며, 트랜잭션 안의 원격 시각 비교가 오래된 재전송의 덮어쓰기를 막는다. 남는 한계: 문서 단위 LWW라 Todo와 Finance 사이의 의미상 불변식은 보장하지 못한다. 교차 문서 충돌 해소는 별도 설계가 필요하다.
- **`pull` 행은 여러 개여도 1회만** LWW 머지를 수행해 전부 해소한다. 이때 `pullAll`이 아닌 private `pullWithoutLogging`(예외를 그대로 던짐)을 쓴다 — `pullAll`은 실패를 삼키고 새 로그를 남겨 성공 여부를 알 수 없다.
  - **LWW 머지 규칙의 단일 출처는 `mergeEvents`/`mergeTodos`/`mergeFinance`/`mergeBudgets` 4개 private 함수다.** `pullAll`은 각각을 `syncSafe`로 감싸 호출하고(그룹별 부분 실패 격리), `pullWithoutLogging`은 그대로 호출한다. 두 경로가 머지 로직을 각자 복사하면 "재시도로 받은 데이터"와 "평소 pull로 받은 데이터"의 기준이 조용히 갈라진다(Phase 17에서 집계 공식 사본이 갈라진 것과 같은 실패 양상).
- **원본 행이 없거나 `entityId`가 null·target이 알 수 없으면 성공으로 해소하지 않는다.** `SourceMissing`과 구체적 사유를 로그에 남겨 화면 미해결 건수와 최근 기록에 `원본 없음`으로 보인다. `getUnresolvedFailures()`에는 계속 포함하므로 원본이 복구되면 수동 재시도·다음 작업 실행에서 해소할 수 있다. 복구되지 않으면 30일 경과 후 다음 가지치기(앱 시작 또는 50번째 삽입)에서 삭제한다. `RetryResult.missing`은 예외 실패와 구분한다.
- 재전송 조회는 삭제된 행도 포함한다(`getByIdIncludingDeleted`). 일반 화면 조회의 `deletedAt IS NULL` 필터를 그대로 쓰면 실패한 tombstone을 원본 없음으로 오인한다.
- 항목별 `runCatching` — 한 건 실패가 나머지 재전송을 막지 않는다. 성공 시 `markResolved(id, now)`.
- **재시도 경로는 `sync_log`에 새 행을 남기지 않는다(`SyncLogger` 미호출).** 재시도 실패를 다시 기록하면 `recordFailure → schedule → 재시도 → 실패 → recordFailure`의 무한 순환이 생기고, 재시도 횟수가 에러율 분모·분자를 오염시킨다. 남은 실패는 워커가 `Result.retry()`로 WorkManager 지수 백오프에 위임한다.

### 12.4 워커

| 클래스 | 내용 |
|--------|------|
| `worker/SyncRetryTrigger` (`@Singleton`) | `OneTimeWorkRequest<SyncRetryWorker>` + `NetworkType.CONNECTED` 제약 + `BackoffPolicy.EXPONENTIAL` 30초. `enqueueUniqueWork("sync_retry", ExistingWorkPolicy.KEEP)` — 실패가 연달아 기록돼도 대기·실행 중인 워커를 유지해 백오프가 리셋되지 않게 한다 |
| `worker/SyncRetryWorker` (`@HiltWorker`) | 미로그인이면 `Result.success()`. 재전송 뒤 **재시도 가능한 미해결 건수**를 재조회해 남았으면 지수 백오프로 `Result.retry()`. `SourceMissing`은 재전송 목록·화면 건수에는 남지만 계속-재시도 판정에서는 제외한다. 최초 실행 포함 최대 5회(`runAttemptCount` 0~4) 뒤에는 로그를 남기고 자동 시도를 멈춘다. 권한 오류의 무한 재시도를 피하기 위한 상한이다. |

`KEEP`은 실행 중 들어온 예약을 버린다. 워커의 마지막 재조회 이후 새 실패가 기록되는 경쟁 조건은 남는다. `LSyncApplication.onCreate()`가 `SyncRetryTrigger.schedule()`을 호출해 다음 앱 시작에 회수하고, 사용자는 화면에서 수동 재시도할 수 있다. `APPEND_OR_REPLACE`는 연속 실패 시 작업 체인이 쌓이고 기존 백오프 동작을 바꾸므로 사용하지 않는다.

### 12.5 로그아웃·격리

- **로그아웃 시 `sync_log`는 비우지 않는다.** `SyncRepository.clearLocalUserData()`의 정리 대상(동기화 5테이블)에 포함되지 않는다 — PRD 2.7의 `reading_plan`·`memos`와 같은 취급(로컬 전용·Firestore에서 복원 불가).
- 다른 계정에 노출되지 않도록 **`SyncLogDao`의 모든 조회가 `userId`로 필터**한다(`observeRecent`/`observeUnresolvedCount`/`observeAttemptsSince`/`observeFailuresSince`/`getUnresolvedFailures`). `pruneOlderThan`만 전체 사용자 대상이다.

### 12.6 화면 (`ui/sync/`)

- **진입:** `HomeScreen` 헤더 메뉴 "동기화 상태" → `NavGraph` `composable("sync")`(하단 탭 아님).
- **`SyncStatusViewModel`**(`@HiltViewModel`): `SyncLogDao` Flow 4개(`observeAttemptsSince`·`observeFailuresSince`·`observeUnresolvedCount`·`observeRecent(limit = 50)`)를 `combine`. 구간(7일/30일) 변경 시 `windowJob.cancel()` 후 새 `since`로 재구독(`ReportViewModel` monthJob 패턴). `retryNow()`는 `isRetrying` 중복 가드 후 `retryFailed` 호출 — 결과 수치는 Room Flow가 갱신한다.
- **에러율 = `failures / attempts`**(구간 내, 0나눗셈 가드 = 0). `passesGate` = 에러율 < 5%(PRD Phase 1 게이트).
- **게이트 분모·분자는 push 원격 연산 수**다. DAO 쿼리가 `target != :pullTarget`로 자동 복원을 제외하고, pull은 별도로 참고 표시한다. 정산 그룹 삭제처럼 사용자 작업 1회가 여러 원격 연산을 만들면 여러 건으로 센다. `pullTarget` 문자열은 `SyncTarget.PULL.key`를 넘긴다.
- 재시도는 행을 만들지 않으므로 **분모·분자 모두에서 제외**된다(§12.3).
- **`SyncStatusScreen`:** 구간 ghost chip → 게이트 카드(에러율 %, `원격 전송 실패 N / 시도 M`, pull 참고 수치, 통과/초과 문구) → 미해결 실패 카드(건수 + "지금 재시도") → 최근 기록 50건(시각 · target, 상태 성공/해소됨/실패/원본 없음, 실패 시 `errorType: errorMessage`).
- **`AccentRed`는 게이트 초과(에러율 수치·판정 문구)와 미해결 실패 로그 행의 "실패" 상태에만** 쓴다. 미해결 건수 자체는 `FgPrimary`, "지금 재시도"는 `AccentBlue`(절제된 경고 색 — UI 가이드와 일관).

---

## 13. 알람 신뢰성 계측 (Phase 25, 기기 실측 대기)

`AppDatabase` v8의 로컬 전용 테이블 네 개를 사용한다. `alarm_log`는 `(alarmKey, expectedAt)`별 기대 발생과 발화·게시 결과를 기록한다. `alarm_delivery_token`은 OS 등록 시도마다 발급한 UUID와 상태(`PREPARED`/`ARMED`/`FAILED`/`RETIRED`)를 보관한다. `alarm_coverage_state`와 `alarm_coverage_gap`은 실제로 관측한 범위와 공백을 구분한다. 이 데이터는 Firestore에 동기화하지 않는다.

- **등록과 수신:** 단일 `AlarmCommandQueue` IO 소비자가 최신 Room 상태를 읽고 48시간 기대 집합을 계산한다. OS 등록 전 토큰을 저장하며, 토큰을 `Intent.data`에 넣어 PendingIntent를 구분한다. 리시버는 수신한 토큰으로 기대 발생을 찾아 발화를 한 번만 기록하고 알림 게시 결과를 별도로 남긴다. 알 수 없는 토큰과 미리보기는 발화로 세지 않는다. 같은 key가 같은 미래 시각·같은 정확/비정확 모드로 이미 `ARMED`이고 OS PendingIntent가 살아 있으면 새 토큰을 만들지 않고 extras만 갱신한다(`AlarmRegistrar.reusableToken`). 재부팅·강제 종료로 PendingIntent가 사라졌거나 권한 변경으로 모드가 바뀌면 새 토큰으로 다시 등록한다. 실제로 거는 다음 트리거는 48시간 창 밖이어도 기대 행을 남긴다(기대 행 없는 토큰은 수신이 집계되지 않음). 누락 판정은 관측 범위 안의 행만 대상이다. `FIRED` 행은 재등록돼도 `exact`를 덮어쓰지 않는다.
- **대사와 보존:** `AlarmRestoreWorker`는 재부팅·앱 업데이트 및 12시간 주기로 실행한다. 등록 명령이 완료된 뒤 관측 범위를 갱신하고, 범위 밖·공백 안의 기대 발생은 누락으로 판정하지 않는다. 기한이 지난 미발화는 정확 알람 15분, 비정확 알람 2시간 유예 후 누락 처리한다. 비활성 기록은 30일 뒤 정리한다.
- **화면과 판정:** 홈 메뉴의 `알람 신뢰성` 화면에서 7일/30일 일정·할 일 발화, 누락, 알림 권한·채널 차단 건수를 보여준다. 관측 가능한 발생의 `(누락 + 게시 차단) / (발화 + 누락)`을 5%와 비교한다. 관측 공백이 있으면 판정 불가, 발생이 없으면 데이터 없음이다. 통독·소비 리마인더는 별도 참고 지표다. `notify()` 호출 성공은 사용자가 알림을 실제로 보았다는 증거가 아니다.
- **검증 상태:** 6→8·7→8 마이그레이션의 데이터 보존은 에뮬레이터 계측 테스트로 확인했다. Doze·재부팅·권한 변경·콜드 스타트 발화 순서와 기대 창·유예값의 실제 기기 실측이 남아 있으므로 현재 5% 결과는 참고 판정이다. 상세 설계와 남은 단계는 `phases/25-alarm-reliability/DESIGN.md`, `STEPS.md`를 따른다.
