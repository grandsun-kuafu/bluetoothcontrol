package com.grandsun.bluetoothcontrol.callback;

import com.grandsun.bluetoothcontrol.exception.BleException;

public abstract class BleRssiCallback extends BleBaseCallback{

    public abstract void onRssiFailure(BleException exception);

    public abstract void onRssiSuccess(int rssi);

}
