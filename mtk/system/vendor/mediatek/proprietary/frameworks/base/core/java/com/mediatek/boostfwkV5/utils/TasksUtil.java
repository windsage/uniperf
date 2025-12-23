/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2021. All rights reserved.
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

package com.mediatek.boostfwkV5.utils;

import static android.os.Process.PROC_OUT_STRING;
import android.content.Context;
import android.app.Activity;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.Window;
import android.os.Process;
import android.os.StrictMode;
import android.util.Log;

import com.mediatek.boostfwkV5.utils.LogUtil;
import com.mediatek.boostfwkV5.utils.Util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.File;
import java.lang.StackTraceElement;
import java.lang.ClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @hide
 */
public final class TasksUtil {

    private static final String TAG = "TasksUtil";
    private static final int[] CMDLINE_OUT = new int[] { PROC_OUT_STRING };
    private static final String sRenderThreadName = "RenderThread";
    public static final String BLINK_WEBVIEW_RENDER_NAME = "CrGpuMain";
    public static final String UC_WEBVIEW_RENDER_NAME = "VizGpuMain";
    public static final String WEB_RENDER_NAME = "Chrome_InProcGp";
    public static final String WEBVIEW_CRITICAL_TASKS = "Compositor,CrRendererMain,"
            + "Chrome_ChildIOT,Chrome_IOThread,VizCompositorTh,ThreadPoolForeg,AcquireImageThr";
    private static final String[] sSpecialTaskList = {"Chrome_InProcGp",
        "Chrome_IOThread", "hippy.js"};
    private static final String sFlutterEngineName = "flutter_engine";
    private static final String sAppBrandUI = "appbrand";
    private static final String[] GAME_TASKS = {"UnityMain", "MainThread-UE4", "UnityChoreograp"};
    private static final String[] GL_TASKS = {"GLThread"};
    public static final String[] GL_MAP_TASKS = {/* maps */"GNaviMap-GL", "Tmcom-MapRender"};
    public static final String[] BLINK_WEBVIEW_PROCESS = {"gpu_process", "leged_process",
        "privileged_process","privilege_process"};
    public static final String[] FLUTTER_RENDER_TASK = {"JNISurfaceTextu"};
    //simple: "1.ui, xxx, yyy"
    public static final String FLUTTER_TASKS = "1.ui";
    public static final String FLUTTER_RENDER_TASKS = "JNISurfaceTextu";
    public static final String FLUTTER_CRITICAL_TASKS = "1.ui,1.raster,JNISurfacseTextu";
    public static final String WEBVIEW_TASKS = "Chrome_InProcGp,Chrome_IOThread,"
        + "hippy.js,CrGpuMain,AcquireImageThr";
    public static final String[] DECODE_TASKS =
            {"glide-weibosour","glide-disk-cach","LibraPicLoader_"};


    //this define is copy from frameworks/base/core/java/android/os/StrictMode.java
    private static final int STRICT_MODE_DETECT_THREAD_DISK_READ = 1 << 1;

    private TasksUtil() {
        super();
    }

    /**
     * Loop from proc/pid/ to find render thread tid,
     * the first render thread that UX render, because this
     * render create when process create init step.
     */
    @Deprecated
    public static int findRenderTheadTid(int pid) {
        int rdTid = -1;
        String filePath = "/proc/" + pid + "/task";

        int[] pids = new int[1024];
        pids = Process.getPids(filePath, pids);

        if (pids == null) {
            return -1;
        }

        for (int tmpPid : pids) {
            if (tmpPid < 0) {
                break;
            }
            filePath = "/proc/" + pid + "/task/" + tmpPid + "/comm";
            String taskName = readCmdlineFromProcfs(filePath);
            if (taskName != null && !taskName.equals("")) {
                if (taskName.trim().equals(sRenderThreadName)) {
                    if (Config.isBoostFwkLogEnable()) {
                        LogUtil.mLogd(TAG, "renderthread tid = " + tmpPid);
                    }
                    rdTid = tmpPid;
                    break;
                }
            }
        }
        if (Config.isBoostFwkLogEnable()) {
            LogUtil.mLogd("ScrollIdentify", "pid = " + pid + "render thread id = " + rdTid);
        }
        return rdTid;
    }

