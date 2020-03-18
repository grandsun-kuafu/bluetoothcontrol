package com.grandsun.bluetoothcontrol.callback;

import com.grandsun.bluetoothcontrol.exception.BleException;

public abstract class BleMtuChangedCallback extends BleBaseCallback {

    public abstract void onSetMTUFailure(BleException exception);

    public abstract void onMtuChanged(int mtu);

}
