# Step 0: glance-setup

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `CLAUDE.md`
- `docs/ARCHITECTURE.md`
- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`

## 작업

Jetpack Glance 의존성을 추가하고, 위젯 시스템에 필요한 뼈대 파일들을 생성한다. 이 step에서는 UI를 구현하지 않는다 — 빈 스텁만 만든다.

### 1. 의존성 추가

`gradle/libs.versions.toml`의 `[versions]` 섹션에 추가하라:

```toml
glance = "1.1.0"
```

`[libraries]` 섹션에 추가하라:

```toml
glance-appwidget = { group = "androidx.glance", name = "glance-appwidget", version.ref = "glance" }
```

`app/build.gradle.kts`의 `dependencies` 블록에 추가하라:

```kotlin
// Glance (홈 위젯)
implementation(libs.glance.appwidget)
```

### 2. 위젯 메타데이터 XML 생성

`app/src/main/res/xml/lsync_widget_info.xml`을 생성하라:

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="250dp"
    android:minHeight="200dp"
    android:targetCellWidth="4"
    android:targetCellHeight="3"
    android:updatePeriodMillis="1800000"
    android:widgetCategory="home_screen"
    android:initialLayout="@android:layout/simple_list_item_1"
    android:description="@string/app_name" />
```

- `updatePeriodMillis="1800000"` — 시스템 최소 주기(30분). WorkManager로 실제 갱신을 관리하므로 이 값은 fallback용이다.
- `targetCellWidth/Height` — Android 12+ 그리드 셀 수. 이전 버전은 `minWidth/Height`를 사용한다.

### 3. 위젯 레이아웃 초기값 (선택)

`app/src/main/res/layout/widget_loading.xml`을 생성하라. 시스템이 위젯을 처음 배치할 때 Glance가 렌더링되기 전까지 표시되는 플레이스홀더다:

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#0A0A0A" />
```

그리고 `lsync_widget_info.xml`의 `android:initialLayout`을 `@layout/widget_loading`으로 교체하라.

### 4. GlanceAppWidget 스텁 생성

`app/src/main/java/com/lsync/app/ui/widget/LSyncWidget.kt`를 생성하라:

```kotlin
package com.lsync.app.ui.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.text.Text

class LSyncWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            Text("L-Sync")
        }
    }
}
```

### 5. GlanceAppWidgetReceiver 생성

`app/src/main/java/com/lsync/app/ui/widget/LSyncWidgetReceiver.kt`를 생성하라:

```kotlin
package com.lsync.app.ui.widget

import androidx.glance.appwidget.GlanceAppWidgetReceiver

class LSyncWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = LSyncWidget()
}
```

### 6. AndroidManifest.xml에 receiver 등록

`app/src/main/AndroidManifest.xml`의 `<application>` 블록 안에 추가하라:

```xml
<!-- 홈 위젯 -->
<receiver
    android:name=".ui.widget.LSyncWidgetReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/lsync_widget_info" />
</receiver>
```

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 체크리스트를 확인한다:
   - `gradle/libs.versions.toml`에 `glance = "1.1.0"` 버전 항목이 있는가?
   - `app/src/main/res/xml/lsync_widget_info.xml`이 생성됐는가?
   - `AndroidManifest.xml`에 `LSyncWidgetReceiver`가 `APPWIDGET_UPDATE` 인텐트 필터와 함께 등록됐는가?
   - `LSyncWidget.kt`와 `LSyncWidgetReceiver.kt`가 `ui/widget/` 패키지에 생성됐는가?
3. 결과에 따라 `phases/6-home-widget/index.json`의 step 0을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "산출물 한 줄 요약"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- UI 로직을 구현하지 마라. 이유: UI는 step 2에서 구현한다. 이 step은 의존성과 뼈대만 다룬다.
- AppModule.kt를 수정하지 마라. 이유: GlanceAppWidgetReceiver는 Hilt가 직접 주입하지 않으므로 DI 등록이 불필요하다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
