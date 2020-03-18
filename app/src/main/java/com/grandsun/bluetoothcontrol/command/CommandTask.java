package com.grandsun.bluetoothcontrol.command;

import android.os.Handler;
import android.util.Log;


import com.grandsun.bluetoothcontrol.BleManager;
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
    public static String upUrl = "http://192.168.199.101:8003/demo/up";

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

                if (BleManager.getInstance().getBleController() == null) {
                    return;
                }
                // 要做的事情
                BleManager.getInstance().getBleController().openCommand(Command.TEMPERATURE_AND_HEARTRATE,
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
                                            BleManager.getInstance().getBleController().closeCommand();
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

                // 要做的事情
                List<String> list = JsonCacheUtil.readJson(BleManager.getInstance().getContext(), "task_temperature_and_heartrate");
                //算平均
                String avg = calAvg(list);
                JsonCacheUtil.writeJson(BleManager.getInstance().getContext(), avg,
                        "avg_temperature_and_heartrate", true);
                //清空历史数据
                JsonCacheUtil.clear(BleManager.getInstance().getContext(), "task_temperature_and_heartrate");

                List<String> listAvg = JsonCacheUtil.readJson(BleManager.getInstance().getContext(), "avg_temperature_and_heartrate");

                try {
                    StringBuilder avgHis = new StringBuilder("[");
                    for (int i = 0; i < listAvg.size(); i++) {
                        avgHis.append(listAvg.get(i));
                        if (i < listAvg.size() - 1) {
                            avgHis.append(",");
                        }
                    }
                    avgHis.append("]");
                    post(upUrl, avgHis.toString());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        pollingUtilMap.get("upTask").startPolling(runnableMap.get("upRunnable"), 30000, true);
    }

    private static String calAvg(List<String> list) {
        // 这个数据是  06 57 01 86。04 57 01 86,04代表无效,57代表心跳，01 86代表体温10倍（16进制）

        BigDecimal temperature = BigDecimal.ZERO;
        BigDecimal heartRate = BigDecimal.ZERO;
        JSONObject object = new JSONObject();
        try {
            int countTem = 0;
            int countHr = 0;
            for (String s : list) {
                if (s.startsWith("06")) {//计算心率，体温
                    String[] subS = s.split(" ");
                    BigDecimal hr = new BigDecimal(Long.parseLong(subS[1], 16));
                    if (hr.compareTo(BigDecimal.ZERO) > 0) {
                        heartRate = heartRate.add(hr);
                        countHr++;
                    }
                    BigDecimal tem = new BigDecimal(Long.parseLong(subS[2] + subS[3], 16));
                    if (tem.compareTo(BigDecimal.ZERO) > 0) {
                        temperature = temperature.add(tem);
                        countTem++;
                    }
                }
            }
            if (countTem > 0)
                object.put("temperature",
                        temperature.divide(new BigDecimal(countTem * 10), 1, BigDecimal.ROUND_HALF_UP).toPlainString());
            if (countHr > 0)
                object.put("heartRate",
                        heartRate.divide(new BigDecimal(countHr), 0, BigDecimal.ROUND_HALF_UP).toPlainString());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return object.toString();
    }

    private static void post(final String url, final String json) {
        Log.d("commandTask", "post up begin");
        // Android 4.0 之后不能在主线程中请求HTTP请求
        new Thread(new Runnable() {
            @Override
            public void run() {
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(5, TimeUnit.SECONDS)//设置连接超时时间
                        .readTimeout(5, TimeUnit.SECONDS)//设置读取超时时间
                        .build();
                RequestBody body = RequestBody.create(json, JSON);
                Request request = new Request.Builder()
                        .url(url)
                        .post(body)
                        .build();
                try (Response response = client.newCall(request).execute()) {
                    response.body().string();
//                    清空上传好了的平均数据
                    JsonCacheUtil.clear(BleManager.getInstance().getContext(), "avg_temperature_and_heartrate");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();

    }
}
