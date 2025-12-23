package com.mediatek.mbrainlocalservice.helper;

import android.util.Pair;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import android.util.Slog;

public class VendorRequestHelper {
    private static final String TAG = "VendorRequestHelper";
    private static final long API_CACHE_TIME = 10 * 60 * 1000;
    private static final Map<String, String> vendorMap = new HashMap<String, String>() {{
        put("vo", "vendorO");
        put("vv", "vendorV");
        put("vx", "vendorX");
    }};

    private static HashMap<String, Pair<String, Long>> apiData = new HashMap<String, Pair<String, Long>>();

    public static String getVendorData(String vendor) {
        if (!Utils.isValidString(vendor))
            return null;

        if (vendorMap.containsKey(vendor)) {
            return vendorMap.get(vendor);
        }
        return null;
    }

    public static String getVendorMethod(String vendor, String version, String method) {
        if (!Utils.isValidString(vendor) || !Utils.isValidString(version))
            return null;

        if (vendorMap.containsValue(vendor)) {
            return vendor.substring(vendor.length() - 1).toUpperCase() + version.substring(0, 1) + "_" + method;
        }
        return null;
    }

    public static void handleApiDataBack(String data) {
        //Slog.d(TAG, "handleApiDataBack: " + data);
        try {
            JSONObject obj = new JSONObject(data);
            String method = obj.optString("method", "");
            if (Utils.isValidString(method)) {
                long timeStamp = obj.optLong("timeStamp", 0L);
                String resultData = obj.optString("resultData", "") + ",\"timeStamp\":" + timeStamp;
                Pair<String, Long> dataPair = new Pair<>(resultData, System.currentTimeMillis());
                apiData.put(method, dataPair);
            }
        } catch (JSONException e) {
        }
    }

    public static String getApiData(String method) {
        //Slog.d(TAG, "getApiData: " + method);
        try {
            Pair<String, Long> dataPair = apiData.get(method);
            if (dataPair == null) {
                //Slog.d(TAG, "getApiData no method cache");
                return null;
            }
            String apiData = dataPair.first;
            Long lastGetTime = dataPair.second;
            if (System.currentTimeMillis() - lastGetTime < API_CACHE_TIME) {
                //Slog.d(TAG, "getApiData local cache");
                return apiData;
            } else {
                //Slog.d(TAG, "getApiData expired");
                return null;
            }
        } catch (Exception e) {
            //Slog.d(TAG, "getApiData Exception " + e.toString());
            return null;
        }
    }
}