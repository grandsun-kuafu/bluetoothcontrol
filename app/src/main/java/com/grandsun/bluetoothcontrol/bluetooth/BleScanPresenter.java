package com.grandsun.bluetoothcontrol.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import com.grandsun.bluetoothcontrol.bluetooth.data.BleMsg;
import com.grandsun.bluetoothcontrol.callback.BleScanPresenterImp;
import com.grandsun.bluetoothcontrol.utils.BleLog;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class BleScanPresenter implements BluetoothAdapter.LeScanCallback {

//    private String[] mDeviceNames;
//    private String mDeviceMac;
//    private boolean mFuzzy;
    private boolean mNeedConnect;//暂时不需要自动连接
    private long mScanTimeout;
    private BleScanPresenterImp mBleScanPresenterImp;

    private List<BluetoothDevice> mBleDeviceList = new ArrayList<>();

    private Handler mMainHandler = new Handler(Looper.getMainLooper());
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private boolean mHandling;

    private static final class ScanHandler extends Handler {

        private final WeakReference<BleScanPresenter> mBleScanPresenter;

        ScanHandler(Looper looper, BleScanPresenter bleScanPresenter) {
            super(looper);
            mBleScanPresenter = new WeakReference<>(bleScanPresenter);
        }

        @Override
        public void handleMessage(Message msg) {
            BleScanPresenter bleScanPresenter = mBleScanPresenter.get();
            if (bleScanPresenter != null) {
                if (msg.what == BleMsg.MSG_SCAN_DEVICE) {
                    final BluetoothDevice bleDevice = (BluetoothDevice) msg.obj;
                    if (bleDevice != null) {
                        bleScanPresenter.handleResult(bleDevice);
                    }
                }
            }
        }
    }

    private void handleResult(final BluetoothDevice bleDevice) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                onLeScan(bleDevice);
            }
        });
        checkDevice(bleDevice);
    }

    public void prepare(boolean needConnect, BleScanPresenterImp bleScanPresenterImp) {
//        mDeviceNames = names;
//        mDeviceMac = mac;
//        mFuzzy = fuzzy;
        mNeedConnect = needConnect;
//        mScanTimeout = timeOut;
        mBleScanPresenterImp = bleScanPresenterImp;

        mHandlerThread = new HandlerThread(BleScanPresenter.class.getSimpleName());
        mHandlerThread.start();
        mHandler = new ScanHandler(mHandlerThread.getLooper(), this);
        mHandling = true;
    }

    public boolean ismNeedConnect() {
        return mNeedConnect;
    }

    public BleScanPresenterImp getBleScanPresenterImp() {
        return mBleScanPresenterImp;
    }

    @Override
    public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
        if (device == null)
            return;

        if (!mHandling)
            return;

        Message message = mHandler.obtainMessage();
        message.what = BleMsg.MSG_SCAN_DEVICE;
        message.obj = device;
        mHandler.sendMessage(message);
    }

    private void checkDevice(BluetoothDevice bleDevice) {
        //不刷选设备
//        if (TextUtils.isEmpty(mDeviceMac) && (mDeviceNames == null || mDeviceNames.length < 1)) {
//            correctDeviceAndNextStep(bleDevice);
//            return;
//        }
//
//        if (!TextUtils.isEmpty(mDeviceMac)) {
//            if (!mDeviceMac.equalsIgnoreCase(bleDevice.getAddress()))
//                return;
//        }
//
//        if (mDeviceNames != null && mDeviceNames.length > 0) {
//            AtomicBoolean equal = new AtomicBoolean(false);
//            for (String name : mDeviceNames) {
//                String remoteName = bleDevice.getName();
//                if (remoteName == null)
//                    remoteName = "";
//                if (mFuzzy ? remoteName.contains(name) : remoteName.equals(name)) {
//                    equal.set(true);
//                }
//            }
//            if (!equal.get()) {
//                return;
//            }
//        }

        correctDeviceAndNextStep(bleDevice);
    }


    private void correctDeviceAndNextStep(final BluetoothDevice bleDevice) {
        if (mNeedConnect) {
            BleLog.i("devices detected  ------"
                    + "  name:" + bleDevice.getName()
                    + "  mac:" + bleDevice.getAddress());

            mBleDeviceList.add(bleDevice);
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    BleScanner.getInstance().stopLeScan();
                }
            });

        } else {
            AtomicBoolean hasFound = new AtomicBoolean(false);
            for (BluetoothDevice result : mBleDeviceList) {
                if (result.equals(bleDevice)) {
                    hasFound.set(true);
                }
            }
            if (!hasFound.get()) {
                BleLog.i("device detected  ------"
                        + "  name: " + bleDevice.getName()
                        + "  mac: " + bleDevice.getAddress());

                mBleDeviceList.add(bleDevice);
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        onScanning(bleDevice);
                    }
                });
            }
        }
    }

    public final void notifyScanStarted(final boolean success) {
        mBleDeviceList.clear();

        removeHandlerMsg();

        if (success && mScanTimeout > 0) {
            mMainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    BleScanner.getInstance().stopLeScan();
                }
            }, mScanTimeout);
        }

        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                onScanStarted(success);
            }
        });
    }

    public final void notifyScanStopped() {
        mHandling = false;
        mHandlerThread.quit();
        removeHandlerMsg();
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                onScanFinished(mBleDeviceList);
            }
        });
    }

    public final void removeHandlerMsg() {
        mMainHandler.removeCallbacksAndMessages(null);
        mHandler.removeCallbacksAndMessages(null);
    }

    public abstract void onScanStarted(boolean success);

    public abstract void onLeScan(BluetoothDevice bleDevice);

    public abstract void onScanning(BluetoothDevice bleDevice);

    public abstract void onScanFinished(List<BluetoothDevice> bleDeviceList);
}
