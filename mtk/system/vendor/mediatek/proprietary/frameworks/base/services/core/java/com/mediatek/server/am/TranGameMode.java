//T-HUB Core[SPD]: add GameModeList For MTK by mingyang.liao 20251010 start
package com.mediatek.server.am;
import com.mediatek.powerhalwrapper.PowerHalWrapper;
import android.os.SystemProperties;
import android.util.Slog;
import com.transsion.hubcore.server.am.ITranActivityManagerService;

public class TranGameMode {
    private PowerHalWrapper mPowerHalWrap = PowerHalWrapper.getInstance();
    private final Object mGameInfosLock = new Object();
    private String mGameInfos = "";
    private boolean isSupportGameMode = false;
    private static final String TAG = "setGameMode";

    public TranGameMode() {
        float kernelVersion = 0;
        try {
            kernelVersion = Float.parseFloat(SystemProperties.get("ro.kernel.version"));
        } catch (Exception e) {
            Slog.e(TAG, e.toString());
        }

        if (kernelVersion >= 5.15 && mPowerHalWrap != null &&  ITranActivityManagerService.Instance() != null) {
            isSupportGameMode = true;
        } else {
            isSupportGameMode = false;
        }
        Slog.d(TAG, "kernelVersion: " + kernelVersion + " isSupportGameMode: " + isSupportGameMode);
    }

    public boolean setGameModeEnabled(String packageName, int pid, String activityName, boolean enabled, boolean isDebug) {
        Slog.d(TAG, "enter setGameModeEnabled");
        if (isSupportGameMode && ITranActivityManagerService.Instance().isContainsPackage("mtkGameScene", packageName)) {
            if (enabled) {
                enableGameMode(packageName, pid, activityName, isDebug);
            } else {
                disableGameMode(packageName, pid, activityName, isDebug);
            }
        }
        return true;
    }

    private void enableGameMode(String packageName, int pid, String activityName, boolean isDebug) {
        synchronized (mGameInfosLock) {
            Slog.d(TAG, "current game app: " + packageName + " is on top, enable GameMode.");
            mPowerHalWrap.mtkGameModeEnable(1, pid);
            mGameInfos = packageName + "," + pid + "," + activityName;
            if (isDebug) {
                Slog.d(TAG, "mGameInfos add: " + packageName + "," + pid + "," + activityName);
            }
        }
    }

    private void disableGameMode(String packageName, int pid, String activityName, boolean isDebug) {
        synchronized (mGameInfosLock) {
            String value = packageName + "," + pid + "," + activityName;
            if (value.equals(mGameInfos)) {
                if (isDebug) {
                    Slog.d(TAG, "mGameInfos remove: " + packageName + "," + pid + "," + activityName);
                }
                Slog.d(TAG, "current game app: " + packageName + " leave, close GameMode.");
                mPowerHalWrap.mtkGameModeEnable(0, pid);
                mGameInfos = "";
            }
        }
    }
}
//T-HUB Core[SPD]: add GameModeList For MTK by mingyang.liao 20251010 end