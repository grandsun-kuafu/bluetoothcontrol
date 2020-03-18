package com.grandsun.bluetoothcontrol;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.grandsun.bluetoothcontrol.bluetooth.BleBluetooth;
import com.grandsun.bluetoothcontrol.bluetooth.BleScanner;
import com.grandsun.bluetoothcontrol.callback.BleGattCallback;
import com.grandsun.bluetoothcontrol.callback.BleScanCallback;
import com.grandsun.bluetoothcontrol.command.CommandTask;
import com.grandsun.bluetoothcontrol.exception.BleException;
import com.grandsun.bluetoothcontrol.exception.OtherException;
import com.grandsun.bluetoothcontrol.utils.BleLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BleManager {

    private Activity context;

    private BleController bleController;

    private BluetoothManager bluetoothManager;

    private BluetoothAdapter bluetoothAdapter;


    private String productId;
    private String uid;


    private static final int DEFAULT_MAX_MULTIPLE_DEVICE = 7;
    private static final int DEFAULT_CONNECT_OVER_TIME = 10000;
    private static final int DEFAULT_CONNECT_RETRY_INTERVAL = 5000;
    private static final int DEFAULT_CONNECT_RETRY_COUNT = 0;
    private static final int DEFAULT_OPERATE_TIME = 5000;

    //两个权限申请
    private static final int REQUEST_CODE_PERMISSION_LOCATION = 2;
    private static final int REQUEST_CODE_OPEN_GPS = 1;
    private int maxConnectCount = DEFAULT_MAX_MULTIPLE_DEVICE;
    private long connectOverTime = DEFAULT_CONNECT_OVER_TIME;
    private long reConnectInterval = DEFAULT_CONNECT_RETRY_INTERVAL;
    private int reConnectCount = DEFAULT_CONNECT_RETRY_COUNT;
    private int operateTimeout = DEFAULT_OPERATE_TIME;

    public String getProductId() {
        return productId;
    }

    public String getUid() {
        return uid;
    }


    private BleGattCallback bleGattCallback = new BleGattCallback() {
        @Override
        public void onStartConnect() {

        }

        @Override
        public void onConnectFail(BluetoothDevice bleDevice, BleException exception) {

        }

        @Override
        public void onConnectSuccess(BluetoothDevice bleDevice, BluetoothGatt gatt, int status) {

        }

        @Override
        public void onDisConnected(boolean isActiveDisConnected, BluetoothDevice device, BluetoothGatt gatt, int status) {

        }
    };

    public BleController getBleController() {
        return bleController;
    }

    private BleManager() {
    }

    private static final BleManager bleManager = new BleManager();

    public static BleManager getInstance() {
        return bleManager;
    }


    /**
     * 外部访问的
     *
     * @param app
     */
    public BleManager init(Activity app, String productId, String uid) {
        if (context == null && app != null) {
            context = app;
            this.productId = productId;
            this.uid = uid;
            if (isSupportBle()) {
                bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
            }
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            checkPermissions();

            //注册蓝牙监听
            registerBoradcastReceiver();
        }
        return this;
    }

    public void updateBleInfo(String productId, String uid) {
        this.productId = productId;
        this.uid = uid;
    }

    public BleManager autoConnect() {
//        this.bleGattCallback = bleGattCallback;
        //看现在是否已经连上了经典蓝牙，而且ble蓝牙没连
        if (bleController == null && isBTConnected()) {
            // 这时获取所有蓝牙设备的方法
            bluetoothAdapter.getProfileProxy(getContext(), new BluetoothProfile.ServiceListener() {
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
                    autoConnectDo(addressList);
                }

                @Override
                public void onServiceDisconnected(int profile) {

                }
            }, BluetoothProfile.HEADSET);
        }
        return this;
    }

    private void autoConnectDo(final List<String> addressList) {
        //扫描所有ble设备尝试连接
        startScan(true, new BleScanCallback() {
            @Override
            public void onScanFinished(final List<BluetoothDevice> scanResultList) {
                for (final BluetoothDevice device : scanResultList) {
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            for (String address : addressList) {
                                //例子 hrm的mac为F4:0E:11:72:FC:54
                                //    耳机经典蓝牙mac为F4:0E:11:72:03:AB
                                if (address.startsWith(device.getAddress().substring(0, 7))) {
                                    BleManager.getInstance().connect(device, bleGattCallback);
                                    break;
                                }
                            }
                        }
                    }, 100);
                }
            }

            @Override
            public void onScanStarted(boolean success) {
            }

            @Override
            public void onScanning(BluetoothDevice bleDevice) {
            }
        });
    }

    private void registerBoradcastReceiver() {
        //注册监听
        IntentFilter stateChangeFilter = new IntentFilter(
                BluetoothAdapter.ACTION_STATE_CHANGED);
        IntentFilter connectedFilter = new IntentFilter(
                BluetoothDevice.ACTION_ACL_CONNECTED);
        IntentFilter disConnectedFilter = new IntentFilter(
                BluetoothDevice.ACTION_ACL_DISCONNECTED);
        context.registerReceiver(stateChangeReceiver, stateChangeFilter);
        context.registerReceiver(stateChangeReceiver, connectedFilter);
        context.registerReceiver(stateChangeReceiver, disConnectedFilter);
    }

    private boolean isBTConnected() {
        BluetoothAdapter blueadapter = BluetoothAdapter.getDefaultAdapter();
//adapter也有getState(), 可获取ON/OFF...其它状态
        int a2dp = blueadapter.getProfileConnectionState(BluetoothProfile.A2DP);              //可操控蓝牙设备，如带播放暂停功能的蓝牙耳机
        int headset = blueadapter.getProfileConnectionState(BluetoothProfile.HEADSET);        //蓝牙头戴式耳机，支持语音输入输出
        int health = blueadapter.getProfileConnectionState(BluetoothProfile.HEALTH);
        return blueadapter != null && (a2dp == BluetoothAdapter.STATE_CONNECTED ||
                headset == BluetoothAdapter.STATE_CONNECTED ||
                health == BluetoothAdapter.STATE_CONNECTED);
    }


    private BroadcastReceiver stateChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)) {
                //连接上了
//                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                autoConnect();
            } else if (action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED)) {
                //蓝牙连接被切断
//                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                disconnect();
                return;
            }
        }
    };


    public Set<BluetoothDevice> getBondedDevices() {
        return bluetoothAdapter.getBondedDevices();
    }

    public void startScan(boolean needConnect, BleScanCallback bleScanCallback) {
        scan(needConnect, bleScanCallback);
    }

    public BleController connect(BluetoothDevice bleDevice, BleGattCallback bleGattCallback) {
        if (bleGattCallback == null) {
            throw new IllegalArgumentException("BleGattCallback can not be Null!");
        }

        if (!isBlueEnable()) {
            BleLog.e("Bluetooth not enable!");
            bleGattCallback.onConnectFail(bleDevice, new OtherException("Bluetooth not enable!"));
            return null;
        }

        if (Looper.myLooper() == null || Looper.myLooper() != Looper.getMainLooper()) {
            BleLog.w("Be careful: currentThread is not MainThread!");
        }

        if (bleDevice == null) {
            bleGattCallback.onConnectFail(bleDevice, new OtherException("Not Found Device Exception Occurred!"));
        } else {
            BleBluetooth bleBluetooth = new BleBluetooth(bleDevice);
            bleBluetooth.connect(bleDevice, false, bleGattCallback);
            bleController = new BleController( bleBluetooth);

            //开启定时任务3分钟获取一次，15分钟上传一次
            CommandTask.startReadTask();
            CommandTask.startUpTask();
        }

        return bleController;
    }


    /**
     * 外部访问的
     */


    public BluetoothAdapter getBluetoothAdapter() {
        return bluetoothAdapter;
    }


    /**
     * Get operate connect Over Time
     *
     * @return
     */
    public long getConnectOverTime() {
        return connectOverTime;
    }

    private boolean isSupportBle() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2
                && context.getApplicationContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    private void checkPermissions() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(context, "open bluetooth pls", Toast.LENGTH_LONG).show();
            return;
        }

        String[] permissions = {Manifest.permission.ACCESS_FINE_LOCATION};
        List<String> permissionDeniedList = new ArrayList<>();
        for (String permission : permissions) {
            int permissionCheck = ContextCompat.checkSelfPermission(context, permission);
            if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                onPermissionGranted(permission);
            } else {
                permissionDeniedList.add(permission);
            }
        }
        if (!permissionDeniedList.isEmpty()) {
            String[] deniedPermissions = permissionDeniedList.toArray(new String[permissionDeniedList.size()]);
            ActivityCompat.requestPermissions(context, deniedPermissions, REQUEST_CODE_PERMISSION_LOCATION);
        }
    }

    private void onPermissionGranted(String permission) {
        switch (permission) {
            case Manifest.permission.ACCESS_FINE_LOCATION:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !checkGPSIsOpen()) {
                    new AlertDialog.Builder(context)
                            .setTitle("Notify")
                            .setMessage("open gps pls")
                            .setNegativeButton("cancel",
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            context.finish();
                                        }
                                    })
                            .setPositiveButton("setting",
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                                            context.startActivityForResult(intent, REQUEST_CODE_OPEN_GPS);
                                        }
                                    })

                            .setCancelable(false)
                            .show();
                } else {

                }
                break;
        }
    }

    private boolean checkGPSIsOpen() {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null)
            return false;
        return locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER);
    }


    /**
     * scan device around
     *
     * @param callback
     */
    private void scan(boolean needConnect, BleScanCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("BleScanCallback can not be Null!");
        }

        if (!isBlueEnable()) {
            BleLog.e("Bluetooth not enable!");
            callback.onScanStarted(false);
            return;
        }
        BleScanner.getInstance().scan(needConnect, callback);
    }

    private boolean isBlueEnable() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }


    public int getMaxConnectCount() {
        return maxConnectCount;
    }

    public Context getContext() {
        return this.context;
    }


    public int getReConnectCount() {
        return reConnectCount;
    }

    public long getReConnectInterval() {
        return reConnectInterval;
    }

    public long getOperateTimeout() {
        return operateTimeout;
    }

//    public boolean isConnected(BluetoothDevice bleDevice) {
//        return getConnectState(bleDevice) == BluetoothProfile.STATE_CONNECTED;
//    }

    public void disconnect() {
        if (bleController != null) {
            bleController.bleBluetooth.disconnect();
            bleController = null;
        }
    }

    //    public int getConnectState(BluetoothDevice bleDevice) {
//        if (bleDevice != null) {
//            return bluetoothManager.getConnectionState(bleDevice, BluetoothProfile.GATT);
//        } else {
//            return BluetoothProfile.STATE_DISCONNECTED;
//        }
//    }
    public void cancelScan() {
        BleScanner.getInstance().stopLeScan();
    }

}
