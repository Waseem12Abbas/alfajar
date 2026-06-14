package com.example.core.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Build
import com.example.models.VehiclePass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

object BluetoothPrinterService {

    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()
        return adapter.bondedDevices.toList()
    }

    // Generate ESC/POS printable bytes for 58mm printer
    fun generateEscPosPassBytes(pass: VehiclePass): ByteArray {
        val list = mutableListOf<Byte>()

        // ESC/POS Commands
        val initPrinter = byteArrayOf(0x1B, 0x40) // Initialize
        val alignCenter = byteArrayOf(0x1B, 0x61, 0x01) // Align Center
        val alignLeft = byteArrayOf(0x1B, 0x61, 0x00) // Align Left
        val textDoubleSize = byteArrayOf(0x1B, 0x21, (0x10 or 0x20).toByte()) // Large text
        val textNormal = byteArrayOf(0x1B, 0x21, 0x00) // Regular text
        val feedPaper = byteArrayOf(0x0A, 0x0A, 0x0A, 0x0A) // 4 lines feed

        list.addAll(initPrinter.toList())
        list.addAll(alignCenter.toList())
        list.addAll(textDoubleSize.toList())
        list.addAll("PAK PASS\n".toByteArray(Charsets.US_ASCII).toList())
        list.addAll(textNormal.toList())
        list.addAll("VEHICLE PASS - گاڑی پاس\n".toByteArray(Charsets.UTF_8).toList())
        list.addAll("--------------------------------\n".toByteArray(Charsets.US_ASCII).toList())

        list.addAll(alignLeft.toList())
        list.addAll("UID: ${pass.unique_id.take(18)}...\n".toByteArray(Charsets.US_ASCII).toList())
        list.addAll("Owner: ${pass.owner_name}\n".toByteArray(Charsets.UTF_8).toList())
        list.addAll("CNIC: ${pass.cnic}\n".toByteArray(Charsets.UTF_8).toList())
        list.addAll("Vehicle: ${pass.vehicle_no}\n".toByteArray(Charsets.UTF_8).toList())
        list.addAll("Phone: ${pass.phone ?: "N/A"}\n".toByteArray(Charsets.UTF_8).toList())
        list.addAll("Expiry: ${pass.expiry_date}\n".toByteArray(Charsets.US_ASCII).toList())
        list.addAll("Created: ${pass.created_at.take(10)}\n".toByteArray(Charsets.US_ASCII).toList())

        list.addAll(alignCenter.toList())
        list.addAll("--------------------------------\n".toByteArray(Charsets.US_ASCII).toList())
        list.addAll("SCAN QR TO VERIFY\n".toByteArray(Charsets.US_ASCII).toList())
        list.addAll("پاس کی تصدیق کے لیے اسکین کریں\n".toByteArray(Charsets.UTF_8).toList())
        list.addAll(feedPaper.toList())

        return list.toByteArray()
    }

    @SuppressLint("MissingPermission")
    suspend fun printPass(device: BluetoothDevice, pass: VehiclePass): Result<Unit> {
        return withContext(Dispatchers.IO) {
            var socket: BluetoothSocket? = null
            var outStream: OutputStream? = null
            try {
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()
                outStream = socket.outputStream

                val bytes = generateEscPosPassBytes(pass)
                outStream.write(bytes)
                outStream.flush()

                Result.success(Unit)
            } catch (e: IOException) {
                e.printStackTrace()
                Result.failure(e)
            } finally {
                try {
                    outStream?.close()
                    socket?.close()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }
}
