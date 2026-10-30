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

Build, lint, unit and device execution results must be recorded after execution;
adding these tests is not evidence that they passed. OEM alarms, production
Firebase authentication and a minified release running on a device remain
outside this regression test.

References:
- https://developer.android.com/jetpack/androidx/releases/lifecycle#2.8.2
- https://developer.android.com/jetpack/androidx/releases/lifecycle#2.8.3
- https://developer.android.com/develop/ui/compose/state
