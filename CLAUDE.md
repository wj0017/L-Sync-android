# 프로젝트: L-Sync

## 기술 스택
- Android (minSdk 26, targetSdk 34), Kotlin
- Jetpack Compose + Material3
- Hilt (Dependency Injection)
- Room 2.6.1 (Local DB)
- Firebase Firestore, Auth, Crashlytics, Analytics
- WorkManager + AlarmManager
- Pretendard / Instrument Serif 폰트

## 아키텍처 규칙
- CRITICAL: Offline-First. Room이 Single Source of Truth. UI는 항상 Room의 Flow를 구독한다. Firestore는 백그라운드 동기화 전용이며 UI가 직접 구독하지 않는다.
- CRITICAL: Todo 삭제 시 연결된 Finance 데이터를 삭제하지 않는다. `sourceTodoId`만 null로 해제한다.
- CRITICAL: Todo 미완료(Uncheck) 시 Finance를 삭제하지 않는다. `isExcluded = true`로 Soft Delete한다.
- CRITICAL: 종일 일정과 Todo의 날짜는 Floating Time(`YYYY-MM-DD` 문자열). 타임존 변환 금지.
- CRITICAL: `collectAsStateWithLifecycle` 사용 금지. `collectAsState()`만 사용한다.
- CRITICAL: userId는 현재 `"local_user"`로 하드코딩되어 있다 (Firebase Auth 미연동). 새 ViewModel에서도 동일하게 `"local_user"`를 사용하고, Firebase Auth를 직접 호출하지 마라.
- CRITICAL: ViewModel은 반드시 `@HiltViewModel` + `@Inject constructor(...)` 패턴으로 작성한다. 이 없으면 Hilt 주입 실패로 런타임 크래시.
- CRITICAL: 새 DAO나 Repository를 추가할 때는 반드시 `di/AppModule.kt`에 `@Provides` 함수를 추가한다. 누락 시 Hilt가 의존성을 찾지 못해 런타임 크래시.
- Firestore Batch Write를 사용하여 Todo ↔ Finance 양방향 업데이트를 원자적으로 처리한다.
- 동기화 실패(네트워크 에러, 권한 에러)는 Crashlytics에 `sync_failed` 이벤트로 기록한다.

## 디렉토리 구조
```
app/src/main/java/com/lsync/app/
├── ui/{screen}/          # Composable 화면 + ViewModel
├── data/local/entity/    # Room Entity
├── data/local/dao/       # Room DAO
├── data/remote/          # FirestoreDataSource
├── data/repository/      # Repository (Room + Firestore 조합)
├── di/                   # Hilt Module (AppModule.kt)
├── notification/         # AlarmScheduler
├── worker/               # WorkManager Workers
└── ui/theme/             # Color.kt, Type.kt, Theme.kt
```

## 디자인 시스템
- 다크 미니멀 테마 (라이트 모드 없음)
- BgPrimary `#0A0A0A` / BgCard `#161616` / FgPrimary `#F5F5F5`
- AccentBlue `#4F7EFF` / AccentGreen `#43A047` / AccentRed `#E53935`
- HairlineWhite `rgba(255,255,255,0.035)` — 카드 테두리
- Pretendard: 본문/UI 폰트 / Instrument Serif Italic: 금액 기호, 성경 절 번호
- Ghost chip 패턴: outline only, active = FgPrimary solid

## 개발 프로세스
- 커밋 메시지: conventional commits (feat:, fix:, chore:, design:, refactor:)
- Co-Authored-By 줄을 커밋 메시지에 절대 포함하지 않는다

## 명령어
```bash
./gradlew assembleDebug                                                          # 디버그 빌드
./gradlew lintDebug                                                               # 린트
./gradlew testDebugUnitTest                                                       # 유닛 테스트
./gradlew assembleDebug appDistributionUploadDebug --no-configuration-cache      # Firebase 배포
```
