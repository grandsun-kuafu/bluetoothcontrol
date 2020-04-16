package com.grandsun.bluetoothcontrol;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import com.grandsun.bluetoothcontrol.utils.LogUtil;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

import static android.bluetooth.BluetoothDevice.TRANSPORT_LE;

public class BleBluetoothService {

    private BluetoothGatt bluetoothGatt;

    private Handler mHandler;

    private Context appContext;

    private static final String UUID_CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR = "00002902-0000-1000-8000-00805f9b34fb";

    public BluetoothDevice bluetoothDevice;

    public BleBluetoothService(Context context, Handler handler) {
        this.appContext = context;
        this.mHandler = handler;
    }

    private Queue<BleRequest> mRequestQueue = new LinkedList<BleRequest>();
    private BleRequest mCurrentRequest = null;

    /**
     * 增加requestQueue管理
     *
     * @param request
     */
    protected void addBleRequest(BleRequest request) {
        synchronized (mRequestQueue) {
            mRequestQueue.add(request);
            processNextRequest();
        }
    }

    private static final int REQUEST_TIMEOUT = 10 * 10; // total timeout =
    // REQUEST_TIMEOUT *
    // 100ms
    private boolean mCheckTimeout = false;
    private int mElapsed = 0;

    private Thread mRequestTimeout;

