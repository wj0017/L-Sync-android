# Phase 23 동기화 신뢰성: 수정 핸드오프

이 문서는 Phase 23 구현의 정적 분석 결과와 다음 수정 작업의 인수인계다. 기준 커밋은 `96f6a1e`이며 검토 범위는 `git diff 1ae9917..96f6a1e -- app/ docs/`이다. 이 문서는 코드 변경을 포함하지 않는다.

## 목표와 제약

- Android 앱의 단일 진실 공급원은 Room이다. UI는 Room Flow를 구독하고 Firestore는 백그라운드 동기화에 쓴다.
- 원격 push는 Repository의 `syncSafe`가 애플리케이션 스코프에서 비동기로 실행한다. 오프라인 서버 응답을 호출부가 기다리게 만들면 안 된다.
- 삭제는 `deletedAt` tombstone으로 전파한다. pull은 항목별 `remote.updatedAt > local.updatedAt`일 때만 Room을 덮어쓴다.
- 연동 Todo 완료는 Todo·Finance 두 문서를 한 번의 원격 배치로 쓴다. 재전송에서도 두 문서의 최종 상태와 원자성을 검토한다.
- `collectAsStateWithLifecycle`은 사용하지 않고 `collectAsState()`를 쓴다. 새 DAO/Repository의 DI 등록은 `AppModule.kt`에서 확인한다.
- 계측 목적은 실패 표시·재전송과 Phase 1의 동기화 에러율 확인이다. **성공 기록이 다른 기기의 최신 상태를 손상시키면 안 된다.**

## 우선 수정할 결함

아래는 코드에서 확인된 결함이다. 각 수정에 재현 시나리오를 검증하는 수단을 붙인다. 구현 방식은 자유지만 수락 조건을 만족해야 한다.

### P0. 재전송이 다른 기기의 최신 변경을 덮어씀

- 위치: `SyncRepository.kt:121-135`, `FirestoreDataSource.kt:24-29,65-71`.
- 재현: 기기 A의 push 실패 후 기기 B가 같은 문서를 더 최근 시각으로 수정한다. A의 `retryFailed()`는 오래된 Room 행을 `set(merge)`로 무조건 전송해 B의 내용을 지운다. `updatedAt`을 새로 찍지 않는 규칙만으로 LWW는 성립하지 않는다. 이후 pull은 이미 덮어쓴 원격 값만 읽는다.
- 수락 조건: 원격 문서가 없으면 생성하고, 원격 `updatedAt`이 Room 행보다 최신이면 덮어쓰지 않는다. 비교와 쓰기 사이에 다른 클라이언트가 수정해도 안전해야 한다. Todo·Finance를 함께 다룰 때 부분 적용과 관계 불일치를 막는다. 충돌로 쓰기를 건너뛴 로그를 언제 해소하고 Room을 어떻게 최신 상태로 맞출지도 명시한다.

### P1. 실행 중 워커가 새 실패를 놓침

- 위치: `SyncRepository.kt:90-118`, `SyncRetryTrigger.kt:29-34`, `SyncRetryWorker.kt:21-25`.
- 재현: 워커가 미해결 목록을 읽은 직후 새 실패가 기록된다. 새 예약은 `ExistingWorkPolicy.KEEP` 때문에 무시된다. 기존 워커가 읽은 목록만 처리하고 `Result.success()`로 끝나면 새 실패는 자동 재시도되지 않는다.
- 수락 조건: 워커의 마지막 조회 이후 새 실패가 기록돼도 결국 재시도할 예약이 남아야 한다. 연속 실패가 백오프를 매번 초기화하지 않도록 한다.

### P1. 가지치기가 미해결 실패까지 삭제함

- 위치: `SyncLogDao.kt:44-46`, `SyncLogger.kt:56-58`.
- 재현: 권한 오류나 장기 오프라인으로 30일 넘게 전송하지 못한 로그가 다음 50번째 삽입 때 삭제된다. 미해결 건수와 재전송 대상에서 함께 사라진다.
- 수락 조건: 보존 기간이 지난 성공 로그와 해소된 실패만 정리한다. 미해결 실패는 해소되거나 사용자가 명시적으로 처리할 때까지 보존한다.

### P1. 원본 행이 없어도 재전송 성공으로 표시함

- 위치: `SyncRepository.kt:109-135`, `AuthRepository.kt:55-60`.
- 재현: push 실패 후 로그아웃으로 동기화 대상 Room 테이블을 비운다. 같은 계정으로 로그인해 pull이 끝나기 전에 워커가 실행되면 `getById()`가 `null`을 반환한다. `resend()`는 정상 반환하고 호출부는 로그를 해소한다. 알 수 없는 target과 빈 `entityId`도 같다.
- 수락 조건: 원본 부재를 원격 성공과 구분한다. 계정 전환·복원 중 해당 로그를 성급히 해소하지 않는다. 원본을 복구할 수 없다면 사용자에게 상태를 드러내거나 명시된 복구 정책을 적용한다. 로그아웃 전에 원격 전송되지 않은 로컬 데이터가 삭제될 수 있는 기존 정책과의 관계도 확인한다.

