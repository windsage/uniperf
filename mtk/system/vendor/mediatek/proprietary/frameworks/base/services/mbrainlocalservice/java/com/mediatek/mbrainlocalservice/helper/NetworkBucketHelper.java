package com.mediatek.mbrainlocalservice.helper;

import android.app.usage.NetworkStats;
import android.app.usage.NetworkStats.Bucket;

import android.os.SystemClock;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class NetworkBucketHelper {
    private static long startRecordingTime = 0;
    private static long txBytes = 0;
    private static long rxBytes = 0;
    private static long txPackets = 0;
    private static long rxPackets = 0;

    public static void setStartRecordingTime(long timeStamp) {
        startRecordingTime = timeStamp;
    }

    public static long getStartRecordingTime() {
        return startRecordingTime;
    }

    public static void parseNetworkStats(NetworkStats stats) {
        Bucket bucket = new Bucket();
        resetStatistics();
        while(stats.hasNextBucket() && stats.getNextBucket(bucket)) {
            long bucketStartTime = bucket.getStartTimeStamp();
            long bucketEndTime = bucket.getEndTimeStamp();
            if (bucketStartTime >= startRecordingTime ||
                (bucketStartTime < startRecordingTime && bucketEndTime >= startRecordingTime)){
                txBytes += bucket.getTxBytes();
                rxBytes += bucket.getRxBytes();
                txPackets += bucket.getTxPackets();
                rxPackets += bucket.getRxPackets();
            }
        }
        stats.close();
    }

    private static String getTime(long timeStamp) {
        Date date = new Date(timeStamp);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HH:mm:ss");
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        return sdf.format(date);
    }

    private static void resetStatistics() {
        txBytes = 0;
        rxBytes = 0;
        txPackets = 0;
        rxPackets = 0;
    }

    public static long getTotalBytes() {
        return rxBytes + txBytes;
    }

    public static long getTotalPackets() {
        return rxPackets + txPackets;
    }

    public static long getTxBytes() {
        return txBytes;
    }

    public static long getRxBytes() {
        return rxBytes;
    }

    public static long getTxPackets() {
        return txPackets;
    }

    public static long getRxPackets() {
        return rxPackets;
    }

    public static long getBootTime() {
        long time = System.currentTimeMillis() - SystemClock.elapsedRealtime();
        return System.currentTimeMillis() - SystemClock.elapsedRealtime();
    }
}