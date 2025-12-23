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
package com.mediatek.server;

import android.content.Context;
import android.content.pm.PackageManager;

import com.android.server.utils.TimingsTraceAndSlog;
import android.media.AudioManager;
import android.util.Slog;

import com.android.server.SystemServiceManager;
import com.android.server.SystemService;
import com.mediatek.search.SearchEngineManagerService;

import android.os.Build;
import android.os.IInterface;
import android.os.SystemProperties;
import android.os.ServiceManager;

import dalvik.system.PathClassLoader;
import dalvik.system.VMRuntime;

import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.lang.reflect.Constructor;

import android.os.StrictMode;

public class MtkSystemServerImpl extends MtkSystemServer {
    private static final String TAG = "MtkSystemServerImpl";
    private static final String SEARCH_ENGINE_SERVICE_CLASS=
            "com.mediatek.search.SearchEngineManagerService";
    private static TimingsTraceAndSlog BOOT_TIMINGS_TRACE_LOG;
    private SystemServiceManager mSystemServiceManager;
    private Context mSystemContext;
    private boolean mMTPROF_disable = false;

    private static final String MTK_STORAGE_MANAGER_SERVICE_CLASS =
            "com.mediatek.server.MtkStorageManagerService$MtkStorageManagerServiceLifecycle";
    /// Add for hdmilocal service
    private static final String HDMI_LOCAL_SERVICE_CLASS =
            "com.mediatek.hdmilocalservice.HdmiLocalService";

    /// Add for powerhallocal service
    private static final String POWER_HAL_SERVICE_CLASS =
            "com.mediatek.powerhalservice.PowerHalMgrService";

    private static final String MTK_ALARM_MANAGER_SERVICE_CLASS =
            "com.mediatek.server.MtkAlarmManagerService";

    private static final String MTK_FM_RADIO_SERVICE_CLASS =
            "com.mediatek.fmradioservice.FmRadioService";

    private static final String MTK_VOW_BRIDGE_SERVICE_CLASS =
            "com.mediatek.server.vow.VoiceWakeupBridgeService";

    private static final String MTK_CONNECTIVITY_SUPPLEMENTAL_SERVICE_CLASS =
            "com.mediatek.server.MtkConnectivitySupplementalService";

	private static final String MBRAIN_LOCAL_SERVICE_CLASS =
            "com.mediatek.mbrainlocalservice.MBrainLocalService";

    /// Add for network policy service
    private static final String MTK_NETWORK_POLICY_SERVICE_CLASS =
            "com.mediatek.networkpolicyservice.NetworkPolicyService";

   @Override
    public void setPrameters(TimingsTraceAndSlog btt, SystemServiceManager ssm,
            Context context) {
        BOOT_TIMINGS_TRACE_LOG = btt;
        mSystemServiceManager = ssm;
        mSystemContext = context;
    }

    /**
     * Starts the small tangle of critical mtk services that are needed to get
     * the system off the ground.  These services have complex mutual dependencies
     * which is why we initialize them all in one place here.  Unless your service
     * is also entwined in these dependencies, it should be initialized in one of
     * the other functions.
     */
   @Override
    public void startMtkBootstrapServices() {
        Slog.i(TAG, "startMtkBootstrapServices");
    }

    /**
     * Starts some essential mtk services that are not tangled up in the bootstrap process.
     */
    @Override
    public void startMtkCoreServices() {
        Slog.i(TAG, "startMtkCoreServices ");
    }

