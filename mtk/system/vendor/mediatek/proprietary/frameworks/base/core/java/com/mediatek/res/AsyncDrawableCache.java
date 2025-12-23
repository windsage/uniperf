/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2017. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */
package com.mediatek.res;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Process;
import android.os.SystemProperties;
import android.os.UserManager;
import android.util.ArrayMap;
import android.util.Log;

import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.ArrayList;

class AsyncDrawableCache {

    private static final String TAG = "AsyncDrawableCache";
    private static boolean isDEBUG = false;

    private static ArrayList<String> sPreloadList = new ArrayList<String>(){
        {
            add("com.bbl.mobilebanking");
            add("air.tv.douyu.android");
        }
    };

    private static AsyncDrawableCache mAsyncDrawableCache = null;
    private static final String sPerfName = "perf_img_scale";
    private static final String sResolutionEnableProp = "ro.vendor.pref_scale_enable_cfg";
    private static String sFeatureConfig = SystemProperties.get(sResolutionEnableProp, "1");
    private static final String sDefResolution = "720";
    private static String sResolution = sDefResolution;
    private static final ArrayMap<Long, Drawable.ConstantState> sDrawableCache
            = new ArrayMap<Long, Drawable.ConstantState>();
    private static ArrayMap<String, Integer> sResolutionList = new ArrayMap<String, Integer>();

    private static final long sClearCacheTime = 10000;
    private static boolean isPreloaded = false;

    private static Object lock = new Object();

    AsyncDrawableCache() {
        sResolutionList.put("480", 307200);
        sResolutionList.put("720", 921600);
        sResolutionList.put("1080", 2073600);
    }

    static AsyncDrawableCache getInstance() {
        if (mAsyncDrawableCache == null) {
            synchronized (AsyncDrawableCache.class) {
                if (mAsyncDrawableCache == null) {
                    mAsyncDrawableCache = new AsyncDrawableCache();
                }
            }
        }
        return mAsyncDrawableCache;
    }

    boolean skipPreload(Context context) {
        if (context == null) {
            return true;
        }
        String pkg = context.getPackageName();
        return pkg == null || sPreloadList.contains(pkg);
    }

    void preloadRes(Context context, Resources res) {
        if (!isEnableFeature()) {
            return;
        }

        if (skipPreload(context)) {
            return;
        }

        if (!isUserUnlocked(context)) {
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(sPerfName, Context.MODE_PRIVATE);
        if (prefs == null || prefs.getAll().size() == 0) {
            return;
        }

        isPreloaded = true;
        AsyncTask.execute(() -> {
            Map<String, ?> map = prefs.getAll();
            for(Map.Entry<String, ?> stringEntry : map.entrySet()){
                String key = stringEntry.getKey();
                int value = (int) stringEntry.getValue();

                if (isDEBUG) {
                    Log.d(TAG, "resource=" + value + ", res obj="
                            + context.getResources().getImpl());
                }
                try {
                    context.getDrawable(value);
                } catch(Resources.NotFoundException e) {
                    Log.w(TAG, "can not found res: " + value + ", maybe dynamic res id.");
                }
            }
            if (isDEBUG) {
                Log.d(TAG, "preloadRes, end of preloadRes");
            }
        });

        clearCacheAfterPreload();
    }

    /**
     * Get Cache drawable from cache list.
     * @param wrapper
     * @param key
     * @param origId
     * @return Drawable
     */
    Drawable getCachedDrawable(Resources wrapper, long key, int origId) {
        if (!isEnableFeature()) {
            return null;
        }

        Drawable.ConstantState boostCache = null;
        synchronized (lock) {
            boostCache = sDrawableCache.get(key);
        }

        if (boostCache != null) {
            return boostCache.newDrawable(wrapper);
        }
        // No cached drawable, we'll need to create a new one.
        return null;
    }

    /**
     * Put drawable in cache.
     * @param key
     * @param dr
     * @param origResId
     * @param context
     */
    void putCacheList(long key, Drawable dr, int origResId, Context context) {
        if (!isEnableFeature()) {
            return;
        }

        if (skipPreload(context)) {
            return;
        }

        if (context.getApplicationInfo().processName.equals("system")
                || context.getApplicationInfo().isSystemApp()) {
            return;
        }

        Drawable.ConstantState boostCache = null;
        synchronized (lock) {
            boostCache = sDrawableCache.get(key);
        }
        if (boostCache == null && context != null) {
            if (needCacheDrawable(dr)) {
                if (isPreloaded) {
                    synchronized (lock) {
                        sDrawableCache.put(key, dr.getConstantState());
                    }
                    if (isDEBUG) {
                        Log.d(TAG, "putCacheList, put cache, size:" + sDrawableCache.size());
                    }
                }

                if (isDEBUG) {
                    Log.d(TAG, "putCacheList, key:" + key + ", origResId:" + origResId);
                }
                storeDrawableId(origResId, context);
            }
        }
    }

    private boolean needCacheDrawable(Drawable dr) {
        boolean needCache = false;

        // compute resolution, only catch big than properties resolution image
        Integer scaleResolution = sResolutionList.get(sResolution);
        int drResolution = dr.getMinimumWidth() * dr.getMinimumHeight();
        if (drResolution >= scaleResolution) {
            if (isDEBUG) {
                Log.d(TAG, "computeResolution, drResolution:" + drResolution
                        + ", scaleResolution:" + scaleResolution);
            }
            needCache = true;
        }
        return needCache;
    }

    private void storeDrawableId(int origResId, Context context) {
        if (context == null) {
            Log.w(TAG, "storeDrawableId got the context is null, id:"
                    + origResId + " cannot save");
            return;
        }

        if (!isUserUnlocked(context)) {
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(sPerfName, Context.MODE_PRIVATE);

        if (prefs != null && !prefs.contains(String.valueOf(origResId))) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(String.valueOf(origResId), origResId);
            editor.commit();
            if (isDEBUG) {
                Log.d(TAG, "storeDrawableId, id:" + origResId);
            }
        }
    }

    private boolean isEnableFeature() {
        boolean isEnable = false;

        if (sFeatureConfig.equals("0")) {
            isEnable = false;
            isDEBUG = false;
        } else if (sFeatureConfig.equals("1")) {
            isEnable = true;
            isDEBUG = false;
        } else if (sFeatureConfig.equals("2")) {
            isEnable = true;
            isDEBUG = true;
        }

        return isEnable;
    }

    private boolean isUserUnlocked(Context context) {
        UserManager userManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
        if (!userManager.isUserUnlocked()) {
            return false;
        }

        return true;
    }

    private void clearCache() {
        if (sDrawableCache != null) {
            if (sDrawableCache.size() != 0) {
                synchronized (lock) {
                    sDrawableCache.clear();
                }
            }
        }
        if (isDEBUG) {
            Log.d(TAG, "clearCache, cache size:" + sDrawableCache.size());
        }
    }

    private void clearCacheAfterPreload() {
        if (isPreloaded) {
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    clearCache();
                    isPreloaded = false;
                }
            }, sClearCacheTime);
        }
    }
}
