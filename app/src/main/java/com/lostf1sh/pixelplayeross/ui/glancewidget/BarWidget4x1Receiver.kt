package com.lostf1sh.pixelplayeross.ui.glancewidget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class BarWidget4x1Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BarWidget4x1()
}