# Step 2: materializer-wiring

## 배경

이 task(`9-repeat-todo`)는 "반복 Todo 템플릿 UI"다.

`TodoMaterializerWorker`는 활성 템플릿의 `rrule`을 파싱해 오늘~+14일치 `TodoEntity` 인스턴스를 만든다(결정론적 ID `{templateId}_{date}`로 멱등, 미완료 미래 인스턴스는 최신 템플릿 정보로 갱신, 완료된 인스턴스는 덮어쓰지 않음). 하지만 현재 마지막 단계에서 `todoDao.upsertAll(toUpsert)`로 **로컬에만** 저장한다 → 생성된 인스턴스가 **Firestore 동기화도, 알람 등록도 받지 못한다.**

**이전 Step(Step 1)에서** `TodoRepository.upsertMaterialized(todos: List<TodoEntity>)`를 추가했다. 이 메서드는 `todoDao.upsertAll` + 각 항목 `alarmScheduler.scheduleForTodo` + `syncSafe { remote.upsertTodo }`를 한다.

이 Step은 worker가 직접 DAO에 쓰는 대신 `upsertMaterialized`를 호출하도록 배선한다. 그러면 반복 Todo 인스턴스도 단발성 Todo와 동일하게 동기화·알람을 받는다.

## 읽어야 할 파일

먼저 아래 파일을 읽고 설계 의도를 파악하라:

- `docs/ARCHITECTURE.md` — Offline-First, 알람 라이프사이클, 백그라운드 워커
- `docs/PRD.md` — 2.3 반복 Todo 정책, `docs/TechSpec.md` — 4장 백그라운드 엔진(멱등성, Materialization Window, 템플릿 Update 정책)
- `CLAUDE.md` — CRITICAL 규칙(Hilt, 데이터 무결성)
- `app/src/main/java/com/lsync/app/worker/TodoMaterializerWorker.kt` — **수정 대상.** 현재 `todoDao.upsertAll(toUpsert)`로 끝나는 구조 확인.
- `app/src/main/java/com/lsync/app/data/repository/TodoRepository.kt` — **Step 1에서 추가된 `upsertMaterialized` 시그니처를 먼저 확인하라.**
- `app/src/main/java/com/lsync/app/worker/AlarmRestoreWorker.kt` — 같은 패키지의 `@HiltWorker` + `@AssistedInject` 구조 참고(의존성 주입 방식)

## 작업

`TodoMaterializerWorker`가 `TodoRepository`를 주입받아, 인스턴스 영속화를 `upsertMaterialized`에 위임하게 한다.

- 생성자에 `private val todoRepository: TodoRepository`를 주입 파라미터로 추가한다. (`@AssistedInject` 생성자의 일반 의존성 파라미터로 추가 — `TodoRepository`는 `@Singleton`이라 Hilt가 제공한다.)
- `materializeTemplate(...)` 끝부분의:
  ```kotlin
  if (toUpsert.isNotEmpty()) {
      todoDao.upsertAll(toUpsert)
  }
  ```
  를 다음으로 교체한다:
  ```kotlin
  if (toUpsert.isNotEmpty()) {
      todoRepository.upsertMaterialized(toUpsert)
  }
  ```
- **읽기 쿼리는 그대로 둔다:** `todoDao.getPendingFutureByTemplate(...)`, `todoDao.getById(...)`는 계속 `todoDao`로 호출한다(이들은 변경 없음). 즉 `todoDao` 주입은 유지하고, **쓰기만** repository로 옮긴다.
- rrule 파싱, DTSTART(템플릿 createdAt 기준), 윈도우 계산, 멱등 ID, 갱신/스킵 판정 등 **materialization 로직은 일절 바꾸지 마라.**

## 핵심 규칙 (반드시 지킬 것)

- **멱등성·Update 정책 보존.** 결정론적 ID `{templateId}_{date}`, "미완료 미래 인스턴스만 갱신, 완료된 인스턴스는 덮어쓰지 않음" 규칙(TechSpec 4장)을 절대 변경하지 마라. 이 Step은 **마지막 쓰기 호출만** 교체한다.
- **알람 중복 없음.** `upsertMaterialized` 내부의 `scheduleForTodo`는 같은 todo id로 재호출돼도 같은 requestCode(`id.hashCode()`)와 `FLAG_UPDATE_CURRENT`라 재등록될 뿐 중복되지 않는다. worker에서 알람을 따로 등록하지 마라(이중 등록 금지).
- `@HiltWorker`/`@AssistedInject` 구조와 기존 주입 필드(`todoTemplateDao`, `todoDao`, `authRepository`)를 유지하라.

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음 (Hilt 그래프에 TodoRepository 주입 해결 포함)
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다. 특히 worker에 `TodoRepository` 주입이 컴파일되는지 확인.
2. 아키텍처 체크리스트:
   - 인스턴스 쓰기가 `todoRepository.upsertMaterialized(...)`로 바뀌었는가? (직접 `todoDao.upsertAll`로 인스턴스를 쓰지 않는가)
   - 읽기 쿼리(`getPendingFutureByTemplate`/`getById`)는 그대로 `todoDao`인가?
   - materialization 로직(ID·윈도우·갱신 정책)이 변경 없이 유지됐는가?
3. 결과에 따라 `phases/9-repeat-todo/index.json`의 step 2를 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "TodoMaterializerWorker가 todoDao.upsertAll → todoRepository.upsertMaterialized로 교체(TodoRepository 주입). 반복 인스턴스도 Firestore 동기화·알람 등록. materialization 로직 불변"`
   - 수정 3회 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 중단

## 금지사항

- `TodoRepository`를 수정하지 마라. 이유: `upsertMaterialized`는 Step 1에서 완성됐다. 시그니처가 안 맞으면 그 파일을 읽고 맞춰 호출하라.
- materialization 알고리즘(rrule 반복, 윈도우, 멱등 ID, 갱신/스킵)을 바꾸지 마라. 이유: 검증된 로직이며 이 Step의 범위는 쓰기 위임뿐이다.
- `enqueuePeriodicWork`(일 1회 등록)를 바꾸지 마라. 즉시 실행 트리거는 Step 3에서 별도로 다룬다.
- 기존 코드를 추가로 리팩토링하지 마라. 이 step의 범위만 작업하라.
