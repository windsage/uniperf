package com.mediatek.mbrainlocalservice.helper;

import android.content.pm.PackageInfo;

import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class NotifyMessageHelper {
    // from MBTypes.h
    public static final int E_VENDOR_BYPASS = 6;

    public static String generateChargeChangeNotifyMsg(int chargeStatus, int chargeApproach, int level) {
        try {
            JSONObject callback = new JSONObject();
            callback.put("charge_status", chargeStatus);
            callback.put("charge_approach", chargeApproach);
            callback.put("level", level);
            if (!Utils.isMBrainPropertyEnable()) {
                 callback.put("vendor", E_VENDOR_BYPASS);
            }
            return callback.toString();
        } catch(JSONException e) {
            return "";
        }
    }

    public static String generateAppSwitchNotifyMsg(
        boolean isForeground, int uid, String packageName, int pid) {
        try {
            JSONObject callback = new JSONObject();
            callback.put("state", (isForeground) ? 1 : 0);
            callback.put("uid", uid);
            callback.put("packagename", packageName);
            callback.put("pid", pid);
            if (!Utils.isMBrainPropertyEnable()) {
                 callback.put("vendor", E_VENDOR_BYPASS);
            }
            return callback.toString();
        } catch(JSONException e) {
            return "";
        }
    }

    public static String generateNetworkChangeNotifyMsg(boolean isWifiOn, boolean isCellularOn, boolean isAirPlaneModeOn) {
        try {
            JSONObject callback = new JSONObject();
            callback.put("wifi", (isWifiOn) ? 1 : 0);
            callback.put("cellular", (isCellularOn) ? 1 : 0);
            callback.put("airplane", (isAirPlaneModeOn) ? 1 : 0);
            return callback.toString();
        } catch(JSONException e) {
            return "";
        }
    }

    public static String generateAppStateChangeNotifyMsg(int state, int uid, String packageName, String versionName) {
        try {
            JSONObject callback = new JSONObject();
            callback.put("state", state);
            callback.put("uid", uid);
            callback.put("packagename", packageName);
            if (Utils.isValidString(versionName)) {
                callback.put("version", versionName);
            }
            return callback.toString();
        } catch(JSONException e) {
            return "";
        }
    }

    public static String generateUsbChangeNotifyMsg(int isConnect, int isHost, String function) {
        try {
            JSONObject callback = new JSONObject();
            callback.put("connect", isConnect);
            callback.put("host", isHost);
            callback.put("function", function);
            if (!Utils.isMBrainPropertyEnable()) {
                 callback.put("vendor", E_VENDOR_BYPASS);
            }
            return callback.toString();
        } catch(JSONException e) {
            return "";
        }
    }

    public static String generateScreenChangeNotifyMsg(int screen) {
        try {
            JSONObject callback = new JSONObject();
            callback.put("screen", screen);
            if (!Utils.isMBrainPropertyEnable()) {
                 callback.put("vendor", E_VENDOR_BYPASS);
            }
            return callback.toString();
        } catch(JSONException e) {
            return "";
        }
    }

    public static String generateTimeChangeNotifyMsg(int status, String timezoneId, String currentDateTime) {
        try {
            JSONObject callback = new JSONObject();
            callback.put("status", status);
            callback.put("timezone", timezoneId);
            callback.put("current_time", currentDateTime);
            return callback.toString();
        } catch(JSONException e) {
            return "";
        }
    }

    public static String generateWifiInfoNotifyMsg(int wifiStandard, int frequency,
    int signalCombo) {
        try {
            JSONObject callback = new JSONObject();
            JSONObject wifiObject = new JSONObject();
            wifiObject.put("standard", wifiStandard);
            wifiObject.put("signal_combo", signalCombo);
            wifiObject.put("frequency", frequency);
            callback.put("wifiInfo", wifiObject);
            return callback.toString();
        } catch(JSONException e) {
            return "";
        }
    }

    public static String generateCellularInfoNotifyMsg(int type, int simOperator, int signalCombo) {
        try {
            JSONObject callback = new JSONObject();
            JSONObject cellularObject = new JSONObject();
            cellularObject.put("type", type);
            cellularObject.put("signal_combo", signalCombo);
            cellularObject.put("sim_operator", simOperator);
            callback.put("cellularInfo", cellularObject);
            return callback.toString();
        } catch(JSONException e) {
            return "";
        }
    }

    public static String generateAudioRouteNotifyMsg(int deviceType, int volume, int volumeMax, int playbackType) {
        try {
            JSONObject callback = new JSONObject();
            callback.put("deviceType", deviceType);
            callback.put("volume", volume);
            callback.put("volumeMax", volumeMax);
            callback.put("playbackType", playbackType);
            return callback.toString();
        } catch(JSONException e) {
            return "";
        }
    }

    public static String generateMtkDbgNotifyMsg(int seq, String vendorType, String licensePath, String method) {
        try {
            JSONObject callback = new JSONObject();
            callback.put("seq", seq);
            callback.put("vendor", vendorType);
            callback.put("licenseData", licensePath);
            callback.put("method", method);
            return callback.toString();
        } catch(JSONException e) {
            return "";
        }
    }

    public static String generateTelephonyRecord(int slotId, int dataConnectionState,
    int serviceState, int signalStrength, boolean userDataEnableState, boolean dataEnableForApn,
    int simState, int radioTech, int subId, int defaultDataSubId) {
        try {
            JSONObject record = new JSONObject();
            record.put("timestamp", System.currentTimeMillis());
            record.put("slotId", slotId);
            record.put("dataConnectionState", dataConnectionState);
            record.put("serviceState", serviceState);
            record.put("signalStrength", signalStrength);
            record.put("userDataEnableState", (userDataEnableState) ? 1 : 0);
            record.put("dataEnableForApn", (dataEnableForApn) ? 1 : 0);
            record.put("simState", simState);
            record.put("radioTech", radioTech);
            record.put("subId", subId);
            record.put("defaultDataSubId", defaultDataSubId);
            return record.toString();
        } catch(JSONException e) {
            return "";
        }
    }

    public static String generateTelephonyStateNotifyMessage(List<String> telephonyRecord) {
        try {
            JSONArray recordArray = new JSONArray();
            for (int i = 0; i < telephonyRecord.size(); i++) {
                JSONObject record = new JSONObject(telephonyRecord.get(i));
                recordArray.put(record);
            }
            return recordArray.toString();
        } catch(JSONException e) {
            return "";
        }
    }

    public static String generateDozeModeNotifyMessage(boolean isDeviceLightDoze,
    boolean isDeviceDeepDoze, boolean isPowerSaveMode) {
        try {
            int type = 0;
            if (isDeviceDeepDoze) {
                type = 2;
            } else if (isDeviceLightDoze) {
                type = 1;
            }

            JSONObject record = new JSONObject();
            record.put("type", type);
            record.put("powerSaveMode", (isPowerSaveMode) ? 1 : 0);
            if (!Utils.isMBrainPropertyEnable()) {
                 record.put("vendor", E_VENDOR_BYPASS);
            }
            return record.toString();
        } catch(JSONException e) {
            return "";
        }
    }

    public static String generateAudioConfigRecord(long time, int uid, String packageName,
    int pid, int state) {
        try {
            JSONObject record = new JSONObject();
            record.put("timestamp", time);
            record.put("uid", uid);
            record.put("name", packageName);
            record.put("pid", pid);
            record.put("state", state);
            return record.toString();
        } catch(JSONException e) {
            return null;
        }
    }

    public static String generateAudioConfigNotifyMessage(List<String> records) {
        try {
            JSONArray recordArray = new JSONArray();
            for (int i = 0; i < records.size(); i++) {
                JSONObject record = new JSONObject(records.get(i));
                recordArray.put(record);
            }

            JSONObject callback = new JSONObject();
            callback.put("type", 1);
            callback.put("data", recordArray);
            return callback.toString();
        } catch(JSONException e) {
            return null;
        }
    }

    public static String generateAudioModeNotifyMessage(int mode) {
        try {
            JSONObject callback = new JSONObject();
            callback.put("type", 2);
            callback.put("mode", mode);
            return callback.toString();
        } catch(JSONException e) {
            return null;
        }
    }

    public static String generateAppLifeCycleNotifyMessage(String packageName, int state,
    String reason, int time) {
        try {
            JSONObject callback = new JSONObject();
            callback.put("packagename", packageName);
            callback.put("state", state);
            callback.put("reason", reason);
            callback.put("time", time);
            if (!Utils.isMBrainPropertyEnable()) {
                 callback.put("vendor", E_VENDOR_BYPASS);
            }
            return callback.toString();
        } catch(JSONException e) {
            return null;
        }
    }
}
