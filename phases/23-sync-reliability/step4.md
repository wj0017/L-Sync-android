# Step 4: docs-catchup

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 기획·아키텍처·문서 스타일을 파악하라:

- `README.md` — **개발 로드맵** 체크리스트(현재 Phase 22까지)
- `docs/PRD.md` — **2. 핵심 도메인 정책**(현재 2.8까지), **3. 마일스톤**(현재 Phase 22까지)
- `docs/TechSpec.md` — **현재 v0.12.** §2 마이그레이션 표, §7 트랜잭션 및 모니터링, §11 웹 클라이언트 동기화 계약
- `docs/ARCHITECTURE.md`
- `web/README.md` — **이미 웹 인증 내용이 반영돼 있다. 수정하지 마라**

그리고 이 phase에서 만들어진 코드 전부를 읽어라. 문서는 코드를 보고 쓴다:

- `app/src/main/java/com/lsync/app/data/local/entity/SyncLogEntity.kt`, `dao/SyncLogDao.kt` (step 0)
- `app/src/main/java/com/lsync/app/data/local/AppDatabase.kt` (step 0 — version 7)
- `app/src/main/java/com/lsync/app/data/sync/SyncLogger.kt` (step 1~2)
- `app/src/main/java/com/lsync/app/data/repository/SyncRepository.kt` (step 2 — `retryFailed`)
- `app/src/main/java/com/lsync/app/worker/SyncRetryTrigger.kt`, `SyncRetryWorker.kt` (step 2)
- `app/src/main/java/com/lsync/app/ui/sync/SyncStatusViewModel.kt`, `SyncStatusScreen.kt` (step 3)

## 배경

이 step은 두 가지 문서 부채를 한 번에 갚는다.

1. **이 phase(동기화 신뢰성 계측)의 내용이 아직 어느 문서에도 없다.**
2. **`1ae9917 feat(web): add Firebase Google authentication`(2026-09-23)이 harness 밖에서 들어와 문서에 반영되지 않았다.** `web/README.md`만 갱신됐고 루트 README 로드맵·PRD·TechSpec §11에는 흔적이 없다.

추가로 **기존 문서 오류 하나**를 고친다 — `docs/TechSpec.md` §2 마이그레이션 표가 **v5까지만** 적혀 있는데, 코드에는 `MIGRATION_5_6`(finance.deletedAt)이 이미 있고 이 phase에서 `MIGRATION_6_7`이 더해졌다.

## 작업

### 1. `docs/TechSpec.md`

- **버전을 v0.12 → v0.13으로 올린다**(문서 첫 줄).
- **§2 마이그레이션 표에 두 행을 추가한다:**
  - `6` — `finance.deletedAt 컬럼 추가 (MIGRATION_5_6)` ← **기존 누락분 보정**
  - `7` — `sync_log 테이블 추가 (MIGRATION_6_7)`
- **§1에 `sync_log` 테이블 절을 추가한다**(§1.6 `budgets` 스타일의 컬럼 표). 로컬 전용·Firestore 미동기화임을 명시한다.
- **§12 「동기화 신뢰성 계측」 절을 신설한다.** 아래를 반드시 포함:
  - 기록 지점: 5벌의 `syncSafe`(Event/Todo/Finance/Budget = `appScope` fire-and-forget, `SyncRepository` = suspend pull). **원격 연산 1회 = 로그 1행**(배치 write도 1행) — 에러율 분모를 사용자 액션 수와 일치시키기 위한 규칙
  - `SyncLogger`가 `AuthRepository`를 주입받지 않는 이유(`AuthRepository → SyncRepository → SyncLogger → AuthRepository` Hilt 순환). userId는 호출부가 엔티티에서 넘긴다
  - Crashlytics 기록은 **그대로 유지**(로컬 = 사용자 확인용, 원격 = 모니터링용)
  - 재전송 계약: **Room 현재 행을 `set(merge)`로 upsert**. `batch.update` 기반 메서드(`uncheckTodoWithFinance`·`deleteTodoUnlinkFinance`)를 재사용하지 않는 이유(문서 부재 시 실패). `todos` 재전송은 `linkedFinanceId`를 동반. `updatedAt`을 새로 찍지 않는 이유(LWW 덮어쓰기)
  - **재시도 경로는 로그를 남기지 않는다** — 실패 → 기록 → 큐잉 → 재시도의 무한 순환 차단. 실패 시 `Result.retry()`로 WorkManager 지수 백오프에 위임
  - 워커: `SyncRetryTrigger`(`NetworkType.CONNECTED`, `enqueueUniqueWork` + `KEEP`) / `SyncRetryWorker`(미로그인 시 `Result.success()`)
  - 보존: 30일 초과 행 가지치기, 삽입 50회마다 1회 실행
  - **로그아웃 시 `sync_log`는 비우지 않는다** — PRD 2.7의 `reading_plan`·`memos`와 같은 취급(로컬 전용·복원 불가). 노출은 모든 조회의 `userId` 필터로 차단
  - 화면: `ui/sync/SyncStatusScreen`, 홈 헤더 메뉴 진입, 7일/30일 구간, 에러율 = `failures / attempts`(재시도 제외)
