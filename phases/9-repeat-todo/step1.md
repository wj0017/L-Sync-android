# Step 1: repository-layer

## 배경

이 task(`9-repeat-todo`)는 "반복 Todo 템플릿 UI"다. 반복 Todo의 백엔드(엔티티·DAO·`TodoMaterializerWorker`)는 이미 완성돼 있으나, 사용자가 템플릿을 만들 경로와, 생성된 인스턴스를 **동기화·알람**까지 처리하는 경로가 부족하다.

현재 상태:
- `TodoRepository`에는 `upsertTemplate(template)`, `getActiveTemplates(userId)`, `observeActiveTemplates(userId)`, `deactivateTemplate(id)`가 이미 있다(저수준).
- 단발성 Todo는 `TodoRepository.create(...)`가 Room 저장 + Firestore 동기화 + (Phase 8에서 추가된) `alarmScheduler.scheduleForTodo(...)` 알람 등록을 한다.
- 그러나 `TodoMaterializerWorker`는 인스턴스를 `todoDao.upsertAll(...)`로 **로컬에만** 저장한다 → 동기화·알람이 빠진다. (이 빈틈은 Step 2에서 worker를 고칠 때 이 Step에서 만드는 메서드를 쓴다.)

이 Step은 두 가지 Repository 메서드를 추가한다: ① 사용자 친화적 `createTemplate(...)`, ② Materializer가 호출할 `upsertMaterialized(todos)`(로컬 + 알람 + 동기화).

## 읽어야 할 파일

먼저 아래 파일을 읽고 설계 의도를 파악하라:

- `docs/ARCHITECTURE.md` — Offline-First(Room이 SSOT, Firestore 백그라운드), 알람 라이프사이클, Todo↔Finance 생명주기
- `CLAUDE.md` — CRITICAL 규칙(데이터 영속성, Hilt, userId)
- `app/src/main/java/com/lsync/app/data/repository/TodoRepository.kt` — **수정 대상.** 기존 `create`, `upsertTemplate`, `complete`, `syncSafe`, `today()` 패턴을 그대로 따른다.
- `app/src/main/java/com/lsync/app/data/local/entity/TodoEntity.kt` — `TodoEntity`, `TodoTemplateEntity` 필드 확인
- `app/src/main/java/com/lsync/app/data/local/dao/TodoTemplateDao.kt` — `upsert`, `deactivate`
- `app/src/main/java/com/lsync/app/data/local/dao/TodoDao.kt` — `upsertAll`
- `app/src/main/java/com/lsync/app/data/remote/FirestoreDataSource.kt` — `upsertTodo` 시그니처(동기화에 사용)
- `app/src/main/java/com/lsync/app/notification/AlarmScheduler.kt` — `scheduleForTodo(todo)` 계약(완료·dueDate==null·과거면 자동 스킵/cancel)

## 작업

`TodoRepository`에 메서드 2개를 추가한다. 생성자/주입은 그대로다(이미 `todoDao`, `todoTemplateDao`, `financeDao`, `remote`, `alarmScheduler` 보유 — Phase 8에서 `alarmScheduler` 추가됨). **새 의존성 추가가 필요 없다.**

### createTemplate

```kotlin
suspend fun createTemplate(
    userId: String,
    title: String,
    rrule: String,
    financeIsLinked: Boolean = false,
    financeType: String? = null,
    financeCategory: String? = null,
    financeAmount: Long? = null,
): TodoTemplateEntity
```

- `TodoTemplateEntity`를 생성한다: `id = UUID.randomUUID().toString()`, `isActive = true`, `createdAt = updatedAt = now`.
- `todoTemplateDao.upsert(template)`로 로컬 저장.
- `syncSafe { remote.upsertTodoTemplate(template) }`로 동기화. **단, `FirestoreDataSource`에 템플릿 upsert 함수가 없으면** 추가하지 말고(범위 밖) `syncSafe` 호출을 생략하라 — 먼저 `FirestoreDataSource.kt`를 읽어 `upsertTodoTemplate` 같은 함수가 있는지 확인하고, 없으면 로컬 저장만 한다. (이유: 원격 스키마 변경은 이 task 범위가 아니다. 템플릿 자체보다 인스턴스 동기화가 중요하다.)
- 생성한 template을 반환.

### upsertMaterialized

```kotlin
suspend fun upsertMaterialized(todos: List<TodoEntity>)
```

- `todoDao.upsertAll(todos)`로 로컬 일괄 저장(SSOT).
- 각 `todo`에 대해 `alarmScheduler.scheduleForTodo(todo)` 호출(알람 등록; 완료·과거는 내부에서 스킵).
- 각 `todo`를 `syncSafe { remote.upsertTodo(todo) }`로 동기화. (리스트가 비어있으면 아무 것도 하지 않음)

## 핵심 규칙 (반드시 지킬 것)

- **Offline-First 순서.** 항상 Room 저장(로컬) → 알람 → Firestore 순서. 동기화/알람 실패가 로컬 저장을 막으면 안 된다. 동기화는 반드시 기존 `syncSafe { }` 래퍼로 감싸라(실패 시 Crashlytics 기록 후 조용히 통과).
- **멱등성 유지.** `upsertMaterialized`는 전달받은 리스트를 그대로 upsert만 한다. ID 생성·중복 판정 로직을 여기서 만들지 마라 — 그것은 Materializer(Step 2)의 책임이다. 같은 ID면 Room upsert가 덮어쓰고, `scheduleForTodo`는 같은 requestCode라 재등록(중복 알람 없음)이다.
- **데이터 영속성.** `deactivateTemplate`(이미 존재)은 hard delete가 아니라 `isActive=0`이다. 이 Step에서 템플릿/인스턴스를 삭제하는 코드를 추가하지 마라.
- userId 하드코딩 금지(`"local_user"` 금지). `createTemplate`은 호출자가 넘긴 `userId`를 쓴다.

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 아키텍처 체크리스트:
   - `createTemplate`이 isActive=true·UUID·timestamps로 템플릿을 만들고 로컬 저장하는가?
   - `upsertMaterialized`가 로컬 upsert → `scheduleForTodo` → `syncSafe` 순서인가?
   - 동기화가 `syncSafe`로 감싸였는가? (Room이 SSOT, 실패해도 로컬 유지)
   - 새 의존성 주입 없이 기존 필드만 사용했는가?
3. 결과에 따라 `phases/9-repeat-todo/index.json`의 step 1을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "TodoRepository에 createTemplate(템플릿 생성+저장)·upsertMaterialized(로컬 upsert+알람+동기화) 추가. Materializer(step2)·ViewModel(step3)이 호출"`
   - 수정 3회 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 중단

## 금지사항

- `TodoMaterializerWorker`를 수정하지 마라. 이유: worker 배선은 Step 2의 범위다. 이 Step은 worker가 쓸 메서드만 제공한다.
- ViewModel / Compose를 수정하지 마라. 이유: Step 3·4의 범위다.
- `FirestoreDataSource`에 템플릿용 원격 스키마/함수를 새로 추가하지 마라. 이유: 원격 스키마 변경은 이 task 범위 밖이다. 기존 함수가 없으면 템플릿은 로컬 저장만 한다.
- 기존 `create`/`complete`/`uncheck`/`delete`의 동작을 바꾸지 마라. 이 step은 메서드 2개 추가뿐이다.
