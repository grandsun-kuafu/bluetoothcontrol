package com.grandsun.bluetoothcontrol.cloud;

import android.os.Handler;
import android.util.Log;


import com.grandsun.bluetoothcontrol.BleManager;
import com.grandsun.bluetoothcontrol.cloud.ServiceConstants;
import com.grandsun.bluetoothcontrol.command.Command;
import com.grandsun.bluetoothcontrol.exception.BleException;
import com.grandsun.bluetoothcontrol.listener.CommandListener;
import com.grandsun.bluetoothcontrol.utils.JsonCacheUtil;
import com.grandsun.bluetoothcontrol.utils.PollingUtil;

import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CommandTask {

    static Map<String, PollingUtil> pollingUtilMap = new HashMap<>();
    static Map<String, Runnable> runnableMap = new HashMap<>();

    public static final MediaType JSON
            = MediaType.get("application/json; charset=utf-8");

    private static boolean readTaskStarted;

    private static boolean upTaskStarted;

    private static boolean haveCloseCommand;

    public static void startReadTask() {
        if (readTaskStarted) {
            return;
        }
        readTaskStarted = true;

        pollingUtilMap.put("readTask", new PollingUtil(new Handler(BleManager.getInstance().getContext().getMainLooper())));
        runnableMap.put("readRunnable", new Runnable() {
            @Override
            public void run() {
                Log.d("commandTask", "readRunnable begin");

                if (!BleManager.getInstance().connectedBleDevice()) {
                    return;
                }
                // 要做的事情
                BleManager.getInstance().openCommand(Command.TEMPERATURE_AND_HEARTRATE,
                        new CommandListener() {
                            @Override
                            public void onCommandSuccess() {

                            }

                            @Override
                            public void onCommandFailure(BleException e) {

                            }

                            @Override
                            public void onCommandResult(String result) {
                                JsonCacheUtil.writeJson(BleManager.getInstance().getContext(), result,
                                        "task_temperature_and_heartrate", true);
                                if (!haveCloseCommand) {
                                    haveCloseCommand = true;
                                    // 5秒中后关闭
                                    new Handler().postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            Log.d("commandTask", "5 second close command");
                                            BleManager.getInstance().closeCommand();
                                            haveCloseCommand = false;
                                        }
                                    }, 5000);
                                }
                            }
                        });
            }
        });
        pollingUtilMap.get("readTask").startPolling(runnableMap.get("readRunnable"), 30000, true);

    }

    public static void startUpTask() {
        if (upTaskStarted) {
            return;
        }
        upTaskStarted = true;
        //读取缓存的数据，进行计算。形成avg，新缓存。再上传
        pollingUtilMap.put("upTask", new PollingUtil(new Handler(BleManager.getInstance().getContext().getMainLooper())));
        runnableMap.put("upRunnable", new Runnable() {
            @Override
            public void run() {
                Log.d("commandTask", "upRunnable begin");
                up(buildUpObj());
            }
        });
        pollingUtilMap.get("upTask").startPolling(runnableMap.get("upRunnable"), 30000, true);
    }

    private static String buildUpObj() {
        // 这个数据是  06 57 01 86。04 57 01 86,04代表无效,57代表心跳，01 86代表体温10倍（16进制）
        // 要做的事情
        List<String> list = JsonCacheUtil.readJson(BleManager.getInstance().getContext(), "task_temperature_and_heartrate");
        //算平均
        BigDecimal temperature = BigDecimal.ZERO;
        BigDecimal heartRate = BigDecimal.ZERO;
        JSONObject object = new JSONObject();
        try {
            int countTem = 0;
            int countHr = 0;
            BigDecimal temperatureMax = BigDecimal.ZERO, temperatureMin = BigDecimal.ZERO,
                    heartRateMax = BigDecimal.ZERO, heartRateMin = BigDecimal.ZERO,
                    temperatureAvg = BigDecimal.ZERO, heartRateAvg = BigDecimal.ZERO;

            for (String s : list) {
                if (s.startsWith("06")) {//计算心率，体温
                    String[] subS = s.split(" ");
                    BigDecimal hr = new BigDecimal(Long.parseLong(subS[1], 16));
                    if (hr.compareTo(BigDecimal.ZERO) > 0) {
                        heartRate = heartRate.add(hr);
                        countHr++;
                        heartRateMax = heartRateMax.compareTo(hr) > 0 ? heartRateMax : hr;
                        heartRateMin = heartRateMin.compareTo(hr) < 0 ? heartRateMin : hr;
                    }
                    BigDecimal tem = new BigDecimal(Long.parseLong(subS[2] + subS[3], 16));
                    if (tem.compareTo(BigDecimal.ZERO) > 0) {
                        temperature = temperature.add(tem);
                        countTem++;
                        temperatureMax = temperatureMax.compareTo(tem) > 0 ? temperatureMax : tem;
                        temperatureMin = temperatureMin.compareTo(tem) < 0 ? temperatureMin : tem;
                    }
                }
            }
            if (countTem > 0)
                temperatureAvg = temperature.divide(new BigDecimal(countTem * 10), 1, BigDecimal.ROUND_HALF_UP);
            object.put("temperatureAvg", temperatureAvg);
            if (countHr > 0)
                heartRateAvg = heartRate.divide(new BigDecimal(countHr), 0, BigDecimal.ROUND_HALF_UP);
            object.put("heartRateAvg", heartRateAvg);

            object.put("temperatureMax", temperatureMax);
            object.put("temperatureMin", temperatureMin);
            object.put("temperatureMaxW", BigDecimal.ZERO);
            object.put("temperatureMinW", BigDecimal.ZERO);
            object.put("heartRateMax", heartRateMax);
            object.put("heartRateMin", heartRateMin);
            object.put("heartRateMaxW", BigDecimal.ZERO);
            object.put("heartRateMinW", BigDecimal.ZERO);

            //清空历史数据
            JsonCacheUtil.clear(BleManager.getInstance().getContext(), "task_temperature_and_heartrate");

            //设置其他参数
            object.put("productId", BleManager.getInstance().getProductId());
            object.put("uid", BleManager.getInstance().getUid());


        } catch (Exception e) {
            e.printStackTrace();
        }
        return object.toString();
    }

    private static void up(final String json) {
        Log.d("commandTask", "post up begin");
        JsonCacheUtil.writeJson(BleManager.getInstance().getContext(), json,
                "up_obj", true);
        List<String> listAvg = JsonCacheUtil.readJson(BleManager.getInstance().getContext(), "up_obj");
        try {
            final StringBuilder avgHis = new StringBuilder("[");
            for (int i = 0; i < listAvg.size(); i++) {
                avgHis.append(listAvg.get(i));
                if (i < listAvg.size() - 1) {
                    avgHis.append(",");
                }
            }
            avgHis.append("]");
            // Android 4.0 之后不能在主线程中请求HTTP请求
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    OkHttpClient client = new OkHttpClient.Builder()
                            .connectTimeout(5, TimeUnit.SECONDS)//设置连接超时时间
                            .readTimeout(5, TimeUnit.SECONDS)//设置读取超时时间
                            .build();
                    RequestBody body = RequestBody.create(avgHis.toString(), JSON);
                    Request request = new Request.Builder()
                            .url(ServiceConstants.upUrl)
                            .post(body)
                            .build();
                    try (Response response = client.newCall(request).execute()) {
                        response.body().string();
//                    清空上传好了的平均数据
                        JsonCacheUtil.clear(BleManager.getInstance().getContext(), "up_obj");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
            t.start();
        } catch (Exception e) {
            e.printStackTrace();
        }


    }
}
