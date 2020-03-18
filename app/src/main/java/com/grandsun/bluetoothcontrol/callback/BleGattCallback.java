package com.grandsun.bluetoothcontrol.callback;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;

import com.grandsun.bluetoothcontrol.exception.BleException;

public abstract class BleGattCallback extends BluetoothGattCallback {

    public abstract void onStartConnect();

    public abstract void onConnectFail(BluetoothDevice bleDevice, BleException exception);

    public abstract void onConnectSuccess(BluetoothDevice bleDevice, BluetoothGatt gatt, int status);

    public abstract void onDisConnected(boolean isActiveDisConnected, BluetoothDevice device, BluetoothGatt gatt, int status);

}