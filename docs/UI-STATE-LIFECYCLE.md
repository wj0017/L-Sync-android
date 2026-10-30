# UI Flow collection: history and verification

## Why the previous rule existed

History commit `589ce11` (2026-05-09) explicitly records a
`LocalLifecycleOwner` collision. It removed lifecycle-runtime-compose and changed
screen collectors to collectAsState. Commit `553eb41` later turned that workaround
into the blanket CLAUDE.md rule. The original crash log is not preserved here, so
the exact exception/environment cannot be reconstructed from the message alone.

The declared stack was Lifecycle 2.8.0 and Compose BOM 2024.05.00 (Compose 1.6).
AndroidX documents the LocalLifecycleOwner interoperability fix in Lifecycle
2.8.2 and the code-shrinking compatibility fix in 2.8.3. Use the 2.8.x patch release
2.8.7, keeping Kotlin, Compose BOM, Navigation, AGP and Gradle unchanged.

## Change

- Add lifecycle-runtime-compose explicitly and align Lifecycle to 2.8.7.
- Use collectAsStateWithLifecycle for Android Activity/screen/navigation Flow
  subscriptions. Plain Compose state and Glance widget code are not converted.
- Replace the obsolete prohibition with a scoped lifecycle-aware collection rule.
- This pauses UI collectors below STARTED, not arbitrary viewModelScope workers,
  database writes, alarms or synchronization jobs.

## Regression coverage

`LifecycleStateCollectionTest` uses a real ComponentActivity with Compose 1.6's
CompositionLocal setup. It checks initial rendering (the historical crash
boundary), zero Flow subscribers after moving to CREATED, latest-value rendering
after returning to RESUMED, and cancellation after content removal. It uses only
an in-memory StateFlow. Existing Room migration/device tests remain included in
connectedDebugAndroidTest; the workflow step name is updated to describe both.

Completed execution evidence is recorded below. OEM alarms, production Firebase
authentication and a minified release running on a device remain outside this
regression test.

References:
- https://developer.android.com/jetpack/androidx/releases/lifecycle#2.8.2
- https://developer.android.com/jetpack/androidx/releases/lifecycle#2.8.3
- https://developer.android.com/develop/ui/compose/state

## Execution record — 2026-10-30

These are completed executions, not results inferred from the presence of test code.

| Date | Environment | Actual command | Result / evidence |
| --- | --- | --- | --- |
| 2026-10-30 | GitHub Actions, Linux / Temurin JDK 17 | `./gradlew assembleDebug lintDebug testDebugUnitTest --no-configuration-cache` | PASS; [post-merge main run](https://github.com/wj0017/L-Sync-android/actions/runs/41049850841) |
| 2026-10-30 | GitHub Actions, API 29 x86_64 emulator | `./gradlew connectedDebugAndroidTest --no-configuration-cache` | PASS, 7 device tests; [run](https://github.com/wj0017/L-Sync-android/actions/runs/41049850841) and [execution excerpt](verification/lifecycle-api29-ci-2026-10-30.txt) |
| 2026-10-30 | Local Windows / JBR 21, SDK installed | `gradlew.bat assembleDebug lintDebug testDebugUnitTest assembleDebugAndroidTest --no-configuration-cache --console=plain` | PASS; 89 unit tests, 0 failures; lint 0 errors (warnings remain) |
| 2026-10-30 | Local disposable API 36 emulator (`emulator-5580`) | `adb -s emulator-5580 shell am instrument -w -r com.lsync.app.test/androidx.test.runner.AndroidJUnitRunner` | PASS, `OK (7 tests)`, instrumentation code -1; [runner transcript](verification/lifecycle-api36-2026-10-30.txt) |

### Source provenance and coverage

- GitHub API29 execution tested main commit `f3ea12c7d12e950869b340c6ab04dc63fae0d6c1` after PR #2 merged.
- Local API36 execution used source `5f47eda4c2aee2c40ef6e8e864779136cc724c52`. Its Git tree equals the tested
  merge commit's tree; this was checked before adding this documentation-only record.
- `LifecycleStateCollectionTest`: **2 JUnit tests**, covering initial rendering,
  cancellation below STARTED, latest value after resume, and cancellation on content removal.
- `AppDatabaseMigrationTest`: **2 JUnit tests**, covering 6→8 and 7→8 legacy Todo
  preservation and access to the added log tables. This does not test preservation
  of every possible table or every real user's data.
- `AlarmLogDaoTest`: **3 JUnit tests**. Total instrumentation tests: **7**.
- The local run used prebuilt app/test APKs installed with `adb install -r`,
  followed by direct `am instrument`; it was **not** a local Gradle
  `connectedDebugAndroidTest` invocation. GitHub used that Gradle task.

### First local attempt and limits

The first empty API36 emulator attempt terminated before test results with
`Process crashed`; logcat recorded an application startup ANR
(`failed to complete startup`). After `cmd package compile -m speed -f` for the
app and test packages, the unchanged suite passed. The precise cause of the
initial startup timeout was not established. A successful retry is not evidence
that startup performance is solved for every device.

Tests used fake configuration and synthetic data. No production sign-in, cloud
workflow, OEM alarm-reliability measurement, or minified-release device test was
performed. These device tests verify UI subscriptions and the listed database
cases, not all background work or all app features.
