package com.grandsun.bluetoothcontrol.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.orhanobut.logger.AndroidLogAdapter;
import com.orhanobut.logger.FormatStrategy;
import com.orhanobut.logger.Logger;
import com.orhanobut.logger.PrettyFormatStrategy;

/**
 * 日志管理
 */
public class LogUtil {
    static {
        FormatStrategy formatStrategy = PrettyFormatStrategy.newBuilder()
                .methodCount(4)
                .methodOffset(6)
                .showThreadInfo(false)
                .tag("YJLog")
                .build();
        Logger.addLogAdapter(new AndroidLogAdapter(formatStrategy) {
            private StringBuilder mMessage = new StringBuilder();

            @Override
            public boolean isLoggable(int priority, @Nullable String tag) {
                return true;//正式包不打

//                return true;
            }

            @Override
            public void log(int priority, @Nullable String tag, @NonNull String message) {
                if ((message.startsWith("{") && message.endsWith("}"))
                        || (message.startsWith("[") && message.endsWith("]"))) {
                    message = formatJson(message) + "\n" + message;
                }
                if (message.startsWith("--> POST") || message.startsWith("<-- ") || mMessage.length() != 0) {
                    mMessage.append(message.concat("\n"));
                }
                if (mMessage.length() == 0) {
                    super.log(priority, tag, message);
                }
                if (message.startsWith("--> END POST") || message.startsWith("<-- END HTTP")) {
                    super.log(priority, tag, mMessage.toString());
                    mMessage.setLength(0);
                }
            }
        });
    }

    public static void d(String tag, String msg) {
        Logger.t(tag).d(msg);
    }

    public static void d(String msg) {
        Logger.d(msg);
    }

    public static void e(String tag, String msg) {
        Logger.t(tag).e(msg);
    }

    public static void e(String msg) {
        Logger.e(msg);
    }

    /**
     * 格式化json字符串
     *
     * @param jsonStr 需要格式化的json串
     * @return 格式化后的json串
     */
    private static String formatJson(String jsonStr) {
        if (null == jsonStr || "".equals(jsonStr)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        char last = '\0';
        char current = '\0';
        int indent = 0;
        for (int i = 0; i < jsonStr.length(); i++) {
            last = current;
            current = jsonStr.charAt(i);
            //遇到{ [换行，且下一行缩进
            switch (current) {
                case '{':
                case '[':
                    sb.append(current);
                    sb.append('\n');
                    indent++;
                    addIndentBlank(sb, indent);
                    break;
                //遇到} ]换行，当前行缩进
                case '}':
                case ']':
                    sb.append('\n');
                    indent--;
                    addIndentBlank(sb, indent);
                    sb.append(current);
                    break;
                //遇到,换行
                case ',':
                    sb.append(current);
                    if (last != '\\') {
                        sb.append('\n');
                        addIndentBlank(sb, indent);
                    }
                    break;
                default:
                    sb.append(current);
            }
        }
        return sb.toString();
    }

    /**
     * 添加空格
     *
     * @param sb     StringBuilder
     * @param indent 空格个数
     */
    private static void addIndentBlank(StringBuilder sb, int indent) {
        for (int i = 0; i < indent; i++) {
            sb.append('\t');
        }
    }
}