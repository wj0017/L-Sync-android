# L-Sync Technical Spec (v0.6)

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
  "hasAlarm": true
}
```

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

### 1.6. 성경 데이터 (로컬 SQLite — Firestore 미사용)

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

### 5.2 결제 알림 자동 가계부
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
- **복원 동기화(pull):** `SyncRepository.pullAll(userId)`가 `FirestoreDataSource.fetchEvents/fetchTodos/fetchFinance`로 원격을 받아 **last-write-wins upsert-only** 머지(항목별 `updatedAt > local.updatedAt`일 때만 덮어씀, 로컬 전용 데이터 미삭제). `AuthRepository`가 로그인·자동로그인 시 마이그레이션 직후 백그라운드 실행. 재설치·기기 변경 복원 경로. 엔티티 그룹별 `syncSafe`로 부분 실패 격리.

---

## 8. Room Entity 현황

| Entity | DB | 주요 필드 |
|--------|-----|-----------|
| EventEntity | AppDatabase | id, userId, title, isAllDay, startDate, endDate, timezone, rrule, hasAlarm, deletedAt? |
| TodoEntity | AppDatabase | id, userId, templateId?, title, isCompleted, dueDate?, financeIsLinked, financeType?, financeCategory?, financeAmount?, linkedFinanceId?, deletedAt? |
| FinanceEntity | AppDatabase | id, userId, type, amount, category, date, note?, sourceTodoId?, isExcluded, settlementGroupId? |
| TodoTemplateEntity | AppDatabase | id, userId, title, rrule, financeIsLinked, financeType?, financeCategory?, financeAmount?, isActive |
| ReadingPlanEntity | AppDatabase | id, date, book, chapter, isRead |
| MemoEntity | AppDatabase | id, book, chapter, verse, text, date |
| BibleVerseEntity | BibleDatabase | idx, book, chapter, verse, text, testament, book_name, book_short |
| EsvVerseEntity | EsvDatabase | idx, book, chapter, verse, text |
