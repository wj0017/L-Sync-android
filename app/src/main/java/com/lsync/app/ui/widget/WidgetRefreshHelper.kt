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
    fun requestUpdate() {
        scope.launch {
            val manager = GlanceAppWidgetManager(context)
            val ids = manager.getGlanceIds(LSyncWidget::class.java)
            ids.forEach { id -> LSyncWidget().update(context, id) }
        }
    }
}
