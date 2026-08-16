# Step 0: web-write-contract

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `docs/ARCHITECTURE.md`
- `docs/TechSpec.md`
- `app/src/main/java/com/lsync/app/data/remote/FirestoreDataSource.kt` — **이 step의 기준 계약. 특히 `toMap()` 4개와 `toXxxEntity()` 4개를 정독하라.**
- `app/src/main/java/com/lsync/app/data/local/entity/EventEntity.kt`
- `app/src/main/java/com/lsync/app/data/local/entity/TodoEntity.kt`
- `app/src/main/java/com/lsync/app/data/local/entity/FinanceEntity.kt`
- `web/lib/db.ts` — 수정 대상
- `web/types/models.ts` — 수정 대상
- `web/hooks/useFinance.ts` — 수정 대상
- `web/hooks/useEvents.ts`, `web/hooks/useTodos.ts` — 참고(이미 tombstone 필터 적용됨)

## 배경 — 왜 이 작업이 필요한가

Android 앱과 웹은 같은 Firestore 컬렉션(`events` / `todos` / `finance`)을 공유한다. 그런데 **웹이 쓰는 문서를 앱이 읽지 못한다.**

`FirestoreDataSource.kt`의 역방향 매핑은 이렇게 생겼다:

```kotlin
private fun DocumentSnapshot.toFinanceEntity(): FinanceEntity? = runCatching {
    FinanceEntity(
        id = getString("id") ?: return null,
        ...
        createdAt = getLong("createdAt") ?: 0L,
        deletedAt = getLong("deletedAt"),
    )
}.getOrNull()
```

그리고 호출부는 `.documents.mapNotNull { it.toFinanceEntity() }` 다. 즉 **필드 타입이 하나라도 안 맞으면 예외가 `runCatching`에 먹히고 그 문서는 통째로 조용히 버려진다.**

현재 웹의 불일치:

| 웹이 쓰는 값 | 앱이 기대하는 값 | 결과 |
|---|---|---|
| `addDoc()` → 랜덤 doc ID, `id` 필드 없음 | doc ID = 엔티티 id, 문서 안에도 `id` 필드 존재 | `getString("id")` 가 null → `return null` → 드롭 |
| `createdAt`/`updatedAt`: `serverTimestamp()` → Firestore Timestamp | `getLong` (epoch millis Number) | 예외 → 드롭 |
| `deletedAt`: `new Date().toISOString()` → String | `getLong` | 예외 → 드롭 |
| `completedAt`: `new Date().toISOString()` → String | `getLong` | 예외 → 드롭 |
| `deleteFinance`: `deleteDoc` (hard delete) | tombstone(`deletedAt` millis) | 앱 Room에 남아 있다가 재push → 부활 |

결과적으로 웹에서 만든 데이터는 앱에 영원히 도달하지 않았고, 웹에서 삭제한 일정·할일의 tombstone도 앱에 전달되지 않았다(앱에서는 계속 살아 있음). 앱→웹 방향만 정상 동작해 왔다.

이 step은 웹의 쓰기 계약을 앱과 일치시켜 양방향 동기화를 실제로 성립시킨다.

## 작업

### 1. `web/lib/db.ts` — 쓰기 계약 정합

**문서 ID 규칙**

`addDoc(collection(db, X), ...)` 를 전부 `setDoc(doc(db, X, id), ...)` 로 바꾼다. `id`는 `crypto.randomUUID()`로 생성한다(앱의 `UUID.randomUUID().toString()`과 동일한 형식 — `app/src/main/java/com/lsync/app/data/repository/EventRepository.kt:55` 참고). 생성한 `id`를 **문서 ID로도 쓰고, 문서 본문의 `id` 필드로도 쓴다.** 둘 다 필요하다 — 앱은 `document(entity.id)`로 쓰고 `getString("id")`로 읽는다.

각 add 함수는 생성된 id를 반환하도록 시그니처를 조정해도 좋다(호출부가 반환값을 쓰지 않으므로 자유).

**시각 필드 규칙**

`serverTimestamp()`와 `new Date().toISOString()`을 전부 **`Date.now()` (epoch millis Number)** 로 바꾼다. 대상: `createdAt`, `updatedAt`, `deletedAt`, `completedAt`.

`serverTimestamp()`를 쓰지 마라. 이유: Firestore가 서버 Timestamp 객체로 저장하고, 앱의 `getLong()`이 이를 Long으로 변환하지 못해 문서 전체가 드롭된다.

**함수별 지시**

