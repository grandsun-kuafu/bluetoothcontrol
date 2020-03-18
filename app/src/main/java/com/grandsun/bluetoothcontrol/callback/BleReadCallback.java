package com.grandsun.bluetoothcontrol.callback;

import com.grandsun.bluetoothcontrol.exception.BleException;

public abstract class BleReadCallback extends BleBaseCallback {

    public abstract void onReadSuccess(byte[] data);

    public abstract void onReadFailure(BleException exception);

}