package com.grandsun.bluetoothcontrol;

public class BleBluetoothState {
    // 当前连接状态
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_CONNECTED = 2;

    public static final int STATE_CONNECT_FAILED= 3;
    public static final int STATE_DISCONNECTED= 4;


    // 消息类型
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_RECEIVE = 2;
    public static final int MESSAGE_STOP_NOTIFY = 3;
    public static final int MESSAGE_SERVICE_DISCOVERED = 4;
    public static final int MESSAGE_TIME_OUT = 5;
    public static final int MESSAGE_REQUEST_FAILED = 6;


    // 请求打开蓝牙
    public static final int REQUEST_ENABLE_BT = 1000;

    public static final String DEVICE_NAME = "device_name";
    public static final String DEVICE_ADDRESS = "device_address";

    public static final String SERVICE_UUIDS = "device_service_uuids";

    /**
     * ble通讯的状态
     */
    public static final int NOTIFY_FAILED = 0;

}
