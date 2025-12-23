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

import android.os.Trace;
import android.util.Slog;
import android.util.Log;

import java.lang.StackTraceElement;
import java.lang.Throwable;

public final class LogUtil {

    public static final boolean DEBUG = Config.isBoostFwkLogEnable();
    private static final String DEBUG_TAG_HEAD = "SBE-";

    public static void slogi(String tag, String msg) {
        Slog.i(tag, msg);
    }

    public static void slogw(String tag, String msg) {
        Slog.w(tag, msg);
    }

    public static void slogd(String tag, String msg) {
        if (DEBUG) {
            Slog.d(DEBUG_TAG_HEAD + tag, msg);
        }
    }

    public static void sloge(String tag, String msg) {
        Slog.e(tag, msg);
    }

    public static void slogeDebug(String tag, String msg) {
        if (DEBUG) {
            Slog.e(DEBUG_TAG_HEAD + tag, msg);
        }
    }

    public static void traceAndLog(String tag, String msg) {
        if (DEBUG) {
            Trace.traceBegin(Trace.TRACE_TAG_VIEW, msg);
            Slog.d(DEBUG_TAG_HEAD + tag, msg);
            Trace.traceEnd(Trace.TRACE_TAG_VIEW);
        }
    }

    public static void traceBeginAndLog(String tag, String msg) {
        if (DEBUG) {
            Trace.traceBegin(Trace.TRACE_TAG_VIEW, msg);
            Slog.d(DEBUG_TAG_HEAD + tag, msg);
        }
    }

    public static void mLogi(String tag, String msg) {
        Log.i(tag, msg);
    }

    public static void mLogw(String tag, String msg) {
        Log.w(tag, msg);
    }

    public static void mLogd(String tag, String msg) {
        if (DEBUG) {
            Log.d(DEBUG_TAG_HEAD + tag, msg);
        }
    }

    public static void mLoge(String tag, String msg) {
        Log.e(tag, msg);
    }

    public static void mLogeDebug(String tag, String msg) {
        if (DEBUG) {
            Log.e(DEBUG_TAG_HEAD + tag, msg);
        }
    }

    public static void traceAndMLogd(String tag, String msg) {
        if (DEBUG) {
            Trace.traceBegin(Trace.TRACE_TAG_VIEW, msg);
            Log.d(DEBUG_TAG_HEAD + tag, msg);
            Trace.traceEnd(Trace.TRACE_TAG_VIEW);
        }
    }

    public static void traceBeginAndMLogd(String tag, String msg) {
        if (DEBUG) {
            Trace.traceBegin(Trace.TRACE_TAG_VIEW, msg);
            Log.d(DEBUG_TAG_HEAD + tag, msg);
        }
    }

    public static void traceBegin(String msg) {
        if (DEBUG) {
            Trace.traceBegin(Trace.TRACE_TAG_VIEW, msg);
        }
    }

    public static void traceEnd() {
        if (DEBUG) {
            Trace.traceEnd(Trace.TRACE_TAG_VIEW);
        }
    }

    public static void trace(String msg) {
        if (DEBUG) {
            Trace.traceBegin(Trace.TRACE_TAG_VIEW, msg);
            Trace.traceEnd(Trace.TRACE_TAG_VIEW);
        }
    }

    public static void traceMessage(String msg) {
        Trace.traceBegin(Trace.TRACE_TAG_VIEW, msg);
        Trace.traceEnd(Trace.TRACE_TAG_VIEW);
    }

    public static String getStackTraceAsString() {
        StackTraceElement[] elements = new Throwable().getStackTrace();
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : elements) {
            sb.append(element.toString());
            sb.append("\n");
        }
        return sb.toString();
    }

}