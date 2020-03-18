package com.grandsun.bluetoothcontrol.callback;

import android.bluetooth.BluetoothDevice;

public interface BleScanPresenterImp {

    void onScanStarted(boolean success);

    void onScanning(BluetoothDevice bleDevice);

}
