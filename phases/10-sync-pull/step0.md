# Step 0: firestore-fetch

## 배경

이 task(`10-sync-pull`)는 "Firestore 복원 동기화"다. 현재 `FirestoreDataSource`는 **push 전용**(upsert/delete)이며 원격 데이터를 다시 읽어오는 경로가 없다. 그래서 새 기기·재설치·다른 기기 로그인 시 Firestore에 쌓인 데이터가 Room으로 **복원되지 않는다.** Offline-First의 클라우드 복원 경로가 비어 있다.

이 Step은 그 첫 조각으로, `FirestoreDataSource`에 사용자별 컬렉션을 읽어 엔티티 리스트로 돌려주는 **fetch(read) 메서드**를 추가한다. 이후 Step 1이 이 결과를 Room에 머지(upsert)하고, Step 2가 로그인/시작 시 트리거한다.

핵심 사실: 기존 `toMap()`이 각 엔티티의 **모든 필드**(id, …, createdAt, updatedAt, deletedAt 포함)를 저장하므로, 역으로 문서 → 엔티티 복원이 가능하다.

## 읽어야 할 파일

먼저 아래 파일을 읽고 설계 의도를 파악하라:

- `docs/ARCHITECTURE.md` — Offline-First(Room이 SSOT, Firestore 백그라운드 동기화 전용, UI는 Firestore 직접 구독 금지)
- `docs/TechSpec.md` — 3장 Firebase 보안 규칙(`request.auth.uid == resource.data.userId`)
- `CLAUDE.md` — userId는 `AuthRepository.currentUserId`, Hilt 규칙
- `app/src/main/java/com/lsync/app/data/remote/FirestoreDataSource.kt` — **수정 대상.** 기존 `events()/todos()/finance()` 컬렉션 헬퍼와 `toMap()` 매핑을 그대로 본보기로 삼아 역방향(문서→엔티티) 매핑을 작성.
- `app/src/main/java/com/lsync/app/data/local/entity/EventEntity.kt` — 필드(특히 `exdatesJson`, `overridesJson`, `deletedAt`)
- `app/src/main/java/com/lsync/app/data/local/entity/TodoEntity.kt` — `TodoEntity` 필드
- `app/src/main/java/com/lsync/app/data/local/entity/FinanceEntity.kt` — 필드(주의: `deletedAt` 없음)

## 작업

`FirestoreDataSource`에 fetch 메서드 3개를 추가한다(주입·기존 메서드는 그대로).

```kotlin
suspend fun fetchEvents(userId: String): List<EventEntity>
suspend fun fetchTodos(userId: String): List<TodoEntity>
suspend fun fetchFinance(userId: String): List<FinanceEntity>
```

각 메서드:
- 해당 컬렉션에서 `whereEqualTo("userId", userId)`로 쿼리해 `.get().await()`.
- 각 `DocumentSnapshot` → 엔티티로 매핑. `toMap()`의 키·타입과 정확히 대응시켜라.
- 숫자 타입 주의: Firestore는 정수를 `Long`으로 돌려준다. `getLong("amount")`, `getLong("createdAt")` 등을 쓰고, null 가능 필드는 안전하게 처리(`?:` 기본값 또는 nullable 유지).
- Boolean은 `getBoolean(...)`, 문자열은 `getString(...)`.
- 개별 문서 매핑 실패는 `runCatching`으로 감싸 **건너뛰고**(`mapNotNull`) 전체가 실패하지 않게 하라. 이유: 한 문서의 스키마 불일치가 복원 전체를 막으면 안 된다.

엔티티 재구성용 private 확장 함수(예: `DocumentSnapshot.toEventEntity(): EventEntity?`)를 만들어 `toMap()` 옆에 두면 일관적이다.

## 핵심 규칙 (반드시 지킬 것)

- **`toMap()`과 1:1 대응.** fetch가 복원하는 필드 집합은 `toMap()`이 저장하는 집합과 정확히 같아야 한다(키 이름·nullability). 빠진 필드가 있으면 복원된 엔티티가 깨진다.
- **read 전용.** 이 Step은 Room을 건드리지 않는다(DAO 호출 금지). 순수 원격 읽기만.
- **userId 필터 필수.** 보안 규칙상 `whereEqualTo("userId", userId)` 없이 컬렉션 전체를 읽으면 권한 거부된다. `userId`는 호출자가 넘긴다(하드코딩 금지).
- 새 컬렉션을 만들지 마라. 기존 `events`/`todos`/`finance` 3개만 읽는다. (todo_templates 컬렉션은 존재하지 않으므로 대상 아님.)

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 아키텍처 체크리스트:
   - `fetchEvents/fetchTodos/fetchFinance`가 추가되고 각 컬렉션을 `userId`로 필터하는가?
   - 문서→엔티티 매핑이 `toMap()` 필드와 1:1인가? (특히 events의 `exdatesJson/overridesJson/deletedAt`)
   - 개별 문서 매핑 실패가 전체를 죽이지 않는가(mapNotNull/runCatching)?
   - Room/DAO를 건드리지 않았는가?
3. 결과에 따라 `phases/10-sync-pull/index.json`의 step 0을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "FirestoreDataSource에 fetchEvents/fetchTodos/fetchFinance(userId) 추가 — 문서→엔티티 복원 매핑(toMap 1:1), 개별 실패 skip. Step1 머지가 소비"`
   - 수정 3회 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 중단

## 금지사항

- Repository/DAO/ViewModel을 수정하지 마라. 이유: 이 Step은 `FirestoreDataSource` 단일 파일의 read 메서드 추가뿐이다. 머지는 Step 1의 몫이다.
- `addSnapshotListener`(실시간 구독)를 쓰지 마라. 이유: 아키텍처상 UI는 Firestore를 직접 구독하지 않는다. 복원은 1회성 `.get()` pull이다.
- 기존 `upsert*/delete*/toMap` 동작을 바꾸지 마라. 이 step은 read 추가뿐이다.
