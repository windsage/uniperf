package com.mediatek.mbrainlocalservice.helper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class TaskHelper {
    // action list
    public final static int ACTION_NONE = 0;

    public final static int ACTION_GET_OS_COMMON = 1001;
    public final static int ACTION_GET_APP_INFO = 1002;
    public final static int ACTION_GET_APP_SWITCH_INFO = 1003;
    public final static int ACTION_GET_CHARGE_INFO = 1004;
    public final static int ACTION_GET_TIMEZONE_INFO = 1005;
    public final static int ACTION_GET_ACCU_TRAFFIC_INFO = 1006;
    public final static int ACTION_GET_UIDS_INFO = 1007;
    public final static int ACTION_GET_DISPLAY_INFO = 1008;
    public final static int ACTION_GET_CONNECTIVITY_INFO = 1009;
    public final static int ACTION_GET_SYSTEM_NETWORK_USAGE = 1010;
    public final static int ACTION_GET_CURRENT_STATUS_INFO = 1011;
    public final static int ACTION_GET_PROCESS_MEMORY = 1012;
    public final static int ACTION_GET_AEE_SYSTEM_INFO = 1013;

    public final static int ACTION_CALLBACK_NETWORK_STAT = 2001;

    public final static int ACTION_CMD_MTKLOGGER_ENABLE = 8001;
    public final static int ACTION_CMD_MTKLOGGER_DISABLE = 8002;

    public final static int ACTION_VENDOR_API_GET_DATA_BACK = 9001;

    public final static int ACTION_VENDOR_GET_DATA = 99999;
    // end action list

    private final static String ATTR_COMMAND = "command";
    private final static String ATTR_HISTROY = "history";
    private final static String ATTR_UID = "uid";
    private final static String ATTR_PACKAGE_NAME = "packagename";
    private final static String ATTR_VENDOR = "vendor";
    private final static String ATTR_PID = "pid";
    private final static String ATTR_AMS= "ams";
    private final static String ATTR_THRESHOLD = "threshold";

    public static int getTaskType(String request) {
        try {
            JSONObject jsonRequest = new JSONObject(request);
            if (!jsonRequest.has(ATTR_COMMAND)) {
                if (jsonRequest.has(ATTR_VENDOR)) {
                    return ACTION_VENDOR_GET_DATA;
                } else {
                     return ACTION_NONE;
                }
            } else {
                return jsonRequest.optInt(ATTR_COMMAND, ACTION_NONE);
            }
        } catch(JSONException e) {
            return ACTION_NONE;
        }
    }

    public static String getTaskUidOrPackageName(String request) {
        try {
            JSONObject jsonRequest = new JSONObject(request);
            if (!jsonRequest.has(ATTR_UID) && !jsonRequest.has(ATTR_PACKAGE_NAME)) {
                return "";
            } else {
                if (jsonRequest.has(ATTR_UID)) {
                    return String.valueOf(jsonRequest.optInt(ATTR_UID, 0));
                } else if (jsonRequest.has(ATTR_PACKAGE_NAME)) {
                    return jsonRequest.optString(ATTR_PACKAGE_NAME, "");
                }
                return "";
            }
        } catch(JSONException e) {
            return "";
        }
    }

    public static int getTaskUid(String request) {
        try {
            JSONObject jsonRequest = new JSONObject(request);
            if (!jsonRequest.has(ATTR_UID) && !jsonRequest.has(ATTR_PACKAGE_NAME)) {
                return -1;
            } else {
                if (jsonRequest.has(ATTR_UID)) {
                    return jsonRequest.optInt(ATTR_UID, 0);
                } else if (jsonRequest.has(ATTR_PACKAGE_NAME)) {
                    String packageName = jsonRequest.optString(ATTR_PACKAGE_NAME, "");
                    if (Utils.isValidString(packageName)) {
                        return ApplicationHelper.getUidByPackageName(packageName);
                    }
                }
                return -1;
            }
        } catch(JSONException e) {
            return -1;
        }
    }

    public static boolean isTaskHistorical(String request) {
        try {
            JSONObject jsonRequest = new JSONObject(request);
            if (!jsonRequest.has(ATTR_COMMAND)) {
                return false;
            } else {
                return jsonRequest.optInt(ATTR_HISTROY, 0) > 0;
            }
        } catch(JSONException e) {
            return false;
        }
    }

    public static int[] getTaskPids(String request) {
        try {
            JSONObject jsonRequest = new JSONObject(request);
            if (jsonRequest.has(ATTR_PID)) {
                JSONArray array = jsonRequest.optJSONArray(ATTR_PID);
                if (array != null) {
                    int[] pids = new int[array.length()];
                    for (int i = 0; i < array.length(); i++) {
                        pids[i] = array.getInt(i);
                    }
                    return pids;
                }
            }
            return null;
        } catch(JSONException e) {
            return null;
        }
    }

    public static boolean isAMSMode(String request) {
        try {
            JSONObject jsonRequest = new JSONObject(request);
            if (jsonRequest.has(ATTR_AMS)) {
                int isAMSMode = jsonRequest.optInt(ATTR_AMS);
                return (isAMSMode == 1) ? true : false;
            }
            return true;
        } catch(JSONException e) {
            return true;
        }
    }

    public static int getAEEThreashold(String request) {
        try {
            JSONObject jsonRequest = new JSONObject(request);
            if (jsonRequest.has(ATTR_THRESHOLD)) {
                return jsonRequest.optInt(ATTR_THRESHOLD, 0);
            }
            return 0;
        } catch(JSONException e) {
            return 0;
        }
    }
}
