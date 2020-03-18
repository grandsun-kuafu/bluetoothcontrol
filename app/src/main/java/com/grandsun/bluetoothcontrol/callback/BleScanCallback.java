package com.grandsun.bluetoothcontrol.callback;

import android.bluetooth.BluetoothDevice;

import java.util.List;

public abstract class BleScanCallback implements BleScanPresenterImp {

    public abstract void onScanFinished(List<BluetoothDevice> scanResultList);

    public void onLeScan(BluetoothDevice bleDevice) {
    }
}
