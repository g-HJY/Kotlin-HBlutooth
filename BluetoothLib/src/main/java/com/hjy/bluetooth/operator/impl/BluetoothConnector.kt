package com.hjy.bluetooth.operator.impl

import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.AsyncTask
import android.os.Build
import android.text.TextUtils
import com.hjy.bluetooth.HBluetooth
import com.hjy.bluetooth.async.BluetoothConnectAsyncTask
import com.hjy.bluetooth.constant.BluetoothState
import com.hjy.bluetooth.entity.BluetoothDevice
import com.hjy.bluetooth.exception.BleException
import com.hjy.bluetooth.inter.BleMtuChangedCallback
import com.hjy.bluetooth.inter.ConnectCallBack
import com.hjy.bluetooth.inter.SendCallBack
import com.hjy.bluetooth.operator.abstra.Connector

/**
 * Created by _H_JY on 2018/10/20.
 */
class BluetoothConnector : Connector {
    private lateinit var mContext: Context
    private var bluetoothAdapter: BluetoothAdapter
    private var connectAsyncTask: BluetoothConnectAsyncTask? = null
    private var connectCallBack: ConnectCallBack? = null
    private var sendCallBack: SendCallBack? = null

    constructor(context: Context, bluetoothAdapter: BluetoothAdapter) {
        mContext = context
        this.bluetoothAdapter = bluetoothAdapter
    }

    @Synchronized
    override fun connect(device: BluetoothDevice, connectCallBack: ConnectCallBack?) {
        this.connectCallBack = connectCallBack
        cancelConnectAsyncTask()
        val hBluetooth: HBluetooth? = HBluetooth.getInstance(mContext)
        hBluetooth?.destroyChannel()
        hBluetooth?.cancelScan()
        val remoteDevice: android.bluetooth.BluetoothDevice = bluetoothAdapter.getRemoteDevice(device.address)
        if (device.type == android.bluetooth.BluetoothDevice.DEVICE_TYPE_CLASSIC) { //Classic Bluetooth Type.
            if (remoteDevice?.bondState != android.bluetooth.BluetoothDevice.BOND_BONDED) { //If no paired,register a broadcast to paired.
                /*Add automatic pairing*/
                val filter = IntentFilter(android.bluetooth.BluetoothDevice.ACTION_PAIRING_REQUEST)
                mContext.registerReceiver(object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        if (android.bluetooth.BluetoothDevice.ACTION_PAIRING_REQUEST == intent.action) {
                            try {
                                val pin = android.bluetooth.BluetoothDevice::class.java.getMethod("convertPinToBytes", String::class.java).invoke(android.bluetooth.BluetoothDevice::class.java, "1234") as ByteArray
                                val m = remoteDevice.javaClass.getMethod("setPin", ByteArray::class.java)
                                m.invoke(remoteDevice, pin)
                                remoteDevice.javaClass.getMethod("setPairingConfirmation", Boolean::class.javaPrimitiveType).invoke(remoteDevice, true)
                                println("PAIRED !")
                                //context.unregisterReceiver(this);
                                /*Paired successfully，interrupt broadcast*/abortBroadcast()
                            } catch (e: Exception) {
                                e.printStackTrace()
                                connectCallBack?.onError(BluetoothState.PAIRED_FAILED, "Automatic pairing failed, please pair manually.")
                            }
                        }
                    }
                }, filter)
            }
            connectAsyncTask = BluetoothConnectAsyncTask(mContext, remoteDevice, this.connectCallBack)
            connectAsyncTask!!.execute()
        } else if (device.type == android.bluetooth.BluetoothDevice.DEVICE_TYPE_LE) { //BLE Type.
            remoteDevice.connectGatt(mContext, false, bluetoothGattCallback)
        }
    }

    fun setSendCallBack(sendCallBack: SendCallBack?) {
        this.sendCallBack = sendCallBack
    }

    fun cancelConnectAsyncTask() {
        if (connectAsyncTask != null && connectAsyncTask?.status == AsyncTask.Status.RUNNING) {
            connectAsyncTask?.cancel(true)
        }
    }

    private val bluetoothGattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                val hBluetooth: HBluetooth? = HBluetooth.getInstance(mContext)
                hBluetooth?.isConnected = true
                val sender = hBluetooth?.sender()
                if (sender != null) {
                    val bluetoothSender = sender as BluetoothSender
                    bluetoothSender.setConnector(this@BluetoothConnector).initChannel(gatt, android.bluetooth.BluetoothDevice.DEVICE_TYPE_LE, connectCallBack)
                    bluetoothSender.discoverServices()
                }

                connectCallBack?.let { it.onConnected(sender) }

            } else if (newState == BluetoothProfile.STATE_CONNECTING) {

                connectCallBack?.let { it.onConnecting() }

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {

                HBluetooth.getInstance(mContext).isConnected = false
                gatt?.close()
                connectCallBack?.let { it.onDisConnected() }

            } else if (newState == BluetoothProfile.STATE_DISCONNECTING) {

                connectCallBack?.let { it.onDisConnecting() }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                //At the software level, MTU setting is supported only when Android API version > = 21 (Android 5.0).
                //At the hardware level, only modules with Bluetooth 4.2 and above can support the setting of MTU.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val mtuSize: Int? = HBluetooth.getInstance(mContext).mtuSize
                    if (mtuSize != null) {
                        if (mtuSize in 24..511) {
                            gatt.requestMtu(mtuSize)
                        }
                    }
                }
                var writeCharacteristicUUID: String? = HBluetooth.getInstance(mContext).writeCharacteristicUUID
                if (TextUtils.isEmpty(writeCharacteristicUUID)) {
                    writeCharacteristicUUID = "0000ffe1-0000-1000-8000-00805f9b34fb"
                }
                val services = gatt.services
                if (services != null && services.size > 0) {
                    for (i in services.indices) {
                        val characteristics = services[i].characteristics
                        if (characteristics != null && characteristics.size > 0) {
                            for (k in characteristics.indices) {
                                val bluetoothGattCharacteristic = characteristics[k]
                                if (writeCharacteristicUUID == bluetoothGattCharacteristic.uuid.toString()) {
                                    HBluetooth.getInstance(mContext).sender()?.initSenderHelper(bluetoothGattCharacteristic)
                                    gatt.setCharacteristicNotification(bluetoothGattCharacteristic, true)
                                }
                            }
                        }
                    }
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            val mtuSize: Int? = HBluetooth.getInstance(mContext).mtuSize
            val callback: BleMtuChangedCallback? = HBluetooth.getInstance(mContext).bleMtuChangedCallback
            callback?.let {
                if (BluetoothGatt.GATT_SUCCESS == status && mtuSize == mtu) {
                    it.onMtuChanged(mtu)
                } else {
                    it.onSetMTUFailure(mtu, BleException("MTU change failed!"))
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            super.onCharacteristicChanged(gatt, characteristic)
            val result = characteristic.value
            sendCallBack?.let { it.onReceived(null, result) }
        }
    }
}