    @Deprecated
    public static int findFlutterRenderTidForPid() {
        int frTid = -1;
        int pid = Process.myPid();
        String filePath = "/proc/" + pid + "/task";

        int[] pids = new int[1024];
        pids = Process.getPids(filePath, pids);

        if (pids == null) {
            return -1;
        }

        for (int tmpPid : pids) {
            if (tmpPid < 0) {
                break;
            }
            filePath = "/proc/" + pid + "/task/" + tmpPid + "/comm";
            String taskName = readCmdlineFromProcfs(filePath);
            if (taskName != null && !taskName.equals("")) {
                String tmp = taskName.trim();
                for (String task : FLUTTER_RENDER_TASK) {
                    if (tmp.equals(task)) {
                        if (LogUtil.DEBUG) {
                            LogUtil.mLogd(TAG, "findFlutterRenderTidForPid tid = " + tmpPid);
                        }
                        frTid = tmpPid;
                        break;
                    }
                }
            }
            if (frTid > 0) {
                break;
            }
        }
        return frTid;
    }

    private static String readCmdlineFromProcfs(String filePath) {
        String[] cmdline = new String[1];
        if (!Process.readProcFile(filePath, CMDLINE_OUT, cmdline, null, null)) {
            return "";
        }
        return cmdline[0];
    }

    /**
     * Check AppBrand comm
     */
    public static boolean isAppBrand() {
        String commName = android.os.Process.myProcessName();
        if (commName != null && commName.contains(sAppBrandUI)) {
            if (Config.isBoostFwkLogEnable()) {
                LogUtil.mLogd(TAG, "This is app brand.");
            }
            return true;
        }
        return false;
    }

    public static boolean isWebview(Map<String, Integer> threadInfo) {
        //only allow equal case
        return containTaskOrNot(threadInfo, sSpecialTaskList, false) != null;
    }

    /**
     * Check cross platform task.
     */
    @Deprecated
    private static boolean hasCrossPlatformTaskV1() {
        int pid = Process.myPid();
        int crossPlatformCount = 0;
        String filePath = "/proc/" + pid + "/task";

        int[] pids = new int[1024];
        pids = Process.getPids(filePath, pids);

        if (pids == null) {
            return false;
        }

        for (int tmpPid : pids) {
            if (tmpPid < 0) {
                break;
            }
            filePath = "/proc/" + pid + "/task/" + tmpPid + "/comm";
            String taskName = readCmdlineFromProcfs(filePath);
            if (taskName != null && !taskName.equals("")) {
                for (String spTaskName : sSpecialTaskList) {
                    if (taskName.trim().equals(spTaskName)) {
                        crossPlatformCount ++;
                        break;
                    }
                }
            }
        }

        if (crossPlatformCount == 0) {
            return false;
        }
        return true;
    }

    public static boolean hasCompose(ClassLoader loader) {
        if (loader == null) {
            return false;
        }
        try {
            Class<?> cls = loader.loadClass("androidx.compose.ui.platform.AndroidComposeView");
            if (LogUtil.DEBUG) {
                LogUtil.mLogd(TAG, "------ hasCompose= " + loader + " " + cls);
            }
            return cls != null;
        } catch (Exception e) {
            return false;
        }
    }

    public static String hasMapTask(Map<String, Integer> threadInfo) {
        if (threadInfo == null) {
            return null;
        }
        List<String> task = containTaskOrNot(threadInfo, GL_MAP_TASKS, true);
        //only one
        return (task == null || task.isEmpty()) ? null : task.get(0);
    }

    /**
     * @return all active thread in this process
     */
    public static Set<Thread> findProThreadInfo() {
        Map<Thread, StackTraceElement[]> allThreads= Thread.getAllStackTraces();
        Set<Thread> threads = allThreads.keySet();
        return threads;
    }

    public static boolean hasGLTask(Map<String, Integer> threadInfo) {
        return containTaskOrNot(threadInfo, GL_TASKS, true) != null;
    }

    public static boolean hasDecodeTask(Map<String, Integer> threadInfo) {
        return containTaskOrNot(threadInfo, DECODE_TASKS, true) != null;
    }

