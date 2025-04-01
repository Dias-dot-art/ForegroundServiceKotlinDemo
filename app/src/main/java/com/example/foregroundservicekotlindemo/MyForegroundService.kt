package com.example.foregroundservicekotlindemo // Adjust package name if needed

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class MyForegroundService : Service() {

    private val TAG = "MyForegroundService"
    private val CHANNEL_ID = "ForegroundServiceKotlinChannel"
    private val NOTIFICATION_ID = 1

    // Coroutine Scope for background tasks tied to the service lifecycle
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    private var counter = 0
    private var isServiceRunning = false

    companion object {
        const val ACTION_BROADCAST = "com.example.foregroundservicekotlindemo.COUNTER_BROADCAST"
        const val EXTRA_COUNTER = "extra_counter"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service onCreate")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Service onStartCommand")

        val notification = createNotification("Service Running...")
        startForeground(NOTIFICATION_ID, notification)

        if (!isServiceRunning) {
            isServiceRunning = true
            startCounter()
        }

        // If the service is killed, restart it
        return START_STICKY
    }

    private fun startCounter() {
        serviceScope.launch {
            while (isServiceRunning) {
                counter++
                Log.i(TAG, "Counter: $counter, Thread ID: ${Thread.currentThread().id}")

                // Send counter value back to Activity
                val broadcastIntent = Intent(ACTION_BROADCAST).apply {
                    putExtra(EXTRA_COUNTER, counter)
                }
                sendBroadcast(broadcastIntent)

                // Update notification text (optional)
                // updateNotification("Counter: $counter")

                delay(1000) // Delay for 1 second
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Service onDestroy")
        isServiceRunning = false
        serviceJob.cancel() // Cancel all coroutines started in this scope
        Log.i(TAG, "Coroutines cancelled.")
        stopForeground(true) // Remove notification
    }

    override fun onBind(intent: Intent): IBinder? {
        // We don't provide binding, so return null
        return null
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Foreground Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT // Importance level
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
            Log.i(TAG, "Notification Channel Created")
        }
    }

    private fun createNotification(contentText: String): Notification {
        // Intent to launch when notification is tapped (optional)
        // val notificationIntent = Intent(this, MainActivity::class.java)
        // val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Foreground Service Active")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Replace with your own icon
            // .setContentIntent(pendingIntent) // Uncomment to make notification clickable
            .setOnlyAlertOnce(true) // Don't sound/vibrate for updates
            .build()
    }

    // Optional: Function to update existing notification
    private fun updateNotification(contentText: String) {
        val notification = createNotification(contentText)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }
}
