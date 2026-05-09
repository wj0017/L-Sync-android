# L-Sync Technical Spec (v0.2)

**목적:** 실제 데이터베이스 구조, 보안 규칙, 안드로이드 권한 및 알림, 백그라운드 엔진 등 구현에 직접적으로 필요한 기술적 명세 제공.

---

## 1. 데이터베이스 스키마

> 공통 메타데이터: `id`, `userId`, `createdAt`, `updatedAt`, `deletedAt`(옵션)

### 1.1. `events` (캘린더 일정)

`isAllDay` 플래그를 도입하여 시간대(Timezone) 적용 여부를 명확히 분리.

```json
{
  "title": "주간 회의",
  "isAllDay": false,
  "startDate": "2024-05-01T09:00:00+09:00",
  "timezone": "Asia/Seoul",
  "endDate": null,
  "rrule": "FREQ=WEEKLY;BYDAY=MO",
  "exdates": ["2024-05-06"],
  "overrides": {
    "2024-05-13": { "startTime": "2024-05-13T11:00:00+09:00" }
  },
  "hasAlarm": true
}
```

### 1.2. `todos` & `todo_templates`

`dueDate`는 `YYYY-MM-DD` (Floating Time).

```json
{
  "id": "template_789_2024-05-15",
  "templateId": "template_789",
  "title": "넷플릭스 구독료 (5월)",
  "isCompleted": false,
  "dueDate": "2024-05-15",
  "financeAction": {
    "isLinked": true,
    "type": "EXPENSE",
    "category": "구독료",
    "amount": 13500
  },
  "linkedFinanceId": "finance_abc"
}
```

### 1.3. `finance` (가계부 트랜잭션)

`date`는 디바이스 로컬 기준 날짜(`YYYY-MM-DD`).

```json
{
  "type": "EXPENSE",
  "amount": 13500,
  "category": "구독료",
  "date": "2024-05-15",
  "sourceTodoId": "template_789_2024-05-15"
}
```

### 1.4. `bible` & `memos` (Phase 3)

```json
// bible (읽기 전용 공용 데이터)
{
  "book": "창세기", "chapter": 1, "verse": 1,
  "text": "태초에...", "translation": "개역개정"
}
// memos (묵상 메모)
{
  "bibleRef": { "book": "창세기", "chapter": 1, "verse": 1 },
  "content": "나의 묵상...", "linkedDate": "2024-05-15"
}
```

---

## 2. Firebase 보안 규칙

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /bible/{docId} {
      allow read: if true;
      allow write: if false;
    }
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

---

## 3. 백그라운드 엔진: Client-side Materialization

- **Idempotency:** Todo ID = `${templateId}_${dueDate}` (결정론적). Upsert로 중복 방지.
- **Materialization Window:** 현재일 기준 +14일치 인스턴스를 미리 생성.
- **템플릿 Update 정책:** 미완료 미래 인스턴스는 새 정의로 덮어쓰기. 완료된 과거 인스턴스는 변경 무시.

---

## 4. 안드로이드 알림 & 권한

- **재부팅 시 알람 복구:** `RECEIVE_BOOT_COMPLETED` → `BootReceiver` → 로컬 DB 조회 → AlarmManager 재등록.
- **권한 요청 흐름 (Android 13+):**
  1. 첫 실행 시 `POST_NOTIFICATIONS` 요청.
  2. 알림 기능 첫 활성화 시 `SCHEDULE_EXACT_ALARM` 체크. 거부 시 시스템 설정 화면 딥링크.

---

## 5. 트랜잭션 및 모니터링

- **원자성 보장:** Todo 완료 → Finance 생성은 반드시 Firestore Batch Write로 묶는다.
- **Crashlytics 기준:** 네트워크 연결 상태에서 명시적 Exception이 발생한 경우만 `sync_failed` 이벤트로 기록.

---

## 6. Room Entity 현황 (Phase 1 기준)

| Entity | 필드 |
|--------|------|
| EventEntity | id, userId, title, isAllDay, startDate, endDate, timezone, rrule, exdatesJson, overridesJson, hasAlarm, createdAt, updatedAt, deletedAt? |
| TodoEntity | id, userId, templateId?, title, isCompleted, dueDate?, completedAt?, financeIsLinked, financeType?, financeCategory?, financeAmount?, linkedFinanceId?, createdAt, updatedAt, deletedAt? |
| FinanceEntity | id, userId, type, amount, category, date, note?, sourceTodoId?, isExcluded, createdAt, updatedAt |
| TodoTemplateEntity | id, userId, title, rrule, financeIsLinked, financeType?, financeCategory?, financeAmount?, isActive, createdAt, updatedAt |