- `addEvent` — `setDoc` 전환. 앱 `EventEntity`에 맞춰 `rrule: null`, `exdatesJson: null`, `overridesJson: null`, `hasAlarm: false`, `deletedAt: null`을 기본값으로 명시(호출부가 값을 넘기면 그것을 우선).
- `addTodo` — `setDoc` 전환. 기존 `isCompleted: false`, `financeIsLinked: false`, `deletedAt: null` 유지.
- `addFinance` — `setDoc` 전환. `isExcluded: false` 유지 + `settlementGroupId: null`, `sourceTodoId: null`, `deletedAt: null` 명시 추가.
- `toggleTodo` — `completedAt`을 `isCompleted ? Date.now() : null`로.
- `deleteEvent` / `deleteTodo` — tombstone 방식 유지, `deletedAt`을 `Date.now()`로.
- `deleteFinance` — **`deleteDoc` → `updateDoc({ deletedAt: Date.now(), updatedAt: Date.now() })`.** `deleteEvent`/`deleteTodo`와 같은 패턴. `deleteDoc` import가 더 이상 쓰이지 않으면 제거한다.

**핵심 규칙(위반 금지)**

- 날짜 문자열 필드(`date`, `dueDate`, `startDate`, `endDate`)는 **Floating Time `YYYY-MM-DD` 문자열 그대로** 둔다. millis로 바꾸지 마라. 이유: 앱이 `getString`으로 읽고, 종일 일정·할일은 타임존 변환을 하지 않는 것이 프로젝트의 CRITICAL 규칙이다.
- `amount`는 Number 그대로 둔다(앱은 `getLong("amount")`).

### 2. `web/types/models.ts` — 타입 정정

- `LSyncFinance`에 `settlementGroupId?: string | null`, `deletedAt?: number | null` 추가.
- 세 인터페이스의 `createdAt`/`updatedAt`을 `string` → `number`로 정정. `LSyncEvent.deletedAt` / `LSyncTodo.deletedAt` / `LSyncTodo.completedAt`도 `string | null` → `number | null`로 정정.
- 기존에 웹이 쓴 문서에는 아직 옛 타입(string/Timestamp)이 남아 있을 수 있으나, 타입 정의는 **앞으로의 계약** 기준으로 맞춘다. 읽기 측은 truthy 검사(`!f.deletedAt`)만 하므로 런타임 호환된다.

### 3. `web/hooks/useFinance.ts` — tombstone 필터

`.filter(f => !f.isExcluded && f.date?.startsWith(prefix))` 에 `!f.deletedAt` 조건을 추가한다. `useEvents.ts` / `useTodos.ts`가 이미 쓰는 패턴과 동일하게 맞춘다.

## Acceptance Criteria

```bash
cd web && pnpm build
```

- 타입 에러 없이 빌드 성공(`tsconfig.json`의 `strict: true` 하에서).
- `web/lib/db.ts`에 `serverTimestamp` / `addDoc` / `deleteDoc` 사용이 하나도 남아 있지 않다:

```bash
cd web && grep -n "serverTimestamp\|addDoc\|deleteDoc" lib/db.ts
```

위 grep이 **아무것도 출력하지 않아야** 한다(unused import까지 정리된 상태).

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. `web/lib/db.ts`의 각 add/update 함수가 쓰는 필드를 `FirestoreDataSource.kt`의 대응 `toMap()`과 1:1로 대조한다. 앱이 `getLong`으로 읽는 필드가 웹에서 Number로 나가는지, `getString`으로 읽는 필드가 String으로 나가는지 확인하라.
3. 아키텍처 체크리스트:
   - 날짜 문자열(`date`/`dueDate`/`startDate`)의 Floating Time 의미가 유지되는가?
   - Android 코드를 건드리지 않았는가? (이 step은 `web/`만 수정한다)
4. 결과에 따라 `phases/22-web-catchup/index.json`의 step 0을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "산출물 한 줄 요약"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- **Android(`app/`) 코드를 수정하지 마라.** 이유: 앱의 읽기 계약이 정답이고 웹이 거기에 맞춰야 한다. 앱을 고치면 이미 배포된 앱 데이터와의 호환이 깨진다.
- **`firestore.rules`를 수정하지 마라.** 이유: 별도의 미배포 이슈가 걸려 있어 이 phase의 범위 밖이다.
- **집계 로직(`web/app/finance/page.tsx`의 income/expense 계산)을 건드리지 마라.** 이유: step 1의 작업이다. 이 step은 `db.ts` / `models.ts` / `useFinance.ts` 세 파일만 수정한다.
- **기존 Firestore 문서를 정리·마이그레이션하는 스크립트를 만들지 마라.** 이유: 이 phase 범위 밖이며, 사용자가 콘솔에서 직접 확인·처리한다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
