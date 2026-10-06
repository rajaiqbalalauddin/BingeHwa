package com.raja.bingehwa.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.raja.bingehwa.MainActivity
import com.raja.bingehwa.R

/** All notification work in one place: channel setup and the "new chapter" alert. */
object Notifier {
    const val CHANNEL_CHAPTERS = "new_chapters"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_CHAPTERS, "New chapters", NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = "Alerts when a series you follow gets a new chapter"
        nm.createNotificationChannel(channel)
    }

    /** Deep link that the Reader destination listens for. */
    fun readerUri(chapterId: String, seriesId: String): Uri = Uri.parse("bingehwa://reader/$chapterId/$seriesId")

    fun showNewChapter(context: Context, seriesId: String, chapterId: String, title: String, number: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannels(context)
        val intent = Intent(Intent.ACTION_VIEW, readerUri(chapterId, seriesId), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context, chapterId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_CHAPTERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("Chapter $number is out")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(chapterId.hashCode(), n)
    }
}