    public static Map<String, Integer> getCurProcThreadNameMap() {
        int pid = Process.myPid();
        String filePath = "/proc/" + pid + "/task";
        int[] pids = new int[1024];
        pids = Process.getPids(filePath, pids);
        Map<String,Integer> idNMap = new HashMap<String,Integer>(100);

        if (pids == null) {
            return idNMap;
        }

        for (int tmpPid : pids) {
            if (tmpPid < 0) {
                break;
            }
            filePath = "/proc/" + pid + "/task/" + tmpPid + "/comm";
            String taskName = readCmdlineFromProcfs(filePath);
            if (taskName != null && !taskName.equals("")) {
                if (taskName.endsWith("?")) {
                    taskName = taskName.substring(0, taskName.length() - 1);
                }
                idNMap.put(taskName.trim(), tmpPid);
            }
        }
        return idNMap;
    }

    /**
     *
     * @param threadInfo
     * @param contain
     * @return name list of contain task, if not exist return null
     */
    private static List<String> containTaskOrNot(Map<String, Integer> threadInfo,
            String[] taskList, boolean contain) {
        if (threadInfo == null || threadInfo.isEmpty()
                || taskList == null || taskList.length == 0) {
            return null;
        }
        List<String> result = new ArrayList<>(10);
        if (contain) {
            Set<String> theadNames = threadInfo.keySet();
            for (String tName : theadNames) {
                for (String taskName : taskList) {
                    if(tName.contains(taskName)) {
                        result.add(tName);
                    }
                }
            }
        } else {
            for (String taskName : taskList) {
                if(threadInfo.get(taskName) != null) {
                    result.add(taskName);
                }
            }
        }
        return result.isEmpty() ? null : result;
    }

    public static boolean containTask(int pid, String[] taskList, boolean contain) {
        int taskCount = 0;
        String filePath = "/proc/" + pid + "/task";

        int[] pids = new int[1024];
        pids = Process.getPids(filePath, pids);

        if (pids == null) {
            return false;
        }

        for (int tmpPid : pids) {
            if (tmpPid < 0) {
                break;
            }
            filePath = "/proc/" + pid + "/task/" + tmpPid + "/comm";
            String taskName = readCmdlineFromProcfs(filePath);
            if (taskName != null && !taskName.equals("")) {
                for (String spTaskName : taskList) {
                    if ((contain && taskName.trim().contains(spTaskName))
                            || taskName.trim().equals(spTaskName)) {
                        taskCount ++;
                        break;
                    }
                }
            }
        }

        if (taskCount == 0) {
            return false;
        }
        return true;
    }

    public static boolean isGameAPP(String packageName) {
        int pid = Process.myPid();
        return Util.isGameApp(packageName)
                || containTask(pid, GAME_TASKS, false);
    }

    public static boolean isFlutterApp(Context context) {
        int pid = Process.myPid();
        boolean isFlutter = false;

        File codeCacheDir = context.getCodeCacheDir();
        File[] fs = codeCacheDir.listFiles();
        if (fs != null && fs.length != 0) {
            for (File f : fs) {
                if (sFlutterEngineName.equals(f.getName())) {
                    if (Config.isBoostFwkLogEnable()) {
                        LogUtil.mLogd(TAG, "This is flutter.");
                    }
                    isFlutter = true;
                }
            }
        }
        return isFlutter;
    }


    public static boolean isAPPInStrictMode(String packageName) {
        //for performance of identify, only check webview case.
        if (packageName == null || !packageName.contains("webview")) {
            return false;
        }
        //checking is app in DETECT_THREAD_DISK_READ mode.
        StrictMode.ThreadPolicy policy = StrictMode.getThreadPolicy();
        if (policy == null) {
            return false;
        }
        //string result must like: "[StrictMode.ThreadPolicy; mask=" + mask + "]";
        String mask = policy.toString()
                            .replace("[StrictMode.ThreadPolicy; mask=", "")
                            .replace("]", "");
        if (mask == null || mask == "") {
            return false;
        }
        boolean result = ((int)Integer.parseInt(mask) & STRICT_MODE_DETECT_THREAD_DISK_READ) != 0;
        if (Config.isBoostFwkLogEnable() && result) {
            LogUtil.mLogd(TAG, "This is app in strictmode -> "+packageName+" mask:"+mask);
        }
        return result;
    }
}