# 🛠️ [문서 2] L-Sync Technical Spec (v0.2)

**목적:** 실제 데이터베이스 구조, 보안 규칙, 안드로이드 권한 및 알림, 백그라운드 엔진 등 구현에 직접적으로 필요한 기술적 명세 제공.

---

## 1. 데이터베이스 스키마 (Firestore JSON 모델)

> 공통 메타데이터: `id`, `userId`, `createdAt`, `updatedAt`, `deletedAt`(옵션)

### 1.1. `events` (캘린더 일정)

`isAllDay` 플래그를 도입하여 시간대(Timezone) 적용 여부를 명확히 분리.

```json
{
  "title": "주간 회의",
  "isAllDay": false,
  // isAllDay == false 인 경우 (Absolute Time)
  "startDate": "2024-05-01T09:00:00+09:00",
  "timezone": "Asia/Seoul",
  // isAllDay == true 인 경우 (Floating Time)
  // "startDate": "2024-05-01",
  // "timezone": null,
  "endDate": null,
  "rrule": "FREQ=WEEKLY;BYDAY=MO",
  "exdates": ["2024-05-06"],
  "overrides": {
    "2024-05-13": { "startTime": "2024-05-13T11:00:00+09:00" }
  },
  "hasAlarm": true
}
```

### 1.2. `todos` (단일 할 일 인스턴스) & `todo_templates`

`dueDate`는 `events`의 종일 일정과 동일한 `YYYY-MM-DD` (Floating Time) 규격 통일.

```json
{
  "id": "template_789_2024-05-15", // [결정론적 ID] templateId + 발생일 (중복 생성 방지)
  "templateId": "template_789",
  "title": "넷플릭스 구독료 (5월)",
  "isCompleted": false,
  "dueDate": "2024-05-15",
  "completedAt": null,
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

`date`는 거래가 발생한 **디바이스 로컬 기준 날짜(`YYYY-MM-DD`)**.

```json
{
  "type": "EXPENSE",
  "amount": 13500,
  "category": "구독료",
  "date": "2024-05-15",
  "sourceTodoId": "template_789_2024-05-15"
}
```

### 1.4. `bible` & `memos` (Phase 3 플레이스홀더)

```json
// bible (읽기 전용 공용 데이터)
{
  "book": "창세기",
  "chapter": 1,
  "verse": 1,
  "text": "태초에...",
  "translation": "개역개정"
}

// memos (묵상 메모)
{
  "bibleRef": { "book": "창세기", "chapter": 1, "verse": 1 },
  "content": "나의 묵상...",
  "linkedDate": "2024-05-15"
}
```

---

## 2. Firebase 보안 규칙 (Security Rules)

`create` 시점의 Null Reference 버그를 수정하고, 사용자 데이터 격리 및 `bible` 공용 데이터 읽기 전용 규칙을 적용합니다.

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    // 1. 공용 성경 데이터: 누구나(로그인 안 해도) 읽기 가능, 쓰기는 Admin SDK만.
    match /bible/{docId} {
      allow read: if true;
      allow write: if false;
    }

    // 2. 개인화 데이터 (events, todos, finance, memos 등)
    match /{collection}/{docId} {
      // 읽기 및 삭제: 기존 DB(resource)의 userId 검증
      allow read, delete: if request.auth != null
                       && request.auth.uid == resource.data.userId;

      // 생성: 새로 들어오는 데이터(request.resource)의 userId 검증
      allow create: if request.auth != null
                 && request.auth.uid == request.resource.data.userId;

      // 업데이트: 기존 데이터의 소유자 확인 + userId 임의 변조 방지
      allow update: if request.auth != null
                 && request.auth.uid == resource.data.userId
                 && request.auth.uid == request.resource.data.userId;
    }
  }
}
```

---

## 3. 백그라운드 엔진: Client-side Materialization

반복 Todo 생성(WorkManager)의 멱등성 및 템플릿 수정 시나리오를 정의합니다.

- **Idempotency (중복 방지):** 생성되는 Todo의 ID는 `sha256(templateId + dueDate)` 또는 `${templateId}_${dueDate}`로 강제하여 두 번 돌아도 덮어쓰기(Upsert) 되도록 처리.
- **Materialization Window:** 현재일 기준 **+14일치** 인스턴스를 미리 생성.
- **템플릿 Update 정책:** 템플릿의 금액/내용 변경 시, 이미 생성된 인스턴스 중 **'미완료' 상태인 미래 인스턴스는 새 정의로 덮어쓰기** 됨. '완료(`isCompleted: true`)'된 과거 인스턴스는 영속성 보호를 위해 변경 무시.

---

## 4. 안드로이드 알림 & 권한 UX 플로우

- **재부팅 시 알람 복구:** `RECEIVE_BOOT_COMPLETED` 권한 획득 후, `BootReceiver`가 실행되어 로컬 DB를 조회해 미래 알람들을 `AlarmManager`에 재등록.
- **권한 요청 흐름 (Android 13+):**
  1. 앱 첫 실행 시 온보딩에서 `POST_NOTIFICATIONS`(푸시 권한) 요청.
  2. 일정/Todo 알림 기능 첫 활성화 시 `SCHEDULE_EXACT_ALARM` 권한 체크. 거부되어 있다면 **"정확한 알림을 위해 설정에서 권한을 켜주세요" 안내 모달 노출 후 시스템 앱 설정 화면으로 딥링크** 이동.

---

## 5. 트랜잭션 및 모니터링 정책

- **양방향 참조 업데이트:** Todo를 완료하여 Finance를 생성할 때, 두 문서의 상태(`linkedFinanceId`, `sourceTodoId`)는 반드시 **Firestore Batch Write** 연산으로 묶어 원자성(Atomicity)을 보장해야 함. 하나라도 실패하면 둘 다 롤백.
- **Crashlytics 로깅 기준:** "동기화 에러"의 정의는 `Firestore.batch().commit()` 등의 쓰기 작업이 **오프라인 캐시 실패가 아닌, 네트워크가 연결된 상태에서 권한/타임아웃 등의 이유로 명시적 Exception을 뱉는 경우**를 커스텀 이벤트(`sync_failed`)로 기록함.