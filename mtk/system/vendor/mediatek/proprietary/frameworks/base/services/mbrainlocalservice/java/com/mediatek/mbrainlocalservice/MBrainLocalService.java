package com.mediatek.mbrainlocalservice;

import com.android.server.LocalServices;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.IActivityManager;
import android.app.IUidObserver;
import android.app.usage.NetworkStats;
import android.app.usage.NetworkStats.Bucket;
import android.app.usage.NetworkStatsManager;
import static android.app.WaitResult.LAUNCH_STATE_COLD;
import static android.app.WaitResult.LAUNCH_STATE_WARM;
import static android.app.WaitResult.LAUNCH_STATE_HOT;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;

import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.ApplicationInfo;

import android.hardware.usb.UsbManager;

import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.media.MediaRouter;
import android.media.MediaRouter.RouteGroup;
import android.media.MediaRouter.RouteInfo;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.TrafficStats;
import android.net.Uri;
import android.net.wifi.WifiManager;

import android.net.NetworkCapabilities;
import android.net.TransportInfo;
import android.net.wifi.WifiInfo;

import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.PowerManager;
import android.os.PowerManager.ServiceType;
import android.os.PowerManagerInternal;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.data.ApnSetting;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.ims.feature.MmTelFeature;
import android.telephony.ims.feature.MmTelFeature.MmTelCapabilities;
import android.telephony.ims.ImsReasonInfo;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.telephony.SignalStrength;
import android.telephony.ServiceState;
import android.text.TextUtils;
import android.view.Display;
import android.view.WindowManager;
import android.util.DisplayMetrics;
import android.util.Slog;


import com.android.ims.ImsConnectionStateListener;
import com.android.ims.ImsException;
import com.android.ims.ImsServiceClass;
import com.android.ims.ImsManager;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;

import com.android.server.power.ShutdownThread;
import com.android.server.SystemService;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.IOException;

import java.lang.NumberFormatException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.HashMap;
import java.util.List;
import java.util.NoSuchElementException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.mediatek.mbrainlocalservice.usb.UsbIntentReceiver;
import com.mediatek.mbrainlocalservice.usb.UsbHelper;
import com.mediatek.mbrainlocalservice.screen.ScreenIntentReceiver;
import com.mediatek.mbrainlocalservice.screen.ScreenHelper;
import com.mediatek.mbrainlocalservice.charge.ChargeIntentReceiver;
import com.mediatek.mbrainlocalservice.appstate.AppStateIntentReceiver;
import com.mediatek.mbrainlocalservice.charge.ChargeHelper;
import com.mediatek.mbrainlocalservice.timezone.TimeChangeIntentReceiver;
import com.mediatek.mbrainlocalservice.timezone.TimeChangeHelper;
import com.mediatek.mbrainlocalservice.helper.*;
import com.mediatek.mbrainlocalservice.helper.NetworkBucketHelper;

import vendor.mediatek.hardware.mbrain.MBrain_Parcelable;

import vendor.mediatek.hardware.mbrainj.IMBrainJava;

import android.os.Debug.MemoryInfo;
import android.os.Debug;

public class MBrainLocalService extends SystemService implements MBrainInfoCallback {
    private final String TAG = "MBrainLocalService";
    private MBrainServiceWrapperAidl mBrainServiceWrapper;
    private Context mContext;
    private ActivityManager mActivityManager;
    private NetworkStatsManager mNetStatsManager;
    private TelephonyManager mTelephonyManager;
    private ConnectivityManager mConnectivityManager;
    private WifiManager mWifiManager;
    private AudioManager mAudioManager;
    private MediaRouter mRouter;

    private List<RunningAppProcessInfo> mRunningAppProcesslist;

    private String mAndroidId;
    private float mScreenRefreshRate;
    private int mScreenWidth;
    private int mScreenHeight;
    private boolean mIsWifiNetwork = false;
    private boolean mIsCellularNetwork = false;
    private Network curWifiNetwork = null;

    private final int APP_SWITCH_MIN_INTERVAL = 50;
    private boolean isLaucherCheckerEnabled = false;
    private boolean isFirstTimeFromScreenOff = false;
    private long preForegroundAppSwitchNotifyTimeStamp = -1;

    private CountDownLatch countDownLatch = new CountDownLatch(1);

    private MBrainNotifyWrapper mMBrainNotifyWrapper = null;
    private boolean mIsUsingAMSChange = false;

    private final int DEFAULT_SIGNAL_STRENGTH = 100;
    private final int DEFAULT_SIGNAL_LEVEL = -1;
    // e.g: combo = -4055 => level: 4, dbm : -55
    // e.g: combo = -1105 => level: 1, dbm : -105
    private final int SIGNAL_COMBO_MULTIPLIER = -1000;

    // -150 < signalstrength < 0
    private int wifiSignalStrength = DEFAULT_SIGNAL_STRENGTH;
    private int mobileSignalStrength = DEFAULT_SIGNAL_STRENGTH;
    // max signal level (by system) < signal level < 0
    private int wifiSignalLevel = -1;
    private int mobileSignalLevel = -1;

    private String AEE_DB_HISTORY_PATH = "/data/aee_exp/db_history";
    private String AEE_FILE_TREE_PATH = "/data/aee_exp/file_tree.txt";

    private final int UID_3RD_APP_BEGIN = 10000;

    //test
    private final int TEST_BROWSER_UID = 10106;
    private final String TEST_BROWSER_PACKAGENAME = "com.android.browser";

    public MBrainLocalService(Context context) {
        super(context);
        mContext = context;
        mMBrainNotifyWrapper = MBrainNotifyWrapper.getInstance();
    }