    private Runnable mTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            mElapsed = 0;
            try {
                while (mCheckTimeout) {
                    // Log.d(TAG, "monitoring timeout seconds: " + mElapsed);
                    Thread.sleep(100);
                    mElapsed++;

                    if (mElapsed > REQUEST_TIMEOUT && mCurrentRequest != null) {
                        LogUtil.d("-processrequest type "
                                + mCurrentRequest.type + " address "
                                + mCurrentRequest.device.getAddress() + " [timeout]");
                        //time out
                        mHandler.obtainMessage(BleBluetoothState.MESSAGE_TIME_OUT, -1, -1).sendToTarget();

//                        if (bluetoothDevice != null) {
//                            reset();
//                        }
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                mCurrentRequest = null;
                                processNextRequest();
                            }
                        }, "th-ble").start();
                        break;
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                LogUtil.d("monitoring thread exception");
            }
            LogUtil.d("monitoring thread stop");
        }
    };

    private synchronized void processNextRequest() {
        if (mCurrentRequest != null) {
            return;
        }

        if (mRequestQueue.isEmpty()) {
            return;
        }
        mCurrentRequest = mRequestQueue.remove();
        LogUtil.d("+processrequest type " + mCurrentRequest.type + " address "
                + mCurrentRequest.device.getAddress() + " remark " + mCurrentRequest.remark);
        startTimeoutThread();
        boolean ret = false;
        switch (mCurrentRequest.type) {
            case CONNECT_GATT:
                ret = connect(mCurrentRequest.device);
                break;
            case DISCOVER_SERVICE:
                ret = this.bluetoothGatt.discoverServices();
                break;
            case OPEN_COMMAND_NOTIFY:
                ret = openCommand(mCurrentRequest.commandUUID);
                break;
            case CHARACTERISTIC_INDICATION:
            case CLOSE_COMMAND_NOTIFY:
                ret = closeCommand(mCurrentRequest.commandUUID);
                break;
            case READ_CHARACTERISTIC:
//                ret = ((IBleRequestHandler) mBle).readCharacteristic(
//                        mCurrentRequest.address, mCurrentRequest.characteristic);
                break;
            case WRITE_CHARACTERISTIC:
//                ret = ((IBleRequestHandler) mBle).writeCharacteristic(
//                        mCurrentRequest.address, mCurrentRequest.characteristic);
                break;
            case READ_DESCRIPTOR:
                break;
            case RESET:
                reset();
                break;
            default:
                break;
        }

        if (!ret) {
            clearTimeoutThread();
            LogUtil.d("-processrequest type " + mCurrentRequest.type
                    + " address " + mCurrentRequest.device.getAddress() + " [fail start]");
            mHandler.obtainMessage(BleBluetoothState.MESSAGE_REQUEST_FAILED, -1, -1).sendToTarget();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    mCurrentRequest = null;
                    processNextRequest();
                }
            }, "th-ble").start();
        }
    }

    //完成了request
    public void requestProcessed(BleRequest.RequestType requestType,
                                 boolean success) {
        if (mCurrentRequest != null && mCurrentRequest.type == requestType) {
            clearTimeoutThread();
            LogUtil.d("-processrequest type " + requestType + " address "
                    + bluetoothDevice.getAddress() + " [success: " + success + "]");
            if (!success) {
                mHandler.obtainMessage(BleBluetoothState.MESSAGE_REQUEST_FAILED, -1, -1).sendToTarget();
            }
            new Thread(new Runnable() {
                @Override
                public void run() {
                    mCurrentRequest = null;
                    processNextRequest();
                }
            }, "th-ble").start();
        }
    }

    private void clearTimeoutThread() {
        if (null != mRequestTimeout && mRequestTimeout.isAlive()) {
            try {
                mCheckTimeout = false;
                mRequestTimeout.join();
                mRequestTimeout = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void startTimeoutThread() {
        mCheckTimeout = true;
        mRequestTimeout = new Thread(mTimeoutRunnable);
        mRequestTimeout.start();
    }

    /**
     * ---------requestQueue end-----------------
     */

    private synchronized boolean connect(BluetoothDevice bleDevice) {

        this.bluetoothDevice = bleDevice;
        mHandler.obtainMessage(BleBluetoothState.MESSAGE_STATE_CHANGE, BleBluetoothState.STATE_CONNECTING, -1).sendToTarget();

        /**
         *  autoConnect  为false  立刻发起一次连接
         * 为true  自动连接，只要蓝牙设备变得可用
         * 一般用false更快
         *
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            bluetoothGatt = bleDevice.connectGatt(appContext,
                    false, coreGattCallback, TRANSPORT_LE);
        } else {
            bluetoothGatt = bleDevice.connectGatt(appContext,
                    false, coreGattCallback);
        }
        if (bluetoothGatt != null) {
            //发送连接成功的消息
            mHandler.obtainMessage(BleBluetoothState.MESSAGE_STATE_CHANGE, BleBluetoothState.STATE_CONNECTED, -1).sendToTarget();
            return true;
        } else {
            disconnectGatt();
            refreshDeviceCache();
            closeBluetoothGatt();
        }
        mHandler.obtainMessage(BleBluetoothState.MESSAGE_STATE_CHANGE, BleBluetoothState.STATE_CONNECT_FAILED, -1).sendToTarget();


        return false;
    }

    private void reset() {
        disconnectGatt();
        refreshDeviceCache();
        closeBluetoothGatt();
        bluetoothGatt = null;
    }

    private synchronized void disconnectGatt() {
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
        }
    }

    private synchronized void refreshDeviceCache() {
        try {
            final Method refresh = BluetoothGatt.class.getMethod("refresh");
            if (refresh != null && bluetoothGatt != null) {
                boolean success = (Boolean) refresh.invoke(bluetoothGatt);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private synchronized void closeBluetoothGatt() {
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
        }
    }


    private boolean openCommand(CommandUUID commandUUID) {
        BluetoothGattService service = null;
        BluetoothGattCharacteristic characteristic = null;

        for (BluetoothGattService servicesub : bluetoothGatt.getServices()) {
            if (commandUUID.getSERVICE_UUID().equals(servicesub.getUuid().toString())) {
                service = servicesub;
            }
        }
        if (null != service) {
            for (BluetoothGattCharacteristic characteristicSub : service.getCharacteristics()) {
                if (commandUUID.getCHARACTER_UUID().equals(characteristicSub.getUuid().toString())) {
                    characteristic = characteristicSub;
                }
            }
        }
        if (service != null && characteristic != null) {
            return enableCharacteristicNotify(characteristic, false);
        } else {
            //
        }
        return false;
    }

    private boolean closeCommand(CommandUUID commandUUID) {

        BluetoothGattService service = null;
        BluetoothGattCharacteristic characteristic = null;

        for (BluetoothGattService servicesub : bluetoothGatt.getServices()) {
            if (commandUUID.getSERVICE_UUID().equals(servicesub.getUuid().toString())) {
                service = servicesub;
            }
        }
        if (null != service) {
            for (BluetoothGattCharacteristic characteristicSub : service.getCharacteristics()) {
                if (commandUUID.getCHARACTER_UUID().equals(characteristicSub.getUuid().toString())) {
                    characteristic = characteristicSub;
                }
            }
        }

        return disableCharacteristicNotify(characteristic, false);
    }

    private BluetoothGattCallback coreGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            bluetoothGatt = gatt;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                bluetoothGatt.discoverServices();
                mHandler.obtainMessage(BleBluetoothState.MESSAGE_STATE_CHANGE, BleBluetoothState.STATE_CONNECTED, -1).sendToTarget();

            } else if (newState == BluetoothProfile.STATE_CONNECTING) {
                mHandler.obtainMessage(BleBluetoothState.MESSAGE_STATE_CHANGE, BleBluetoothState.STATE_CONNECTING, -1).sendToTarget();


            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mHandler.obtainMessage(BleBluetoothState.MESSAGE_STATE_CHANGE, BleBluetoothState.STATE_DISCONNECTED, -1).sendToTarget();

            } else if (newState == BluetoothProfile.STATE_DISCONNECTING) {

            }
            LogUtil.d("onConnectionStateChange");

        }

        @Override
        public void onPhyUpdate(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
            super.onPhyUpdate(gatt, txPhy, rxPhy, status);
            LogUtil.d("onPhyUpdate");
//        mHandler.obtainMessage(BleBluetoothState.MESSAGE_PROCESS_OVER, -1, -1).sendToTarget();
        }

        @Override
        public void onPhyRead(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
            super.onPhyRead(gatt, txPhy, rxPhy, status);
            LogUtil.d("onPhyRead");
//        mHandler.obtainMessage(BleBluetoothState.MESSAGE_PROCESS_OVER, -1, -1).sendToTarget();
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            bluetoothGatt = gatt;
            // 发送一个发现service的响应
            ArrayList<String> serviceUUIDs = new ArrayList<>();
            for (BluetoothGattService service : gatt.getServices()) {
                serviceUUIDs.add(service.getUuid().toString());
            }
            Message msg = mHandler.obtainMessage(BleBluetoothState.MESSAGE_SERVICE_DISCOVERED);
            Bundle bundle = new Bundle();
            bundle.putStringArrayList(BleBluetoothState.SERVICE_UUIDS, serviceUUIDs);
            msg.setData(bundle);
            mHandler.sendMessage(msg);

            LogUtil.d("onServicesDiscovered");


        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            LogUtil.d("onCharacteristicRead");
            //        mHandler.obtainMessage(BleBluetoothState.MESSAGE_PROCESS_OVER, -1, -1).sendToTarget();

        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            LogUtil.d("onCharacteristicWrite");

            //        mHandler.obtainMessage(BleBluetoothState.MESSAGE_PROCESS_OVER, -1, -1).sendToTarget();

        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            LogUtil.d("onCharacteristicChanged");
            //发送连接成功的消息
            BleCommand command = BleCommandUtil.parseReceiveCommand(characteristic.getUuid().toString(), characteristic.getValue());
            mHandler.obtainMessage(BleBluetoothState.MESSAGE_RECEIVE, command).sendToTarget();
            //        mHandler.obtainMessage(BleBluetoothState.MESSAGE_PROCESS_OVER, -1, -1).sendToTarget();
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorRead(gatt, descriptor, status);
            LogUtil.d("onDescriptorRead");

        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            LogUtil.d("onDescriptorWrite");
            BleRequest request = mCurrentRequest;
            if (request.type == BleRequest.RequestType.OPEN_COMMAND_NOTIFY
//                    || request.type == BleRequest.RequestType.CHARACTERISTIC_INDICATION
                    || request.type == BleRequest.RequestType.CLOSE_COMMAND_NOTIFY) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    requestProcessed(BleRequest.RequestType.OPEN_COMMAND_NOTIFY, false);
                    return;
                }
                if (request.type == BleRequest.RequestType.OPEN_COMMAND_NOTIFY) {
                    requestProcessed(BleRequest.RequestType.OPEN_COMMAND_NOTIFY, true);

                } else if (request.type == BleRequest.RequestType.CLOSE_COMMAND_NOTIFY) {
                    requestProcessed(BleRequest.RequestType.CLOSE_COMMAND_NOTIFY, true);
                }
                return;
            }
        }

        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            super.onReliableWriteCompleted(gatt, status);
            LogUtil.d("onReliableWriteCompleted");

        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
            LogUtil.d("onReadRemoteRssi");

        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            LogUtil.d("onMtuChanged");

        }
    };

    /**
     * 对ble对处理
     */
    /**
     * notify
     */
    public boolean enableCharacteristicNotify(BluetoothGattCharacteristic characteristic, boolean userCharacteristicDescriptor) {
        if (characteristic != null
                && (characteristic.getProperties() | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
            return setCharacteristicNotification(bluetoothGatt, characteristic, userCharacteristicDescriptor, true);
        } else {
            mHandler.obtainMessage(BleBluetoothState.MESSAGE_STATE_CHANGE, BleBluetoothState.NOTIFY_FAILED, -1).sendToTarget();
            return false;
        }
    }

    /**
     * stop notify
     */
    public boolean disableCharacteristicNotify(BluetoothGattCharacteristic characteristic, boolean useCharacteristicDescriptor) {
        if (characteristic != null
                && (characteristic.getProperties() | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
            return setCharacteristicNotification(bluetoothGatt, characteristic,
                    useCharacteristicDescriptor, false);
        } else {
            return false;
        }
    }

    /**
     * notify setting
     */
    private boolean setCharacteristicNotification(BluetoothGatt gatt,
                                                  BluetoothGattCharacteristic characteristic,
                                                  boolean useCharacteristicDescriptor,
                                                  boolean enable) {
        if (gatt == null || characteristic == null) {
            mHandler.obtainMessage(BleBluetoothState.MESSAGE_STATE_CHANGE, BleBluetoothState.NOTIFY_FAILED, -1).sendToTarget();
            return false;
        }

        boolean success1 = gatt.setCharacteristicNotification(characteristic, enable);
        if (!success1) {
            mHandler.obtainMessage(BleBluetoothState.MESSAGE_STATE_CHANGE, BleBluetoothState.NOTIFY_FAILED, -1).sendToTarget();
            return false;
        }

        BluetoothGattDescriptor descriptor;
        if (useCharacteristicDescriptor) {
            descriptor = characteristic.getDescriptor(characteristic.getUuid());
        } else {
            descriptor = characteristic.getDescriptor(formUUID(UUID_CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR));
        }
        if (descriptor == null) {
            mHandler.obtainMessage(BleBluetoothState.MESSAGE_STATE_CHANGE, BleBluetoothState.NOTIFY_FAILED, -1).sendToTarget();
            return false;
        } else {
            descriptor.setValue(enable ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE :
                    BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
            boolean success2 = gatt.writeDescriptor(descriptor);
            if (!success2) {
                mHandler.obtainMessage(BleBluetoothState.MESSAGE_STATE_CHANGE, BleBluetoothState.NOTIFY_FAILED, -1).sendToTarget();
            }
            return success2;
        }
    }

    private UUID formUUID(String uuid) {
        return uuid == null ? null : UUID.fromString(uuid);
    }
}

