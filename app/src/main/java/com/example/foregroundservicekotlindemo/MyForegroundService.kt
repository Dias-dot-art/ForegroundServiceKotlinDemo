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

        // Create the initial notification
        val notification = createNotification("Service Running...")
        // Promote the service to foreground
        startForeground(NOTIFICATION_ID, notification)
        Log.i(TAG,"Service started in foreground.")

        if (!isServiceRunning) {
            isServiceRunning = true
            startCounter()
            Log.i(TAG,"Counter coroutine started.")
        } else {
            Log.i(TAG,"Service already running, not starting new counter.")
        }

        // If the service is killed, restart it automatically
        return START_STICKY
    }

    private fun startCounter() {
        serviceScope.launch {
            Log.d(TAG, "Counter loop starting on thread: ${Thread.currentThread().name}")
            while (isServiceRunning && isActive) { // Check isActive for coroutine cancellation
                try {
                    counter++
                    Log.d(TAG, "Counter: $counter, Thread ID: ${Thread.currentThread().id}")

                    // Send counter value back to Activity
                    val broadcastIntent = Intent(ACTION_BROADCAST).apply {
                        putExtra(EXTRA_COUNTER, counter)
                    }
                    sendBroadcast(broadcastIntent)

                    // Optional: Update notification text
                    // updateNotification("Counter: $counter")

                    delay(1000) // Delay for 1 second
                } catch (e: CancellationException) {
                    Log.i(TAG, "Counter coroutine cancelled.")
                    break // Exit loop if cancelled
                } catch (e: InterruptedException) {
                    Log.i(TAG, "Counter coroutine interrupted.")
                    Thread.currentThread().interrupt() // Restore interrupt status
                    break // Exit loop if interrupted
                } catch (e: Exception) {
                    Log.e(TAG, "Error in counter loop", e)
                    // Decide how to handle other errors, maybe stop the service or retry
                    isServiceRunning = false // Stop running on unexpected error
                }
            }
            Log.d(TAG, "Counter loop finished.")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Service onDestroy")
        isServiceRunning = false
        serviceJob.cancel() // Cancel all coroutines started in this scope
        Log.i(TAG, "Coroutines cancelled.")
        // stopForeground(true) is deprecated, use stopForeground(STOP_FOREGROUND_REMOVE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        Log.i(TAG,"Foreground state stopped, notification removed.")
    }

    override fun onBind(intent: Intent): IBinder? {
        // We don't provide binding, so return null
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Foreground Service Channel",
                NotificationManager.IMPORTANCE_LOW // Use LOW to avoid sound/vibration by default
            ).apply {
                description = "Channel for Foreground Service Example"
                // Optionally disable sound/vibration
                // setSound(null, null)
                // enableVibration(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
            Log.i(TAG, "Notification Channel Created")
        } else {
            Log.i(TAG, "Notification Channel not needed for API < 26")
        }
    }

    private fun createNotification(contentText: String): Notification {
        // Intent to launch when notification is tapped (optional)
        val notificationIntent = Intent(this, MainActivity::class.java)
        // Ensure FLAG_IMMUTABLE or FLAG_MUTABLE is set based on needs (IMMUTABLE recommended)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)


        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Counter Service")
            .setContentText(contentText)
            // Make sure you have 'ic_notification' or use a default like 'ic_launcher_foreground'
            // You might need to create this drawable resource.
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Placeholder icon
            .setContentIntent(pendingIntent) // Make notification clickable -> opens MainActivity
            .setOnlyAlertOnce(true) // Don't sound/vibrate for updates if notification exists
            .setOngoing(true) // Indicates it's an ongoing task
            .build()
    }

    // Optional: Function to update existing notification efficiently
    private fun updateNotification(contentText: String) {
        val notification = createNotification(contentText)
        val notificationManager = getSystemService(NotificationManager::class.java)
        // Use the same NOTIFICATION_ID to update the existing notification
        notificationManager?.notify(NOTIFICATION_ID, notification)
        Log.d(TAG, "Notification updated with text: $contentText")
    }
}