- **§11에 웹 인증 내용을 추가한다.** `git show --stat 1ae9917`과 `web/components/AuthProvider.tsx`·`web/lib/firebase.ts`·`web/hooks/*.ts`를 직접 읽고 사실만 적어라. 최소한 다음이 드러나야 한다: 웹이 Google 로그인으로 실제 UID를 얻으므로 Firestore 보안 규칙(§3)의 `request.auth.uid == resource.data.userId`를 웹에서도 만족한다는 것, 그리고 목록 조회가 `userId` 동등 필터를 유지해야 한다는 기존 제약과의 관계.

### 2. `docs/PRD.md`

- **2.9 「동기화 신뢰성 계측 정책」** 신설:
  - 동기화 실패가 조용히 사라지지 않고 로컬에 남아 사용자가 확인·재시도할 수 있어야 한다
  - 에러율 = `실패 / 전체 시도`이며 **재시도는 분모·분자 어디에도 넣지 않는다**(사용자 액션 기준 유지)
  - 진단 기록이 사용자 데이터 저장을 방해해서는 안 된다(기록 실패는 삼킨다)
  - 계측 데이터는 기기 로컬 전용이며 Firestore 동기화 대상이 아니다
- **마일스톤에 `### ✅ Phase 23: 동기화 신뢰성 계측 — 완료`** 항목을 추가한다(기존 Phase 1~22 항목의 서술 밀도에 맞춘다). **Phase 1 게이트("동기화 에러율 5% 미만")를 이 화면으로 판정할 수 있게 됐다**는 연결을 명시한다.

### 3. `README.md`

- 개발 로드맵에 `- [x] **Phase 23** — 동기화 신뢰성 계측 (...)` 한 줄 추가.
- **웹 Google 인증은 새 Phase 번호를 주지 않는다.** 기존 `Phase 19 — 웹 따라잡기` 줄에 인증 내용을 덧붙여 보강한다. 이유: 별도 마일스톤을 세울 규모가 아니고(10파일 변경), 웹 관련 계약은 이미 TechSpec §11 한 곳에 모여 있다. 번호를 새로 주면 harness 디렉토리 번호와의 불일치가 또 한 겹 쌓인다.
- 디렉토리 구조 블록에 `data/sync/`·`ui/sync/`가 빠져 있으면 채운다.

### 4. `docs/ARCHITECTURE.md`

동기화 계측 흐름(`Repository.syncSafe` → `SyncLogger` → Room `sync_log` → `SyncStatusScreen` / `SyncRetryWorker`)과 신규 디렉토리(`data/sync/`, `ui/sync/`)를 기존 서술 방식에 맞춰 반영한다.

## Acceptance Criteria

```powershell
.\gradlew.bat assembleDebug --no-configuration-cache
```

```powershell
git status --short
```

**AC 판정 기준:**
- 빌드 출력에 `BUILD SUCCESSFUL`이 보여야 한다(문서만 고쳤으므로 당연히 통과해야 하며, 실패하면 문서 외 파일을 건드린 것이다). java를 못 찾으면: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; $env:GRADLE_USER_HOME = "C:\ghome"; .\gradlew.bat assembleDebug --no-configuration-cache --console=plain`
- `git status --short` 결과에 **`README.md`, `docs/*.md`, `phases/23-sync-reliability/index.json` 외의 파일이 나오면 안 된다.** `app/`·`web/` 파일이 보이면 이 step의 범위를 벗어난 것이므로 되돌려라.

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 문서를 다시 읽으며 확인하라:
   - TechSpec 첫 줄 버전이 **v0.13**인가?
   - §2 표에 **v6·v7 두 행**이 모두 있는가? (v6는 기존 누락분이다)
   - §12의 서술이 실제 코드와 일치하는가? **코드를 보지 않고 이 step 파일의 요약만 옮겨 적지 마라** — step 0~3에서 실제로 만들어진 클래스명·함수 시그니처를 확인하고 쓴다
   - §11의 웹 인증 서술이 `1ae9917`의 실제 diff와 일치하는가? (추측 금지)
   - PRD 기존 Phase 1~22 항목과 2.1~2.8 정책이 **하나도 삭제·수정되지 않았는가?**
   - README 로드맵에서 Phase 19 줄이 보강됐고, 새 줄은 Phase 23 하나뿐인가?
3. 아키텍처 체크리스트:
   - 문서가 CLAUDE.md의 CRITICAL 규칙과 모순되는 내용을 담고 있지 않은가?
   - Room이 SSOT라는 서술이 유지되는가? (`sync_log`도 Room에만 산다)
4. 결과에 따라 `phases/23-sync-reliability/index.json`의 step 4를 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "산출물 한 줄 요약"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- **앱·웹 코드를 수정하지 마라.** 문서를 쓰다가 코드의 문제를 발견해도 고치지 말고, step 4의 `summary`에 적어 남겨라. 이 step은 문서 전용이다.
- **`web/README.md`를 수정하지 마라.** `1ae9917`에서 이미 갱신됐다.
- **PRD의 기존 Phase 번호를 재배치하지 마라.** README·PRD의 Phase 번호는 harness 디렉토리 번호와 원래 다르며(과거 실행 순서 때문), 지금 맞추려 들면 이미 쓰인 문서 참조가 전부 어긋난다.
- **웹 인증에 새 Phase 번호를 부여하지 마라.** Phase 19 항목 보강 + TechSpec §11 추가로 처리한다.
- **추측으로 문서를 쓰지 마라.** 클래스명·함수명·컬럼명은 전부 실제 파일에서 확인한 것만 적는다. 문서가 코드보다 앞서가면 다음 phase가 틀린 전제로 작업한다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
