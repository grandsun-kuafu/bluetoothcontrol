package com.grandsun.bluetoothcontrol;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

import com.grandsun.bluetoothcontrol.bluetooth.BleBluetooth;
import com.grandsun.bluetoothcontrol.callback.BleNotifyCallback;
import com.grandsun.bluetoothcontrol.command.Command;
import com.grandsun.bluetoothcontrol.exception.BleException;
import com.grandsun.bluetoothcontrol.exception.OtherException;
import com.grandsun.bluetoothcontrol.listener.CommandListener;
import com.grandsun.bluetoothcontrol.utils.HexUtil;

import java.util.HashSet;
import java.util.Set;

public class BleController {


    BluetoothGattService service;

    BluetoothGattCharacteristic characteristic;

    BleBluetooth bleBluetooth;

    public BleController(BleBluetooth bleBluetooth) {
        this.bleBluetooth = bleBluetooth;
    }

    public static boolean isOpenCommand;


    private BluetoothGatt getBluetoothGatt() {
        return bleBluetooth.getBluetoothGatt();
    }


    private BleBluetooth getBleBluetooth() {
        return bleBluetooth;
    }

    public void openCommand(Command command, final CommandListener listener) {
        //这边的逻辑从连接以后
        //直接定时获取
        //拿最新的
        //本地缓存最多24小时的数据。等待上传
        //标记是哪个

        if (bleBluetooth == null) {
            return;
        }

        BluetoothGatt gatt = getBluetoothGatt();

        for (BluetoothGattService servicesub : gatt.getServices()) {
            if (command.getSERVICE_UUID().equals(servicesub.getUuid().toString())) {
                service = servicesub;
            }
        }
        if (null != service) {
            for (BluetoothGattCharacteristic characteristicSub : service.getCharacteristics()) {
                if (command.getCHARACTER_UUID().equals(characteristicSub.getUuid().toString())) {
                    characteristic = characteristicSub;
                }
            }
        }

        if (service != null && characteristic != null) {
            notify(
                    characteristic.getService().getUuid().toString(),
                    characteristic.getUuid().toString(),
                    new BleNotifyCallback() {
                        @Override
                        public void onNotifySuccess() {
                            isOpenCommand = true;
                            Log.d("BleController", "notify success");

                            listener.onCommandSuccess();
                        }

                        @Override
                        public void onNotifyFailure(BleException exception) {
                            Log.d("BleController", "notify failure"+ exception.getDescription());

                            listener.onCommandFailure(exception);
                        }

                        // 关闭到事情留给定时任务
                        @Override
                        public void onCharacteristicChanged(byte[] data) {
                            //获取到正确数字立即停止
                            String st = HexUtil.formatHexString(data, true);
                            Log.d("BleController", "notify result:"+ st);
                            listener.onCommandResult(st);
//                            if (st.startsWith("06")) {
//                                stopNotify(
//                                        characteristic.getService().getUuid().toString(),
//                                        characteristic.getUuid().toString());
//                                listener.onCommandResult(st);
//
//                            } else {
//
//                            }
                        }
                    }
            );
        } else {
            //
        }
    }

    public void closeCommand() {
        Log.d("BleController", "close Command");

        if (isOpenCommand) {
            stopNotify(
                    characteristic.getService().getUuid().toString(),
                    characteristic.getUuid().toString());
        }

    }

    public Set<String> getHistoryByCommand(Command command) {
        //从线上拉取，如果没有拉到，根据本地缓存的形成
        SharedPreferences preferences = BleManager.getInstance().getContext().getSharedPreferences("temperature", Context.MODE_PRIVATE);
        return preferences.getStringSet("temperature", new HashSet<String>());
    }

    private void notify(
            String uuid_service,
            String uuid_notify,
            BleNotifyCallback callback) {
        notify(uuid_service, uuid_notify, false, callback);
    }

    private void notify(
            String uuid_service,
            String uuid_notify,
            boolean useCharacteristicDescriptor,
            BleNotifyCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("BleNotifyCallback can not be Null!");
        }
        if (bleBluetooth == null) {
            callback.onNotifyFailure(new OtherException("This device not connect!"));
        } else {
            bleBluetooth.newBleConnector()
                    .withUUIDString(uuid_service, uuid_notify)
                    .enableCharacteristicNotify(callback, uuid_notify, useCharacteristicDescriptor);
        }
    }

    /**
     * stop notify, remove callback
     *
     * @param
     * @param uuid_service
     * @param uuid_notify
     * @return
     */
    private boolean stopNotify(
            String uuid_service,
            String uuid_notify) {
        return stopNotify(uuid_service, uuid_notify, false);
    }

    /**
     * stop notify, remove callback
     *
     * @param
     * @param uuid_service
     * @param uuid_notify
     * @param useCharacteristicDescriptor
     * @return
     */
    private boolean stopNotify(
            String uuid_service,
            String uuid_notify,
            boolean useCharacteristicDescriptor) {
        if (bleBluetooth == null) {
            return false;
        }
        boolean success = bleBluetooth.newBleConnector()
                .withUUIDString(uuid_service, uuid_notify)
                .disableCharacteristicNotify(useCharacteristicDescriptor);
        if (success) {
            bleBluetooth.removeNotifyCallback(uuid_notify);
        }
        return success;
    }

}