    /**
     * Starts a miscellaneous grab bag of stuff that has yet to be refactored
     * and organized for mtk.
     */
    @Override
    public void startMtkOtherServices() {
       final Context context = mSystemContext;
        Slog.i(TAG, "startOtherMtkService ");
        boolean disableSearchManager = SystemProperties.getBoolean("config.disable_searchmanager",
                                                                    false);
        boolean disableNonCoreServices = SystemProperties.getBoolean("config.disable_noncore",
                                                                      false);

        boolean enableHdmiServices = !("".equals(SystemProperties.get("ro.vendor.mtk_tb_hdmi")));

        if (!disableNonCoreServices && !disableSearchManager) {
          traceBeginAndSlog("StartSearchEngineManagerService");
          try {
             ServiceManager.addService("search_engine_service",
                          new SearchEngineManagerService(context));
          } catch (Throwable e) {
             Slog.e(TAG, "StartSearchEngineManagerService "+e.toString());
          }
        }

        ///Start FmRadioService @{
        try {
            if (Class.forName("com.mediatek.fmradio.FmRadioPackageManager") != null) {
                traceBeginAndSlog("addService FmRadioService");
                try {
                    startService(MTK_FM_RADIO_SERVICE_CLASS);
                } catch (Throwable e) {
                    reportWtf("starting FmRadioService", e);
                }
                traceEnd();
            }
        }
        catch (Exception e){
             Slog.e(TAG, "com.mediatek.fmradio.FmRadioPackageManager not found ");
        }
        /// @}

        ///start hdmilocal Service@{
        if (enableHdmiServices){
            traceBeginAndSlog("StartHdmiLocalService");
            try{
                startService(HDMI_LOCAL_SERVICE_CLASS);
            } catch (Throwable e) {
                reportWtf("starting HdmiLocalService", e);
            }
            traceEnd();
        }
        ///start powerhalmgr Service@{

        traceBeginAndSlog("StartPowerHalMgrService");
        try{
            startService(POWER_HAL_SERVICE_CLASS);
        } catch (Throwable e) {
            reportWtf("starting PowerHalMgrService", e);
        }
        traceEnd();

        /// @}

        /// start network policy Service@{
        traceBeginAndSlog("JXNPS: StartNetworkPolicyService");
        try {
            startService(MTK_NETWORK_POLICY_SERVICE_CLASS);
        } catch (Throwable e) {
            reportWtf("JXNPS: StartNetworkPolicyService", e);
        }
        traceEnd();
        /// @}

        /// M: add for VOW 2E2K feature @{
        //PackageManager pm = mSystemContext.getPackageManager();
        //if (pm != null && pm.hasSystemFeature("com.mediatek.hardware.vow.2e2k")) {
        boolean isValidVowConfig = isWakeupSupport(mSystemContext)
                && (isTriggerSupport(mSystemContext) || isVowPDKSupported(mSystemContext));

        traceBeginAndSlog("V_W_B_S isValidVowConfig " + isValidVowConfig);
        if (isValidVowConfig) {
            traceBeginAndSlog("addService VoiceWakeupBridgeService");
            try {
                startService(MTK_VOW_BRIDGE_SERVICE_CLASS);
            } catch (Throwable e) {
               reportWtf("starting VoiceWakeupBridgeService", e);
            }
            traceEnd();
        } else {
            traceBeginAndSlog("not addService VoiceWakeupBridgeService");
        }
        // }
        /// @}

        try {
            if (Class.forName(MTK_CONNECTIVITY_SUPPLEMENTAL_SERVICE_CLASS) != null) {
                traceBeginAndSlog("StartConnectivitySupplementalService");
                try {
                    startService(MTK_CONNECTIVITY_SUPPLEMENTAL_SERVICE_CLASS);
                } catch (Throwable e) {
                    reportWtf("Failed to start ConnectivitySupplementalService", e);
                }
                traceEnd();
            } else {
                Slog.e(TAG, MTK_CONNECTIVITY_SUPPLEMENTAL_SERVICE_CLASS + "is null");
            }
        } catch (Exception e){
            Slog.e(TAG, MTK_CONNECTIVITY_SUPPLEMENTAL_SERVICE_CLASS + "not found");
        }

		try {
            if (Class.forName(MBRAIN_LOCAL_SERVICE_CLASS) != null) {
                traceBeginAndSlog("StartMBrainLocalService");
                try {
                    startService(MBRAIN_LOCAL_SERVICE_CLASS);
                } catch (Throwable e) {
                    reportWtf("Failed to start MBrainLocalService", e);
                }
                traceEnd();
            } else {
                Slog.e(TAG, MBRAIN_LOCAL_SERVICE_CLASS + "is null");
            }
        } catch (Exception e){
            Slog.e(TAG, MBRAIN_LOCAL_SERVICE_CLASS + "not found");
        }

    }

    /**
     * Starts MtkAlarmManagerService if existed
     */

    @Override
    public boolean startMtkAlarmManagerService() {

        traceBeginAndSlog("startMtkAlarmManagerService");
        try {
            startService(MTK_ALARM_MANAGER_SERVICE_CLASS);
        } catch (Throwable e) {
            Slog.e(TAG, "Exception while starting MtkAlarmManagerService" +e.toString());
            return false;
        }
        traceEnd();
        return true;
   }


    /**
     * Starts MtkStorageManagerService if existed
     */
    @Override
    public boolean startMtkStorageManagerService() {
        final boolean mIsPrivacyProtectionLockSupport =
            SystemProperties.get("ro.vendor.mtk_privacy_protection_lock").equals("1");

        if(!mIsPrivacyProtectionLockSupport) {
            Slog.i(TAG, "PPL not supported, retruning, will start AOSP StorageManagerService");
            return false;
        }
        traceBeginAndSlog("StartMtkStorageManagerService");
        try {
            startService(MTK_STORAGE_MANAGER_SERVICE_CLASS);
        } catch (Throwable e) {
            Slog.e(TAG, "Exception while starting MtkStorageManagerService" +e.toString());
            return false;
        }
        traceEnd();
        return true;
    }

