package com.grandsun.bluetoothcontrol;

import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Message;

import androidx.annotation.NonNull;

import com.grandsun.bluetoothcontrol.utils.LogUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public class BleManager {

    private BluetoothManager bluetoothManager;

    private BluetoothAdapter mBtAdapter;
    private BleBluetoothService mService;
    private MyHandler mHandler;
    private List<BluetoothStateListener> listenerList = new ArrayList<>();

    private static BleManager bleManager;
    private Application app;

    private String prodId;

    private String uid;

    private BleManager() {
    }

    public synchronized static BleManager getInstance() {
        if (bleManager == null) {
            bleManager = new BleManager();
        }
        return bleManager;
    }

    private boolean isConnecting = false;

    private int getConnectState(BluetoothDevice bleDevice) {
        if (bleDevice != null) {
            return bluetoothManager.getConnectionState(bleDevice, BluetoothProfile.GATT);
        } else {
            LogUtil.d("bleDevice is null");
            return BluetoothProfile.STATE_DISCONNECTED;
        }
    }

    public boolean isConnected() {
        return getConnectState(mService.bluetoothDevice) == BluetoothProfile.STATE_CONNECTED;
    }

    class MyHandler extends Handler {
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case BleBluetoothState.MESSAGE_RECEIVE:
                    for (BluetoothStateListener listener : listenerList) {
                        listener.receiveCommand((BleCommand) msg.obj);
                    }
                    break;
                //发送指令
                case BleBluetoothState.MESSAGE_STATE_CHANGE:
                    if (msg.arg1 == BleBluetoothState.STATE_CONNECTED) {
                        String name = msg.getData().getString(BleBluetoothState.DEVICE_NAME);
                        String address = msg.getData().getString(BleBluetoothState.DEVICE_ADDRESS);
                        for (BluetoothStateListener listener : listenerList) {
                            listener.bleConnected(name, address);
                        }
                        mService.requestProcessed(BleRequest.RequestType.CONNECT_GATT, true);
                    } else if (msg.arg1 == BleBluetoothState.STATE_DISCONNECTED) {
                        for (BluetoothStateListener listener : listenerList) {
                            listener.bleDisconnected();
                        }
                        mService.requestProcessed(BleRequest.RequestType.RESET, false);
                    } else if (msg.arg1 == BleBluetoothState.STATE_CONNECT_FAILED) {
                        for (BluetoothStateListener listener : listenerList) {
                            listener.bleConnectFailed();
                        }
                        mService.requestProcessed(BleRequest.RequestType.CONNECT_GATT, false);
                    }
                    break;
                case BleBluetoothState.MESSAGE_SERVICE_DISCOVERED:
                    List<String> uuis = msg.getData().getStringArrayList(BleBluetoothState.SERVICE_UUIDS);
                    for (BluetoothStateListener listener : listenerList) {
                        listener.serviceDiscovered();
                    }
                    break;
                case BleBluetoothState.MESSAGE_STOP_NOTIFY:
                    mService.requestProcessed(BleRequest.RequestType.CLOSE_COMMAND_NOTIFY, true);
                case BleBluetoothState.MESSAGE_TIME_OUT:
                    break;
            }
        }
    }

    public BleManager autoConnect() {
//        this.bleGattCallback = bleGattCallback;
        //看现在是否已经连上了经典蓝牙，而且ble蓝牙没连
        // 这时获取所有蓝牙设备的方法
        mBtAdapter.getProfileProxy(this.app, new BluetoothProfile.ServiceListener() {
            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                List<BluetoothDevice> mDevices = proxy.getConnectedDevices();
                List<String> addressList = new ArrayList<>();
                if (mDevices != null && mDevices.size() > 0) {
                    for (BluetoothDevice device : mDevices) {
                        if (device != null) {
                            addressList.add(device.getAddress());
                        }
                    }
                }
                if (addressList.size() > 0)
                    autoConnectDo(addressList);
            }

            @Override
            public void onServiceDisconnected(int profile) {

            }
        }, BluetoothProfile.HEADSET);

        return this;
    }

    private void autoConnectDo(final List<String> addressList) {
        //扫描所有ble设备尝试连接,暂时用体温uuid过滤一下
        mBtAdapter.startLeScan(new UUID[]{UUID.fromString(CommandUUID.TEMPERATURE_AND_HEARTRATE.getSERVICE_UUID())
        }, new BluetoothAdapter.LeScanCallback() {

            @Override
            public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                LogUtil.d("found device:" + device.getAddress());
                //例子 hrm的mac为F4:0E:11:72:FC:54
                //    耳机经典蓝牙mac为F4:0E:11:72:03:AB
                if (addressList.get(0).startsWith(device.getAddress().substring(0, 7)) && !addressList.get(0).equals(device.getAddress())) {
                    BleManager.getInstance().connect(device.getAddress());
                }
            }

        });
    }


    public Application getApplication() {
        return this.app;
    }

    /**
     * 初始化
     */
    public void init(Application application) {
        this.app = application;
        if (isSupportBle()) {
            bluetoothManager = (BluetoothManager) app.getSystemService(Context.BLUETOOTH_SERVICE);
        }
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        app.registerReceiver(mReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
        app.registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));
        app.registerReceiver(
                mReceiver,
                new IntentFilter("android.bluetooth.a2dp.profile.action.ACTIVE_DEVICE_CHANGED")
        );
        app.registerReceiver(mReceiver, new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED));
        app.registerReceiver(mReceiver, new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED));
        app.registerReceiver(mReceiver, new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED));
        mHandler = new MyHandler();
        mService = new BleBluetoothService(app, mHandler);
        //把定时任务的监听加入进去
        addListener(CommandTask.listener);
        CommandTask.startUpTask();
    }

    public boolean isSupportBle() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2
                && app.getApplicationContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }


    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            if (BluetoothDevice.ACTION_FOUND == action) {
                // 发现新设备

                for (BluetoothStateListener listener : listenerList) {
                    listener.foundDevice(device);
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED == action) {
                // 蓝牙搜索结束
                for (BluetoothStateListener listener : listenerList) {
                    listener.discoveryFinish();
                }
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED == action) {
                // 绑定状态改变
                int state =
                        intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);

                for (BluetoothStateListener listener : listenerList) {
                    listener.onBondStateChanged(state, device);
                }
            } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                // 有设备连接
                String state = BluetoothDevice.ACTION_ACL_CONNECTED;
                for (BluetoothStateListener listener : listenerList) {
                    listener.onConnectionStateChanged(state, device);
                }
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                // 有设备断开连接
                String state = BluetoothDevice.ACTION_ACL_DISCONNECTED;
                for (BluetoothStateListener listener : listenerList) {
                    listener.onConnectionStateChanged(state, device);
                }
            } else if ("android.bluetooth.a2dp.profile.action.ACTIVE_DEVICE_CHANGED" == action) {
                // A2DP活跃设备发生变化
//                EventBus.getDefault().post(A2DPActiveDeviceChanged());
            }
        }
    };


    /**
     * 绑定数据到一个proid和uid
     */
    public void updateBleInfo(String prodId, String uid) {
        this.prodId = prodId;
        this.uid = uid;
    }

    /**
     * 连接设备
     */
    public void connect(String address) {
        BluetoothDevice device = mBtAdapter.getRemoteDevice(address);
        LogUtil.d("device type:" + (device.getType() == BluetoothDevice.DEVICE_TYPE_CLASSIC ? "CLASSIC" :
                device.getType() == BluetoothDevice.DEVICE_TYPE_LE ? "Low Energy device" :
                        device.getType() == BluetoothDevice.DEVICE_TYPE_DUAL ? "DUAL device" ://双模式
                                "Unknown device"));

        mService.addBleRequest(new BleRequest(BleRequest.RequestType.CONNECT_GATT, device));

    }

    /**
     * 断开连接
     */
    public void disconnect() {
        if (mService != null) {
            mService.addBleRequest(new BleRequest(BleRequest.RequestType.RESET, mService.bluetoothDevice));
        }
    }

    /**
     * 是否app主动打开了命令
     */
    private boolean isAppOpenCommand = false;

    /**
     * 发送指令
     *
     * @param
     * @param
     */
    public void openComand(CommandUUID commandUUID) {
        if (isConnected()) {
            isAppOpenCommand = true;
            mService.addBleRequest(new BleRequest(BleRequest.RequestType.OPEN_COMMAND_NOTIFY, mService.bluetoothDevice, commandUUID));
        }
    }

    /**
     * 发送指令
     *
     * @param
     * @param
     */
    public void closeComand(CommandUUID commandUUID) {
        if (isConnected()) {
            mService.addBleRequest(new BleRequest(BleRequest.RequestType.CLOSE_COMMAND_NOTIFY, mService.bluetoothDevice, commandUUID));
            isAppOpenCommand = false;
        }
    }


    public void openComandForTask(CommandUUID commandUUID) {
        if (isConnected()) {
            mService.addBleRequest(new BleRequest(BleRequest.RequestType.OPEN_COMMAND_NOTIFY, mService.bluetoothDevice, commandUUID));
        }
    }

    public void closeComandForTask(CommandUUID commandUUID) {
        if (isConnected()) {
            if (!isAppOpenCommand) {//app调用没有主动打开则关闭
                mService.addBleRequest(new BleRequest(BleRequest.RequestType.CLOSE_COMMAND_NOTIFY, mService.bluetoothDevice, commandUUID));
            }
        }
    }

    /**
     * 添加监听器
     */
    public void addListener(BluetoothStateListener listener) {
        if (!listenerList.contains(listener)) {
            listenerList.add(listener);
        }
        LogUtil.d("addListener end");
    }

    /**
     * 移除监听器
     */
    public void removeListener(BluetoothStateListener listener) {
        if (listenerList.contains(listener)) {
            listenerList.remove(listener);
        }
    }

    public interface BluetoothStateListener {
        void foundDevice(BluetoothDevice device);

        void discoveryFinish();

        void bleConnected(String name, String address);

        void bleConnectFailed();

        void bleDisconnected();

        void receiveCommand(BleCommand command);

        void onConnectionStateChanged(String state, BluetoothDevice device);

        void onBondStateChanged(int state, BluetoothDevice device);

        void serviceDiscovered();
    }
}
