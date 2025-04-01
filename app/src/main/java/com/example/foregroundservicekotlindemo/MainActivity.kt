package com.example.foregroundservicekotlindemo // Adjust package name if needed

import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.foregroundservicekotlindemo.databinding.ActivityMainBinding // Import generated binding class

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var serviceIntent: Intent
    private var counter = 0

    private val TAG = "MainActivity"

    // --- Notification Permission Handling (Android 13+) ---
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.i(TAG, "POST_NOTIFICATIONS permission granted.")
            startService() // Proceed to start service after permission granted
        } else {
            Log.w(TAG, "POST_NOTIFICATIONS permission denied.")
            Toast.makeText(this, "Notification permission denied. Service cannot show notification.", Toast.LENGTH_LONG).show()
            // Handle the case where the user denies the permission
            // You might want to explain why the permission is needed
        }
    }

    private fun askNotificationPermission() {
        // This is only necessary for API level >= 33 (TIRAMISU)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Log.i(TAG, "POST_NOTIFICATIONS permission already granted.")
                    startService() // Permission already granted, start the service
                }
                shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS) -> {
                    // Explain to the user why the permission is needed
                    Toast.makeText(this, "Notification permission is required to show service status.", Toast.LENGTH_LONG).show()
                    // Then request the permission
                    requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    // Directly ask for the permission
                    Log.i(TAG, "Requesting POST_NOTIFICATIONS permission.")
                    requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            startService() // No runtime permission needed for older versions
        }
    }
    // --- End Notification Permission Handling ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        serviceIntent = Intent(this, MyForegroundService::class.java)

        binding.buttonStart.setOnClickListener {
            Log.d(TAG, "Start Button Clicked")
            askNotificationPermission() // Ask for permission first (includes starting service if granted)
        }

        binding.buttonStop.setOnClickListener {
            Log.d(TAG, "Stop Button Clicked")
            stopService()
        }
    }

    private fun startService() {
        Log.i(TAG,"Attempting to start service...")
        // For Android 8 (Oreo) and above, startForegroundService is required
        // for starting services when the app is not in the foreground.
        // It requires the service to call startForeground() within 5 seconds.
        try {
            ContextCompat.startForegroundService(this, serviceIntent)
            Log.i(TAG,"startForegroundService called.")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting service: ${e.message}", e)
            Toast.makeText(this, "Error starting service: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopService() {
        Log.i(TAG,"Attempting to stop service...")
        stopService(serviceIntent)
        binding.textViewCounter.text = "Service Stopped" // Reset UI immediately
    }

    // BroadcastReceiver to get updates from the service
    private val counterReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == MyForegroundService.ACTION_BROADCAST) {
                counter = intent.getIntExtra(MyForegroundService.EXTRA_COUNTER, 0)
                binding.textViewCounter.text = "Counter: $counter"
                Log.d(TAG, "Received counter update: $counter")
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Register the BroadcastReceiver
        val filter = IntentFilter(MyForegroundService.ACTION_BROADCAST)
        ContextCompat.registerReceiver(
            this,
            counterReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        Log.d(TAG, "BroadcastReceiver registered")
    }

    override fun onStop() {
        super.onStop()
        // Unregister the BroadcastReceiver
        unregisterReceiver(counterReceiver)
        Log.d(TAG, "BroadcastReceiver unregistered")
    }
}