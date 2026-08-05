# L-Sync Technical Spec (v0.8)

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

---

## 2. AppDatabase 마이그레이션

| 버전 | 변경 내용 |
|------|----------|
| 1 | 초기: events, todos, todo_templates, finance |
| 2 | reading_plan 테이블 추가 (MIGRATION_1_2) |
| 3 | memos 테이블 추가 (MIGRATION_2_3) |
| 4 | finance.settlementGroupId 컬럼 추가 (MIGRATION_3_4) |
| 5 | budgets 테이블 추가 (MIGRATION_4_5) |

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
- **등록 시점:** 생성/수정 시 Repository가 `AlarmScheduler.scheduleForEvent`/`scheduleForTodo`로 즉시 AlarmManager 등록. 삭제·완료 시 `cancel`. (재부팅 전에도 발화)
- **트리거 시각(단일 계산):** 시간 지정 일정 = 시작 시각 / 종일 일정·마감일 Todo = 그 날 **09:00**(`REMINDER_HOUR=9`, `ZoneId.systemDefault()`). `trigger ≤ now`면 등록 스킵.
- **게이팅:** Event = `hasAlarm` 플래그. Todo = 마감일 있는 모든 미완료 Todo 자동 리마인더(별도 플래그 없음).
- **재부팅 복원:** `RECEIVE_BOOT_COMPLETED` → `BootReceiver` → `AlarmRestoreWorker` → `getFutureAlarmedEvents`/`getFutureAlarmedTodos` 조회 → 동일한 `scheduleForEvent`/`scheduleForTodo` 재사용(라이브와 시각 로직 일치).
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
- **소비 내용 규칙:** `currentUserId == null`이면 스킵(재무장은 수행). 매일 = 오늘 순지출 + 이번 달 순지출 + 전체 예산(`"${userId}___TOTAL__"`) 사용률. 주간 = **최근 7일 vs 직전 7일** 증감 + 최다 카테고리(EXPENSE 원금) + 이번 달 누적. 집계는 `data/local/FinanceTotals.kt`(`netExpense` = `expense − reimbursed`, 음수 방지).
- **Hilt 리시버:** `@AndroidEntryPoint` + `onReceive` 첫 줄 `super.onReceive(context, intent)`(주입 시점) + `goAsync()`(Room 조회 동안 프로세스 유지).
- **설정 UI:** `ui/settings/NotificationSettingsScreen.kt` — 토글 + 시각(`ui.window.Dialog` + material3 `TimePicker`; material3 1.2.x에 `TimePickerDialog` 컴포저블이 없어 직접 구성) + 요일 ghost chip + "지금 미리보기"(켜진 리마인더를 명시적 브로드캐스트로 즉시 1회 발송). `NavGraph` `composable("notifications")` + `HomeScreen` 헤더 메뉴 진입.

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
- **복원 동기화(pull):** `SyncRepository.pullAll(userId)`가 `FirestoreDataSource.fetchEvents/fetchTodos/fetchFinance/fetchBudgets`로 원격을 받아 **last-write-wins upsert-only** 머지(항목별 `updatedAt > local.updatedAt`일 때만 덮어씀, 로컬 전용 데이터 미삭제). `AuthRepository`가 로그인·자동로그인 시 마이그레이션 직후 백그라운드 실행. 재설치·기기 변경 복원 경로. 엔티티 그룹별 `syncSafe`로 부분 실패 격리.
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
- **UI:** `ui/report/ReportScreen.kt` — `collectAsState()`만 사용, `Canvas` 직접 드로잉(차트 라이브러리 미사용), 지출에 `AccentRed` 미사용. 진입은 `composable("report")` + `HomeScreen` 헤더 아이콘(하단 4-tab 불변).

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
| BibleVerseEntity | BibleDatabase | idx, book, chapter, verse, text, testament, book_name, book_short |
| EsvVerseEntity | EsvDatabase | idx, book, chapter, verse, text |
