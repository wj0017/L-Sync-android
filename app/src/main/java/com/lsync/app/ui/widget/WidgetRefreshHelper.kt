package com.lsync.app.ui.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.lsync.app.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetRefreshHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) {
    // 호출부(Home/Finance/Schedule VM)는 그대로 두고 여기서 모든 위젯을 갱신한다.
    fun requestUpdate() {
        scope.launch {
            val manager = GlanceAppWidgetManager(context)
            manager.getGlanceIds(LSyncWidget::class.java)
                .forEach { id -> LSyncWidget().update(context, id) }
            manager.getGlanceIds(ReportWidget::class.java)
                .forEach { id -> ReportWidget().update(context, id) }
        }
    }
}
