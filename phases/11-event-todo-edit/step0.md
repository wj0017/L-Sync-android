# Step 0: repository-update

## 배경

이 task(`11-event-todo-edit`)는 "일정·할 일 수정(edit)"이다. 현재 이벤트·Todo는 **생성·삭제만** 가능하고 수정 경로가 없다(`ScheduleViewModel`에 update 없음, UI에 편집 진입 없음). 사용자가 오타·날짜·금액을 고치려면 삭제 후 재생성해야 한다.

이 Step은 Repository 레이어에 수정 메서드를 추가한다.

현재 상태(중요):
- `EventRepository.save(event)`는 이미 upsert + Firestore 동기화 + (Phase 8) `alarmScheduler.scheduleForEvent` 알람 재등록을 한다 → **이벤트 수정에 그대로 재사용 가능**.
- `TodoRepository`에는 단발성 `create`만 있고 update가 없다. Todo 수정 시 **완료 상태·가계부 연동(`isCompleted`, `completedAt`, `linkedFinanceId`)을 보존**해야 한다(이 필드를 건드리면 데이터 무결성 깨짐).
- `FinanceRepository.update(...)`는 이미 존재(가계부 수정은 이 task 범위 아님).

## 읽어야 할 파일

먼저 아래 파일을 읽고 설계 의도를 파악하라:

- `docs/ARCHITECTURE.md` — Offline-First, 알람 라이프사이클, Todo↔Finance 생명주기
- `CLAUDE.md` — CRITICAL 규칙(Todo 미완료/삭제 시 Finance 보존, userId)
- `app/src/main/java/com/lsync/app/data/repository/EventRepository.kt` — `save`, `create`, `syncSafe`
- `app/src/main/java/com/lsync/app/data/repository/TodoRepository.kt` — **수정 대상.** `create`, `complete`, `uncheck`, `delete`, `syncSafe` 패턴. (Phase 8/9에서 `alarmScheduler` 주입됨)
- `app/src/main/java/com/lsync/app/data/local/entity/EventEntity.kt`, `TodoEntity.kt` — 필드와 불변 필드(createdAt 등)
- `app/src/main/java/com/lsync/app/notification/AlarmScheduler.kt` — `scheduleForEvent`/`scheduleForTodo`(수정 후 알람 재등록 계약)

## 작업

### EventRepository.update

```kotlin
suspend fun update(
    id: String,
    title: String,
    isAllDay: Boolean,
    startDate: String,
    rrule: String? = null,
    hasAlarm: Boolean = false,
): EventEntity?
```

- 기존 엔티티를 `dao.getById(id)`로 읽어, 없으면 `null` 반환.
- 기존 엔티티를 `copy(...)`로 갱신: 변경 필드 + `updatedAt = now`. **`id`, `userId`, `createdAt`는 보존.**
- `save(updated)` 호출(= upsert + 동기화 + 알람 재등록). save가 이미 `scheduleForEvent`로 알람을 재처리하므로 별도 알람 코드 불필요.
- 갱신한 엔티티 반환.

### TodoRepository.update

```kotlin
suspend fun update(
    id: String,
    title: String,
    dueDate: String?,
    financeIsLinked: Boolean,
    financeType: String?,
    financeCategory: String?,
    financeAmount: Long?,
): TodoEntity?
```

- 기존 엔티티를 `todoDao.getById(id)`로 읽어, 없으면 `null` 반환.
- `copy(...)`로 **편집 가능 필드만** 갱신: `title`, `dueDate`, `financeIsLinked`, `financeType`, `financeCategory`, `financeAmount`, `updatedAt = now`.
- **보존(절대 덮어쓰지 말 것):** `isCompleted`, `completedAt`, `linkedFinanceId`, `templateId`, `id`, `userId`, `createdAt`.
- `todoDao.upsert(updated)` + `syncSafe { remote.upsertTodo(updated) }` + `alarmScheduler.scheduleForTodo(updated)`(날짜/완료 변화에 맞춰 알람 재등록; 내부에서 과거·완료 스킵).
- 갱신한 엔티티 반환.

## 핵심 규칙 (반드시 지킬 것)

- **불변 필드 보존.** `id`, `userId`, `createdAt`는 수정에서 절대 바뀌면 안 된다. Todo는 추가로 `isCompleted/completedAt/linkedFinanceId/templateId`를 보존하라. 이유: 완료·가계부 연결을 덮으면 데이터 무결성·정산이 깨진다.
- **Offline-First 순서.** Room 저장 → 알람 → 동기화. 동기화는 `syncSafe`로 감싼다.
- **알람 재등록은 기존 메서드로.** 이벤트는 `save`가, Todo는 `scheduleForTodo`가 처리. 직접 AlarmManager를 만지지 마라.
- userId 하드코딩 금지.

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 아키텍처 체크리스트:
   - `EventRepository.update`가 기존 엔티티를 읽어 불변 필드 보존 후 `save`하는가?
   - `TodoRepository.update`가 `isCompleted/completedAt/linkedFinanceId/templateId`를 보존하는가?
   - 두 update 모두 알람 재등록 경로를 타는가?
3. 결과에 따라 `phases/11-event-todo-edit/index.json`의 step 0을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "EventRepository.update(save 재사용)·TodoRepository.update(편집필드만 갱신, 완료/연동/createdAt 보존, 알람 재등록) 추가. ViewModel(step1)이 호출"`
   - 수정 3회 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 중단

## 금지사항

- ViewModel/Compose를 수정하지 마라. 이유: Step 1·2의 범위다.
- Todo `update`에서 `isCompleted`/`linkedFinanceId`/`completedAt`를 변경하지 마라. 이유: 완료·가계부 연동은 `complete`/`uncheck` 경로의 책임이며, 편집이 이를 덮으면 정산·통계가 깨진다.
- `FinanceRepository`를 수정하지 마라(가계부 수정은 이미 있음, 범위 밖).
- 기존 `create`/`complete`/`uncheck`/`delete` 동작을 바꾸지 마라. 이 step은 `update` 추가뿐이다.
