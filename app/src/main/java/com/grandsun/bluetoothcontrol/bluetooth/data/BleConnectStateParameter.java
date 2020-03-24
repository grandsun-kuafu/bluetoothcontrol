package com.grandsun.bluetoothcontrol.bluetooth.data;

public class BleConnectStateParameter {
    private int status;


    public BleConnectStateParameter(int status) {
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

}
