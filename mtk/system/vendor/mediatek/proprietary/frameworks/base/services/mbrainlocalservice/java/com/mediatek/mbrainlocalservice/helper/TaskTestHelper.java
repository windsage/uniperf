package com.mediatek.mbrainlocalservice.helper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class TaskTestHelper {
    private static final int TEST_BROWSER_UID = 10106;
    private static final String TEST_BROWSER_PACKAGENAME = "com.android.browser";

    public static String testInvalidTask() {
        JSONObject test = new JSONObject();
        return test.toString();
    }

    public static String testUnsopportedTask() {
        try {
            JSONObject test = new JSONObject();
            test.put("command", 5566);
            return test.toString();
        } catch(JSONException e) {
            return "";
        }
    }

    public static String testCommonTask() {
        try {
            JSONObject test = new JSONObject();
            test.put("command", TaskHelper.ACTION_GET_OS_COMMON);
            return test.toString();
        } catch(JSONException e) {
            return "";
        }
    }

    public static String testAppInfoTask() {
        try {
            JSONObject test = new JSONObject();
            test.put("command", TaskHelper.ACTION_GET_APP_INFO);
            test.put("uid", TEST_BROWSER_UID);
            test.put("packagename", TEST_BROWSER_PACKAGENAME);
            return test.toString();
        } catch(JSONException e) {
            return "";
        }
    }

    public static String testSystemUidInfoTask() {
        try {
            JSONObject test = new JSONObject();
            test.put("command", TaskHelper.ACTION_GET_APP_INFO);
            test.put("uid", 1000); //Process.SYSTEM_UID
            return test.toString();
        } catch(JSONException e) {
            return "";
        }
    }

    public static String testAppSwitchTask() {
        try {
            JSONObject test = new JSONObject();
            test.put("command", TaskHelper.ACTION_GET_APP_SWITCH_INFO);
            test.put("history", 1);
            return test.toString();
        } catch(JSONException e) {
            return "";
        }
    }

    public static String testGetAllUids() {
        try {
            JSONObject test = new JSONObject();
            test.put("command", TaskHelper.ACTION_GET_UIDS_INFO);
            return test.toString();
        } catch(JSONException e) {
            return "";
        }
    }

    public static String testGetDisplayInfo() {
        try {
            JSONObject test = new JSONObject();
            test.put("command", TaskHelper.ACTION_GET_DISPLAY_INFO);
            return test.toString();
        } catch(JSONException e) {
            return "";
        }
    }

    public static String testGetTrafficStats() {
        try {
            JSONObject test = new JSONObject();
            test.put("command", TaskHelper.ACTION_GET_ACCU_TRAFFIC_INFO);
            test.put("packagename", "com.android.browser");
            return test.toString();
        } catch(JSONException e) {
            return "";
        }
    }

    public static String testGetConnectivity() {
        try {
            JSONObject test = new JSONObject();
            test.put("command", TaskHelper.ACTION_GET_CONNECTIVITY_INFO);
            return test.toString();
        } catch(JSONException e) {
            return "";
        }
    }

    public static String testGetCharge() {
        try {
            JSONObject test = new JSONObject();
            test.put("command", TaskHelper.ACTION_GET_CHARGE_INFO);
            return test.toString();
        } catch(JSONException e) {
            return "";
        }
    }

    public static String testGetSystemNetworkUsage() {
        try {
            JSONObject test = new JSONObject();
            test.put("command", TaskHelper.ACTION_GET_SYSTEM_NETWORK_USAGE);
            return test.toString();
        } catch(JSONException e) {
            return "";
        }
    }

    public static String testEnableMtklogger() {
        try {
            JSONObject test = new JSONObject();
            test.put("command", TaskHelper.ACTION_CMD_MTKLOGGER_ENABLE);
            return test.toString();
        } catch(JSONException e) {
            return "";
        }
    }

    public static String testDisableMtklogger() {
        try {
            JSONObject test = new JSONObject();
            test.put("command", TaskHelper.ACTION_CMD_MTKLOGGER_DISABLE);
            return test.toString();
        } catch(JSONException e) {
            return "";
        }
    }

    public static String testGetSystemCurrentStatus() {
        try {
            JSONObject test = new JSONObject();
            test.put("command", TaskHelper.ACTION_GET_CURRENT_STATUS_INFO);
            return test.toString();
        } catch(JSONException e) {
            return "";
        }
    }

    public static String testGetProcessMemory(int[] pids, int amsMode) {
        try {
            JSONObject test = new JSONObject();
            test.put("command", TaskHelper.ACTION_GET_PROCESS_MEMORY);
            test.put("ams", amsMode);
            JSONArray pidArray = new JSONArray();
            for(int pid : pids) {
                pidArray.put(pid);
            }
            test.put("pid", pidArray);
            return test.toString();
        } catch(JSONException e) {
            return "";
        }
    }
}
