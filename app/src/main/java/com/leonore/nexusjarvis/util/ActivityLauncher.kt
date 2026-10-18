package com.leonore.nexusjarvis.util

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import com.leonore.nexusjarvis.AppForeground
import com.leonore.nexusjarvis.notifications.NotificationHelper

enum class LaunchOutcome { STARTED, NOTIFICATION_SENT, FAILED }

/**
 * Android 10+ melarang memulai Activity dari latar belakang. Jika aplikasi tidak sedang di depan,
 * kita tidak berpura-pura berhasil: kirim notifikasi yang bisa diketuk pengguna.
 */
object ActivityLauncher {
    fun launch(context: Context, intent: Intent, label: String): LaunchOutcome {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (AppForeground.isForeground) {
            return try {
                context.startActivity(intent)
                LaunchOutcome.STARTED
            } catch (e: ActivityNotFoundException) {
                LaunchOutcome.FAILED
            } catch (e: SecurityException) {
                LaunchOutcome.FAILED
            }
        }
        val pi = PendingIntent.getActivity(
            context, label.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val posted = NotificationHelper.post(
            context, 20_000 + (label.hashCode() and 0xFFF), NotificationHelper.CH_TASKS,
            "Ketuk untuk membuka", label, pi
        )
        return if (posted) LaunchOutcome.NOTIFICATION_SENT else LaunchOutcome.FAILED
    }
}
