package com.lsync.app.ui.widget

import androidx.glance.appwidget.GlanceAppWidgetReceiver

class LSyncWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = LSyncWidget()
}
