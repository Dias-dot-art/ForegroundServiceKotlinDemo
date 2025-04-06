package com.example.foregroundservicekotlindemo // Adjust package name if needed

import android.Manifest // Import Manifest class
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
    private var currentCounter = 0 // Store last known counter value for persistence

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
            Toast.makeText(this, "Notification permission denied. Service cannot show status notification.", Toast.LENGTH_LONG).show()
            // Explain why it's needed or disable functionality
        }
    }

    private fun askNotificationPermission() {
        // Only needed for Android 13 (API level 33) and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS // Use Manifest.permission
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Log.i(TAG, "POST_NOTIFICATIONS permission already granted.")
                    startService() // Permission already granted, start the service
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // Show UI explaining why the permission is needed
                    Toast.makeText(this, "Notification permission is required to show service status.", Toast.LENGTH_LONG).show()
                    // Then request the permission
                    Log.i(TAG, "Showing rationale and requesting POST_NOTIFICATIONS permission.")
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    // Directly ask for the permission the first time or if rationale shouldn't be shown
                    Log.i(TAG, "Requesting POST_NOTIFICATIONS permission directly.")
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // No runtime permission needed for notifications below Android 13
            Log.i(TAG, "Notification permission not required for this Android version.")
            startService()
        }
    }
    // --- End Notification Permission Handling ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.d(TAG, "onCreate")

        // Restore counter value if Activity is recreated
        savedInstanceState?.let {
            currentCounter = it.getInt("counterValue", 0)
            updateCounterText(currentCounter) // Update UI with restored value
        }

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

    // Save counter value when Activity might be destroyed
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("counterValue", currentCounter)
        Log.d(TAG, "onSaveInstanceState: Saving counter $currentCounter")
    }


    private fun startService() {
        Log.i(TAG,"Attempting to start service...")
        try {
            ContextCompat.startForegroundService(this, serviceIntent)
            Log.i(TAG,"startForegroundService called.")
            // Optionally update UI immediately, though receiver will update it too
            binding.textViewCounter.text = "Service Starting..."
        } catch (e: Exception) {
            Log.e(TAG, "Error starting service: ${e.message}", e)
            Toast.makeText(this, "Error starting service: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopService() {
        Log.i(TAG,"Attempting to stop service...")
        try {
            stopService(serviceIntent)
            binding.textViewCounter.text = "Service Stopped" // Reset UI immediately
            currentCounter = 0 // Reset counter value
            Log.i(TAG,"stopService called.")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping service: ${e.message}", e)
            Toast.makeText(this, "Error stopping service: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // BroadcastReceiver to get updates from the service
    private val counterReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == MyForegroundService.ACTION_BROADCAST) {
                val receivedCounter = intent.getIntExtra(MyForegroundService.EXTRA_COUNTER, 0)
                currentCounter = receivedCounter // Update stored counter
                updateCounterText(currentCounter)
                Log.d(TAG, "BroadcastReceiver: Received counter update: $currentCounter")
            }
        }
    }

    private fun updateCounterText(count: Int) {
        binding.textViewCounter.text = if (count > 0) "Counter: $count" else "Service Stopped"
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart: Registering receiver")
        // Register the BroadcastReceiver
        val filter = IntentFilter(MyForegroundService.ACTION_BROADCAST)
        // Use RECEIVER_NOT_EXPORTED for security on Android 13+ if the receiver is only used internally
        val receiverFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.RECEIVER_NOT_EXPORTED
        } else {
            0 // No flag needed for older versions in this case
        }
        ContextCompat.registerReceiver(this, counterReceiver, filter, receiverFlags)

        // Optional: Check if service is already running when activity starts/resumes
        // This requires a more complex check (e.g., static flag, querying ActivityManager - not recommended)
        // The broadcast receiver handles updating the UI if the service *is* running.
        // If the service was stopped, the UI should reflect that (set in stopService).
        // We update the text here based on the last known value, which might be from onSaveInstanceState
        updateCounterText(currentCounter)
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop: Unregistering receiver")
        // Unregister the BroadcastReceiver
        try {
            unregisterReceiver(counterReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Receiver already unregistered or never registered.", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        // Note: Service might continue running if started and not explicitly stopped
        // If you want service to stop when activity is destroyed (not usually desired for foreground service):
        // stopService()
    }
}