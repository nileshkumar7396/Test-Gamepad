package com.example.btgamepad

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {

    private lateinit var btManager: BluetoothGamepadManager
    private lateinit var tvStatus: TextView
    private lateinit var vibrator: Vibrator

    private var joyX = 128
    private var joyY = 128
    private var btnMask1 = 0
    private var btnMask2 = 0

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            showPairedDevicesDialog()
        } else {
            Toast.makeText(this, "Bluetooth permission zaroori hai", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        enableImmersiveMode()
        setupVibrator()

        tvStatus = findViewById(R.id.tvStatus)

        btManager = BluetoothGamepadManager { connected, name ->
            if (connected) {
                tvStatus.text = "● Connected: $name"
                tvStatus.setTextColor(Color.parseColor("#00E676"))
            } else {
                tvStatus.text = "● Disconnected"
                tvStatus.setTextColor(Color.parseColor("#FF5252"))
            }
        }

        findViewById<Button>(R.id.btnConnect).setOnClickListener {
            checkPermissionsAndConnect()
        }

        findViewById<JoystickView>(R.id.joystickView).onMoveListener = { x, y ->
            joyX = x
            joyY = y
            btManager.sendGamepadState(joyX, joyY, btnMask1, btnMask2)
        }

        bindButton(R.id.btnA, maskGroup = 1, bit = 0)
        bindButton(R.id.btnB, maskGroup = 1, bit = 1)
        bindButton(R.id.btnX, maskGroup = 1, bit = 2)
        bindButton(R.id.btnY, maskGroup = 1, bit = 3)
        bindButton(R.id.btnUp, maskGroup = 1, bit = 4)
        bindButton(R.id.btnDown, maskGroup = 1, bit = 5)
        bindButton(R.id.btnLeft, maskGroup = 1, bit = 6)
        bindButton(R.id.btnRight, maskGroup = 1, bit = 7)

        bindButton(R.id.btnL1, maskGroup = 2, bit = 0)
        bindButton(R.id.btnR1, maskGroup = 2, bit = 1)
        bindButton(R.id.btnStart, maskGroup = 2, bit = 2)
        bindButton(R.id.btnSelect, maskGroup = 2, bit = 3)
    }

    private fun enableImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun setupVibrator() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun triggerHaptic() {
        vibrator.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindButton(viewId: Int, maskGroup: Int, bit: Int) {
        val button = findViewById<View>(viewId)
        val bitMask = 1 shl bit

        button.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    v.isPressed = true
                    triggerHaptic()
                    if (maskGroup == 1) btnMask1 = btnMask1 or bitMask
                    else btnMask2 = btnMask2 or bitMask
                    btManager.sendGamepadState(joyX, joyY, btnMask1, btnMask2, forceSend = true)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    if (maskGroup == 1) btnMask1 = btnMask1 and bitMask.inv()
                    else btnMask2 = btnMask2 and bitMask.inv()
                    btManager.sendGamepadState(joyX, joyY, btnMask1, btnMask2, forceSend = true)
                    true
                }
                else -> false
            }
        }
    }

    private fun checkPermissionsAndConnect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val needed = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
            val missing = needed.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) {
                permissionLauncher.launch(missing.toTypedArray())
                return
            }
        }
        showPairedDevicesDialog()
    }

    @SuppressLint("MissingPermission")
    private fun showPairedDevicesDialog() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter: BluetoothAdapter? = bluetoothManager.adapter

        if (adapter == null || !adapter.isEnabled) {
            Toast.makeText(this, "Pehle phone ka Bluetooth ON karein", Toast.LENGTH_SHORT).show()
            return
        }

        val pairedDevices: List<BluetoothDevice> = adapter.bondedDevices.toList()
        if (pairedDevices.isEmpty()) {
            Toast.makeText(this, "Koi paired device nahi mila. Phone settings se HC-05/ESP32 pair karein.", Toast.LENGTH_LONG).show()
            return
        }

        val deviceNames = pairedDevices.map { "${it.name ?: "Unknown"} (${it.address})" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Target Device Select Karein")
            .setItems(deviceNames) { _, which ->
                tvStatus.text = "● Connecting..."
                tvStatus.setTextColor(Color.parseColor("#FFD600"))
                btManager.connect(pairedDevices[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        btManager.disconnect()
    }
}
