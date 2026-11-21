package com.example.btgamepad

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

class BluetoothGamepadManager(
    private val onStatusChanged: (connected: Boolean, deviceName: String?) -> Unit
) {
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var lastPacket = ByteArray(6)
    private var lastSendTime = 0L

    @Volatile
    var isConnected = false
        private set

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        scope.launch {
            disconnect()
            try {
                val btSocket = device.createRfcommSocketToServiceRecord(sppUuid)
                btSocket.connect()
                socket = btSocket
                outputStream = btSocket.outputStream
                isConnected = true
                withContext(Dispatchers.Main) {
                    onStatusChanged(true, device.name ?: device.address)
                }
            } catch (e: IOException) {
                disconnect()
                withContext(Dispatchers.Main) {
                    onStatusChanged(false, null)
                }
            }
        }
    }

    fun sendGamepadState(joyX: Int, joyY: Int, btnMask1: Int, btnMask2: Int, forceSend: Boolean = false) {
        if (!isConnected) return

        val packet = byteArrayOf(
            0xAA.toByte(),
            joyX.toByte(),
            joyY.toByte(),
            btnMask1.toByte(),
            btnMask2.toByte(),
            0x55.toByte()
        )

        val now = System.currentTimeMillis()
        if (!forceSend && packet.contentEquals(lastPacket)) return
        if (!forceSend && (now - lastSendTime) < 18) return

        lastPacket = packet
        lastSendTime = now

        scope.launch {
            try {
                outputStream?.write(packet)
            } catch (e: IOException) {
                disconnect()
                withContext(Dispatchers.Main) {
                    onStatusChanged(false, null)
                }
            }
        }
    }

    fun disconnect() {
        isConnected = false
        try {
            outputStream?.close()
            socket?.close()
        } catch (_: IOException) {}
        outputStream = null
        socket = null
    }
}
