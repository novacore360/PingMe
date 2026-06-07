package com.messagingapp.service

import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.messagingapp.MessagingApp
import com.messagingapp.R
import com.messagingapp.data.repository.AuthRepository
import com.messagingapp.data.repository.MessageRepository
import kotlinx.coroutines.*

class MessageNotificationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val authRepo = AuthRepository()
    private val msgRepo = MessageRepository()
    private var notifId = 1000

    override fun onCreate() {
        super.onCreate()
        startForeground(99, buildForegroundNotification())
        listenForMessages()
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, MessagingApp.CHANNEL_ID)
            .setContentTitle("Glimpse")
            .setContentText("Listening for messages...")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
    }

    private fun listenForMessages() {
        val userId = authRepo.currentUserId() ?: return
        scope.launch {
            msgRepo.listenToAllMessages(userId).collect { message ->
                val senderProfile = authRepo.getProfile(message.senderId).getOrNull()
                val senderName = senderProfile?.nickname ?: "Someone"
                showNotification(senderName, message.content)
            }
        }
    }

    private fun showNotification(sender: String, content: String) {
        if (ActivityCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, MessagingApp.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(sender)
            .setContentText(content)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(this).notify(notifId++, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, MessageNotificationService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
