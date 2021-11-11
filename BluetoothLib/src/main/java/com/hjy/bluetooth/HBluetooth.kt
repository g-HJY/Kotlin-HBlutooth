package com.hjy.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.support.annotation.IntDef
import com.hjy.bluetooth.constant.ValueLimit
import com.hjy.bluetooth.exception.BleException
import com.hjy.bluetooth.inter.BleMtuChangedCallback
import com.hjy.bluetooth.inter.ScanCallBack
import com.hjy.bluetooth.operator.abstra.Connector
import com.hjy.bluetooth.operator.abstra.Scanner
import com.hjy.bluetooth.operator.abstra.Sender
import com.hjy.bluetooth.operator.impl.BluetoothConnector
import com.hjy.bluetooth.operator.impl.BluetoothScanner
import com.hjy.bluetooth.operator.impl.BluetoothSender

/**
 * Created by _H_JY on 2018/10/20.
 */
class HBluetooth private constructor(private val mContext: Context) {
    private lateinit var mAdapter: BluetoothAdapter
    private lateinit var scanner: Scanner
    private lateinit var connector: Connector
    private lateinit var sender: Sender
    var isConnected = false
    var mBleConfig: BleConfig? = null

    @IntDef(BluetoothDevice.DEVICE_TYPE_CLASSIC.toLong(), BluetoothDevice.DEVICE_TYPE_LE.toLong())
    @kotlin.annotation.Retention(AnnotationRetention.SOURCE)
    annotation class BluetoothType

    /**
     * You must call it first after initialize this class.
     *
     * @return
     */
    fun enableBluetooth(): HBluetooth = apply {
        mAdapter = BluetoothAdapter.getDefaultAdapter()

        if (mAdapter == null) {
            throw RuntimeException("Bluetooth unsupported!")
        }
        if (!mAdapter.isEnabled) {
            mAdapter.enable()
        }
        scanner = BluetoothScanner(mContext, mAdapter)
        connector = BluetoothConnector(mContext, mAdapter)
        sender = BluetoothSender()
    }

    val bondedDevices: Set<BluetoothDevice>?
        get() = if (mAdapter == null) null else mAdapter.bondedDevices

    fun scan(@BluetoothType scanType: Int, scanCallBack: ScanCallBack) {
        scanner?.scan(scanType, scanCallBack)
    }

    fun scan(@BluetoothType scanType: Int, timeUse: Int, scanCallBack: ScanCallBack) {
        scanner?.scan(scanType, timeUse, scanCallBack)
    }

    fun scanner(): Scanner? {
        if (mAdapter == null || !mAdapter.isEnabled) {
            throw RuntimeException("you must call enableBluetooth() first.")
        }
        return scanner
    }

    @Synchronized
    fun cancelScan() {
        scanner?.stopScan()
    }

    @Synchronized
    fun destroyChannel() {
        sender?.destroyChannel()
    }

    fun connector(): Connector? {
        if (mAdapter == null || !mAdapter.isEnabled) {
            throw RuntimeException("you must call enableBluetooth() first.")
        }
        return connector
    }

    fun sender(): Sender? {
        if (mAdapter == null || !mAdapter.isEnabled) {
            throw RuntimeException("you must call enableBluetooth() first.")
        }
        return sender
    }


    class BleConfig {
        var serviceUUID: String? = null
            private set
        var writeCharacteristicUUID: String? = null
            private set
        var notifyCharacteristicUUID: String? = null
            private set
        var isUseCharacteristicDescriptor = false
            private set
        var mtuSize = 0
            private set
        private var mBleMtuChangedCallback: BleMtuChangedCallback? = null

        fun withServiceUUID(serviceUUID: String?): BleConfig = apply {
            this.serviceUUID = serviceUUID
        }

        fun withWriteCharacteristicUUID(writeCharacteristicUUID: String?): BleConfig = apply {
            this.writeCharacteristicUUID = writeCharacteristicUUID
        }

        fun withNotifyCharacteristicUUID(notifyCharacteristicUUID: String?): BleConfig = apply {
            this.notifyCharacteristicUUID = notifyCharacteristicUUID
        }

        fun useCharacteristicDescriptor(useCharacteristicDescriptor: Boolean): BleConfig = apply {
            isUseCharacteristicDescriptor = useCharacteristicDescriptor
        }

        /**
         * set Mtu
         *
         * @param mtuSize
         * @param callback
         */
        fun setMtu(mtuSize: Int, callback: BleMtuChangedCallback?): BleConfig = apply {
            requireNotNull(callback) { "BleMtuChangedCallback can not be Null!" }
            if (mtuSize > ValueLimit.DEFAULT_MAX_MTU) {
                callback.onSetMTUFailure(mtuSize, BleException("requiredMtu should lower than 512 !"))
            }
            if (mtuSize < ValueLimit.DEFAULT_MTU) {
                callback.onSetMTUFailure(mtuSize, BleException("requiredMtu should higher than 23 !"))
            }
            this.mtuSize = mtuSize
            mBleMtuChangedCallback = callback
        }

        fun getBleMtuChangedCallback(): BleMtuChangedCallback? {
            return mBleMtuChangedCallback
        }
    }


    @Synchronized
    fun release() {
        cancelScan()
        destroyChannel()
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var mHBluetooth: HBluetooth? = null

        fun getInstance(context: Context): HBluetooth {
            if (mHBluetooth == null) {
                synchronized(HBluetooth::class) {
                    if (mHBluetooth == null) {
                        mHBluetooth = HBluetooth(context.applicationContext)
                    }
                }
            }
            return mHBluetooth!!
        }
    }

}
