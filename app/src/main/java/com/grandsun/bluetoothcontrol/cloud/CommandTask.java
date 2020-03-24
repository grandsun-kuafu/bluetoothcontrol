package com.grandsun.bluetoothcontrol.cloud;

import android.os.Handler;
import android.util.Log;


import com.grandsun.bluetoothcontrol.BleManager;
import com.grandsun.bluetoothcontrol.command.Command;
import com.grandsun.bluetoothcontrol.command.CommandResult;
import com.grandsun.bluetoothcontrol.exception.BleException;
import com.grandsun.bluetoothcontrol.listener.CommandListener;
import com.grandsun.bluetoothcontrol.utils.JsonCacheUtil;
import com.grandsun.bluetoothcontrol.utils.PollingUtil;

import org.json.JSONObject;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Calendar;
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

    public synchronized static void startReadTask() {
        if (readTaskStarted) {
            return;
        }
        readTaskStarted = true;

        pollingUtilMap.put("readTask", new PollingUtil(new Handler(BleManager.getInstance().getContext().getMainLooper())));
        runnableMap.put("readRunnable", new Runnable() {
            @Override
            public void run() {
                Log.d("commandTask", Calendar.getInstance().getTime()+"readRunnable begin");
                BleManager.getInstance().addTaskCommandListener(Command.TEMPERATURE_AND_HEARTRATE_TASK,
                        new CommandListener() {
                            @Override
                            public void onCommandSuccess() {

                            }

                            @Override
                            public void onCommandFailure(BleException e) {

                            }

                            @Override
                            public void onCommandResult(CommandResult result) {
                                JsonCacheUtil.writeJson(BleManager.getInstance().getContext(), result.getResult(),
                                        "task_temperature_and_heartrate", true);
                                Log.d("commandResult", result.getResult());
                                closeCommand();
                            }
                        });
            }
        });
        pollingUtilMap.get("readTask").startPolling(runnableMap.get("readRunnable"), 30000, true);

    }

    public static synchronized void closeCommand() {
        if (!haveCloseCommand) {
            haveCloseCommand = true;
            // 5秒中后关闭
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    Log.d("commandTask", Calendar.getInstance().getTime() + "5 second close command");
                    BleManager.getInstance().removeTaskCommandListener(
                            Command.TEMPERATURE_AND_HEARTRATE_TASK);
                    haveCloseCommand = false;
                }
            }, 5000);
        }
    }

    public synchronized static void startUpTask() {
        if (upTaskStarted) {
            return;
        }
        upTaskStarted = true;
        //读取缓存的数据，进行计算。形成avg，新缓存。再上传
        pollingUtilMap.put("upTask", new PollingUtil(new Handler(BleManager.getInstance().getContext().getMainLooper())));
        runnableMap.put("upRunnable", new Runnable() {
            @Override
            public void run() {
                Log.d("commandTask", Calendar.getInstance().getTime()+"upRunnable begin");
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
            BigDecimal temperatureMax = null, temperatureMin = null,
                    heartRateMax = null, heartRateMin = null,
                    temperatureAvg = BigDecimal.ZERO, heartRateAvg = BigDecimal.ZERO;

            for (String s : list) {
//                if (s.startsWith("06")) {//计算心率，体温
                String[] subS = s.split(" ");
                BigDecimal hr = new BigDecimal(Long.parseLong(subS[1], 16));
                if (hr.compareTo(BigDecimal.ZERO) > 0) {
                    heartRate = heartRate.add(hr);
                    countHr++;
                    heartRateMax = heartRateMax == null ? hr : (heartRateMax.compareTo(hr) > 0 ? heartRateMax : hr);
                    heartRateMin = heartRateMin == null ? hr : (heartRateMin.compareTo(hr) < 0 ? heartRateMin : hr);
                }
                BigDecimal tem = new BigDecimal(Long.parseLong(subS[2] + subS[3], 16));
                if (tem.compareTo(BigDecimal.ZERO) > 0) {
                    temperature = temperature.add(tem);
                    countTem++;
                    temperatureMax = temperatureMax == null ? tem : (temperatureMax.compareTo(tem) > 0 ? temperatureMax : tem);
                    temperatureMin = temperatureMin == null ? tem : (temperatureMin.compareTo(tem) < 0 ? temperatureMin : tem);
                }
//                }
            }
            if (countTem > 0)
                temperatureAvg = temperature.divide(new BigDecimal(countTem * 10), 1, BigDecimal.ROUND_HALF_UP);
            object.put("temperatureAvg", temperatureAvg);
            if (countHr > 0)
                heartRateAvg = heartRate.divide(new BigDecimal(countHr), 0, BigDecimal.ROUND_HALF_UP);
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
            object.put("measureDate", dateFormat.format(Calendar.getInstance().getTime()));
            object.put("heartRateAvg", heartRateAvg);

            object.put("temperatureMax", temperatureMax == null ? temperatureMax :
                    temperatureMax.divide(new BigDecimal(10), 1, BigDecimal.ROUND_HALF_UP));
            object.put("temperatureMin", temperatureMin == null ? temperatureMin :
                    temperatureMin.divide(new BigDecimal(10), 1, BigDecimal.ROUND_HALF_UP));
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
            for (int k = 0; k <= listAvg.size() / 10; k++) {            //最多上传十条
                if (k * 10 == listAvg.size()) {
                    break;
                }
                int limit = listAvg.size() < (k + 1) * 10 ? listAvg.size() : (k + 1) * 10;
                final StringBuilder avgHis = new StringBuilder("[");
                for (int i = k * 10; i < limit; i++) {
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
//                    清空上传好了的平均数据
                            JsonCacheUtil.clear(BleManager.getInstance().getContext(), "up_obj");
                        } catch (Exception e) {//忽略这个异常
//                            e.printStackTrace();
                        }
                    }
                });
                t.start();
            }
        } catch (
                Exception e) {
            e.printStackTrace();
        }


    }
}
