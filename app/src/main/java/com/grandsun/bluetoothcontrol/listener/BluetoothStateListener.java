package com.grandsun.bluetoothcontrol.listener;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;

import com.grandsun.bluetoothcontrol.exception.BleException;

public abstract class BluetoothStateListener extends BluetoothGattCallback {

    public abstract void onStartConnect();

    public abstract void onConnectFail(BluetoothDevice bleDevice, BleException exception);

    public abstract void onConnectSuccess(BluetoothDevice bleDevice, BluetoothGatt gatt, int status);

    public abstract void onDisConnected( BluetoothDevice device, BluetoothGatt gatt, int status);

}