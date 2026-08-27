# Step 2: report-export-ui

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `docs/ARCHITECTURE.md`
- `docs/TechSpec.md` — § 월간 리포트
- `docs/UI_GUIDE.md` — 다크 미니멀 디자인 시스템, ghost chip 패턴
- `app/src/main/java/com/lsync/app/ui/report/ReportScreen.kt` — **주 수정 대상.** `ReportTopBar`(상단 바)와 **step 1에서 추가된 `ReportShareCard`**
- `app/src/main/java/com/lsync/app/ui/report/ReportCardRenderer.kt` — **step 1 산출물.** `suspend fun renderReportCard(context, state, widthPx): Bitmap?`
- `app/src/main/java/com/lsync/app/ui/report/ReportSummaryText.kt` — **step 0 산출물.** `fun buildReportSummaryText(state: ReportUiState): String`
- `app/src/main/java/com/lsync/app/ui/finance/FinanceScreen.kt` (40~110행) — **기존 CSV 공유 패턴.** `Intent.ACTION_SEND` + `createChooser`
- `app/src/main/java/com/lsync/app/ui/home/HomeScreen.kt` — **`ModalBottomSheet` 사용 선례.** 시트 스타일을 여기에 맞춰라
- `app/src/main/AndroidManifest.xml` — `<provider>` 등록 위치
- `app/src/main/res/xml/` — 기존 `lsync_widget_info.xml`, `report_widget_info.xml`

**step 0·1에서 만들어진 코드를 꼼꼼히 읽고, 설계 의도를 이해한 뒤 작업하라.**

## 배경 — 반드시 알아야 할 제약

이 step에서 월간 리포트 내보내기의 사용자 진입점을 완성한다. 형태는 두 가지다:

1. **이미지로 공유** — `renderReportCard`로 만든 Bitmap을 PNG로 저장 → `FileProvider` URI → `ACTION_SEND (image/png)`
2. **텍스트로 공유** — `buildReportSummaryText` 결과를 `ACTION_SEND (text/plain)` + `EXTRA_TEXT`

### 이 프로젝트에 `FileProvider`가 없다

기존 CSV 내보내기(`FinanceScreen`)는 **순수 텍스트를 `EXTRA_TEXT`로만** 넘긴다. 즉 이 프로젝트는 파일 URI 공유를 한 번도 해본 적이 없고 참고할 선례가 없다. 아래 함정들을 반드시 처리하라:

- **`Intent.FLAG_GRANT_READ_URI_PERMISSION`이 없으면** 수신 앱(카카오톡·Gmail 등)에서 `SecurityException`이 난다.
- **`clipData`를 설정하지 않으면** Android 13+ Sharesheet에 썸네일 미리보기가 뜨지 않는다.
- **공유 직후 캐시 파일을 삭제하면 안 된다.** 수신 앱은 비동기로 읽는다. 정리는 "**다음 공유 시 이전 파일 삭제**" 방식이어야 한다.

### 렌더 결과는 사용자가 눈으로 확인해야 한다

`assembleDebug`/`lintDebug`는 이미지가 비었는지, 잘렸는지 검증하지 못한다. 그래서 **이미지 공유는 미리보기 다이얼로그를 반드시 거친다.** 이건 UX이자 유일한 시각 검증 수단이다. 미리보기를 생략하지 마라.

## 작업

### 1. `app/src/main/res/xml/file_paths.xml` (신규)

```xml
<paths>
    <cache-path name="shared" path="shared/" />
</paths>
```

### 2. `AndroidManifest.xml` — FileProvider 등록

`<application>` 안에 추가한다. 기존 `androidx.startup.InitializationProvider` `<provider>` 블록을 건드리지 마라(WorkManager+Hilt 연동이라 깨지면 워커가 죽는다).

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

authority를 문자열로 하드코딩하지 마라 — `${applicationId}` placeholder를 쓰고, 코드에서는 `"${context.packageName}.fileprovider"`로 만든다. `androidx.core`(core-ktx 1.13.1)는 이미 의존성에 있다.

### 3. `app/src/main/java/com/lsync/app/data/export/ShareFiles.kt` (신규)

파일 공유 공용 유틸. 리포트 전용이 아니라 이후 다른 화면도 쓸 수 있게 일반적으로 만든다.

