package com.grandsun.bluetoothcontrol;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
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

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.grandsun.bluetoothcontrol.bluetooth.BleBluetooth;
import com.grandsun.bluetoothcontrol.bluetooth.BleConnector;
import com.grandsun.bluetoothcontrol.command.CommandResult;
import com.grandsun.bluetoothcontrol.listener.BluetoothStateListener;
import com.grandsun.bluetoothcontrol.callback.BleNotifyCallback;
import com.grandsun.bluetoothcontrol.cloud.CommandTask;
import com.grandsun.bluetoothcontrol.cloud.ServiceConstants;
import com.grandsun.bluetoothcontrol.command.Command;
import com.grandsun.bluetoothcontrol.exception.BleException;
import com.grandsun.bluetoothcontrol.listener.CommandListener;
import com.grandsun.bluetoothcontrol.utils.BleLog;
import com.grandsun.bluetoothcontrol.utils.HexUtil;
import com.grandsun.bluetoothcontrol.utils.JsonCacheUtil;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class BleManager {

    private Activity context;

    private BluetoothAdapter bluetoothAdapter;

    private String productId;
    private String uid;


    //两个权限申请
    private static final int REQUEST_CODE_PERMISSION_LOCATION = 2;
    private static final int REQUEST_CODE_OPEN_GPS = 1;
//    private int maxConnectCount = DEFAULT_MAX_MULTIPLE_DEVICE;
//    private long connectOverTime = DEFAULT_CONNECT_OVER_TIME;
//    private long reConnectInterval = DEFAULT_CONNECT_RETRY_INTERVAL;
//    private int reConnectCount = DEFAULT_CONNECT_RETRY_COUNT;
//    private int operateTimeout = DEFAULT_OPERATE_TIME;

    BleBluetooth bleBluetooth;

    private boolean autoConnect;


    public String getProductId() {
        return productId;
    }

    public String getUid() {
        return uid;
    }


    private BleManager() {
    }

    private static final BleManager bleManager = new BleManager();

    public static BleManager getInstance() {
        return bleManager;
    }


    /**
     * 初始化
     *
     * @param app
     * @param
     * @param
     * @return
     */
    public BleManager init(Activity app) {
        if (context == null && app != null) {
            context = app;
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            checkPermissions();
            //注册蓝牙监听
            registerBoradcastReceiver();
        }
        return this;
    }

    public BleManager autoConnect() {
        this.autoConnect = autoConnect;
        autoDoConnect();
        return this;
    }

    public void destroy() {
        if (stateChangeReceiver != null) {
            context.unregisterReceiver(stateChangeReceiver);
        }
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

    private BroadcastReceiver stateChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)) {
                //连接上了
                if (autoConnect)
                    autoDoConnect();
            } else if (action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED)) {
                //蓝牙连接被切断
//                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                disconnect();
                return;
            }
        }
    };

    /**
     * 更新绑定的uid信息
     *
     * @param productId
     * @param uid
     */
    public void updateBleInfo(String productId, String uid) {
        this.productId = productId;
        this.uid = uid;
    }

    /**
     * 自动连接
     *
     * @return
     */
    private void autoDoConnect() {
        //看现在是否已经连上了经典蓝牙，而且ble蓝牙没连
        if (isBTConnected()) {
            // 这时获取所有蓝牙设备的方法
            bluetoothAdapter.getProfileProxy(getContext(), new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    List<BluetoothDevice> mDevices = proxy.getConnectedDevices();
                    final List<String> addressList = new ArrayList<>();
                    if (mDevices != null && mDevices.size() > 0) {
                        for (BluetoothDevice device : mDevices) {
                            if (device != null) {
                                addressList.add(device.getAddress());
                            }
                        }
                    }
                    //扫描所有ble设备尝试连接
                    startScan(new BluetoothAdapter.LeScanCallback() {
                        @Override
                        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    for (String address : addressList) {
                                        //例子 hrm的mac为F4:0E:11:72:FC:54
                                        //    耳机经典蓝牙mac为F4:0E:11:72:03:AB
                                        if (address.startsWith(device.getAddress().substring(0, 7))) {
                                            connect(device);
                                            break;
                                        }
                                    }
                                }
                            }, 100);
                        }
                    });
                }

                @Override
                public void onServiceDisconnected(int profile) {

                }
            }, BluetoothProfile.HEADSET);
        }
        return;
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


    /**
     * 开始扫描
     *
     * @param
     */
    public void startScan(BluetoothAdapter.LeScanCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("BleScanCallback can not be Null!");
        }

        if (!(bluetoothAdapter != null && bluetoothAdapter.isEnabled())) {
            BleLog.e("Bluetooth not enable!");
//            callback.onScanStarted(false);
            return;
        }
        /**
         * uuid  后面应该加上其他指令的
         *
         */
        UUID[] uuids = {UUID.fromString(Command.TEMPERATURE_AND_HEARTRATE.getSERVICE_UUID())};
