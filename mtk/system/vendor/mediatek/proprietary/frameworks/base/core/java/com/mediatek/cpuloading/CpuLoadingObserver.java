/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 *
 * MediaTek Inc. (C) 2018. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */

package com.mediatek.cpuloading;

import android.os.StrictMode;
import android.os.SystemProperties;
import android.os.UEventObserver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;

import android.util.Slog;

/**
 * CPU loading observer is used to observe CPU loading, when CPU loading
 * is at high status, it will callback to the observer.
 */
public class CpuLoadingObserver {
    private static final String TAG = CpuLoadingObserver.class.getSimpleName();

    private static final String UEVENT_PATH = "DEVPATH=/devices/virtual/misc/cpu_loading";
    private static final String SPECIFY_32BIT_CPUS_PATH = "sys/devices/system/cpu/aarch32_el0";
    private static final String BACKGROUND_CPUS_PATH = "/dev/cpuset/background/cpus";
    private static final String CPUS_PATH = "/dev/cpuset/cpus";
    private static final String CPU_POLICY_PATH_PREFIX = "sys/devices/system/cpu/cpufreq/policy";
    private static final String RELATED_CPUS = "/related_cpus";

    private static final String POLLING_ON_OFF = "/proc/cpu_loading/onoff";
    private static final String OVER_THRESHOLD = "/proc/cpu_loading/overThrhld";
    private static final String POLLING_TIME_SECOND = "/proc/cpu_loading/poltime_secs";

    private static final String SPECIFY_CPUS = "/proc/cpu_loading/specify_cpus";
    private static final String SPECIFY_OVER_THRESHOLD ="/proc/cpu_loading/specify_overThrhld";

    private static final String CORE_CPUS = "/proc/cpu_loading/core_cpus";
    private static final String CORE_CPUS_THRESHOLD ="/proc/cpu_loading/core_cpus_overThrhld";

    private static int DEFAULT_THRESHOLD = 85;
    private static int DEFAULT_WINDOW = 10;
    private static int DEFAULT_SPECIFY_THRESHOLD = 85;
    private static int SPECIFY_RELEASE_TARGET = 15;

    private static int MAX_CORE_COUNT = 16;

    private Observer mObserver;
    private MyUEventObserver mUEventObserver;
    private int mThreshold = DEFAULT_THRESHOLD;
    private int mWindow = DEFAULT_WINDOW;
    private int mSpecifyThreshold = DEFAULT_SPECIFY_THRESHOLD;
    private String mSpecifyCpus = "";
    private String mCoreCpus = "";

    /**
     * Observer to callback high CPU loading event;
     */
    public interface Observer {
        void onHighCpuLoading(int release);
    }

    public CpuLoadingObserver() {
        mUEventObserver = new MyUEventObserver();
        mSpecifyCpus = readSpecifyCpus();
        mCoreCpus = readCoreCpus();
    }

    /**
     * Set observer to observe high CPU loading.
     *
     * @param observer Instance of {@link Observer}.
     */
    public void setObserver(Observer observer) {
        mObserver = observer;
    }

    /**
     * Start observing CPU loading.
     */
    public void startObserving() {
        writeFile(POLLING_ON_OFF, "1");
        writeFile(OVER_THRESHOLD, String.valueOf(mThreshold));
        writeFile(POLLING_TIME_SECOND, String.valueOf(mWindow));

        if (!mSpecifyCpus.equals("")) {
            writeFile(SPECIFY_CPUS, mSpecifyCpus);
            writeFile(SPECIFY_OVER_THRESHOLD, String.valueOf(mSpecifyThreshold));
        }

        if (!mCoreCpus.equals(mSpecifyCpus) && !mCoreCpus.equals("")) {
            writeFile(CORE_CPUS, mCoreCpus);
            writeFile(CORE_CPUS_THRESHOLD, String.valueOf(DEFAULT_SPECIFY_THRESHOLD));
        }

        mUEventObserver.startObserving(UEVENT_PATH);
    }

    private String readSpecifyCpus() {
        String specifyCpus = readFile(SPECIFY_32BIT_CPUS_PATH);
        if (specifyCpus.equals("")) {
            specifyCpus = readFile(BACKGROUND_CPUS_PATH);
        }

        if (!specifyCpus.equals("")) {
            String[] arr = specifyCpus.split("-");
            if(arr.length == 2) {
                specifyCpus = arr[1].trim() + arr[0].trim();
            }
            return specifyCpus;
        }

        return "";
    }

    private String readCoreCpus() {
        String coreCpus = "";
        int cpuCount = 0;

        String cpusValue = readFile(CPUS_PATH);
        if (!cpusValue.equals("")) {
            String[] arr = cpusValue.split("-");
            if(arr.length == 2) {
                cpuCount = Integer.valueOf(arr[1].trim());
            }
        }

        int startCpuNumber = 0;
        int endCpuNumber = 0;
        for (int i = 0; i < MAX_CORE_COUNT && startCpuNumber < cpuCount; i++) {
            String coreXCpus = readFile(CPU_POLICY_PATH_PREFIX + i + RELATED_CPUS);
            if (coreXCpus.equals("")) {
                continue;
            }
            String[] arr = coreXCpus.split(" ");
            if(arr.length > 0) {
                endCpuNumber = Integer.valueOf(arr[arr.length - 1].trim());
            }

            if (i > 0 && (endCpuNumber >= cpuCount || endCpuNumber == startCpuNumber)) {
                break;
            }
            coreCpus = "" + endCpuNumber + startCpuNumber + coreCpus;
            startCpuNumber = endCpuNumber + 1;
            i = endCpuNumber;
        }
        return coreCpus;
    }

    /**
     * Stop observing CPU loading.
     */
    public void stopObserving() {
        writeFile(POLLING_ON_OFF, "0");
        mUEventObserver.stopObserving();
        mObserver = null;
    }

    private void writeFile(String filePath, String value) {
        if (filePath == null) {
            return;
        }

        File file = new File(filePath);
        if (!file.exists()) {
            Slog.w(TAG, "file is not exist, path:" + filePath);
            return;
        }

        FileOutputStream out = null;

        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        StrictMode.allowThreadDiskWrites();

        try {
            out = new FileOutputStream(file);
            out.write(value.getBytes());
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    private String readFile(String filePath) {
        if (filePath == null) {
            return "";
        }

        File file = new File(filePath);
        if (!file.exists()) {
            Slog.w(TAG, "file is not exist, path:" + filePath);
            return "";
        }
        FileInputStream in = null;

        try {
            in = new FileInputStream(file);
            InputStreamReader input = new InputStreamReader(in, "UTF-8");
            StringWriter output = new StringWriter();
            char[] buffer = new char[1024];
            long count = 0;
            int n = 0;
            while (-1 != (n = input.read(buffer))) {
                output.write(buffer, 0, n);
                count += n;
            }
            return output.toString();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        }
        return "";
    }

    private class MyUEventObserver extends UEventObserver {

        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            String over = event.get("over");
            String specify_over = event.get("specify_over");
            String cpu_cores_over = event.get("cpu_cores_over");
            if (over != null && over.equals("1")) {
                mObserver.onHighCpuLoading(-1);
            } else if (specify_over != null && specify_over.equals("1")) {
                mObserver.onHighCpuLoading(SPECIFY_RELEASE_TARGET);
            } else if (cpu_cores_over != null && cpu_cores_over.equals("1")) {
                mObserver.onHighCpuLoading(SPECIFY_RELEASE_TARGET);
            }
        }
    }
}
