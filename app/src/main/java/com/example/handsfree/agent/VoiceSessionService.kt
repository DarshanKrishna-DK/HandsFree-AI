package com.example.handsfree.agent

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.example.handsfree.MainActivity

/** Started only from a visible activity after the user grants microphone access. */
class VoiceSessionService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            AgentController.get(this).stop()
            VoiceController.get(this).endSession()
            stopSelf()
            return START_NOT_STICKY
        }
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel("conversation", "Voice conversation", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 2, Intent(this, VoiceSessionService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "conversation") else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        val notification = builder.setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("HandsFree conversation is on")
            .setContentText("Conversation stays on. Say stop, or tap Stop to end microphone access.")
            .setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build()).build()
        try {
            if (Build.VERSION.SDK_INT >= 30) startForeground(24, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(24, notification)
            VoiceController.get(this).sessionReady = true
        } catch (_: SecurityException) {
            VoiceController.get(this).sessionReady = false
            stopSelf()
        }
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        VoiceController.get(this).sessionReady = false
        VoiceController.get(this).serviceStopped()
        super.onDestroy()
    }
}