```kotlin
package com.lsync.app.data.export

// cacheDir/shared 아래에 PNG를 쓰고 FileProvider content URI를 돌려준다.
// 호출 전 이전 공유 파일을 정리한다(공유 직후 삭제는 금지 — 수신 앱이 비동기로 읽는다).
suspend fun savePngForShare(context: Context, bitmap: Bitmap, fileName: String): Uri?

// FLAG_GRANT_READ_URI_PERMISSION + clipData가 설정된 이미지 공유 Intent.
fun buildImageShareIntent(context: Context, uri: Uri, subject: String): Intent

// EXTRA_TEXT 기반 텍스트 공유 Intent.
fun buildTextShareIntent(text: String, subject: String): Intent
```

- `savePngForShare`는 **`Dispatchers.IO`에서 실행**한다(파일 IO). 압축은 `Bitmap.CompressFormat.PNG`.
- 저장 위치는 `File(context.cacheDir, "shared")` — `file_paths.xml`의 `path`와 정확히 일치해야 한다. 불일치하면 런타임에 `IllegalArgumentException: Failed to find configured root`가 난다.
- **이전 파일 정리:** 저장 직전에 `shared/` 디렉토리의 기존 파일을 지운다. 같은 달을 여러 번 공유해도 캐시가 무한히 늘지 않게 한다.
- 실패 시 예외를 삼키고 `null` 반환 + 로그.
- `buildImageShareIntent`는 `type = "image/png"`, `EXTRA_STREAM = uri`, `EXTRA_SUBJECT = subject`, **`addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)`**, **`clipData = ClipData.newUri(context.contentResolver, subject, uri)`** 를 모두 설정한다.

### 4. `ReportScreen.kt` — 공유 진입점

**(a) 상단 바에 공유 버튼**

현재 `ReportTopBar`는 `Row(horizontalArrangement = Arrangement.spacedBy(4.dp))`에 뒤로 아이콘과 "리포트" 제목만 있다. 오른쪽 끝에 공유 아이콘을 붙이려면 제목 뒤에 `Spacer(Modifier.weight(1f))`를 넣어야 한다.

- 아이콘은 `Icons.Outlined.Share`, `contentDescription = "내보내기"`.
- `ReportTopBar`에 `onShare: () -> Unit` 파라미터를 추가한다.

**(b) 형식 선택 시트**

공유 아이콘을 누르면 `ModalBottomSheet`를 띄운다(`HomeScreen`의 시트 스타일·배경색을 따른다). 항목 2개:

- **이미지로 공유** → (c)로
- **텍스트로 공유** → `buildReportSummaryText(state)` → `buildTextShareIntent(...)` → `startActivity(Intent.createChooser(...))`

**(c) 이미지 미리보기 → 공유**

1. 시트를 닫고 렌더링을 시작한다. `rememberCoroutineScope()`에서 `renderReportCard(context, state)` 호출.
2. **렌더링 중에는 진행 표시를 보여줘라.** 1080px 렌더 + 파일 IO에 수백 ms가 걸려 아무 표시가 없으면 앱이 멈춘 것처럼 보인다.
3. 결과가 `null`이면 스낵바나 짧은 안내로 실패를 알리고 **텍스트 공유를 대안으로 제시**한다. 조용히 아무 일도 안 일어나게 두지 마라.
4. 성공하면 **미리보기 다이얼로그**를 띄운다: 렌더된 Bitmap을 `Image(bitmap.asImageBitmap(), ...)`로 **가로 폭에 맞춰 축소 표시**(`ContentScale.Fit`, 세로가 길면 스크롤) + `공유하기` / `취소` 버튼.
5. `공유하기` → `savePngForShare(context, bitmap, "report_${state.yearMonth}.png")` → `buildImageShareIntent(...)` → `startActivity(Intent.createChooser(intent, "내보내기"))`.
6. 다이얼로그를 닫을 때 Bitmap 참조를 상태에서 놓아 GC되게 하라(수 MB다).

**(d) 파일명·제목**

- 파일명: `report_2026-08.png` 형태(`state.yearMonth.toString()`은 `2026-08`).
- `EXTRA_SUBJECT`: `"L-Sync 월간 리포트 2026-08"` 형태. `FinanceScreen`의 `"L-Sync 가계부 ${uiState.yearMonth}"` 관례를 따른다.