    @Override
    public void onStart() {
        registerMBrainCallback();
        registerScreenReceiver();
        registerUsbReceiver();
        registerChargeReceiver();
        registerAppStateReceiver();
        registerTimeChangeReceiver();
        registerShutDownRebootReceiver();
        registerWifiCellularListener();
        registerWifiSignalStrengthListener();
        registerDefaultDataSubscriptionListener();
        registerTelephonyListener();
        registerDozeModeReceiver();
        registerActivityManager();
        registerMediaRouter();
        registerAudioCodecListener();
        registerAirplaneModeListener();
        if (mMBrainNotifyWrapper != null) {
            mMBrainNotifyWrapper.setAmsResumeListener(new MBrainNotifyWrapper.AmsResumeListener() {
                int lastTopUid = -1;
                int lastBackgroundUid = -1;
                @Override
                public void notifyAMSChange(boolean isOnTop, int uid, String packageName, int pid) {
                    // Slog.d(TAG, "calling notifyAMSChange(), isOnTop: " + isOnTop +
                    // ", uid: " + uid + ", packageName: " + packageName + ", pid: " + pid);
                    mIsUsingAMSChange = true;
                    if (isLaucherCheckerEnabled) {
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                if (isOnTop && lastTopUid == uid ||
                                    !isOnTop && lastBackgroundUid == uid) {
                                    return;
                                }
                                if (isOnTop) {
                                    lastTopUid = uid;
                                } else {
                                   lastBackgroundUid = uid;
                                }

                                notifyAppSwitch(isOnTop, uid, packageName, pid);
                            }
                        });
                    }
                }

                @Override
                public void notifyAppLaunchState(String packageName, int launchTimeInMs,
                int launchState) {
                    // Slog.d(TAG, "calling notifyAppLaunchState(), name: " + packageName +
                    // ", time: " + launchTimeInMs + ", launchState: " + launchState);
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            notifyAppLifeCycle(
                                NotifyMessageHelper.generateAppLifeCycleNotifyMessage(
                                    packageName, launchState, "", launchTimeInMs));
                        }
                    });
                }

                @Override
                public void notifyAppProcessDied(String packageName, String reason) {
                    // Slog.d(TAG, "calling notifyAppProcessDied(), name: " + packageName +
                    // ", reason: " + reason);
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            notifyAppLifeCycle(
                                NotifyMessageHelper.generateAppLifeCycleNotifyMessage(
                                    packageName, -1, reason, 0));
                        }
                    });

                }
            });
        }
    }

    private void initializeAfterMBrainCallbackEstablished() {
        publishMBrainJava();

        getBuildInfo();
        updateDisplayInfo();
        refreshInstalledPackageInfo();
        refreshAudioMode();
    }

    private void initAndroidId() {
        // removed
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == SystemService.PHASE_BOOT_COMPLETED) {
            NetworkBucketHelper.setStartRecordingTime(System.currentTimeMillis());
            //testTask();
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    isLaucherCheckerEnabled = true;
                }
            }, 5000);
        }
    }

    private String getTrafficStats(int uid) {
        NetworkStats wifiNetStats = getWifiStatsbyUid(uid);
        NetworkStats cellularNetStats = getCellularStatsbyUid(uid);

        long totalWifiTxBytes = 0;
        long totalCellularTxBytes = 0;
        long totalWifiRxBytes = 0;
        long totalCellularRxBytes = 0;
        long totalWifiTxPackets = 0;
        long totalCellularTxPackets = 0;
        long totalWifiRxPackets = 0;
        long totalCellularRxPackets = 0;

        if (wifiNetStats == null) {
            Slog.e(TAG, "wifiNetStats cannot be retrieved");
        } else {
            NetworkBucketHelper.parseNetworkStats(wifiNetStats);
            totalWifiRxBytes = NetworkBucketHelper.getRxBytes();
            totalWifiTxBytes = NetworkBucketHelper.getTxBytes();
            totalWifiTxPackets = NetworkBucketHelper.getTxPackets();
            totalWifiRxPackets = NetworkBucketHelper.getRxPackets();
        }

        if (cellularNetStats == null) {
            Slog.e(TAG, "cellularNetStats cannot be retrieved");
        } else {
            NetworkBucketHelper.parseNetworkStats(cellularNetStats);
            totalCellularTxBytes = NetworkBucketHelper.getTxBytes();
            totalCellularRxBytes = NetworkBucketHelper.getRxBytes();
            totalCellularTxPackets = NetworkBucketHelper.getTxPackets();
            totalCellularRxPackets = NetworkBucketHelper.getRxPackets();
        }

        return TaskResponseHelper.getTrafficStatResponse(
            totalWifiTxBytes + totalCellularTxBytes ,
            totalWifiTxPackets + totalCellularTxPackets,
            totalWifiRxBytes + totalCellularRxBytes,
            totalWifiRxPackets + totalCellularRxPackets);
    }
    boolean isMBrainJavaLaunched = false;
    private void publishMBrainJava() {
        if (Utils.isVendorFreezeConditionPass()) {
            String mbrainMode = SystemProperties.get("ro.vendor.mbrain.mode", "none");
            if ("mtk".equals(mbrainMode) == false || isMBrainJavaLaunched) {
                return;
            }

            try {
                String serviceName = IMBrainJava.DESCRIPTOR + "/default";
                publishBinderService(serviceName, new MBrainJava());
                isMBrainJavaLaunched = true;
                //Slog.d(TAG, "MBrainJava launch succeed");
            } catch (SecurityException e) {
                isMBrainJavaLaunched = false;
                Slog.e(TAG, "MBrainJava launch failed: SecurityException");
            } catch (IllegalArgumentException e) {
                isMBrainJavaLaunched = false;
                Slog.d(TAG, "MBrainJava launch failed: IllegalArgumentException");
            }
        }
    }

    private final IUidObserver mUidObserver = new IUidObserver.Stub() {
        private HashMap<Integer, Integer> uidProcStateMap = new HashMap<>();
        private boolean isFirstNonLauncherAppLaunched = false;
        private int lastNotifyForegroundUid;
        @Override
        public void onUidStateChanged(int uid, int procState, long procStateSeq, int capability) {
            boolean isLauncher = ApplicationHelper.isLauncher(uid);
            uidProcStateMap.put(uid, procState);
            //Slog.d(TAG, "IUidObserver onUidStateChanged uid: " + uid + ", procState: " + procState + ", packageName: " + ApplicationHelper.getPackageNameFromUid(uid) + ", isLauncher: " + isLauncher);
            if (isForegroundUid(procState)) {
                //Slog.d(TAG, "state top uid: " + uid + ", packageName: " + ApplicationHelper.getPackageNameFromUid(uid));

                publishMBrainJava();
                if (!isLauncher && isLaucherCheckerEnabled && !isFirstNonLauncherAppLaunched) {
                    isFirstNonLauncherAppLaunched = true;
                    // first non-laucher app notify
                    if (notifyAppSwitch(true, uid)) {
                        lastNotifyForegroundUid = uid;
                    }
                } else if (isFirstTimeFromScreenOff && isFirstNonLauncherAppLaunched) {
                    isFirstTimeFromScreenOff = false;
                    // for screen off case
                    if (notifyAppSwitch(true, uid)) {
                        lastNotifyForegroundUid = uid;
                    }
                } else if (lastNotifyForegroundUid == -1) {
                    // for uid gone case
                    if (notifyAppSwitch(true, uid)) {
                        lastNotifyForegroundUid = uid;
                    }
                }
            } else if (isBackgroundUid(procState, isLauncher)) {
                //Slog.d(TAG, "state background uid: " + uid + ", packageName: " + ApplicationHelper.getPackageNameFromUid(uid));
                notifyAppSwitch(false, uid);
            }
        }

        private boolean isForegroundUid(int procState) {
            return procState == ActivityManager.PROCESS_STATE_TOP;
        }

        private boolean isBackgroundUid(int procState, boolean isLauncher) {
             return procState == ActivityManager.PROCESS_STATE_LAST_ACTIVITY || (isLauncher && procState == ActivityManager.PROCESS_STATE_BOUND_FOREGROUND_SERVICE);
        }

        @Override
        public void onUidGone(int uid, boolean disabled) {
            //Slog.d(TAG, "IUidObserver onUidGone: " + uid + ", packageName: " + ApplicationHelper.getPackageNameFromUid(uid));
            lastNotifyForegroundUid = -1;
        }

        @Override
        public void onUidActive(int uid) {
            //Slog.d(TAG, "IUidObserver onUidActive: " + uid + ", packageName: " + ApplicationHelper.getPackageNameFromUid(uid));
        }

        @Override
        public void onUidIdle(int uid, boolean disabled) {
            //Slog.d(TAG, "IUidObserver onUidIdle: " + uid);
        }

        @Override
        public void onUidCachedChanged(int uid, boolean cached) {
            //Slog.d(TAG, "IUidObserver onUidCachedChanged: " + uid);
        }

        @Override
        public void onUidProcAdjChanged(int uid, int adj) {
            if (adj == 0 && isFirstNonLauncherAppLaunched) {
                //Slog.d(TAG, "IUidObserver onUidProcAdjChanged() curForegroundId = " + uid + ", app: " + ApplicationHelper.getPackageNameFromUid(uid));
                Integer procState = uidProcStateMap.get(uid);
                boolean isUidTop = (procState == null) ? false : isForegroundUid(procState);
                if (isUidTop && lastNotifyForegroundUid != uid && notifyAppSwitch(true, uid)) {
                    lastNotifyForegroundUid = uid;
                }
            }
        }
    };

    private boolean notifyAppSwitch(boolean isForeground, int uid) {
        if (mIsUsingAMSChange == false) {
            return notifyAppSwitch(isForeground, uid, null, 0);
        }
        return false;
    }


    private boolean notifyAppSwitch(boolean isForeground, int uid, String packageName, int pid) {
        // notifyAppSwitch() foreground trigger case
        // 1.  onUidProcAdjChanged() -> normal case
        // 2.1 onUidStateChanged() -> first non launcher app
        // 2.2 onUidStateChanged() -> first time after screen on
        long curTimeStamp = System.currentTimeMillis();
        if (curTimeStamp - preForegroundAppSwitchNotifyTimeStamp < 0) {
            //time might be changed, reset it.
            preForegroundAppSwitchNotifyTimeStamp = -1;
        }
        if (isForeground && curTimeStamp - preForegroundAppSwitchNotifyTimeStamp < APP_SWITCH_MIN_INTERVAL) {
            //Slog.d(TAG, "notifyAppSwitch failed: too close");
            return false;
        } else {
            if (isMBrainServiceWrapperInitialized()) {
                try {
                    if (packageName == null) {
                        packageName = ApplicationHelper.getPackageNameFromUid(uid);
                    }

                    String notifyInfo = NotifyMessageHelper
                        .generateAppSwitchNotifyMsg(isForeground, uid, packageName, pid);
                    if (Utils.isValidString(notifyInfo)) {
                        //Slog.d(TAG, "notifyAppSwitch: " + notifyInfo);
                        if (isForeground) {
                            preForegroundAppSwitchNotifyTimeStamp = curTimeStamp;
                        }
                        mBrainServiceWrapper.notifyAppSwitchToService(notifyInfo);
                    }
                    return true;
                } catch (RemoteException e) {
                    Slog.e(TAG, "notifyAppSwitch RemoteException");
                }
            } else {
                Slog.e(TAG, "notifyAppSwitch failed");
            }
            return false;
        }
    }

    private boolean isNetworkStatsManagerInitialized() {
        if (mNetStatsManager == null) {
            mNetStatsManager = (NetworkStatsManager) mContext.getSystemService(Context.NETWORK_STATS_SERVICE);
        }
        if (mNetStatsManager == null) {
            Slog.e(TAG, "Fail to get NetworkStatsManager");
            return false;
        }
        return true;
    }

    private boolean isTelephonyManagerInitialized() {
        if (mTelephonyManager == null) {
            mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        }
        if (mTelephonyManager == null) {
            Slog.e(TAG, "Fail to get TelephonyManager");
            return false;
        }
        return true;
    }

    private boolean isWifiManagerInitialized() {
        if (mWifiManager == null) {
            mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        }
        if (mWifiManager == null) {
            Slog.e(TAG, "Fail to get WifiManager");
            return false;
        }
        return true;
    }

    private boolean isActivityManagerInitialized() {
        if (mActivityManager == null) {
            mActivityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        }
        if (mActivityManager == null) {
            Slog.e(TAG, "Fail to get ActivityManager");
            return false;
        }
        return true;
    }

    private boolean isConnectivityManagerInitialized() {
        if (mConnectivityManager == null) {
            mConnectivityManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        }
        if (mConnectivityManager == null) {
            Slog.e(TAG, "Fail to get ConnectivityManager");
            return false;
        }
        return true;
    }

    private boolean isAudioManagerInitialized() {
        if (mAudioManager == null) {
            mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        }
        if (mAudioManager == null) {
            Slog.e(TAG, "Fail to get mAudioManager");
            return false;
        }
        return true;
    }

    private boolean isMediaRouterInitialized() {
        if (mRouter == null) {
            mRouter = (MediaRouter) mContext.getSystemService(Context.MEDIA_ROUTER_SERVICE);
        }
        if (mRouter == null) {
            Slog.e(TAG, "Fail to get mRouter");
            return false;
        }
        return true;
    }

    private boolean isMBrainServiceWrapperInitialized() {
        if (mBrainServiceWrapper == null) {
            registerMBrainCallback();
            return false;
        }
        return true;
    }

    private void refreshInstalledPackageInfo() {
        ApplicationHelper.initPackageManager(mContext);
        ApplicationHelper.updateInstalledApplication();
    }

    private void refreshAudioMode() {
        if (isAudioManagerInitialized()) {
            int mode = mAudioManager.getMode();
            notifyAudioModeChange(NotifyMessageHelper.generateAudioModeNotifyMessage(mode));
        }
    }

    private void registerMBrainCallback() {
        if (Utils.isVendorFreezeConditionPass()) {
            String mbrainMode = SystemProperties.get("ro.vendor.mbrain.mode", "none");
            if ("mtk".equals(mbrainMode) == false) {
                //Slog.d(TAG, "ro.vendor.mbrain.mode is invalid: " + mbrainMode);
                return;
            }
            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (mBrainServiceWrapper == null) {
                            mBrainServiceWrapper = new MBrainServiceWrapperAidl(
                                new MBrainRegCallbackAidl(MBrainLocalService.this),
                                new MBrainServiceWrapperAidl.ServiceManagerStub() {});
                            if (mBrainServiceWrapper != null) {
                                initializeAfterMBrainCallbackEstablished();
                            }
                        } else {
                            //Slog.d(TAG, "registerMBrainCallback alreay done");
                        }
                    } catch (RemoteException e) {
                        Slog.e(TAG, "registerMBrainCallback RemoteException");
                        mBrainServiceWrapper = null;
                    } catch (NoSuchElementException e) {
                        Slog.e(TAG, "registerMBrainCallback NoSuchElementException");
                        mBrainServiceWrapper = null;
                    } catch (SecurityException e) {
                        Slog.e(TAG, "registerMBrainCallback SecurityException");
                        mBrainServiceWrapper = null;
                    }
                }
            });
        } else {
            Slog.d(TAG, "isVendorFreezeConditionPass is false");
        }
    }

    // TODO: this is MBrainInfocallback, need to check event type for further task
    @Override
    public boolean notifyToClient(String event) {
        //Slog.d(TAG, "notifyToClient start: " + event);
        return true;
    }

    @Override
    public boolean getMessgeFromClient(MBrain_Parcelable input) {
        //Slog.d(TAG, "getMessgeFromClient start: " + input.privData);
        return true;
    }

    private void registerActivityManager() {
        if (isActivityManagerInitialized()) {
            refreshRunningAppPrecessesInfo();
            try {
                IActivityManager service = mActivityManager.getService();
                if (service != null) {
                    service.registerUidObserver(mUidObserver,
                            ActivityManager.UID_OBSERVER_PROCSTATE | ActivityManager.UID_OBSERVER_PROC_OOM_ADJ |
                            ActivityManager.UID_OBSERVER_ACTIVE | ActivityManager.UID_OBSERVER_GONE,
                            ActivityManager.PROCESS_STATE_UNKNOWN, null);
                }

                mActivityManager.addOnUidImportanceListener(new ActivityManager.OnUidImportanceListener() {
                    @Override
                    public void onUidImportance(final int uid, final int importance) {
                        // for first time back from screen off
                        if (ScreenHelper.isScreenOff()) {
                            isFirstTimeFromScreenOff = true;
                        }
                    }
                }, ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND);

            } catch (RemoteException e) {
                Slog.e(TAG, "registerUidObserver exception");
            } catch (IllegalArgumentException e) {
                Slog.e(TAG, "addOnUidImportanceListener IllegalArgumentException");
            }
        }
    }

    private void registerMediaRouter() {
        if (isMediaRouterInitialized()) {
            mRouter.addCallback(MediaRouter.ROUTE_TYPE_LIVE_AUDIO, new MediaRouter.Callback() {
                @Override
                public void onRouteAdded(MediaRouter router, RouteInfo routeInfo) {
                    //Slog.d(TAG, "updateRouteInfo onRouteAdded");
                    updateRouteInfo(routeInfo);
                    notifyAudioRouteChange();
                 }

                @Override
                public void onRouteChanged(MediaRouter router, RouteInfo routeInfo) {
                    //Slog.d(TAG, "updateRouteInfo onRouteChanged");
                    updateRouteInfo(routeInfo);
                    notifyAudioRouteChange();
                }

                @Override
                public void onRouteGrouped(MediaRouter router, RouteInfo routeInfo, RouteGroup group, int index) {
                    //Slog.d(TAG, "updateRouteInfo onRouteGrouped");
                }

                @Override
                public void onRoutePresentationDisplayChanged(MediaRouter router, RouteInfo routeInfo) {
                }

                @Override
                public void onRouteRemoved(MediaRouter router, RouteInfo routeInfo) {
                    //Slog.d(TAG, "updateRouteInfo onRouteRemoved");
                }

                @Override
                public void onRouteSelected(MediaRouter router, int type, RouteInfo routeInfo) {
                    //Slog.d(TAG, "updateRouteInfo onRouteSelected");
                    updateRouteInfo(routeInfo);
                    notifyAudioRouteChange();
                }

                @Override
                public void onRouteUngrouped(MediaRouter router, RouteInfo routeInfo, RouteGroup group) {
                    //Slog.d(TAG, "updateRouteInfo onRouteUngrouped");
                }

                @Override
                public void onRouteUnselected(MediaRouter router, int type, RouteInfo routeInfo) {
                    //Slog.d(TAG, "updateRouteInfo onRouteUnselected");
                }

                @Override
                public void onRouteVolumeChanged(MediaRouter router, RouteInfo routeInfo) {
                    //Slog.d(TAG, "updateRouteInfo onRouteVolumeChanged");
                    updateRouteInfo(routeInfo);
                    notifyAudioRouteChange();
                }

            }, 0);
        }
    }

    private int mRouteDeviceType;
    private int mRouteVolume;
    private int mRouteVolumeMax;
    private int mRoutePlaybackType;
    private void updateRouteInfo(RouteInfo routeInfo) {
        mRouteDeviceType = AudioDeviceInfo.TYPE_UNKNOWN;
        String name = String.valueOf(routeInfo.getName());
        String description = (routeInfo.getDescription() != null) ? String.valueOf(routeInfo.getDescription()) : null;
        if (name.toLowerCase().contains("headphone")) {
            mRouteDeviceType = AudioDeviceInfo.TYPE_WIRED_HEADPHONES;
        } else if (name.toLowerCase().contains("headset")) {
            mRouteDeviceType = AudioDeviceInfo.TYPE_WIRED_HEADSET;
        } else if (name.toLowerCase().contains("phone")) {
            mRouteDeviceType = AudioDeviceInfo.TYPE_BUILTIN_SPEAKER;
        } else if (description != null && description.toLowerCase().contains("bluetooth")) {
            mRouteDeviceType = AudioDeviceInfo.TYPE_BLUETOOTH_A2DP;
        }

        mRouteVolume = routeInfo.getVolume();
        mRouteVolumeMax = routeInfo.getVolumeMax();
        mRoutePlaybackType = routeInfo.getPlaybackType();
    }

    private void notifyAudioRouteChange() {
        //Slog.d(TAG, "notifyAudioRouteChange, routeDeviceType=" + deviceType + ", volume: " + volume + ", VolumeMax: " + volumeMax + ", playbacktype: " + playbackType);
        if (isMBrainServiceWrapperInitialized()) {
            try {
                int dt = mRouteDeviceType;
                int rv = mRouteVolume;
                int rvMax = mRouteVolumeMax;
                int pt = mRoutePlaybackType;
                String notifyMsg =
                    NotifyMessageHelper.generateAudioRouteNotifyMsg(dt, rv, rvMax, pt);
                if (Utils.isValidString(notifyMsg)) {
                    //Slog.d(TAG, "notifyAudioRouteChange: " + notifyMsg);
                    mBrainServiceWrapper.notifyAudioRouteChangeToService(notifyMsg);
                }
            } catch (RemoteException e) {
                Slog.d(TAG, "notifyAudioRouteChange RemoteException");
            }
        } else {
            Slog.d(TAG, "notifyAudioRouteChange failed");
        }
    }

    List<String> audioConfigRecords = new ArrayList<>();
    Runnable reportAudioConfigChange = new Runnable() {
        @Override
        public void run() {
            notifyAudioModeChange(
                NotifyMessageHelper.generateAudioConfigNotifyMessage(audioConfigRecords));
            audioConfigRecords.clear();
        }
    };

    private void registerAudioCodecListener() {
        if (isAudioManagerInitialized()) {
            mAudioManager.registerAudioPlaybackCallback(new AudioManager.AudioPlaybackCallback() {
                @Override
                public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configs) {
                    long timestamp = System.currentTimeMillis();
                    for (AudioPlaybackConfiguration config: configs) {
                        int uid = config.getClientUid();
                        if (uid >= UID_3RD_APP_BEGIN) {
                            String packageName = ApplicationHelper.getPackageNameFromUid(
                                config.getClientUid());
                            int pid = config.getClientPid();
                            if (Utils.isValidString(packageName)) {
                                String record = NotifyMessageHelper.generateAudioConfigRecord(
                                timestamp, uid, packageName, pid, config.getPlayerState());
                                if (Utils.isValidString(record)) {
                                    if (audioConfigRecords.size() > AUDIO_HARD_THRESHOLD) {
                                        Slog.d(TAG, "audioConfigRecords: over hard threshold");
                                        audioConfigRecords.clear();
                                    }
                                    audioConfigRecords.add(record);
                                }
                            }
                        }
                    }

                    audioConfigCountDownHandler.removeCallbacksAndMessages(null);
                    if (audioConfigRecords.size() >= AUDIO_NOTIFY_THRESHOLD) {
                        Slog.d(TAG, "audioConfigRecords: notify directly");
                        audioConfigCountDownHandler.post(reportAudioConfigChange);
                    } else {
                        audioConfigCountDownHandler.
                        postDelayed(reportAudioConfigChange, AUDIO_NOTIFY_COOL_TIME);
                    }
                }
            }, null);

            mAudioManager.addOnModeChangedListener(mContext.getMainExecutor(),
                new AudioManager.OnModeChangedListener() {
                    @Override
                    public void onModeChanged(int mode) {
                        notifyAudioModeChange(NotifyMessageHelper.
                        generateAudioModeNotifyMessage(mode));
                    }
                }
            );

        }
    }

    private void notifyAudioModeChange(String notifyMsg) {
        if (isMBrainServiceWrapperInitialized()) {
            //Slog.d(TAG, "notifyAudioModeChange: " + notifyMsg);
            try {
                if (Utils.isValidString(notifyMsg)) {
                    mBrainServiceWrapper.notifyAudioModeChangeToService(notifyMsg);
                }
            } catch (RemoteException e) {
                Slog.d(TAG, "notifyAudioModeChange RemoteException");
            }
        } else {
            Slog.d(TAG, "notifyAudioModeChange failed");
        }
    }

    private void registerAirplaneModeListener() {
        IntentFilter ifilter = new IntentFilter();
        ifilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        mContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                notifyNetworkChange(mIsWifiNetwork, mIsCellularNetwork);
            }
        }, ifilter);
    }

    private void notifyAppLifeCycle(String notifyMsg) {
        if (isMBrainServiceWrapperInitialized()) {
            //Slog.d(TAG, "notifyAppLifeCycle: " + notifyMsg);
            try {
                if (Utils.isValidString(notifyMsg)) {
                    mBrainServiceWrapper.notifyAppLifecycleToService(notifyMsg);
                }
            } catch (RemoteException e) {
                Slog.d(TAG, "notifyAppLifeCycle RemoteException");
            }
        } else {
            Slog.d(TAG, "notifyAppLifeCycle failed");
        }
    }

    private void notifyAPIGetDataToService(int seq, String vendorType, String licenseData, String method) {
        //Slog.d(TAG, "notifyAPIGetDataToService, seq=" + seq + ", vendorType: " + vendorType + ", licenseData: " + licenseData + ", method: " + method);
        if (isMBrainServiceWrapperInitialized()) {
            try {
                String notifyMsg = NotifyMessageHelper.generateMtkDbgNotifyMsg(seq, vendorType, licenseData, method);
                if (Utils.isValidString(notifyMsg)) {
                    //Slog.d(TAG, "notifyAPIGetDataToService: " + notifyMsg);
                    mBrainServiceWrapper.notifyAPIGetDataToService(notifyMsg);
                }
            } catch (RemoteException e) {
                Slog.d(TAG, "notifyAPIGetDataToService RemoteException");
            }
        } else {
            Slog.d(TAG, "notifyAPIGetDataToService failed");
        }
    }

    private void refreshRunningAppPrecessesInfo() {
        if (isActivityManagerInitialized()) {
             mRunningAppProcesslist = mActivityManager.getRunningAppProcesses();
        }
    }

    private List<RunningAppProcessInfo> getRunningAppProcessByUid(int uid) {
        refreshRunningAppPrecessesInfo();
        if (mRunningAppProcesslist == null) {
            return null;
        }
        List<RunningAppProcessInfo> processesInfo = new ArrayList<>();
        for (RunningAppProcessInfo info: mRunningAppProcesslist) {
            if (uid != 0 && uid == info.uid) {
                processesInfo.add(info);
            }
        }
        return processesInfo;
    }

    private List<Integer> getPidFromPackageName(String packageName) {
        List<RunningAppProcessInfo> list = mActivityManager.getRunningAppProcesses();
        List<Integer> pids = new ArrayList<>();
        for (RunningAppProcessInfo info: list) {
            if (info.processName.equalsIgnoreCase(packageName)) {
                pids.add(info.pid);
            }
        }
        return pids;
    }

    private void getBuildInfo() {
        initAndroidId();
    }

    private void updateDisplayInfo() {
        Display display = mContext.getDisplay();
        if (display != null) {
            mScreenRefreshRate = display.getRefreshRate();

            DisplayMetrics metrics = new DisplayMetrics();
            display.getRealMetrics(metrics);
            mScreenWidth = metrics.widthPixels;
            mScreenHeight = metrics.heightPixels;

            //Slog.d(TAG, "refreshRate: " + mScreenRefreshRate + ", screen width: " + mScreenWidth + ", screen height:" + mScreenHeight);
        } else {
            Slog.e(TAG, "get display failed");
        }
    }

    private String getSystemNetworkUsage(int networkType) {
        long totalTxByte = 0;
        long totalTxPacket = 0;
        long totalRxByte = 0;
        long totalRxPacket = 0;
        long endTime = System.currentTimeMillis();
        long startTime = System.currentTimeMillis() - SystemClock.elapsedRealtime();

        if (isNetworkStatsManagerInitialized()) {
            try {
                Bucket bucket = mNetStatsManager.querySummaryForDevice(networkType,
                getSubscriberId(networkType), startTime, endTime);
                totalTxByte = bucket.getTxBytes();
                totalTxPacket = bucket.getTxPackets();
                totalRxByte = bucket.getRxBytes();
                totalRxPacket = bucket.getRxPackets();
            } catch (RemoteException | SecurityException e) {
                Slog.e(TAG, "getSystemNetworkUsage exception");
            }
        } else {
           Slog.e(TAG, "getSystemNetworkUsage isNetworkStatsManagerInitialized failed");
        }

        if (networkType == ConnectivityManager.TYPE_WIFI) {
            return TaskResponseHelper.getSystemWifiUsageResponse(totalTxByte, totalTxPacket, totalRxByte, totalRxPacket);
        } else if (networkType == ConnectivityManager.TYPE_MOBILE) {
            return TaskResponseHelper.getSystemMobileUsageResponse(totalTxByte, totalTxPacket, totalRxByte, totalRxPacket);
        } else {
            return "";
        }
    }

    private String getSystemWifiUsage() {
        return getSystemNetworkUsage(ConnectivityManager.TYPE_WIFI);
    }

    private String getSystemMobileUsage() {
        return getSystemNetworkUsage(ConnectivityManager.TYPE_MOBILE);
    }

    private String getSystemNetworkUsage() {
        return TaskResponseHelper.getSystemNetworkUsageResponse(getSystemWifiUsage(), getSystemMobileUsage());
    }

    private NetworkStats getWifiStatsbyUid(int uid) {
        if (isNetworkStatsManagerInitialized()) {
            try {
                return mNetStatsManager.queryDetailsForUid(
                    ConnectivityManager.TYPE_WIFI,
                    getSubscriberId(ConnectivityManager.TYPE_WIFI),
                    Long.MIN_VALUE, Long.MAX_VALUE, uid);
            } catch (SecurityException e) {
                Slog.e(TAG, "getWifiStatsbyUid SecurityException");  
                return null;
            }
        }
        return null;
    }

    private NetworkStats getCellularStatsbyUid(int uid) {
        if (isNetworkStatsManagerInitialized()) {
            try {
                return mNetStatsManager.queryDetailsForUid(
                    ConnectivityManager.TYPE_MOBILE,
                    getSubscriberId(ConnectivityManager.TYPE_MOBILE),
                    Long.MIN_VALUE, Long.MAX_VALUE, uid);
            } catch (SecurityException e) {
                Slog.e(TAG, "getCellularStatsbyUid SecurityException");
                return null; 
            }
        }
        return null;
    }

    private String getSubscriberId(int networkType) {
        if (isTelephonyManagerInitialized() && ConnectivityManager.TYPE_MOBILE == networkType) {
            return mTelephonyManager.getSubscriberId();
        }
        return "";
    }

    private String getAndroidId() {
        if (Utils.isValidString(mAndroidId)) {
            return mAndroidId;
        }
        return "";
    }

    private void registerTimeChangeReceiver() {
        IntentFilter ifilter = new IntentFilter();
        ifilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        ifilter.addAction(Intent.ACTION_TIME_CHANGED);
        mContext.registerReceiver(new TimeChangeIntentReceiver(this::isMBrainServiceWrapperInitialized, this::getMBrainServiceWrapper), ifilter);
    }

    private void registerShutDownRebootReceiver() {
        IntentFilter ifilter = new IntentFilter();
        ifilter.addAction(Intent.ACTION_SHUTDOWN);
        ifilter.addAction(Intent.ACTION_REBOOT);
        mContext.registerReceiver(shutDownReceiver, ifilter);
    }

    BroadcastReceiver shutDownReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            int state = checkShutDownOrReboot(action);
            if (isMBrainServiceWrapperInitialized()) {
                try {
                    JSONObject test = new JSONObject();
                    test.put("state", state);
                    String notifyInfo = test.toString();
                    if (Utils.isValidString(notifyInfo)) {
                        //Slog.d(TAG, "notifyDeviceShutDownToService: " + notifyInfo + ", action: " + action);
                        mBrainServiceWrapper.notifyDeviceShutDownToService(notifyInfo);
                    }
                } catch (RemoteException e) {
                    Slog.e(TAG, "notifyDeviceShutDownToService RemoteException");
                } catch (JSONException ex) {
                    Slog.e(TAG, "notifyDeviceShutDownToService JSONException");
                }
            } else {
                Slog.e(TAG, "notifyDeviceShutDownToService failed");
            }
        }

        private int checkShutDownOrReboot(String action) {
            int turnOffState = -1;
            if (Intent.ACTION_SHUTDOWN.equals(action)) {
                turnOffState = 0;
            } else if (Intent.ACTION_REBOOT.equals(action)) {
                turnOffState = 1;
            }

            // this checking code from SystemServer.java
            String shutdownAction = SystemProperties.get(ShutdownThread.SHUTDOWN_ACTION_PROPERTY, "");
            if (shutdownAction != null && shutdownAction.length() > 0) {
                turnOffState = (shutdownAction.charAt(0) == '1') ? 1 : 0;
            }
            return turnOffState;
        }
    };

    private void registerDozeModeReceiver() {
        IntentFilter ifilter = new IntentFilter();
        ifilter.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED);
        ifilter.addAction(PowerManager.ACTION_DEVICE_LIGHT_IDLE_MODE_CHANGED);
        ifilter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        mContext.registerReceiver(dozeModeReceiver, ifilter);
    }

    BroadcastReceiver dozeModeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            //Slog.d(TAG, "doze mode action: " + action);

            PowerManager powerManager = (PowerManager)
            context.getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                boolean isDeviceIdle = powerManager.isDeviceIdleMode();
                boolean isDeviceLightDoze = powerManager.isDeviceLightIdleMode();
                boolean isDeepDoze = (isDeviceIdle && !isDeviceLightDoze);
                // batterySavedEnabled in DeviceIdleController
                boolean isPowerSaveMode = getLocalService(PowerManagerInternal.class).
                getLowPowerState(PowerManager.ServiceType.QUICK_DOZE).batterySaverEnabled;
                //Slog.d(TAG, "isDeviceIdle: " + isDeviceIdle + ", isDeviceLightDoze: " +
                //isDeviceLightDoze + ", isDeepDoze: " + isDeepDoze + ", isPowerSaveMode: "
                // + isPowerSaveMode);
                notifyDozeModeChange(isDeviceLightDoze, isDeepDoze, isPowerSaveMode);
            }
        }


    };

    private void notifyDozeModeChange(boolean isDeviceLightDoze, boolean isDeviceDeepDoze,
    boolean isPowerSaveMode) {
        if (isMBrainServiceWrapperInitialized()) {
            try {
                String notifyMsg = NotifyMessageHelper.generateDozeModeNotifyMessage
                (isDeviceLightDoze, isDeviceDeepDoze, isPowerSaveMode);
                if (Utils.isValidString(notifyMsg)) {
                    mBrainServiceWrapper.notifyDozeModeChangeToService(notifyMsg);
                }
            } catch (RemoteException e) {
                Slog.e(TAG, "notifyDozeModeChange RemoteException");
            }
        } else {
            Slog.d(TAG, "notifyDozeModeChange failed");
        }
    }

    private void registerScreenReceiver() {
        IntentFilter ifilter = new IntentFilter();
        ifilter.addAction(Intent.ACTION_SCREEN_ON);
        ifilter.addAction(Intent.ACTION_SCREEN_OFF);
        ifilter.addAction(Intent.ACTION_USER_PRESENT);
        mContext.registerReceiver(new ScreenIntentReceiver(this::isMBrainServiceWrapperInitialized, this::getMBrainServiceWrapper), ifilter);
    }

    private void registerUsbReceiver() {
        IntentFilter ifilter = new IntentFilter();
        ifilter.addAction(UsbManager.ACTION_USB_STATE);
        mContext.registerReceiver(new UsbIntentReceiver(this::isMBrainServiceWrapperInitialized, this::getMBrainServiceWrapper), ifilter);
    }

    private void registerChargeReceiver() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        mContext.registerReceiver(new ChargeIntentReceiver(this::isMBrainServiceWrapperInitialized, this::getMBrainServiceWrapper), ifilter);
    }
    
    private void registerAppStateReceiver() {
        IntentFilter packageIntentFilter = new IntentFilter();
        packageIntentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        packageIntentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packageIntentFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        packageIntentFilter.addDataScheme("package");
        mContext.registerReceiver(new AppStateIntentReceiver(this::isMBrainServiceWrapperInitialized, this::getMBrainServiceWrapper), packageIntentFilter);
    }
    
    private MBrainServiceWrapperAidl getMBrainServiceWrapper() {
        return mBrainServiceWrapper;
    }

    private void registerWifiCellularListener() {
        if (isConnectivityManagerInitialized()) {
            final NetworkRequest wifiRequest = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build();
            mConnectivityManager.registerNetworkCallback(wifiRequest, new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    curWifiNetwork = network;
                    mIsWifiNetwork = true;
                    notifyNetworkChange(true, false);

                }
                @Override
                public void onLost(Network network) {
                    curWifiNetwork = null;
                    mIsWifiNetwork = false;
                    resetWifiSignal();
                    notifyNetworkChange(false, false);
                }

                private void resetWifiSignal() {
                    wifiSignalStrength = DEFAULT_SIGNAL_STRENGTH;
                    wifiSignalLevel = -1;
                }
            });

            final NetworkRequest cellularRequest = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .build();
            mConnectivityManager.registerNetworkCallback(cellularRequest, new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    mIsCellularNetwork = true;
                    notifyNetworkChange(false, true);
                }
                @Override
                public void onLost(Network network) {
                    mIsCellularNetwork = false;
                    resetMobileSignal();
                    notifyNetworkChange(false, false);
                }
                private void resetMobileSignal() {
                    mobileSignalStrength = 100;
                    mobileSignalLevel = -1;
                }
            });
        } else {
            Slog.e(TAG, "registerWifiCellularListener failed");
        }
    }

    private void notifyNetworkChange(boolean isWifiOn, boolean isCellularOn) {
        //Slog.d(TAG, "notifyNetworkChange, isWifiOn:" + isWifiOn + ", isCellularOn: " + isCellularOn);
        if (isMBrainServiceWrapperInitialized()) {
            try {
                String notifyMsg = NotifyMessageHelper.generateNetworkChangeNotifyMsg
                (isWifiOn, isCellularOn, Utils.isAirplaneModeOn(mContext));
                if (isWifiOn) {
                    notifyMsg = Utils.mergeJSONObject(new JSONObject(notifyMsg), new JSONObject(getNotifyWifiInfo())).toString();
                } else if (isCellularOn) {
                    notifyMsg = Utils.mergeJSONObject(new JSONObject(notifyMsg), new JSONObject(getNotifyCellularInfo())).toString();
                }

                if (Utils.isValidString(notifyMsg)) {
                    //Slog.d(TAG, "notifyNetworkChange: " + notifyMsg +
                    //", wifi: " + wifiSignalStrength + ", mobile: " + mobileSignalStrength);
                    mBrainServiceWrapper.notifyNetworkChangeToService(notifyMsg);
                }
            } catch (RemoteException e) {
                Slog.e(TAG, "notifyNetworkChange RemoteException");
            } catch (JSONException e) {
                Slog.e(TAG, "notifyNetworkChange JSONException");
            }
        } else {
            Slog.d(TAG, "notifyNetworkChange failed");
        }
    }

    BroadcastReceiver wifiSignalStrengthReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }

            if (intent.getAction() == WifiManager.RSSI_CHANGED_ACTION) {
                if (isWifiManagerInitialized()) {
                    wifiSignalStrength = intent.getIntExtra(WifiManager.EXTRA_NEW_RSSI, 0);
                    int curWifiSignalLevel = mWifiManager.calculateSignalLevel(wifiSignalStrength);
                    if (isWifiSignalLevelChanged(curWifiSignalLevel)) {
                        setWifiSignalStrength(
                            intent.getIntExtra(WifiManager.EXTRA_NEW_RSSI,
                            DEFAULT_SIGNAL_STRENGTH));
                        wifiSignalLevel = mWifiManager.calculateSignalLevel(wifiSignalStrength);
                        notifyNetworkChange(true, false);
                    }
                }
                //Slog.d(TAG, "onReceive: wifi signal strength changed: " +
                //wifiSignalStrength + ", level: " + wifiSignalLevel);
            }
        }

        private void setWifiSignalStrength(int dbm) {
            if (dbm > -150 && dbm < 0) {
                wifiSignalStrength = dbm;
            } else {
                wifiSignalStrength = DEFAULT_SIGNAL_STRENGTH;
            }
        }

        private boolean isWifiSignalLevelChanged(int curWifiSignalLevel) {
            return Math.abs(curWifiSignalLevel - wifiSignalLevel) > 0;
        }
    };

    private void registerWifiSignalStrengthListener() {
        IntentFilter ifilter = new IntentFilter();
        ifilter.addAction(WifiManager.RSSI_CHANGED_ACTION);
        mContext.registerReceiver(wifiSignalStrengthReceiver, ifilter);
    }

    private class SignalStrengthsListener extends TelephonyCallback
        implements TelephonyCallback.SignalStrengthsListener {
          @Override
          public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            if (signalStrength != null) {
                int curMobileSignalLevel = signalStrength.getLevel();
                if (isMobileSignalLevelChanged(curMobileSignalLevel)) {
                    setMobileSignalStrength(signalStrength.getDbm());
                    mobileSignalStrength = signalStrength.getDbm();
                    mobileSignalLevel = signalStrength.getLevel();
                    notifyNetworkChange(false, true);
                }
                //Slog.d(TAG, "onReceive: telephony signal strength changed: " +
                //mobileSignalStrength + ", level: " + mobileSignalLevel);
            }
          }

          private void setMobileSignalStrength(int dbm) {
            if (dbm > -150 && dbm < 0) {
                mobileSignalStrength = dbm;
            } else {
                mobileSignalStrength = DEFAULT_SIGNAL_STRENGTH;
            }
          }

          private boolean isMobileSignalLevelChanged(int curMobileSignalLevel) {
            return Math.abs(curMobileSignalLevel - mobileSignalLevel) > 0;
          }
    };

    // count down for audio config change
    private Handler audioConfigCountDownHandler = new Handler();
    // sending change notify after 2 seconds of the last change happened
    private final int AUDIO_NOTIFY_COOL_TIME = 2 * 1000;
    // sending change notify if data size is too large
    private final int AUDIO_NOTIFY_THRESHOLD = 100;
    // prevent too many data
    private final int AUDIO_HARD_THRESHOLD = 1000;

    class TelephonyInfo {
        private Context context;

        private TelephonyManager telephonyManager;
        private ImsManager imsManager;
        private ImsMmTelManager imsMmTelManager;

        private boolean isActiveDataSubscriptionListnerRegistered = false;

        private int slotId = -1;
        private int subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        private int dataConnectionState = TelephonyManager.DATA_UNKNOWN; // -1
        private int serviceState = ServiceState.STATE_OUT_OF_SERVICE; // 1
        private int telephonySignalStrength = DEFAULT_SIGNAL_STRENGTH;
        private int telephonySignalLevel = -1;
        private boolean isUserMobileDataEnabled = false;
        private boolean dataEnableForApn = false;
        private int radioTech = -1;

        private SignalStrengthsListener signalStrengthListener = new SignalStrengthsListener();
        private DataConnectionStateListener dataConnectionStateListener =
        new DataConnectionStateListener();
        private ServiceStateListener serviceStateListener = new ServiceStateListener();
        private UserMobileDataStateListener userMobileDataStateListener =
        new UserMobileDataStateListener();
        private ActiveDataSubscriptionIdListener activeDataSubScriptionIdListener =
        new ActiveDataSubscriptionIdListener();

        public TelephonyInfo(Context context, int slotId) {
            this.context = context;
            this.slotId = slotId;

            registerTelephonyCallback();
            if (isTelephonyManagerInitialized()) {
                isActiveDataSubscriptionListnerRegistered = true;
                mTelephonyManager.registerTelephonyCallback(mContext.getMainExecutor(),
                activeDataSubScriptionIdListener);
            }
        }

        private void registerActiveDataSubscriptionListener() {
            if (!isActiveDataSubscriptionListnerRegistered &&
            isTelephonyManagerInitialized()) {
                isActiveDataSubscriptionListnerRegistered = true;
                mTelephonyManager.registerTelephonyCallback(mContext.getMainExecutor(),
                activeDataSubScriptionIdListener);
            }
        }

        private int getSubIdFromSlotId() {
            SubscriptionManager subScriptionManager = (SubscriptionManager)
                    mContext.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            if (subScriptionManager != null) {
                SubscriptionInfo info = subScriptionManager.
                getActiveSubscriptionInfoForSimSlotIndex(slotId);
                if (info != null) {
                    return info.getSubscriptionId();
                } else {
                    return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
                }
            }
            return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }

        private void updateManagers() {
            imsManager = ImsManager.getInstance(context, slotId);
            if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                imsMmTelManager = ImsMmTelManager.createForSubscriptionId(subId);
            }
            registerListenerForVoLTEVoNR();
        }

        private void registerListenerForVoLTEVoNR() {
            try {
                if (imsManager != null) {
                    imsManager.addRegistrationListener(ImsServiceClass.MMTEL, imsRegListener);
                }
            } catch (ImsException  e) {
                Slog.e(TAG, "registerForImsStateChange, addRegistrationListener exception=" + e);
            }
        }

        private void registerTelephonyCallback() {
            if (!isTelephonyManagerInitialized())
                return;

            if (telephonyManager != null) {
                telephonyManager.unregisterTelephonyCallback(signalStrengthListener);
                telephonyManager.unregisterTelephonyCallback(dataConnectionStateListener);
                telephonyManager.unregisterTelephonyCallback(serviceStateListener);
                telephonyManager.unregisterTelephonyCallback(userMobileDataStateListener);
            }

            if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                telephonyManager = mTelephonyManager.createForSubscriptionId(subId);
            } else {
                telephonyManager = mTelephonyManager;
            }

            if (telephonyManager != null) {
                telephonyManager.registerTelephonyCallback(mContext.getMainExecutor(),
                signalStrengthListener);
                telephonyManager.registerTelephonyCallback(mContext.getMainExecutor(),
                dataConnectionStateListener);
                telephonyManager.registerTelephonyCallback(mContext.getMainExecutor(),
                serviceStateListener);
                telephonyManager.registerTelephonyCallback(mContext.getMainExecutor(),
                userMobileDataStateListener);
            }
        }

        private class SignalStrengthsListener extends TelephonyCallback
            implements TelephonyCallback.SignalStrengthsListener {
            @Override
            public void onSignalStrengthsChanged(SignalStrength signalStrength) {
                if (signalStrength != null) {
                    int curMobileSignalLevel = signalStrength.getLevel();
                    if (isMobileSignalLevelChanged(curMobileSignalLevel)) {
                        setMobileSignalStrength(signalStrength.getDbm());
                        telephonySignalStrength = signalStrength.getDbm();
                        telephonySignalLevel = signalStrength.getLevel();
                    }
                }
            }

            private void setMobileSignalStrength(int dbm) {
                if (dbm > -150 && dbm < 0) {
                    telephonySignalStrength = dbm;
                } else {
                    telephonySignalStrength = DEFAULT_SIGNAL_STRENGTH;
                }
            }

            private boolean isMobileSignalLevelChanged(int curMobileSignalLevel) {
                return Math.abs(curMobileSignalLevel - telephonySignalStrength) > 0;
            }
        };

        private class DataConnectionStateListener extends TelephonyCallback
            implements TelephonyCallback.DataConnectionStateListener {
            @Override
            public void onDataConnectionStateChanged(int state, int networkType) {
                if (dataConnectionState != state) {
                    dataConnectionState = state;
                    //only record the state
                    updateTelephonyRecord();
                }
            }
        };

        private class ServiceStateListener extends TelephonyCallback
            implements TelephonyCallback.ServiceStateListener {
            @Override
            public void onServiceStateChanged(ServiceState state) {
                if (state != null) {
                    if (serviceState != state.getState()) {
                        serviceState = state.getState();
                        updateTelephonyRecord();
                    }
                }
            }
        };

        private class UserMobileDataStateListener extends TelephonyCallback
            implements TelephonyCallback.UserMobileDataStateListener {
            @Override
            public void onUserMobileDataStateChanged(boolean enabled) {
                if (isUserMobileDataEnabled != enabled) {
                    isUserMobileDataEnabled = enabled;
                    updateTelephonyRecord();
                }
            }
        };

        private class ActiveDataSubscriptionIdListener extends TelephonyCallback
            implements TelephonyCallback.ActiveDataSubscriptionIdListener {
            @Override
            public void onActiveDataSubscriptionIdChanged(int id){
                int currentSubId = getSubIdFromSlotId();
                if (currentSubId != subId) {
                    subId = currentSubId;
                    updateTelephonyRecord();
                    updateManagers();
                    registerTelephonyCallback();
                }
            }
        };

        private final ImsConnectionStateListener imsRegListener =
        new ImsConnectionStateListener() {
            @Override
            public void onImsConnected(int imsRadioTech) {
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (imsMmTelManager != null) {
                            try {
                                boolean isVolteAvailable = imsMmTelManager.isAvailable(
                                MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE,
                                ImsRegistrationImplBase.REGISTRATION_TECH_LTE);

                                boolean isVoNrAvailable = imsMmTelManager.isAvailable(
                                MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE,
                                ImsRegistrationImplBase.REGISTRATION_TECH_NR);

                                if (isVolteAvailable) {
                                    radioTech = ImsRegistrationImplBase.REGISTRATION_TECH_LTE;
                                } else if (isVoNrAvailable) {
                                    radioTech = ImsRegistrationImplBase.REGISTRATION_TECH_NR;
                                }
                                updateTelephonyRecord();
                            } catch (RuntimeException e) {
                                Slog.d(TAG, "ims not updated: " + e.getMessage());
                            }
                        }
                    }
                }, 100);
            }

            @Override
            public void onImsDisconnected(ImsReasonInfo imsReasonInfo) {
                radioTech = -1;
            }
        };

        private void updateTelephonyRecord() {
            boolean dataEnableForApn =
            telephonyManager.isDataEnabledForApn(ApnSetting.TYPE_DEFAULT);
            String record = NotifyMessageHelper.generateTelephonyRecord(
                slotId, dataConnectionState, serviceState, telephonySignalStrength,
                dataEnableForApn, isUserMobileDataEnabled, simState, radioTech,
                subId, defaultDataSubId);
            recordTelephonyState(record);
        }
    }
    private TelephonyInfo telephonyInfo_0;
    private TelephonyInfo telephonyInfo_1;

    private int simState = 0;
    private int defaultDataSubId = -1;
    private List<String> telephonyRecord = new ArrayList<String>();

    // count down for telephony change notify
    private Handler telephonyCountDownHandler = new Handler();
    // sending change notify after 10 seconds of the last change happened
    private final int TELEPHONY_NOTIFY_COOL_TIME = 10 * 1000;

    private SignalStrengthsListener signalStrengthListener = new SignalStrengthsListener();

    BroadcastReceiver simStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SIM_STATE_CHANGED.equals(action)) {
                if (isTelephonyManagerInitialized()) {
                    simState = mTelephonyManager.getSimState();
                }
            }
        }
    };

    BroadcastReceiver defaultDataSubscriptionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (TelephonyManager.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED
                .equals(action)) {
                SubscriptionManager subScriptionManager = (SubscriptionManager)
                    mContext.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                if (subScriptionManager != null) {
                    defaultDataSubId = subScriptionManager.getDefaultDataSubscriptionId();
                }
            }
        }
    };

    private void registerDefaultDataSubscriptionListener() {
        IntentFilter ifilter = new IntentFilter();
        ifilter.addAction(TelephonyManager.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED);
        mContext.registerReceiver(defaultDataSubscriptionReceiver, ifilter);
    }

    private void registerTelephonyListener() {
        IntentFilter ifilter = new IntentFilter();
        ifilter.addAction(Intent.ACTION_SIM_STATE_CHANGED);
        mContext.registerReceiver(simStateReceiver, ifilter);
        telephonyInfo_0 = new TelephonyInfo(mContext, 0);
        telephonyInfo_1 = new TelephonyInfo(mContext, 1);
    }

    Runnable reportTelephonyChange = new Runnable() {
        @Override
        public void run() {
            notifyTelephonyStateChange();
        }
    };

    private void recordTelephonyState(String state) {
        if (Utils.isValidString(state)) {
            telephonyRecord.add(state);
            telephonyCountDownHandler.removeCallbacksAndMessages(null);
            telephonyCountDownHandler.
            postDelayed(reportTelephonyChange, TELEPHONY_NOTIFY_COOL_TIME);
        }
    }

    private void notifyTelephonyStateChange() {
        if (isMBrainServiceWrapperInitialized()) {
            try {
                if (telephonyRecord != null && !telephonyRecord.isEmpty()) {
                    String notifyMsg = NotifyMessageHelper.
                    generateTelephonyStateNotifyMessage(telephonyRecord);
                    telephonyRecord.clear();
                    if (Utils.isValidString(notifyMsg)) {
                        mBrainServiceWrapper.notifyTelephonyStateChangeToService(notifyMsg);
                    }
                }
            } catch (RemoteException e) {
                Slog.e(TAG, "notifyTelephonyStateChange RemoteException");
            }
        } else {
            Slog.d(TAG, "notifyTelephonyStateChange failed");
        }
    }

    class MBrainJava extends IMBrainJava.Stub {
        @Override
        public void notify(String request) {
            // Slog.d(TAG, "notify by IMBrainJava request: " + request);
            // new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            //     @Override
            //     public void run() {
            //         sendMessageToMBrain();
            //     }
            // }, 15000);
        }

        @Override
        public String getData(String request) {
            //Slog.d(TAG, "getData by IMBrainJava request: " + request);
            return handleComingTask(request);
        }

        @Override
        public int getInterfaceVersion() {
            return this.VERSION;
        }  

        @Override
        public String getInterfaceHash() {
            return this.HASH;
        }

        private void sendMessageToMBrain() {
            try {
                mBrainServiceWrapper.sendMessageToService("this is callback after notify() from MBrain");
            } catch (RemoteException e) {
                Slog.e(TAG, "IMBrainJava sendMessageToService RemoteException");
            }
        }
    }

    private AtomicInteger apiSeq = new AtomicInteger();
    private String handleComingTask(String request) {
        int taskType = TaskHelper.getTaskType(request);
        if (taskType != TaskHelper.ACTION_NONE) {
            switch(taskType) {
                case TaskHelper.ACTION_GET_OS_COMMON:
                    return TaskResponseHelper.getOSCommonResponse(getAndroidId(), TimeChangeHelper.getTimeZoneId());
                case TaskHelper.ACTION_GET_APP_INFO:
                    String identity = TaskHelper.getTaskUidOrPackageName(request);
                    if (!Utils.isValidString(identity)) {
                        return TaskResponseHelper.getFailTaskResponse("ACTION_GET_APP_INFO task " + request + " is invalid task");
                    }
                    return getTaskAppInfo(identity);
                case TaskHelper.ACTION_GET_APP_SWITCH_INFO:
                    //Slog.d(TAG, "handleComingTask ACTION_GET_APP_SWITCH_INFO");
                    break;
                case TaskHelper.ACTION_GET_CHARGE_INFO:
                    //Slog.d(TAG, "handleComingTask ACTION_GET_CHARGE_INFO");
                    return getChargeInfo();
                case TaskHelper.ACTION_GET_TIMEZONE_INFO:
                    //Slog.d(TAG, "handleComingTask ACTION_GET_TIMEZONE_INFO");
                    break;
                case TaskHelper.ACTION_GET_ACCU_TRAFFIC_INFO:
                    int uid = TaskHelper.getTaskUid(request);
                    if (uid == -1) {
                        return TaskResponseHelper.getFailTaskResponse("ACTION_GET_ACCU_TRAFFIC_INFO task " + request + " is invalid task");
                    }
                    // due to permission limitation, using Future to handle it.
                    try {
                        CompletableFuture<String> getTrafficStatFuture = CompletableFuture.supplyAsync(() -> getTrafficStats(uid));
                        return getTrafficStatFuture.get();
                    } catch (ExecutionException e) {
                        return TaskResponseHelper.getFailTaskResponse("ACTION_GET_ACCU_TRAFFIC_INFO task " + request + " with execution error");
                    } catch (InterruptedException ex) {
                        return TaskResponseHelper.getFailTaskResponse("ACTION_GET_ACCU_TRAFFIC_INFO task " + request + " with interrupt error");
                    }
                case TaskHelper.ACTION_CALLBACK_NETWORK_STAT:
                    //Slog.d(TAG, "handleComingTask ACTION_CALLBACK_NETWORK_STAT");
                    break;
                case TaskHelper.ACTION_GET_UIDS_INFO:
                    //Slog.d(TAG, "handleComingTask ACTION_GET_UIDS_INFO");
                    if (!ApplicationHelper.isPackageManagerInitialized()) {
                        ApplicationHelper.initPackageManager(mContext);
                    }
                    return TaskResponseHelper.getUidInfoResponse(ApplicationHelper.getAllUids());
                case TaskHelper.ACTION_GET_DISPLAY_INFO:
                    //Slog.d(TAG, "handleComingTask ACTION_GET_DISPLAY_INFO");
                    updateDisplayInfo();
                    return TaskResponseHelper.getDisplayInfoResponse(mScreenRefreshRate, mScreenWidth, mScreenHeight, getScreenBrightness());
                case TaskHelper.ACTION_GET_CONNECTIVITY_INFO:
                    //Slog.d(TAG, "handleComingTask ACTION_GET_CONNECTIVITY_INFO");
                    return getConnectivityInfo();
                case TaskHelper.ACTION_GET_SYSTEM_NETWORK_USAGE:
                    //Slog.d(TAG, "handleComingTask ACTION_GET_SYSTEM_NETWORK_USAGE");
                    try {
                        CompletableFuture<String> getTrafficStatFuture =
                        CompletableFuture.supplyAsync(() -> getSystemNetworkUsage());
                        return getTrafficStatFuture.get();
                    } catch (ExecutionException e) {
                        return TaskResponseHelper
                        .getFailTaskResponse(
                        "ACTION_GET_SYSTEM_NETWORK_USAGE task " + request + " with execution error"
                        );
                    } catch (InterruptedException ex) {
                        return TaskResponseHelper
                        .getFailTaskResponse(
                        "ACTION_GET_SYSTEM_NETWORK_USAGE task " + request + " with interrupt error"
                        );
                    }
                case TaskHelper.ACTION_GET_CURRENT_STATUS_INFO:
                    return getSystemStatusInfo();
                case TaskHelper.ACTION_GET_PROCESS_MEMORY:
                    try {
                        int[] pid = TaskHelper.getTaskPids(request);
                        boolean isAMSMode = TaskHelper.isAMSMode(request);
                        if (pid == null)
                            return TaskResponseHelper
                            .getFailTaskResponse("ACTION_GET_PROCESS_MEMORY with no process array");
                        CompletableFuture<String> getProcessMemoryFuture =
                        CompletableFuture.supplyAsync(() -> getProcessMemoryInfo(pid, isAMSMode));
                        return getProcessMemoryFuture.get();
                    } catch (ExecutionException e) {
                        Slog.e(TAG, e.toString());
                        return TaskResponseHelper
                        .getFailTaskResponse("ACTION_GET_PROCESS_MEMORY  with execution error");
                    } catch (InterruptedException ex) {
                        return TaskResponseHelper
                        .getFailTaskResponse("ACTION_GET_PROCESS_MEMORY with interrupt error");
                    }
                case TaskHelper.ACTION_GET_AEE_SYSTEM_INFO:
                    int checkThreshold = TaskHelper.getAEEThreashold(request);
                    return getAEEInfo(checkThreshold);
                case TaskHelper.ACTION_CMD_MTKLOGGER_ENABLE:
                    MtkLoggerHelper.notifyMtkLoggerMDTaglog(mContext);
                    return TaskResponseHelper.getSucceesTaskResponse();
                // case TaskHelper.ACTION_CMD_MTKLOGGER_DISABLE:
                //     MtkLoggerHelper.notifyMtkLogger(mContext, false);
                //     return TaskResponseHelper.getSucceesTaskResponse();
                case TaskHelper.ACTION_VENDOR_API_GET_DATA_BACK:
                    //Slog.d(TAG, "handleComingTask ACTION_VENDOR_API_GET_DATA_BACK: " + request);
                    VendorRequestHelper.handleApiDataBack(request);
                    countDownLatch.countDown();
                    countDownLatch = new CountDownLatch(1);
                    return TaskResponseHelper.getSucceesTaskResponse();
                case TaskHelper.ACTION_VENDOR_GET_DATA:
                    //special handling for return data through MTK DBG Request
                    //Slog.d(TAG, "handleComingTask ACTION_VENDOR_GET_DATA");
                    return handleMTkDBGRequest(apiSeq.incrementAndGet(), request);
                default:
                    //Slog.d(TAG, "this task " + request + " is not available");
            }
        } else {
            return TaskResponseHelper.getFailTaskResponse("this task " + request + " is invalid task");
        }

        return TaskResponseHelper.getFailTaskResponse("End of Task");
    }

    private String getAEEInfo(int checkThreshold) {
        try {
            Path filePath = Path.of(AEE_DB_HISTORY_PATH);
            long lastModifiedTime = Files.getLastModifiedTime(filePath).toMillis();
            if (System.currentTimeMillis() - lastModifiedTime < checkThreshold) {
                String dbHistory = getLastLine(new File(AEE_DB_HISTORY_PATH));
                String fileTree = getLastLine(new File(AEE_FILE_TREE_PATH));
                return TaskResponseHelper.getAEEInfo(dbHistory, fileTree);
            } else {
                return TaskResponseHelper.getFailTaskResponse("db_history is not updated");
            }
        } catch (IOException e) {
            e.printStackTrace();
            return TaskResponseHelper.getFailTaskResponse("file is not found");
        }
    }

    private String getLastLine(File file) {
        String lastLine = "";
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkRead(file.getPath());
        }
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String currentLine;
            while ((currentLine = br.readLine()) != null) {
                if (sm != null) {
                    sm.checkRead(file.getPath());
                }
                lastLine = currentLine;
            }
        } catch (IOException e) {
            Slog.d(TAG, "IOException" + e.toString());
        }
        return lastLine;
    }

    private String getSystemStatusInfo() {
        String batteryInfo = getBatteryStatusInfo();
        String networkInfo =
            TaskResponseHelper.getNetworkStatusInfo(mIsWifiNetwork, mIsCellularNetwork);
        String audioInfo = getAudioStatusInfo();
        String usbInfo = getUsbStatusInfo();
        return TaskResponseHelper.getSystemStatusInfo(batteryInfo, networkInfo, audioInfo, usbInfo);
    }

    private String getBatteryStatusInfo() {
        int batteryLevel = ChargeHelper.getLevel();
        int chargeStatus = ChargeHelper.getStatus();
        int chargeApproach = ChargeHelper.getApproach();
        return TaskResponseHelper.getBatteryStatusInfo(batteryLevel, chargeStatus, chargeApproach);
    }

    private String getAudioStatusInfo() {
        if (isMediaRouterInitialized()) {
            updateRouteInfo(mRouter.getDefaultRoute());
        }
        return TaskResponseHelper
        .getAudioStatusInfo(mRouteDeviceType, mRouteVolume, mRouteVolumeMax, mRoutePlaybackType);
    }

    private String getUsbStatusInfo() {
        int connect = UsbHelper.getConnect();
        int host = UsbHelper.getHost();
        String function = UsbHelper.getFunction();
        return TaskResponseHelper.getUsbStatusInfo(connect, host, function);
    }

    private String getProcessMemoryInfo(int[] pids, boolean isAMSMode) {
        String[] processes = null;
        if (isAMSMode && isActivityManagerInitialized()) {
            MemoryInfo[] memoryInfo = mActivityManager.getProcessMemoryInfo(pids);
            if (memoryInfo != null && memoryInfo.length > 0) {
                processes = new String[memoryInfo.length];
                for (int i = 0; i < memoryInfo.length; i++) {
                    processes[i] = getJSONFromMemoryInfo(pids[i], memoryInfo[i]);
                }
                return TaskResponseHelper.getProcessMemory(processes);
            } else {
                return TaskResponseHelper.getFailTaskResponse("MemoryInfo is empty");
            }
        } else if (isAMSMode == false) {
            processes = new String[pids.length];
            for (int i = 0; i < pids.length; i ++) {
                final Debug.MemoryInfo memoryInfo = new Debug.MemoryInfo();
                Debug.getMemoryInfo(pids[i], memoryInfo);
                processes[i] = getJSONFromMemoryInfo(pids[i], memoryInfo);
            }
            return TaskResponseHelper.getProcessMemory(processes);
        }

        return TaskResponseHelper.getFailTaskResponse("no ActivityManager");
    }

    private String getJSONFromMemoryInfo(int pid, MemoryInfo info) {
        if (info != null) {
            int pss = info.getTotalPss();
            int uss = info.getTotalPrivateClean() + info.getTotalPrivateDirty();
            int rss = uss + info.getTotalSharedClean() + info.getTotalSharedDirty();
            int swap = info.getTotalSwappablePss();
            int javaHeap = info.dalvikPss;
            int nativeHeap = info.nativePss;
            return TaskResponseHelper
            .getProcessMemoryJSON(pid, pss, uss, rss, swap, javaHeap, nativeHeap);
        }
        return null;
    }

    private String getTaskAppInfo(String identity) {
        if (!ApplicationHelper.isPackageManagerInitialized()) {
            ApplicationHelper.initPackageManager(mContext);
        }
        List<RunningAppProcessInfo> processesInfo = new ArrayList<>();
        List<ApplicationInfo> appsInfo = new ArrayList<>();
        int uid = -1;
        if (TextUtils.isDigitsOnly(identity)) {
            uid = Integer.parseInt(identity);
            if (Process.isApplicationUid(uid)) {
                appsInfo = ApplicationHelper.getAppInfoByUid(uid);
                processesInfo = getRunningAppProcessByUid(uid);
            } else {
                return TaskResponseHelper.getFailTaskResponse("ACTION_GET_APP_INFO " + uid + " is not application uid");
            }
        } else {
            ApplicationInfo appInfo = ApplicationHelper.getAppInfoByPackageName(identity);
            if (appInfo != null) {
                uid = appInfo.uid;
                appsInfo.add(appInfo);
                processesInfo = getRunningAppProcessByUid(uid);
            }
        }

        if (uid <= 0 || processesInfo == null) {
            return TaskResponseHelper.getFailTaskResponse("ACTION_GET_APP_INFO task cannot get uid");
        }

        List<JSONObject> appsList = new ArrayList<>();
        if (appsInfo != null && !appsInfo.isEmpty()) {
            for(ApplicationInfo info: appsInfo) {
                PackageInfo packageInfo = ApplicationHelper.getPackageInfoByPackageName(info.packageName);
                if (packageInfo != null) {
                    appsList.add(TaskResponseHelper.getAppInfoObject(uid, packageInfo.packageName, packageInfo.versionName, packageInfo.firstInstallTime, packageInfo.lastUpdateTime));
                }
            }
        } else {
            return TaskResponseHelper.getFailTaskResponse("ACTION_GET_APP_INFO task cannot get apps");
        }

        JSONArray appsArray = new JSONArray();
        for (JSONObject obj : appsList) {
            appsArray.put(obj);
        }

        List<JSONObject> processesList = new ArrayList<>();
        if (!appsInfo.isEmpty()) {
            for(RunningAppProcessInfo processInfo: processesInfo) {
                processesList.add(TaskResponseHelper.getProcessInfoObject(processInfo.pid, processInfo.processName));
            }
        }

        JSONArray processesArray = new JSONArray();
        for (JSONObject obj : processesList) {
            processesArray.put(obj);
        }

        return TaskResponseHelper.getAppResponse(uid, appsArray, processesArray);
    }

    private int getScreenBrightness() {
        return Settings.System.getInt(mContext.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, -1);
    }

    private String getConnectivityInfo() {
        String response;
        if (mIsWifiNetwork && mIsCellularNetwork) {
            response = TaskResponseHelper.getWifiCellularNetworkResponse(getWifiResponse(), getTelephonyResponse());
        } else if (mIsWifiNetwork) {
            response = getWifiResponse();
        } else if (mIsCellularNetwork) {
            response = getTelephonyResponse();
        } else {
            response = TaskResponseHelper.getNoWifiCellularNetworkResponse();
        }
        return TaskResponseHelper.addAirplaneMode(response, Utils.isAirplaneModeOn(mContext));
    }

    private String getTelephonyResponse() {
        if (isTelephonyManagerInitialized()) {
            int networkType = mTelephonyManager.getDataNetworkType();
            String operator = mTelephonyManager.getSimOperator();
            int simOperator = 0;
            try {
                simOperator = Integer.parseInt(operator);
            } catch(NumberFormatException e) {
                Slog.e(TAG, "sim operator failed: " + operator);
            }

            SignalStrength signalStrength = mTelephonyManager.getSignalStrength();
            int signalStrengthLevel = -1;
            if (signalStrength != null) {
                signalStrengthLevel = signalStrength.getLevel();
            }
            return TaskResponseHelper.getCellularNetworkResponse(networkType, signalStrengthLevel, simOperator);
        } else {
            return TaskResponseHelper.getWifiCellularNetworkFailResponse("getTelephonyInfo failed");
        }
    }

    private String getWifiResponse() {
        if (curWifiNetwork != null && isConnectivityManagerInitialized()) {
            NetworkCapabilities networkcapability = mConnectivityManager.getNetworkCapabilities(curWifiNetwork);
            if (networkcapability != null) {
                TransportInfo transportInfo = networkcapability.getTransportInfo();
                if (transportInfo instanceof WifiInfo) {
                    int wifiStandard = ((WifiInfo)transportInfo).getWifiStandard();
                    int frequency = ((WifiInfo)transportInfo).getFrequency();
                    return TaskResponseHelper.getWifiNetworkResponse(wifiStandard, frequency);
                } else {
                    return TaskResponseHelper.getWifiCellularNetworkFailResponse("not wifi, cannot not get protocol");
                }
            } else {
                return TaskResponseHelper.getWifiCellularNetworkFailResponse("networkcapability is null");
            }
        } else {
            return TaskResponseHelper.getWifiCellularNetworkFailResponse("getConnectivityInfo failed");
        }
    }

    private String getNotifyCellularInfo() {
        if (isTelephonyManagerInitialized()) {
            int networkType = mTelephonyManager.getDataNetworkType();
            String operator = mTelephonyManager.getSimOperator();
            int simOperator = 0;
            try {
                simOperator = Integer.parseInt(operator);
            } catch(NumberFormatException e) {
                Slog.d(TAG, "sim operator failed: " + operator);
            }
            int signalCombo = getNetworkSignalCombo(false);
            return NotifyMessageHelper
            .generateCellularInfoNotifyMsg(networkType, simOperator, signalCombo);
        }
        return "";
    }

    private String getNotifyWifiInfo() {
        if (curWifiNetwork != null && isConnectivityManagerInitialized()) {
            NetworkCapabilities networkcapability = mConnectivityManager.getNetworkCapabilities(curWifiNetwork);
            if (networkcapability != null) {
                TransportInfo transportInfo = networkcapability.getTransportInfo();
                if (transportInfo instanceof WifiInfo) {
                    int wifiStandard = ((WifiInfo)transportInfo).getWifiStandard();
                    int frequency = ((WifiInfo)transportInfo).getFrequency();
                    int signalCombo = getNetworkSignalCombo(true);
                    return NotifyMessageHelper
                    .generateWifiInfoNotifyMsg(wifiStandard, frequency, signalCombo);
                }
            }
        }
        return "";
    }

    private int getNetworkSignalCombo(boolean isWifi) {
        if (isWifi) {
            if (wifiSignalStrength > 0) {
                return 0;
            } else {
                return wifiSignalStrength +
                    ((wifiSignalLevel > 0) ? wifiSignalLevel * SIGNAL_COMBO_MULTIPLIER : 0);
            }
        } else {
            if (mobileSignalStrength > 0) {
                return 0;
            } else {
                return mobileSignalStrength +
                    ((mobileSignalLevel > 0) ? mobileSignalLevel * SIGNAL_COMBO_MULTIPLIER : 0);
            }
        }
    }

    private String getChargeInfo() {
        return TaskResponseHelper.getChargeInfo(ChargeHelper.getStatus(), ChargeHelper.getApproach(), ChargeHelper.getLevel());
    }

    private String handleMTkDBGRequest(int seq, String request) {
        String passedMethod;
        try {
            //Slog.d(TAG, "handleMTkDBGRequest: " + request);
            JSONObject requestObj = new JSONObject(request);
            String vendortype = VendorRequestHelper.getVendorData(requestObj.optString("vendor", ""));
            if (vendortype == null) {
                //Slog.d(TAG, "vendortype is null");
                return "request parse error";
            }
            String method = requestObj.optString("method", "");
            String licenseData = requestObj.optString("licenseData", "");
            passedMethod =  VendorRequestHelper.getVendorMethod(vendortype, requestObj.optString("version", ""), method);
            if (!Utils.isValidString(passedMethod)) {
                //Slog.d(TAG, "cannot find valid method");
                return "request parse error";
            }
            //Slog.d(TAG, "method: " + method + ", licenseData: " + licenseData + ", passedMethod: " + passedMethod);
            String localCache = VendorRequestHelper.getApiData(passedMethod);
            if (Utils.isValidString(localCache)) {
                return localCache;
            } else {
                notifyAPIGetDataToService(seq, vendortype, licenseData, passedMethod);
            }
        } catch(JSONException e) {
            //Slog.d(TAG, "JSONException error: " + e.toString());
            return "request parse error";
        }

        // due to permission limitation, using Future to handle it.
        try {
            CompletableFuture<String> waitApiDataFuture = CompletableFuture.supplyAsync(() -> waitApiData(seq, passedMethod));
            return waitApiDataFuture.get();
        } catch (ExecutionException e) {
            return TaskResponseHelper.getFailTaskResponse("GETDATA task " + request + " with execution error");
        } catch (InterruptedException ex) {
            return TaskResponseHelper.getFailTaskResponse("GETDATA task " + request + " with interrupt error");
        }
    }

    private String waitApiData(int seq, String method) {
        try {
            //Slog.d(TAG, "waitApiData seq: " + seq);
            countDownLatch.await(3, TimeUnit.SECONDS);
            String result = VendorRequestHelper.getApiData(method);
            //Slog.d(TAG, "data comes as: " + result);
            return result;
        } catch (Exception e) {
            //Slog.e(TAG, "waitApiData exception: " + e.getMessage());
            return "waitApiData Exception " + e.toString();
        }
    }

    private int[] getRunningPid() {
        if (isActivityManagerInitialized()) {
            List<RunningAppProcessInfo> list = mActivityManager.getRunningAppProcesses();
            int[] pids = new int[10];
            for (int i = 0; i < 10; i ++) {
                pids[i] = list.get(i).pid;
            }
            return pids;
        }
        return null;
    }

    private void testTask() {

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Slog.d(TAG, "Start testTaskHelper");
                Slog.d(TAG, handleComingTask(TaskTestHelper.testInvalidTask()));
                Slog.d(TAG, handleComingTask(TaskTestHelper.testUnsopportedTask()));
                Slog.d(TAG, handleComingTask(TaskTestHelper.testCommonTask()));
                Slog.d(TAG, handleComingTask(TaskTestHelper.testAppInfoTask()));
                Slog.d(TAG, handleComingTask(TaskTestHelper.testSystemUidInfoTask()));
                Slog.d(TAG, handleComingTask(TaskTestHelper.testAppSwitchTask()));
                Slog.d(TAG, handleComingTask(TaskTestHelper.testGetAllUids()));
                Slog.d(TAG, handleComingTask(TaskTestHelper.testGetDisplayInfo()));
                Slog.d(TAG, handleComingTask(TaskTestHelper.testGetTrafficStats()));
                Slog.d(TAG, handleComingTask(TaskTestHelper.testGetConnectivity()));
                Slog.d(TAG, handleComingTask(TaskTestHelper.testGetCharge()));
                Slog.d(TAG, handleComingTask(TaskTestHelper.testGetSystemNetworkUsage()));
                Slog.d(TAG, handleComingTask(TaskTestHelper.testGetSystemCurrentStatus()));
                getRunningAppProcessByUid(10095);
                int[] pid = getRunningPid();
                if (pid != null) {
                    Slog.d(TAG, handleComingTask(TaskTestHelper.testGetProcessMemory(pid, 1)));
                }
            }

        }, 25000);
    }
}
