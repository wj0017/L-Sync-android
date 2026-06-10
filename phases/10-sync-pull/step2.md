# Step 2: sync-trigger

## 배경

이 task(`10-sync-pull`)는 "Firestore 복원 동기화"다.

**이전 Step들에서:**
- **Step 0**: `FirestoreDataSource.fetchEvents/fetchTodos/fetchFinance(userId)`.
- **Step 1**: `SyncRepository.pullAll(userId)` — 원격 전체를 last-write-wins로 Room에 머지.

이 Step은 `pullAll`을 **실제로 호출**하도록 배선한다. 복원이 필요한 시점은 **로그인 직후**(새 기기/다른 기기)와 **앱 시작 시 자동 로그인**이다. 이는 기존 userId 마이그레이션이 도는 지점과 동일하다.

## 읽어야 할 파일

먼저 아래 파일을 읽고 설계 의도를 파악하라:

- `docs/ARCHITECTURE.md` — Firebase Auth(`AuthRepository`/`AuthMigrationHelper`), Offline-First
- `CLAUDE.md` — userId, Hilt, `@ApplicationScope`
- `app/src/main/java/com/lsync/app/data/repository/SyncRepository.kt` — **Step 1의 `pullAll(userId)`를 먼저 확인하라.**
- `app/src/main/java/com/lsync/app/data/repository/AuthRepository.kt` — `signInWithGoogle`, `currentUserId`, `init`(자동 로그인 시 마이그레이션을 `scope.launch`로 도는 패턴)
- `app/src/main/java/com/lsync/app/data/repository/AuthMigrationHelper.kt` — 마이그레이션이 호출되는 위치·멱등 플래그 패턴(복원 트리거도 같은 흐름에 얹는다)
- `app/src/main/java/com/lsync/app/di/Qualifiers.kt` — `@ApplicationScope`(필요 시 백그라운드 실행에 사용)

## 작업

`SyncRepository.pullAll(currentUserId)`를 아래 두 시점에 호출하도록 연결한다. **기존 userId 마이그레이션 직후**에 얹는 것이 가장 자연스럽다(마이그레이션으로 로컬 userId가 실제 UID로 정렬된 뒤 원격을 당겨와야 하므로 순서가 중요).

1. **신규 로그인:** `signInWithGoogle()`가 마이그레이션을 await한 직후 `pullAll(uid)` 실행.
2. **앱 재실행(자동 로그인):** `AuthRepository.init`의 마이그레이션 `scope.launch { ... }` 블록에서 마이그레이션 후 `pullAll(currentUserId)` 실행.

구현 방침(둘 중 재량으로 선택, 단 순환 의존 주의):
- **방법 A:** `AuthRepository`/마이그레이션 흐름에 `SyncRepository`를 주입해 직접 호출. 단 `SyncRepository`가 `AuthRepository`를 역으로 의존하지 않으므로 순환은 없다. (`SyncRepository`는 userId를 **파라미터로** 받는다 — Auth를 주입하지 않는다.)
- **방법 B:** 동기화를 백그라운드로 빼고 싶으면 `@ApplicationScope` 스코프에서 `launch`로 `pullAll` 실행(앱 시작 차단 방지). 신규 로그인 시에는 마이그레이션과 마찬가지로 완료를 기다려도 되고, 백그라운드로 던져도 된다(UI는 Room Flow로 자동 반영되므로 굳이 await 불필요).

권장: pull은 네트워크 작업이므로 **백그라운드(`scope.launch`)** 로 실행해 로그인/시작을 막지 않는다. 실패는 Step 1의 `pullAll` 내부 `syncSafe`가 이미 삼킨다.

## 핵심 규칙 (반드시 지킬 것)

- **마이그레이션 이후에 pull.** 반드시 userId 마이그레이션이 끝난 뒤 `pullAll`을 호출하라. 이유: 마이그레이션 전이면 로컬 데이터가 아직 `"local_user"`라 원격(UID) 데이터와 머지 기준이 어긋난다.
- **userId는 파라미터로.** `SyncRepository`에 `AuthRepository`를 주입해 순환 의존을 만들지 마라. userId 문자열을 넘겨라.
- **시작 차단 금지.** pull을 메인/UI 흐름에서 동기 await로 막지 마라. 백그라운드 스코프(`@ApplicationScope`)에서 실행. UI는 Room Flow 구독으로 자동 갱신된다.
- **멱등.** 같은 세션에서 pull이 여러 번 돌아도 안전하다(Step 1이 upsert+updatedAt 비교). 다만 불필요한 반복 호출은 피하라(로그인/시작 각 1회).

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음 (Hilt 그래프, 순환 의존 없음)
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다. 특히 순환 의존(Auth ↔ Sync)이 없는지 컴파일로 확인.
2. 아키텍처 체크리스트:
   - 신규 로그인·자동 로그인 두 경로 모두에서 마이그레이션 **이후** `pullAll`이 호출되는가?
   - `SyncRepository`가 `AuthRepository`를 주입하지 않고 userId를 파라미터로 받는가?
   - pull이 시작 흐름을 동기적으로 막지 않는가(백그라운드)?
3. 결과에 따라 `phases/10-sync-pull/index.json`의 step 2를 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "로그인·자동로그인 시 마이그레이션 직후 SyncRepository.pullAll을 백그라운드 실행하도록 배선. Firestore→Room 복원 end-to-end 완성"`
   - 수정 3회 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 중단

## 금지사항

- `SyncRepository`/`FirestoreDataSource`를 수정하지 마라. 이유: Step 0·1에서 완성됐다. 이 Step은 호출 배선뿐이다.
- 주기적 동기화(Worker)를 추가하지 마라. 이유: 이 task의 범위는 로그인/시작 시 복원이다. 주기 동기화는 별도 task다.
- userId 마이그레이션 로직을 바꾸지 마라. pull 호출만 그 흐름 뒤에 얹는다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
