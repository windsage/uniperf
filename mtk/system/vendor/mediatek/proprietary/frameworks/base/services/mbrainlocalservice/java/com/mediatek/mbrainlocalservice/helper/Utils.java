package com.mediatek.mbrainlocalservice.helper;

import android.content.Context;
import android.provider.Settings;
import android.os.Build;
import android.os.SystemProperties;
import java.util.Iterator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Utils {
    private static final String PROP_FIRST_API_LEVEL = "ro.board.first_api_level";
    private static final int REQUIRED_VENDOR_API_LEVEL = 34;
    private static final int VENDOR_API_LEVEL = SystemProperties.getInt("ro.vendor.api_level",
                                                Build.VERSION.DEVICE_INITIAL_SDK_INT);
    // Check vendor image version for VF project.
    private static final int FIRST_API_LEVEL = SystemProperties.getInt(PROP_FIRST_API_LEVEL, REQUIRED_VENDOR_API_LEVEL);

    private static boolean mIsMBrainSupport = false;

    public static boolean isValidString(String str) {
        return str != null && !str.isEmpty();
    }

    public static long convertByteToMByte(long bytes) {
        return bytes/1024/1024;
    }

    public static JSONObject mergeJSONObject(JSONObject... jsonObjects) {
        JSONObject mergedObject = new JSONObject();
        try {
            for (JSONObject obj : jsonObjects) {
                Iterator it = obj.keys();
                while (it.hasNext()) {
                    String key = (String)it.next();
                    mergedObject.put(key, obj.get(key));
                }
            }
            return mergedObject;
        } catch(JSONException e) {
            return mergedObject;
        }
    }

    public static boolean isPassThroughMode(String msg) {
        try {
            JSONObject obj = new JSONObject(msg);
            if (obj.optInt("vendor", 0) == NotifyMessageHelper.E_VENDOR_BYPASS) {
                return true;
            }
            return false;
        } catch(JSONException e) {
            return false;
        }
    }

    public static boolean isVendorFreezeConditionPass() {
        if (FIRST_API_LEVEL < REQUIRED_VENDOR_API_LEVEL) {
            return false;
        }
        return true;
    }

    public static boolean isAirplaneModeOn(Context context) {
        if (context != null) {
            return Settings.Global
            .getInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
        }
        return false;
    }

    public static void setMBrainSupport(boolean isMBrainSupport) {
        mIsMBrainSupport = isMBrainSupport;
    }

    public static boolean isMBrainPropertyEnable() {
        return mIsMBrainSupport;
    }
}
