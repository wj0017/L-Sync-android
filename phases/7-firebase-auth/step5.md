# Step 5: signout-ui

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `docs/UI_GUIDE.md`
- `app/src/main/java/com/lsync/app/ui/home/HomeScreen.kt`
- `app/src/main/java/com/lsync/app/ui/home/HomeViewModel.kt`
- `app/src/main/java/com/lsync/app/ui/auth/AuthViewModel.kt`  ← step 1에서 생성됨
- `app/src/main/java/com/lsync/app/ui/navigation/NavGraph.kt`  ← step 2에서 수정됨

## 이전 step 요약

Step 0~4 완료: Firebase Auth 기반 구축, ViewModel, LoginScreen, userId 교체, 데이터 마이그레이션 모두 완료.

## 배경

현재 사용자가 로그아웃할 방법이 없다. 앱 데이터를 직접 지우는 방법 외에는 로그아웃 불가. 개인 앱이라 빈도가 낮지만 최소한의 진입점이 필요하다.

## 작업

`HomeScreen.kt` 헤더 우측에 로그아웃 메뉴를 추가한다.

### 구현 방식

UI_GUIDE.md 헤더 구조를 준수한다:
```
Row (좌우 정렬, vertical = Bottom)
├── "L·SYNC"          20sp SemiBold FgPrimary
└── Column (우측)
    ├── 연도            10sp Medium FgTertiary
    └── "MMM d, EEEE"  14sp Medium FgSecondary
```

우측 Column 또는 Row에 `IconButton`(MoreVert 아이콘)을 추가한다. 아이콘 크기는 20dp, 색상 FgSecondary.

탭 시 `DropdownMenu`가 나타나며 "로그아웃" 항목 하나만 포함:
- 항목 텍스트: "로그아웃", 14sp, FgPrimary
- 탭 시: `authViewModel.signOut()` 호출, 메뉴 닫기

**`HomeScreen`의 시그니처 변경:**

```kotlin
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),  // 추가
)
```

**NavGraph에서 `HomeScreen` 호출 시 `authViewModel` 전달:**

```kotlin
HomeScreen(authViewModel = authViewModel)  // step 2에서 NavGraph에 authViewModel이 이미 있음
```

### DropdownMenu 색상

- 메뉴 컨테이너 배경: `BgElevated` (#202020)
- 항목 텍스트: FgPrimary
- 위험 항목이 아니므로 AccentRed 사용 금지

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 체크리스트를 확인한다:
   - `HomeScreen`에 MoreVert IconButton이 추가됐는가?
   - 탭 시 DropdownMenu에 "로그아웃" 항목이 나타나는가?
   - 로그아웃 탭 시 `authViewModel.signOut()` 호출 후 NavGraph가 `LoginScreen`으로 전환되는가? (authState Flow가 자동 처리)
   - UI_GUIDE.md 색상 시스템을 따르는가?
   - CLAUDE.md CRITICAL 규칙을 위반하지 않았는가?
3. 결과에 따라 `phases/7-firebase-auth/index.json`의 step 5를 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "산출물 한 줄 요약"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- SettingsScreen 등 별도 화면을 신설하지 마라. 이유: 이 step의 범위는 단순 로그아웃 진입점이다.
- 로그아웃 확인 다이얼로그를 추가하지 마라. 이유: 개인 앱이라 불필요한 클릭 수를 늘리지 않는다.
- "로그아웃" 항목에 AccentRed를 사용하지 마라. 이유: UI_GUIDE.md에서 AccentRed는 삭제 버튼(위험 동작)에만 사용한다. 로그아웃은 위험 동작이 아니다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
