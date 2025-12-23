package com.mediatek.smartplatform;

import java.util.LinkedHashMap;
import android.text.TextUtils;
import android.util.Log;


/**
     * use for onVideoTaken params
     */
public class VideoInfoMap {
    public static final String KEY_RECORDER_EVENT_TYPE = "event_type";
    public static final String KEY_RECORDER_CAMERA_ID = "camera_id";
    public static final String KEY_RECORDER_RECORDER_SOURCE = "recorder_source";
    public static final String KEY_RECORDER_COMMNENT = "comment";
    public static final String KEY_RECORDER_FILE_PATH = "file_path";
    public static final String KEY_RECORDER_START_TIME = "start_time";
    public static final String KEY_RECORDER_END_TIME = "end_time";
    public static final String KEY_RECORDER_DURATION = "duration";
    public static final String KEY_RECORDER_ENCRYPT_TYPE = "encrypt_type";

    private LinkedHashMap<String, Object> mMap;
    private String TAG = "VideoInfoMap";

    VideoInfoMap() {
        mMap = new LinkedHashMap<String, Object>(128);
    }

    /**
          * Returns the value of an  parameter.
          *
          * @param key the key name for the parameter
          * @return the int value of the parameter
          */
    public Object get(String key) {
        return mMap.get(key);
    }

    /**
          * Returns the value of an string parameter.
          *
          * @param key the key name for the parameter
          * @return the int value of the parameter
          */
    public String getString(String key) {
        Object value = mMap.get(key);
        if (value == null) {
            Log.e(TAG, "has no Key:" + key);
            return null;
        }
        return String.valueOf(value);
    }

    public String getString(String key, String defaultValue) {
        Object value = mMap.get(key);
        if (value == null) {
            Log.e(TAG, "has no Key:" + key + " return defaultValue:" + defaultValue);
            return defaultValue;
        }
        return String.valueOf(value);
    }

    /**
          * Returns the value of an integer parameter.
          *
          * @param key the key name for the parameter
          * @return the int value of the parameter
          */
    public int getInt(String key) {
        Object value = mMap.get(key);
        if (value == null) {
            Log.e(TAG, "has no Key:" + key);
            return -1;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            Log.e(TAG, "invalid Key:" + key + " value:" + value);
            return -1;
        }
    }

    /**
            * Returns the value of an integer parameters.
            */
    public int getInt(String key, int defaultValue) {
        Object value = mMap.get(key);
        if (value == null) {
            Log.e(TAG, "has no Key:" + key + " return defaultValue:" + defaultValue);
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            Log.e(TAG, "invalid Key:" + key + " value:" + value + " return defaultValue:" + defaultValue);
            return defaultValue;
        }
    }

    /**
          * Returns the value of an long parameter.
          *
          * @param key the key name for the parameter
          * @return the int value of the parameter
          */
    public long getLong(String key) {
        Object value = mMap.get(key);
        if (value == null) {
            Log.e(TAG, "has no Key:" + key);
            return -1;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            Log.e(TAG, "invalid Key:" + key + " value:" + value);
            return -1;
        }
    }

    /**
          * Returns the value of an long parameter.
          *
          * @param key the key name for the parameter
          * @return the int value of the parameter
          */
    public long getLong(String key, long defaultValue) {
        Object value = mMap.get(key);
        if (value == null) {
            Log.e(TAG, "has no Key:" + key + " return defaultValue:" + defaultValue);
            return -1;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            Log.e(TAG, "invalid Key:" + key + " value:" + value + " return defaultValue:" + defaultValue);
            return defaultValue;
        }
    }

    public String toString() {
        return mMap.toString();
    }

    /**
          * Takes a flattened string of parameters and adds each one to this
          * Parameters object.
          * <p>
          * The {@link #flatten()} method does the reverse.
          * </p>
          *
          * @param flattened
          *           a String of parameters (key-value paired) that are
          *           semi-colon delimited
          */
    void unflatten(String flattened) {
        mMap.clear();
        TextUtils.StringSplitter splitter = new TextUtils.SimpleStringSplitter(';');
        splitter.setString(flattened);

        for (String kv : splitter) {
            int pos = kv.indexOf('=');

            if (pos == -1) {
                continue;
            }

            String k = kv.substring(0, pos);
            String v = kv.substring(pos + 1);
            mMap.put(k, v);
        }
    }

    /**
         * Creates a single string with all the parameters set in this
         * Parameters object.
         * <p>
         * The {@link #unflatten(String)} method does the reverse.
         * </p>
         *
         * @return a String with all values from this Parameters object, in
         *  semi-colon delimited key-value pairs
         */
    String flatten() {
        StringBuilder flattened = new StringBuilder(128);

        for (String k : mMap.keySet()) {
            flattened.append(k);
            flattened.append("=");
            flattened.append(mMap.get(k));
            flattened.append(";");
        }

        // chop off the extra semicolon at the end
        flattened.deleteCharAt(flattened.length() - 1);
        return flattened.toString();
    }

    void remove(String key) {
        mMap.remove(key);
    }

    private void put(String key, Object value) {
        mMap.remove(key);
        mMap.put(key, value);
    }

    /**
            * Sets an string parameter.
            *
            * @param key
            *    the key name for the parameter
            * @param value
            *    the int value of the parameter
            */
    void set(String key, Object value) {
        if (key == null || value == null) {
            Log.e(TAG, "key or value is null, key:" + key + ", value:" + value);
            return;
        }
        if (key.indexOf('=') != -1 || key.indexOf(';') != -1 || key.indexOf(0) != -1) {
            Log.e(TAG, "Key \"" + key + "\" contains invalid character (= or ; or \\0)");
            return;
        }
        if (String.valueOf(value).indexOf('=') != -1 || String.valueOf(value).indexOf(';') != -1 || String.valueOf(value).indexOf(0) != -1) {
            Log.e(TAG, "Value \"" + value + "\" contains invalid character (= or ; or \\0)");
            return;
        }
        put(key, value);
    }
}


