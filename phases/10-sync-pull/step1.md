# Step 1: sync-repository

## 배경

이 task(`10-sync-pull`)는 "Firestore 복원 동기화"다.

**이전 Step(Step 0)에서** `FirestoreDataSource`에 `fetchEvents(userId)`, `fetchTodos(userId)`, `fetchFinance(userId)`를 추가했다(원격 → 엔티티 리스트).

이 Step은 그 fetch 결과를 Room에 **머지(복원)** 하는 `SyncRepository`를 만든다. 충돌은 **last-write-wins(`updatedAt` 비교)** 로 해결하고, **upsert-only**(원격 데이터로 로컬을 채우되, 로컬이 더 최신이면 보존)다.

**삭제 처리(중요):** 별도 tombstone 로직이 필요 없다. 이유:
- Event/Finance는 원격에서 hard delete(`document.delete()`)되므로, 삭제된 문서는 fetch 결과에 **없다** → 복원 시 다시 생기지 않는다.
- Todo는 원격에서 soft delete(`deletedAt` 설정)되므로, fetch 결과에 `deletedAt`이 채워진 채로 와서 그대로 복원된다 → 삭제 상태가 유지된다.
따라서 fetch한 것을 그대로 upsert하면 삭제도 올바르게 반영된다.

## 읽어야 할 파일

먼저 아래 파일을 읽고 설계 의도를 파악하라:

- `docs/ARCHITECTURE.md` — Offline-First(Room이 SSOT), 알람 라이프사이클
- `CLAUDE.md` — Hilt(`@Provides` 규칙), userId, 데이터 영속성
- `app/src/main/java/com/lsync/app/data/remote/FirestoreDataSource.kt` — **Step 0의 `fetchEvents/fetchTodos/fetchFinance` 시그니처를 먼저 확인하라.**
- `app/src/main/java/com/lsync/app/data/local/dao/EventDao.kt` — `getById(id)`, `upsert`, `upsertAll`
- `app/src/main/java/com/lsync/app/data/local/dao/TodoDao.kt` — `getById(id)`, `upsert`, `upsertAll`
- `app/src/main/java/com/lsync/app/data/local/dao/FinanceDao.kt` — `getById(id)`, `upsert`
- `app/src/main/java/com/lsync/app/data/repository/EventRepository.kt` — `syncSafe` 패턴(Crashlytics 기록) 참고
- `app/src/main/java/com/lsync/app/di/AppModule.kt` — Repository는 이 모듈의 `@Provides`로 제공된다. **새 Repository 추가 시 여기에 `@Provides`를 추가해야 한다(누락 시 Hilt 런타임 크래시).**

## 작업

### SyncRepository (새 파일)

`app/src/main/java/com/lsync/app/data/repository/SyncRepository.kt`를 만든다.

```kotlin
@Singleton
class SyncRepository @Inject constructor(
    private val remote: FirestoreDataSource,
    private val eventDao: EventDao,
    private val todoDao: TodoDao,
    private val financeDao: FinanceDao,
) {
    // 원격 전체를 pull해 last-write-wins로 Room에 머지. 실패는 삼키고 false 반환 등으로 신호.
    suspend fun pullAll(userId: String)
}
```

`pullAll(userId)` 동작:
- `remote.fetchEvents(userId)` / `fetchTodos(userId)` / `fetchFinance(userId)`를 호출.
- 각 엔티티에 대해 머지 규칙 적용:
  - `val local = dao.getById(entity.id)`
  - `local == null` 이면 upsert(신규 복원).
  - `local != null` 이고 `entity.updatedAt > local.updatedAt` 이면 upsert(원격이 더 최신).
  - 그 외(로컬이 같거나 더 최신)면 **건드리지 않음**(로컬 보존).
- 네트워크/권한 실패는 `EventRepository.syncSafe`와 동일하게 Crashlytics에 기록(`sync_failed`)하고 조용히 통과. (앱 흐름을 막지 않는다.)

### AppModule 등록

`SyncRepository`가 `@Inject constructor`이면 Hilt가 자동 제공할 수도 있으나, **이 프로젝트의 다른 Repository들은 `AppModule`의 `@Provides`로 제공된다.** 빌드가 주입을 해결하지 못하면 `AppModule`에 `provideSyncRepository(...)`를 추가하라. (먼저 `AppModule.kt`를 읽어 기존 Repository 제공 방식을 그대로 따른다.)

## 핵심 규칙 (반드시 지킬 것)

- **Last-write-wins, upsert-only.** 로컬이 더 최신이면 절대 덮어쓰지 마라. 로컬에만 있고 원격에 없는 데이터를 삭제하지 마라(이 Step은 복원이지 원격 미러링이 아니다).
- **Room이 SSOT.** pull은 Room에 쓰는 것으로 끝난다. UI는 이미 Room Flow를 구독하므로 머지 후 자동 갱신된다. ViewModel/UI를 여기서 건드리지 마라.
- **알람/위젯 재처리 금지(이 Step 범위 밖).** 복원된 Todo의 알람 재등록이 필요하면 그것은 기존 `AlarmRestoreWorker`(부팅)나 후속 작업이 담당한다. `pullAll`에서 알람을 등록하지 마라(범위 최소화, 이중 등록 위험).
- 실패를 예외로 전파하지 마라 — `syncSafe`로 감싸 조용히 처리.

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음 (Hilt 그래프 포함)
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다. 특히 `SyncRepository` 주입이 컴파일되는지(필요 시 AppModule 등록) 확인.
2. 아키텍처 체크리스트:
   - `pullAll`이 fetch → `getById` 비교 → `updatedAt` 기준 조건부 upsert인가?
   - 로컬이 더 최신일 때 보존하는가? 로컬 전용 데이터를 삭제하지 않는가?
   - 실패가 `syncSafe`(Crashlytics)로 처리되는가?
   - `AppModule`에 필요한 `@Provides`가 있는가(주입 해결)?
3. 결과에 따라 `phases/10-sync-pull/index.json`의 step 1을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "SyncRepository.pullAll(userId) 추가 — fetch 후 updatedAt 기반 last-write-wins upsert(로컬 최신 보존), 실패 syncSafe. (AppModule 등록). Step2가 로그인/시작 시 호출"`
   - 수정 3회 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 중단

## 금지사항

- `FirestoreDataSource`를 수정하지 마라. 이유: fetch는 Step 0에서 완성됐다.
- ViewModel/UI/트리거 배선을 건드리지 마라. 이유: 트리거는 Step 2의 범위다.
- 원격에 쓰기(push)하거나 원격 문서를 삭제하지 마라. 이 Step은 원격→로컬 단방향 복원만 한다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
