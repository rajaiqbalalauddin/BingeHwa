package com.raja.bingehwa.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.raja.bingehwa.BingeApp
import com.raja.bingehwa.domain.QuietHours
import java.util.Calendar

/**
 * Receives the data-only message the chapter worker sends. Data-only on purpose: the app then
 * decides whether to show it, which is what makes the in-app switch and quiet hours work.
 */
class BingeMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val d = message.data
        val seriesId = d["seriesId"] ?: return
        val chapterId = d["chapterId"] ?: return
        val settings = (application as BingeApp).container.settings.current
        if (!settings.notificationsOn) return
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (settings.quietHours && QuietHours.isQuiet(hour)) return
        Notifier.showNewChapter(
            context = applicationContext,
            seriesId = seriesId,
            chapterId = chapterId,
            title = d["title"] ?: "New chapter",
            number = d["number"] ?: "?",
        )
    }
}
