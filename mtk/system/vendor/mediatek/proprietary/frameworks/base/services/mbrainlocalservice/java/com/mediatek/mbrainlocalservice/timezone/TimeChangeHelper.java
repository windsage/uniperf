package com.mediatek.mbrainlocalservice.timezone;

import android.util.Slog;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class TimeChangeHelper {
    private static final String TAG = "TimeChangeHelper";
    private static String mTimeZoneId = "";

    public static void updateTimeZone() {
        TimeZone tz = TimeZone.getDefault();
        mTimeZoneId = tz.getID();
    }

    public static String getTimeZoneId() {
        return mTimeZoneId;
    }

    public static String getCurrentDateAndTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        Slog.d(TAG, "getCurrentDateAndTime:" + sdf.format(new Date()));
        return sdf.format(new Date());
    }
}