### 상태 관리

공유는 화면 로컬 동작이다. **`ReportViewModel`에 상태를 추가하지 마라.** `remember`/`mutableStateOf`로 Composable 안에서 처리한다. 이유: 렌더된 Bitmap을 ViewModel에 두면 화면 회전·백스택 이탈 후에도 수 MB가 붙잡혀 있게 된다.

`collectAsStateWithLifecycle`을 쓰지 마라 — `collectAsState()`만 쓴다(CLAUDE.md CRITICAL).

## Acceptance Criteria

```powershell
.\gradlew.bat assembleDebug lintDebug --no-configuration-cache
```

```powershell
.\gradlew.bat testDebugUnitTest --no-configuration-cache
```

## 검증 절차

1. 위 AC 커맨드를 실행한다. **기존 단위 테스트가 깨지면 안 된다.**
2. **AC로는 공유 동작을 검증할 수 없다.** 코드를 다시 읽으며 아래를 하나씩 확인하라:
   - `file_paths.xml`의 `path="shared/"` 와 `ShareFiles`의 저장 경로 `cacheDir/shared` 가 **문자열까지 일치**하는가? (불일치 = 런타임 `Failed to find configured root`)
   - manifest authority `${applicationId}.fileprovider` 와 코드의 `"${context.packageName}.fileprovider"` 가 일치하는가?
   - `buildImageShareIntent`에 `FLAG_GRANT_READ_URI_PERMISSION`과 `clipData`가 **둘 다** 있는가?
   - 공유 직후 파일을 삭제하는 코드가 없는가? (정리는 다음 저장 직전에만)
   - `renderReportCard`가 `null`을 반환하는 경로에 사용자 안내가 있는가?
   - 미리보기 다이얼로그를 거치지 않고 바로 공유되는 경로가 없는가?
   - `ReportViewModel`에 Bitmap이나 공유 상태가 들어가지 않았는가?
3. 아키텍처 체크리스트:
   - ARCHITECTURE.md 디렉토리 구조를 따르는가? (`data/export/`, `ui/report/`, `res/xml/`)
   - CLAUDE.md CRITICAL 규칙을 위반하지 않았는가? (`collectAsStateWithLifecycle` 금지, `@HiltViewModel` 패턴 유지)
   - Room이 SSOT인가? (이 step은 데이터 계층을 건드리지 않는다)
   - **새 DAO·Repository를 추가하지 않았으므로 `di/AppModule.kt` 수정이 필요 없다.** `ShareFiles`는 top-level 함수이므로 Hilt 등록 대상이 아니다. 억지로 클래스화해 주입하지 마라.
4. 결과에 따라 `phases/21-report-export/index.json`의 step 2를 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "산출물 한 줄 요약"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- **미리보기 다이얼로그를 생략하고 바로 공유하지 마라.** 이유: 렌더 결과를 검증할 자동화 수단이 없다. 미리보기가 유일한 시각 검증 지점이다.
- **`WRITE_EXTERNAL_STORAGE`·`READ_EXTERNAL_STORAGE` 권한을 추가하지 마라.** 이유: `cacheDir` + `FileProvider`면 권한이 전혀 필요 없다. 권한을 늘리면 설치 마찰만 생긴다.
- **`Uri.fromFile()` 이나 `file://` URI를 쓰지 마라.** 이유: Android 7+에서 `FileUriExposedException`이 난다.
- **공유 직후 캐시 파일을 지우지 마라.** 수신 앱이 비동기로 읽어 파일이 사라진다.
- **`ReportViewModel`을 수정하지 마라.** 공유는 화면 로컬 상태다.
- **`FinanceScreen`의 CSV 내보내기를 수정·통합하지 마라.** 이유: 검증된 기존 동작이다. `ShareFiles`로 리팩토링하고 싶어도 이 step의 범위가 아니다.
- **`Bitmap`을 `Intent` extra로 직접 넣지 마라.** 이유: Binder 트랜잭션 한도(~1MB)를 넘겨 `TransactionTooLargeException`이 난다. 반드시 파일 + content URI로 넘긴다.
- **기존 `<provider android:name="androidx.startup.InitializationProvider">` 블록을 수정하지 마라.** WorkManager+Hilt 초기화가 깨진다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
