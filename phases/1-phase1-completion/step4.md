# Step 4: workmanager-wiring

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `CLAUDE.md`
- `docs/ARCHITECTURE.md`
- `app/src/main/java/com/lsync/app/LSyncApplication.kt`
- `app/src/main/java/com/lsync/app/worker/TodoMaterializerWorker.kt` (Step 1에서 구현됨)
- `app/src/main/java/com/lsync/app/worker/AlarmRestoreWorker.kt` (Step 2에서 구현됨)
- `app/src/main/java/com/lsync/app/notification/AlarmReceiver.kt`
- `app/src/main/java/com/lsync/app/notification/BootReceiver.kt`
- `app/src/main/AndroidManifest.xml`
- `phases/1-phase1-completion/index.json`

이전 step에서 만들어진 코드를 꼼꼼히 읽고, 설계 의도를 이해한 뒤 작업하라.

## 작업

TodoMaterializerWorker를 앱 시작 시 등록하고, BootReceiver와 AlarmReceiver가 AndroidManifest에 올바르게 등록됐는지 확인 및 수정하라.

### 1. LSyncApplication 업데이트

`LSyncApplication.kt`의 `onCreate()`에서 MaterializerWorker를 등록하라:

```kotlin
override fun onCreate() {
    super.onCreate()
    TodoMaterializerWorker.enqueuePeriodicWork(this)
}
```

`TodoMaterializerWorker.enqueuePeriodicWork()`는 Step 1에서 `ExistingPeriodicWorkPolicy.KEEP`으로 구현됐으므로 중복 등록 걱정 없이 호출해도 된다.

### 2. AndroidManifest 검증 및 수정

아래 항목들이 `AndroidManifest.xml`에 올바르게 등록됐는지 확인하라. 빠진 것이 있으면 추가하라:

**BootReceiver:**
```xml
<receiver
    android:name=".notification.BootReceiver"
    android:enabled="true"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.LOCKED_BOOT_COMPLETED" />
    </intent-filter>
</receiver>
```

**AlarmReceiver:**
```xml
<receiver
    android:name=".notification.AlarmReceiver"
    android:exported="false" />
```

**WorkManager (HiltWorkerFactory 사용 시):**
AndroidManifest에 WorkManager 초기화 provider가 있는지 확인하라. Hilt WorkManager 통합을 사용하는 경우 다음이 있어야 한다:
```xml
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    android:exported="false"
    tools:node="merge">
    <meta-data
        android:name="androidx.work.WorkManagerInitializer"
        android:value="androidx.startup"
        tools:node="remove" />
</provider>
```
(`Configuration.Provider`를 `LSyncApplication`이 구현하는 방식이면 이 블록이 필요하다. 현재 코드 확인 후 필요한 경우에만 추가하라.)

### 3. 최종 빌드 검증

모든 step이 연결됐는지 전체 빌드로 최종 확인한다.

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
./gradlew testDebugUnitTest  # 유닛 테스트 통과 (테스트가 없으면 생략)
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 아키텍처 체크리스트를 확인한다:
   - `LSyncApplication.onCreate()`에서 `TodoMaterializerWorker.enqueuePeriodicWork()`를 호출하는가?
   - `AndroidManifest.xml`에 `BootReceiver`가 `BOOT_COMPLETED` + `LOCKED_BOOT_COMPLETED` 인텐트로 등록됐는가?
   - `AndroidManifest.xml`에 `AlarmReceiver`가 등록됐는가?
   - CLAUDE.md CRITICAL 규칙을 위반하지 않았는가?
3. `phases/1-phase1-completion/index.json`의 step 4 AND 최상위 `phases/index.json`의 `1-phase1-completion` 항목을 업데이트한다:
   - step 4 성공 → step 4 `"status": "completed"`, `"summary": "산출물 한 줄 요약"`
   - phase 전체 완료 → `phases/index.json`의 `1-phase1-completion` `"status": "completed"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- `WorkManager` 초기화를 중복으로 등록하지 마라. 이유: 이미 Hilt 통합으로 초기화됐을 경우 충돌한다. 현재 코드를 먼저 확인하라.
- BootReceiver의 `exported`를 `false`로 설정하지 마라. 이유: 시스템 브로드캐스트(`BOOT_COMPLETED`)를 받으려면 exported가 true여야 한다.
- 기존 LSyncApplication 코드(Firebase 초기화 등)를 수정하지 마라. 이유: `enqueuePeriodicWork` 호출 한 줄만 추가하면 된다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
