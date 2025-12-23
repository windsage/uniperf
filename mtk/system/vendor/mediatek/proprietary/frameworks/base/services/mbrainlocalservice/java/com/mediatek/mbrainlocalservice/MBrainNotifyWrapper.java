package com.mediatek.mbrainlocalservice;

public class MBrainNotifyWrapper {
    private static MBrainNotifyWrapper sInstance = null;
    private static Object lock = new Object();
    private static AmsResumeListener mListener = null;

    public static MBrainNotifyWrapper getInstance() {
        synchronized (lock) {
            if (null == sInstance) {
                sInstance = new MBrainNotifyWrapper();
            }
            return sInstance;
        }
    }

    private MBrainNotifyWrapper() {

    }

    public void activityChange(boolean isOnTop, int uid, String packageName, int pid) {
        if (mListener != null) {
            mListener.notifyAMSChange(isOnTop, uid, packageName, pid);
        }
    }

    public void recordAppLaunch(String packageName, int launchTimeInMs, int launchState) {
        if (mListener != null) {
            mListener.notifyAppLaunchState(packageName, launchTimeInMs, launchState);
        }
    }

    public void recordAppDied(String packageName, String reason) {
        if (mListener != null) {
            mListener.notifyAppProcessDied(packageName, reason);
        }
    }

    public void setAmsResumeListener(AmsResumeListener listener) {
        if (listener != null) {
            mListener = listener;
        }
    }

    public interface AmsResumeListener {
        public void notifyAMSChange(boolean isOnTop, int uid, String packageName, int pid);
        public void notifyAppLaunchState(String packageName, int launchTimeInMs, int launchState);
        public void notifyAppProcessDied(String packageName, String reason);
    }
}
