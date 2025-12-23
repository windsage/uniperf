package com.mediatek.mbrainlocalservice.helper;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.util.Slog;

import com.mediatek.mbrainlocalservice.screen.ScreenHelper;

public class MtkLoggerHelper {
    private static final String TAG = "MtkLoggerHelper";
    private static boolean isEnableMtkLogger = false;

    public static void notifyMtkLogger(Context context, boolean enable) {
        if (enable && (isMDLoggerRunning() || !ScreenHelper.isScreenOff())) {
            //Slog.d(TAG, "notifyMtkLogger enabled failed, it is running now or screen is on");
            return;
        } else if (isEnableMtkLogger == false && enable == false) {
            //Slog.d(TAG, "notifyMtkLogger disable failed, mbrain should not disable if mbrain didn't enabled it ");
            return;
        }

        String cmd = (enable) ? "start" : "stop";
        Intent intent = new Intent("com.debug.loggerui.ADB_CMD");
        intent.putExtra("cmd_name", cmd);
        intent.putExtra("cmd_target", 2);
        intent.setComponent(
            ComponentName.unflattenFromString("com.debug.loggerui/.framework.LogReceiver"));
        context.sendBroadcastAsUser(intent, new UserHandle(UserHandle.USER_OWNER));
        isEnableMtkLogger = enable;
    }

    public static void notifyMtkLoggerMDTaglog(Context context) {
        if (isMDLoggerRunning()) {
            Intent intent = new Intent("com.mediatek.log2server.EXCEPTION_HAPPEND");
            intent.putExtra("path", "SaveLogManually");
            intent.putExtra("db_filename", "mbrain_md_taglog");
            intent.putExtra("is_need_zip", false);
            intent.putExtra("is_need_all_logs", false);
            intent.setComponent(
                ComponentName.unflattenFromString("com.debug.loggerui/.framework.LogReceiver"));
            context.sendBroadcastAsUser(intent, new UserHandle(UserHandle.USER_OWNER));
        }
    }

    public static void screenStatusChanged(Context context) {
        boolean isDisableMtkLogger = !ScreenHelper.isScreenOff();
        notifyMtkLogger(context, isDisableMtkLogger);
    }

    private static boolean isMDLoggerRunning() {
        return SystemProperties.get("vendor.mdlogger.Running", "0").equals("1");
    }

     public static void endTest(Context context) {
        Intent intent = new Intent("com.debug.loggerui.ADB_CMD");
        intent.putExtra("cmd_name", "execute_dynamic_settings_command_value_CLICK");
        intent.putExtra("cmd_target", "MBrain/Operation//End Test");
        intent.setComponent(
            ComponentName.unflattenFromString("com.debug.loggerui/.framework.LogReceiver"));
        context.sendBroadcastAsUser(intent, new UserHandle(UserHandle.USER_OWNER));
    }

}