//        boolean success =
        bluetoothAdapter
                .startLeScan(uuids, callback);
    }


    public void cancelScan(BluetoothAdapter.LeScanCallback callback) {
        bluetoothAdapter.stopLeScan(callback);
    }


    /**
     * 主动连接
     * addConnectGattCallback(callback);
     *
     * @param bleDevice
     * @param
     */
    public synchronized void connect(BluetoothDevice bleDevice) {
        if (!(bluetoothAdapter != null && bluetoothAdapter.isEnabled())) {
            BleLog.e("Bluetooth not enable!");
            return;
        }

        if (Looper.myLooper() == null || Looper.myLooper() != Looper.getMainLooper()) {
            BleLog.w("Be careful: currentThread is not MainThread!");
        }
        if (bleBluetooth != null) {
            return;
        }
        bleBluetooth = new BleBluetooth(bleDevice);
        bleBluetooth.connect(bleDevice, false);
        //开启定时任务3分钟获取一次，15分钟上传一次
        CommandTask.startReadTask();
        CommandTask.startUpTask();
        return;
    }


    /**
     * 增加蓝牙连接状态监听器
     *
     * @param stateListener
     */
    public void addBlueToothStateListener(BluetoothStateListener stateListener) {
        bleBluetooth.addConnectGattCallback(stateListener);
    }

    public void removeBlueToothStateListener() {
        bleBluetooth.removeConnectGattCallback();
    }

    private Set<String> listenCmdSet = new HashSet<>();

    /**
     * 增加蓝牙命令监听器
     */
    public void addCommandListener(Command command, final CommandListener commandListener) {
        listenCmdSet.add(command.getUuid());
        addTaskCommandListener(command, commandListener);
    }


    /**
     * 移除监听器
     *
     * @param command
     */
    public void removeCommandListener(Command command) {
        bleBluetooth.removeNotifyCallback(command.getUuid());
        BleConnector connector = bleBluetooth.newBleConnector().withUUIDString(
                Command.TEMPERATURE_AND_HEARTRATE.getSERVICE_UUID(),
                Command.TEMPERATURE_AND_HEARTRATE.getCHARACTER_UUID());
        connector.disableCharacteristicNotify(false);
    }

    public void addTaskCommandListener(Command command, final CommandListener commandListener) {
        if (bleBluetooth == null) {
            return;
        }

        BleConnector connector = bleBluetooth.newBleConnector().withUUIDString(
                Command.TEMPERATURE_AND_HEARTRATE.getSERVICE_UUID(),
                Command.TEMPERATURE_AND_HEARTRATE.getCHARACTER_UUID());
        //这里后面要封装,让各个callback都调用listener,现在只有一个指令
        connector.enableCharacteristicNotify(new BleNotifyCallback() {
            @Override
            public void onNotifySuccess() {
                commandListener.onCommandSuccess();
            }

            @Override
            public void onNotifyFailure(BleException exception) {
                commandListener.onCommandFailure(exception);
            }

            @Override
            public void onCharacteristicChanged(byte[] data) {
                CommandResult commandResult = new CommandResult();
                commandResult.setCommand(Command.TEMPERATURE_AND_HEARTRATE);
                commandResult.setResult(HexUtil.formatHexString(data, true));
                commandListener.onCommandResult(commandResult);
            }
        }, command.getUuid(), false);
    }

    public void removeTaskCommandListener(Command command) {
        if (!listenCmdSet.contains(command.getUuid())) {
            removeCommandListener(command);
        }
    }

    /**
     * 断开连接
     */

    public void disconnect() {
        if (bleBluetooth != null) {
            bleBluetooth.disconnect();
            bleBluetooth = null;
        }
    }

    /**
     * 读取历史数据
     *
     * @param command
     * @return
     */
    public JSONObject getHistoryByCommand(final Command command) {
        //从线上拉取，如果没有拉到，根据本地缓存的形成,暂时只有一个指令，不需要分
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(5, TimeUnit.SECONDS)//设置连接超时时间
                        .readTimeout(5, TimeUnit.SECONDS)//设置读取超时时间
                        .build();
                Request request = new Request.Builder()
                        .url(ServiceConstants.historyUrl + "?productId=" + getProductId()
                                + "&uid=" + getUid() + "&type=" + command.name())
                        .build();
                try (Response response = client.newCall(request).execute()) {
//                    清空上传好了的平均数据
                    JsonCacheUtil.writeJson(getContext(), response.body().string(), "history", false);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        t.start();
        JSONObject jsonObject = null;
        try {
            //等待线程执行三秒
            t.join(3000);
            List<String> list = JsonCacheUtil.readJson(getContext(), "history");
            if (list.size() > 0) {
                jsonObject = new JSONObject(list.get(0));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return jsonObject;
    }

//    public boolean isConnected() {
//        return bleBluetooth != null;
//    }

//    private boolean isSupportBle() {
//        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2
//                && context.getApplicationContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
//    }

    private void checkPermissions() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!bluetoothAdapter.isEnabled()) {
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


    public Context getContext() {
        return this.context;
    }


}