### P2. 에러율의 단위 설명이 실제 분모와 다름

- 위치: `SyncLogDao.kt:20-25`, `FinanceRepository.kt:136-153`, `docs/TechSpec.md` §12.6.
- 현재 분모는 **push 원격 연산 수**다. 정산 그룹 삭제처럼 사용자 작업 하나가 여러 Finance 쓰기를 만들면 여러 로그 행이 쌓인다. 문서의 “사용자 작업 수” 설명과 다르다.
- 수락 조건: 원격 연산 기준을 유지한다면 화면·PRD·TechSpec의 단위 설명을 일치시킨다. 사용자 작업 기준으로 바꾼다면 그룹 식별자와 부분 실패 집계 규칙을 정의한다. pull은 게이트에서 제외하되 실패 수치를 별도로 표시한다.

## 확인된 사항과 남은 검증

| 항목 | 정적 분석 결과 |
|---|---|
| Room v6→v7 | `SyncLogEntity` 컬럼·NULL 허용과 `MIGRATION_6_7` SQL이 일치한다. 인덱스 이름 `index_sync_log_userId_createdAt`이 같고 `AppModule`에 등록됐다. 기기 실행 검증은 아직 없다. |
| DI 순환 | `AuthRepository → SyncRepository → SyncLogger → AuthRepository` 순환을 피하려고 호출부에서 `userId`를 넘기는 판단은 타당하다. |
| 재시도 로그 루프 | `retryFailed()`는 `SyncLogger`와 `pullAll()`을 호출하지 않는다. pull 재시도는 `pullWithoutLogging()`을 쓴다. |
| 워커 구성 | 미로그인 시 `Result.success()`를 반환한다. `@HiltWorker`, `HiltWorkerFactory`, 기본 WorkManager 초기화 제거가 연결됐다. |
| 동시성·Flow | 가지치기 카운터는 `AtomicInteger`다. `SyncStatusViewModel.windowJob`은 구간 변경 때 이전 구독을 취소한다. 빠른 구간 변경의 기기 검증은 아직 없다. |
| 계측·UI | `target != pull`로 push 게이트를 집계하고 pull을 별도 표시한다. 변경 범위에서 UI의 Firestore 직접 구독과 금지된 `collectAsStateWithLifecycle` 사용은 발견되지 않았다. |
| pull 머지 | `pullAll()`과 `pullWithoutLogging()`은 같은 네 개의 `merge*` 함수를 쓴다. `pullAll()`의 그룹별 예외 격리도 유지됐다. 이는 **pull에만 적용**되며 P0을 해결하지 않는다. |

추가로 확인할 사항:

1. 같은 엔티티의 실패 로그 여러 건을 처리할 때 중복 전송과 `RetryResult.attempted`의 의미.
2. 원래 push가 나중에 성공했는데 앞선 실패 로그가 미해결로 남는 경우의 해소 정책.
3. `appScope.launch`의 원격 작업과 로그 기록 사이에 프로세스가 종료될 때 생기는 유실 구간. 이번 계측이 이를 완전히 해결한다고 주장하지 않는다.
4. 로그아웃 후 `sync_log` 보존 시 `userId` 필터 누락 여부와 `errorMessage`에 민감한 서버 응답이 담길 가능성. 현재 조회는 `userId`로 필터된다.
5. Todo 완료→미완료→삭제가 연달아 실패한 뒤 재시도할 때, `linkedFinanceId`의 Finance와 정산 그룹 구성원의 최종 상태.

## 검증과 보고 방식

핵심 파일: `SyncRepository.kt`, `FirestoreDataSource.kt`, `SyncRetryTrigger.kt`, `SyncRetryWorker.kt`, `SyncLogger.kt`, `SyncLogDao.kt`, `AuthRepository.kt`, `TodoRepository.kt`, `FinanceRepository.kt`, `SyncStatusViewModel.kt`, `AppDatabase.kt`, `AppModule.kt`.

P0/P1 시나리오의 상태 변화를 시간순으로 적고 수정 전후 결과를 비교한다. Firestore 조건부 쓰기, WorkManager 예약 경쟁, Room 마이그레이션은 가능하면 기기·에뮬레이터에서 검증한다. 실행하지 못한 항목은 정적 분석과 구분한다. 이전 핸드오프의 컴파일·유닛테스트 통과 보고는 이 문서에서 새로 검증한 결과가 아니다.

수정 보고에는 항목별로 **파일:라인 · 심각도 · 재현 시나리오 · 해결 방식 · 검증 결과 · 남은 위험**을 포함한다. 데이터 손실, 잘못된 성공 판정, 영구 미재시도 같은 실제 실패를 우선한다.
