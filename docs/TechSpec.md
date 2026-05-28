# L-Sync Technical Spec (v0.5)

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

> 현재 `userId = "local_user"` 하드코딩 상태 (Firebase Auth 미연동).

---

## 4. 백그라운드 엔진

- **Idempotency:** Todo ID = `${templateId}_${dueDate}`. Upsert로 중복 방지.
- **Materialization Window:** 현재일 기준 +14일치 인스턴스 미리 생성.
- **템플릿 Update 정책:** 미완료 미래 인스턴스 덮어쓰기, 완료된 과거 인스턴스 변경 무시.

---

## 5. 안드로이드 알림 & 권한

### 5.1 일정/할 일 알람
- `RECEIVE_BOOT_COMPLETED` → `BootReceiver` → 로컬 DB 조회 → AlarmManager 재등록.
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
| 크기 | 4×3 cells (minWidth 250dp, minHeight 200dp) |
| 시스템 갱신 주기 | 1800000ms (30분, fallback) |
| 실제 갱신 주체 | `WidgetRefreshWorker` (WorkManager PeriodicWork, 30분) |
| 데이터 소스 | Room 전용 (Firestore 미사용) |

### 6.2 위젯 섹션

| 섹션 | 데이터 | 표시 규칙 |
|------|--------|----------|
| 할 일 | `TodoDao.getIncompleteByDate(today)` | 최대 3개 + "+N개 더" |
| 일정 | `EventDao.getByDate(today)` | 최대 3개, 시간 지정 일정은 "HH:mm 제목" 형식 |
| 가계부 | `FinanceDao.getAllByDateRange(userId, monthStart, monthEnd)` | 이달 지출·수입 집계, 정산 항목 제외 |
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

- `WidgetRefreshWorker`: `@HiltWorker` 없는 순수 `CoroutineWorker`. `LSyncWidget().updateAll(context)` 호출.
- `ExistingPeriodicWorkPolicy.KEEP` — 앱 재시작마다 실행 타이머가 리셋되지 않도록 기존 워커 유지.
- `LSyncApplication.onCreate()`에서 `enqueuePeriodicWork()` 호출.

---

## 7. 트랜잭션 및 모니터링

- **원자성:** Todo 완료 → Finance 생성은 Firestore Batch Write로 묶음.
- **Crashlytics:** 네트워크 연결 상태에서 Exception 발생 시 `sync_failed` 이벤트 기록.

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