    private SystemService startService(String className) {
        final Class<SystemService> serviceClass;
        try {
            serviceClass = (Class<SystemService>)Class.forName(className);
        } catch (ClassNotFoundException ex) {
            throw new RuntimeException("Failed to create service " + className
                    + ": service class not found, usually indicates that the caller should "
                    + "have called PackageManager.hasSystemFeature() to check whether the "
                    + "feature is available on this device before trying to start the "
                    + "services that implement it", ex);
        }
        return mSystemServiceManager.startService(serviceClass);
    }

    private static void traceBeginAndSlog(String name) {
        Slog.i(TAG, name);
/* fix me in S migration
        BOOT_TIMINGS_TRACE_LOG.traceBegin(name);
*/
    }

    private static void traceEnd() {
/* fix me in S migration
        BOOT_TIMINGS_TRACE_LOG.traceEnd();
*/
    }

    private void reportWtf(String msg, Throwable e) {
        Slog.w(TAG, "***********************************************");
        Slog.wtf(TAG, "BOOT FAILURE " + msg, e);
    }

    // Add BOOTPROF LOG @{
    public void addBootEvent(String bootevent) {
        if (mMTPROF_disable) {
            return ;
        }
        // skip this in Cuttlestone
        if (Build.HARDWARE.equals("cutf_cvm")) {
            return;
        }

        //Temporary shut-off StrctMode for the write disk during phase AP_Init
        final StrictMode.ThreadPolicy oldPolicy = StrictMode.getThreadPolicy();
        if(bootevent.contains("AP_Init")){
             StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy
                                        .Builder(oldPolicy).permitDiskWrites().build());
        }

        FileOutputStream fbp = null;
        try {
            fbp = new FileOutputStream("/proc/bootprof");
            fbp.write(bootevent.getBytes());
            fbp.flush();
        } catch (FileNotFoundException e) {
            Slog.e("BOOTPROF", "Failure open /proc/bootprof, not found!", e);
        } catch (java.io.IOException e) {
            Slog.e("BOOTPROF", "Failure open /proc/bootprof entry", e);
        } finally {
            if (fbp != null) {
                try {
                    fbp.close();
                } catch (java.io.IOException e) {
                    Slog.e("BOOTPROF", "Failure close /proc/bootprof entry", e);
                }
             }
         }
        //Temporary shut-off StrctMode for the write disk during phase AP_Init
        if(bootevent.contains("AP_Init")){
             StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    /**
     * Used to check whether VOW feature is supported.
     * @param context android context instance
     * @return if true, the feature was supported
     */
    private boolean isWakeupSupport(Context context) {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) {
            traceBeginAndSlog("isWakeupSupport get audio service is null");
            return false;
        }
        String state = am.getParameters("MTK_VOW_SUPPORT");
        traceBeginAndSlog("[isWakeupSupport] " + state);
        if (state != null) {
            return state.equalsIgnoreCase("MTK_VOW_SUPPORT=true");
        }
        return false;
    }

    /**
     * Used to check whether voice trigger feature is supported.
     * @param mContext android context instance
     * @return if true, the feature was supported
     */
    private boolean isTriggerSupport(Context mContext) {
        boolean isAlexa = false;
        AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) {
            traceBeginAndSlog("[isTriggerSupport] get audio service is null");
            return false;
        }
        String AlexaSupport = am.getParameters("MTK_VOW_AMAZON_SUPPORT");
        traceBeginAndSlog("[isTriggerSupport] AlexaSupport " + AlexaSupport);
        if (AlexaSupport.equals("MTK_VOW_AMAZON_SUPPORT=true")) {
            isAlexa = true;
        }
        traceBeginAndSlog("[isTriggerSupport] isAlexa " + isAlexa);
        return isAlexa;
    }

    /**
     * Get Number of Supported DSP engines.
     * @return return isVoWSupported or not.
     */
    private boolean isVowPDKSupported(Context mContext) {
        AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) {
            traceBeginAndSlog("[isVowPDKSupported] get audio service is null");
            return false;
        }
        int size = 0;
        String[] result = am.getParameters("MTK_VOW_MAX_PDK_NUMBER").split("=");
        if(result != null && result.length >= 2){
            size = Integer.parseInt(result[1]);
        }
        traceBeginAndSlog("[isVowPDKSupported] DSP Engine Size " + size);
        return size > 0;
    }
}
