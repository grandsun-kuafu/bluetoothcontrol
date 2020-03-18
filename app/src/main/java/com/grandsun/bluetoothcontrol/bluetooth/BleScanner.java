package com.grandsun.bluetoothcontrol.bluetooth;

import android.bluetooth.BluetoothDevice;

import com.grandsun.bluetoothcontrol.BleManager;
import com.grandsun.bluetoothcontrol.bluetooth.data.BleScanState;
import com.grandsun.bluetoothcontrol.callback.BleScanCallback;
import com.grandsun.bluetoothcontrol.callback.BleScanPresenterImp;
import com.grandsun.bluetoothcontrol.command.Command;
import com.grandsun.bluetoothcontrol.utils.BleLog;

import java.util.List;
import java.util.UUID;


public class BleScanner {

    private BleScanner() {
    }

    public static BleScanner getInstance() {
        return BleScannerHolder.sBleScanner;
    }

    private BleScanState mBleScanState = BleScanState.STATE_IDLE;


    private static class BleScannerHolder {
        private static final BleScanner sBleScanner = new BleScanner();
    }

    private BleScanPresenter mBleScanPresenter = new BleScanPresenter() {

        @Override
        public void onScanStarted(boolean success) {
            BleScanPresenterImp callback = mBleScanPresenter.getBleScanPresenterImp();
            if (callback != null) {
                callback.onScanStarted(success);
            }
        }

        @Override
        public void onLeScan(BluetoothDevice bleDevice) {
//            if (mBleScanPresenter.ismNeedConnect()) {
//                BleScanAndConnectCallback callback = (BleScanAndConnectCallback)
//                        mBleScanPresenter.getBleScanPresenterImp();
//                if (callback != null) {
//                    callback.onLeScan(bleDevice);
//                }
//            } else {
            BleScanCallback callback = (BleScanCallback) mBleScanPresenter.getBleScanPresenterImp();
            if (callback != null) {
                callback.onLeScan(bleDevice);
            }
//            }
        }

        @Override
        public void onScanning(BluetoothDevice result) {
            BleScanPresenterImp callback = mBleScanPresenter.getBleScanPresenterImp();
            if (callback != null) {
                callback.onScanning(result);
            }
        }

        @Override
        public void onScanFinished(List<BluetoothDevice> bleDeviceList) {
            BleScanCallback callback = (BleScanCallback) mBleScanPresenter.getBleScanPresenterImp();
            if (callback != null) {
                callback.onScanFinished(bleDeviceList);
            }
//            if (mBleScanPresenter.ismNeedConnect()) {
////                final BleScanAndConnectCallback callback = (BleScanAndConnectCallback)
////                        mBleScanPresenter.getBleScanPresenterImp();
//                if (bleDeviceList == null || bleDeviceList.size() < 1) {
//                    if (callback != null) {
//                        callback.onScanFinished(null);
//                    }
//                } else {
//                    if (callback != null) {
//                        callback.onScanFinished(bleDeviceList.get(0));
//                    }
//                    final List<BluetoothDevice> list = bleDeviceList;
//                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
//                        @Override
//                        public void run() {
//                            BleManager.getInstance().connect(list.get(0), callback);
//                        }
//                    }, 100);
//                }
//            } else {
//
//            }
        }
    };

    public void scan(boolean needConnect, BleScanCallback callback) {
        startLeScan(needConnect, callback);
    }

    private synchronized void startLeScan(boolean needConnect, BleScanPresenterImp imp) {

        if (mBleScanState != BleScanState.STATE_IDLE) {
            BleLog.w("scan action already exists, complete the previous scan action first");
            if (imp != null) {
                imp.onScanStarted(false);
            }
            return;
        }

        mBleScanPresenter.prepare(needConnect, imp);
        UUID[] uuids = {UUID.fromString(Command.TEMPERATURE_AND_HEARTRATE.getSERVICE_UUID())};
        boolean success = BleManager.getInstance().getBluetoothAdapter()
                .startLeScan(uuids, mBleScanPresenter);
        mBleScanState = success ? BleScanState.STATE_SCANNING : BleScanState.STATE_IDLE;
        mBleScanPresenter.notifyScanStarted(success);
    }

    public synchronized void stopLeScan() {
        BleManager.getInstance().getBluetoothAdapter().stopLeScan(mBleScanPresenter);
        mBleScanState = BleScanState.STATE_IDLE;
        mBleScanPresenter.notifyScanStopped();
    }

}
