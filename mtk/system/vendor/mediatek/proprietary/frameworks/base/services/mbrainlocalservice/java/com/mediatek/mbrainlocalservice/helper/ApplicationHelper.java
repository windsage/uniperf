package com.mediatek.mbrainlocalservice.helper;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.ApplicationInfo;
import android.util.Slog;

import java.util.List;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ApplicationHelper {
    private static final String TAG = "ApplicationHelper";
    private static List<ApplicationInfo> mInstalledApplication = new ArrayList<>();
    private static PackageManager mPackageManager;
    private static String[] launcherList = {
        "com.android.launcher",
        "com.miui.home",
        "com.bbk.launcher",
        "launcher"
    };
    private static final int RETRY_LIMIT = 3;
    private static int retry;

    public static void initPackageManager(Context context) {
        if (mPackageManager == null && retry <= RETRY_LIMIT) {
            mPackageManager = context.getPackageManager();
            retry ++;
        }
    }

    public static void updateInstalledApplication() {
        if (isPackageManagerInitialized())  {
            List<ApplicationInfo> application = mPackageManager.getInstalledApplications(PackageManager.GET_META_DATA);
            mInstalledApplication.clear();
            mInstalledApplication.addAll(application);
        }
    }

    public static boolean isLauncher(int uid) {
        String packageName = getPackageNameFromUid(uid);
        if (packageName != null && !packageName.isEmpty()) {
            return isLauncher(packageName);
        }
        return false;
    }

    public static boolean isLauncher(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        } else {
            for (int i = 0; i < launcherList.length; i++) {
                if (packageName.contains(launcherList[i]))
                    return true;
            }
            return false;
        }
    }

    public static ApplicationInfo getAppInfoByPackageName(String packageName) {
        for (ApplicationInfo app: mInstalledApplication) {
            if (app.packageName.equals(packageName)) {
                return app;
            }
        }
        return null;
    }

    public static int getUidByPackageName(String packageName) {
        ApplicationInfo app = getAppInfoByPackageName(packageName);
        if (app != null) {
            return app.uid;
        }
        return -1;
    }

    public static List<ApplicationInfo> getAppInfoByUid(int uid) {
        List<ApplicationInfo> appList = new ArrayList<>();
        for (ApplicationInfo app: mInstalledApplication) {
            if (app.uid == uid) {
                appList.add(app);
            }
        }
        return appList;
    }

    public static JSONArray getAllUids() {
        try {
            JSONArray uidArray = new JSONArray();
            for (ApplicationInfo info: mInstalledApplication) {
                JSONObject obj = new JSONObject();
                obj.put("uid", info.uid);
                obj.put("packagename", info.packageName);
                String versionName = getVersionNameByPackageName(info.packageName);
                if (versionName != null) {
                    obj.put("version", versionName);
                }
                uidArray.put(obj);
            }
            return uidArray;
        } catch(JSONException e) {
            return null;
        }
    }

    public static PackageInfo getPackageInfoByPackageName(String packageName) {
        if (isPackageManagerInitialized()) {
             try {
                return mPackageManager.getPackageInfo(packageName, 0);
            } catch (PackageManager.NameNotFoundException e) {
                Slog.e(TAG, "getPackageInfoByPackageName PackageManager.NameNotFoundException");
                return null;
            }
        }

        return null;
    }

    public static String getPackageNameFromUid(int uid) {
        if (isPackageManagerInitialized()) {
            return mPackageManager.getNameForUid(uid);
        }
        return "";
    }

    public static String getVersionNameByPackageName(String packageName) {
        PackageInfo packageInfo = getPackageInfoByPackageName(packageName); 
        if (packageInfo != null) {
            return packageInfo.versionName;
        }

        return null;
    }

    public static boolean isPackageManagerInitialized() {
        if (mPackageManager == null) {
            if (retry <= RETRY_LIMIT) {
                //Slog.e(TAG, "Fail to get PackageManager");
            }
            return false;
        }
        return true;
    }
}