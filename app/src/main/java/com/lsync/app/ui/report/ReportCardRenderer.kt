package com.lsync.app.ui.report

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.util.Log
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Density
import com.lsync.app.ui.theme.LSyncTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.withContext

private const val TAG = "ReportCardRenderer"

// BgPrimary(#0A0A0A) — 비트맵 바탕. 투명 PNG로 두면 밝은 배경 메신저에서 흰 글씨가 사라진다.
private const val BACKGROUND_ARGB = 0xFF0A0A0A.toInt()

// 렌더 밀도 고정값. widthPx / RENDER_DENSITY = 실제 dp 폭(1080px → 360dp).
// 기기 밀도를 그대로 쓰면 같은 widthPx가 기기마다 다른 dp 폭이 되어 이미지가 달라진다.
private const val RENDER_DENSITY = 3f

/**
 * 오프스크린 [ComposeView]를 measure/layout한 뒤 소프트웨어 Canvas에 그려 PNG용 [Bitmap]을 만든다.
 *
 * Compose BOM 2024.05.00(Compose 1.6.7)에는 `rememberGraphicsLayer()`/`toImageBitmap()`이 없어
 * 이 방식을 쓴다. 화면의 리포트 카드 Composable([ReportShareCard])을 그대로 재사용하므로
 * 화면과 공유 이미지가 갈라지지 않는다.
 *
 * 실패(Activity 없음, 측정 실패, 예외)하면 null을 반환한다 — 호출자가 텍스트 공유로 대체한다.
 */
suspend fun renderReportCard(
    context: Context,
    state: ReportUiState,
    widthPx: Int = 1080,
): Bitmap? = withContext(Dispatchers.Main) {
    // View measure/layout/draw는 메인 스레드 전용이므로 여기 전체가 Main에서 돈다.
    val activity = context.findActivity()
    if (activity == null) {
        Log.w(TAG, "Activity를 찾지 못해 렌더를 건너뜁니다 (context=${context.javaClass.name})")
        return@withContext null
    }
    val root = activity.findViewById<ViewGroup>(android.R.id.content)
    if (root == null) {
        Log.w(TAG, "android.R.id.content를 찾지 못했습니다")
        return@withContext null
    }

    val composeView = ComposeView(activity).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent {
            // 밀도·글꼴 배율을 고정해 기기 설정과 무관하게 같은 결과가 나오게 한다.
            CompositionLocalProvider(
                LocalDensity provides Density(density = RENDER_DENSITY, fontScale = 1f),
            ) {
                LSyncTheme { ReportShareCard(state) }
            }
        }
    }

    try {
        // ComposeView는 window에 붙어 ViewTree*Owner를 얻어야 컴포지션이 시작된다.
        // 보이지 않게(1×1 INVISIBLE) 붙였다가 finally에서 반드시 뗀다.
        root.addView(composeView, ViewGroup.LayoutParams(1, 1))
        composeView.visibility = View.INVISIBLE

        // 컴포지션·레이아웃이 한 바퀴 돌 시간을 준다.
        awaitFrame()
        awaitFrame()

        val widthSpec = MeasureSpec.makeMeasureSpec(widthPx, MeasureSpec.EXACTLY)
        val heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        composeView.measure(widthSpec, heightSpec)

        if (composeView.measuredHeight <= 0) {
            // 느린 기기에서 첫 두 프레임 안에 컴포지션이 끝나지 않을 수 있다.
            // 이 실패는 "빈 이미지"로만 드러나 원인 파악이 어려우니 한 번 더 기다렸다 재측정한다.
            awaitFrame()
            composeView.measure(widthSpec, heightSpec)
        }

        val height = composeView.measuredHeight
        if (height <= 0) {
            Log.w(TAG, "측정 높이가 0입니다 — 컴포지션이 끝나지 않았습니다")
            return@withContext null
        }

        composeView.layout(0, 0, composeView.measuredWidth, height)

        val bitmap = Bitmap.createBitmap(composeView.measuredWidth, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(BACKGROUND_ARGB)
        composeView.draw(canvas)
        bitmap
    } catch (e: Exception) {
        Log.w(TAG, "리포트 카드 렌더 실패", e)
        null
    } finally {
        // 누락하면 보이지 않는 뷰가 Activity에 쌓이고 컴포지션이 살아남는다.
        (composeView.parent as? ViewGroup)?.removeView(composeView)
    }
}

// ContextWrapper 체인을 따라 Activity를 찾는다(ComposeView는 Activity Context가 필요).
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
