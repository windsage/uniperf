package com.mediatek.mbrainlocalservice.helper;

import android.os.Build;
import android.os.SystemProperties;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class TaskResponseHelper {
    public static JSONObject getSucceesTaskBaseResponse() {
        try {
            JSONObject response = new JSONObject();
            response.put("result", 1);
            return response;
        } catch(JSONException e) {
            return null;
        }
    }

    public static String getSucceesTaskResponse() {
        JSONObject response = getSucceesTaskBaseResponse();
        if (response != null) {
            return getSucceesTaskBaseResponse().toString();
        } else {
            return "";
        }
    }

    public static String getFailTaskResponse(String reason) {
        try {
            JSONObject response = new JSONObject();
            response.put("result", 0);
            response.put("reason", reason);
            return response.toString();
        } catch(JSONException e) {
            return "";
        }
    }

    public static String getOSCommonResponse(String androidId, String timeZoneId) {
        try {
            JSONObject response = getSucceesTaskBaseResponse();
            response.put("manufacturer", Build.SOC_MANUFACTURER);
            response.put("model", Build.SOC_MODEL);
            response.put("android_version", Build.VERSION.RELEASE);
            response.put("android_sdk", Build.VERSION.SDK_INT);
            response.put("serial", Build.SERIAL);
            response.put("android_id", androidId);
            response.put("kernel", SystemProperties.get("ro.kernel.version"));
            response.put("product", Build.DEVICE);
            response.put("timezone", timeZoneId);
            return response.toString();
        } catch(JSONException e) {
            return "";
        }
    }

    public static String getSystemUidResponse(JSONObject appInfoObj) {
        if (appInfoObj != null) {
            try {
                JSONObject response = getSucceesTaskBaseResponse();
                if (response != null) {
                    JSONArray appArray = new JSONArray();
                    appArray.put(appInfoObj);
                    response.put("apps", appArray);
                    return response.toString();
                }
            } catch(JSONException e) {
                return "";
            }
        }
        return "";
    }

    public static JSONObject getAppInfoObject(int uid, String packageName, String version, long installTime, long updateTime) {
        try {
            JSONObject response = new JSONObject();
            response.put("uid", uid);
            response.put("packagename", packageName);
            if (version != null && !version.isEmpty()) {
                response.put("version", version);
            }
            if (installTime > 0L) {
                response.put("installtime", installTime);
            }
            if (updateTime > 0L) {
                response.put("updatetime", updateTime);
            }
            return response;
        } catch(JSONException e) {
            return null;
        }
    }
    
    public static JSONObject getProcessInfoObject(int pid, String processName) {
        try {
            JSONObject response = new JSONObject();
            response.put("pid", pid);
            response.put("processname", processName);
            return response;
        } catch(JSONException e) {
            return null;
        }
    }

    public static String getAppResponse(int uid, JSONArray appInfoArray, JSONArray processInfoArray) {
        try {
            JSONObject response = getSucceesTaskBaseResponse();
            if (response != null && appInfoArray != null) {
                response.put("uid", uid);
                response.put("apps", appInfoArray);
                response.put("pids", processInfoArray);
                return response.toString();
            }
        } catch(JSONException e) {
            return "";
        }
        return "";
    }

    public static String getUidInfoResponse(JSONArray uidInfoArray) {
        try {
            JSONObject response = getSucceesTaskBaseResponse();
            if (response != null && uidInfoArray != null) {
                response.put("uids", uidInfoArray);
                return response.toString();
            }
        } catch(JSONException e) {
            return "";
        }
        return "";
    }

    public static String getDisplayInfoResponse(float refreshRate, int screenWidth, int screenHeight, int screenBrightness) {
        try {
            JSONObject response = getSucceesTaskBaseResponse();
            if (response != null) {
                response.put("refresh_rate", (int)refreshRate);
                response.put("screen_width", screenWidth);
                response.put("screen_height", screenHeight);
                response.put("brightness", screenBrightness);
                return response.toString();
            }
        } catch(JSONException e) {
            return "";
        }
        return "";
    }

    public static String getTrafficStatResponse(long txByte, long txPacket, long rxByte, long rxPacket) {
        try {
            JSONObject response = getSucceesTaskBaseResponse();
            if (response != null) {
                response.put("txByte", txByte);
                response.put("txPacket", txPacket);
                response.put("rxByte", rxByte);
                response.put("rxPacket", rxPacket);
                return response.toString();
            }
        } catch(JSONException e) {
            return "";
        }
        return "";
    }

    public static String getChargeInfo(int status, int approach, int level) {
        try {
            JSONObject response = getSucceesTaskBaseResponse();
            if (response != null) {
                response.put("charge_status", status);
                response.put("charge_approach", approach);
                response.put("level", level);
                return response.toString();
            }
        } catch(JSONException e) {
            return "";
        }
        return "";
    }

    public static String getWifiCellularNetworkFailResponse(String reason) {
        return getFailTaskResponse(reason);
    }

    public static String getNoWifiCellularNetworkResponse() {
        try {
            JSONObject response = getSucceesTaskBaseResponse();
            if (response != null) {
                response.put("isConnected", 0);
                return response.toString();
            }
        } catch(JSONException e) {
            return getFailTaskResponse("getNoWifiCellularNetworkResponse failed");
        }
        return "";
    }

    public static String getWifiNetworkResponse(int wifiStandard, int frequency) {
        try {
            JSONObject response = getSucceesTaskBaseResponse();
            if (response != null) {
                response.put("isConnected", 1);
                JSONObject wifiObject = new JSONObject();
                wifiObject.put("standard", wifiStandard);
                wifiObject.put("frequency", frequency);
                response.put("wifiInfo", wifiObject);
                return response.toString();
            }
        } catch(JSONException e) {
            return getFailTaskResponse("getWifiNetworkResponse failed");
        }
        return "";
    }

    public static String getCellularNetworkResponse(int type, int signalStrengthLevel, int simOperator) {
        try {
            JSONObject response = getSucceesTaskBaseResponse();
            if (response != null) {
                response.put("isConnected", 1);
                JSONObject cellularObject = new JSONObject();
                cellularObject.put("type", type);
                cellularObject.put("signal_strength_level", signalStrengthLevel);
                cellularObject.put("sim_operator", simOperator);
                response.put("cellularInfo", cellularObject);
                return response.toString();
            }
        } catch(JSONException e) {
            return getFailTaskResponse("getCellularNetworkResponse failed");
        }
        return "";
    }

    public static String getWifiCellularNetworkResponse(String wifiJson, String cellularJson) {
        try {
            return Utils.mergeJSONObject(new JSONObject(wifiJson), new JSONObject(cellularJson)).toString();
        } catch(JSONException e) {
            return getFailTaskResponse("getWifiCellularNetworkResponse failed");
        }
    }

    public static String getSystemWifiUsageResponse(long txByte, long txPacket, long rxByte, long rxPacket) {
        try {
            JSONObject response = getSucceesTaskBaseResponse();
            if (response != null) {
                response.put("wifiTxByte", txByte);
                response.put("wifiTxPacket", txPacket);
                response.put("wifiRxByte", rxByte);
                response.put("wifiRxPacket", rxPacket);
                return response.toString();
            }
        } catch(JSONException e) {
            return getFailTaskResponse("getSystemWifiUsageResponse failed");
        }
        return "";
    }

    public static String getSystemMobileUsageResponse(long txByte, long txPacket, long rxByte, long rxPacket) {
        try {
            JSONObject response = getSucceesTaskBaseResponse();
            if (response != null) {
                response.put("mobileTxByte", txByte);
                response.put("mobileTxPacket", txPacket);
                response.put("mobileRxByte", rxByte);
                response.put("mobileRxPacket", rxPacket);
                return response.toString();
            }
        } catch(JSONException e) {
            return getFailTaskResponse("getsystemMobileUsageResponse failed");
        }
        return "";
    }

    public static String getSystemNetworkUsageResponse(String wifiJson, String cellularJson) {
        try {
            return Utils.mergeJSONObject(new JSONObject(wifiJson), new JSONObject(cellularJson)).toString();
        } catch(JSONException e) {
            return getFailTaskResponse("getSystemNetworkUsageResponse failed");
        }
    }

    public static String addAirplaneMode(String responseJson, boolean isAirplaneModeOn) {
        try {
            JSONObject response = new JSONObject(responseJson);
            response.put("airplane", (isAirplaneModeOn) ? 1 : 0);
            return response.toString();
        } catch(JSONException e) {
            return getFailTaskResponse("addAirplaneMode failed");
        }
    }

    public static String getBatteryStatusInfo (int level, int status, int approach) {
        try {
            JSONObject response = new JSONObject();
            response.put("level", level);
            response.put("charge_status", status);
            response.put("charge_approach", status);
            return response.toString();
        } catch(JSONException e) {
            return getFailTaskResponse("getBatteryStatusInfo failed");
        }
    }

    public static String getNetworkStatusInfo(boolean wifi, boolean cellular) {
        try {
            JSONObject response = new JSONObject();
            response.put("wifi", (wifi) ? 1 : 0);
            response.put("cellular", (cellular)? 1 : 0);
            return response.toString();
        } catch(JSONException e) {
            return getFailTaskResponse("getNetworkStatusInfo failed");
        }
    }

    public static String getAudioStatusInfo(int deviceType, int volume,
        int volumeMax, int playbackType) {
        try {
            JSONObject callback = new JSONObject();
            callback.put("deviceType", deviceType);
            callback.put("volume", volume);
            callback.put("volumeMax", volumeMax);
            callback.put("playbackType", playbackType);
            return callback.toString();
        } catch(JSONException e) {
            return getFailTaskResponse("getAudioStatusInfo failed");
        }
    }

    public static String getUsbStatusInfo(int connect, int host, String function) {
        try {
            JSONObject callback = new JSONObject();
            callback.put("connect", connect);
            callback.put("host", host);
            callback.put("function", function);
            return callback.toString();
        } catch(JSONException e) {
            return getFailTaskResponse("getUsbStatusInfo failed");
        }
    }

    public static String getSystemStatusInfo(String battery, String network,
        String audio, String usb) {
        try {
            JSONObject response = getSucceesTaskBaseResponse();
            response.put("battery", new JSONObject(battery));
            response.put("network", new JSONObject(network));
            response.put("audio", new JSONObject(audio));
            response.put("usb", new JSONObject(usb));
            return response.toString();
        } catch(JSONException e) {
            return getFailTaskResponse("getUsbStatusInfo failed");
        }
    }

    public static String getProcessMemoryJSON(int pid, int pss,
        int uss, int rss, int swap, int javaHeap, int nativeHeap) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("pid", pid);
            obj.put("pss", pss);
            obj.put("uss", uss);
            obj.put("rss", rss);
            obj.put("swap", swap);
            obj.put("javaHeap", javaHeap);
            obj.put("nativeHeap", nativeHeap);
            return obj.toString();
        } catch(JSONException e) {
            return null;
        }
    }

    public static String getProcessMemory(String[] processes) {
        try {
            JSONObject response = getSucceesTaskBaseResponse();
            JSONArray memoryArray = new JSONArray();
            for(String process : processes) {
                memoryArray.put(new JSONObject(process));
            }
            response.put("process", memoryArray);
            return response.toString();
        } catch(JSONException e) {
            return null;
        }
    }

    public static String getAEEInfo (String db_history, String file_tree) {
        try {
            JSONObject response = new JSONObject();
            response.put("db", db_history);
            response.put("file", file_tree);
            return response.toString();
        } catch(JSONException e) {
            return getFailTaskResponse("getAEEInfo failed");
        }
    }
}
