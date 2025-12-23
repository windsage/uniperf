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

package com.mediatek.boostfwk.info;

import android.content.Context;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.pm.ApplicationInfo;
import android.app.Application;
import android.os.Bundle;
import android.os.Process;
import android.graphics.BLASTBufferQueue;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.Surface;
import android.view.ThreadedRenderer;
import android.view.ViewConfiguration;
import android.view.Window;
import android.util.SparseArray;

import com.mediatek.boostfwk.identify.frame.FrameIdentify;
import com.mediatek.boostfwk.info.RenderInfo;
import com.mediatek.boostfwk.utils.TasksUtil;
import com.mediatek.powerhalwrapper.PowerHalWrapper;
import com.mediatek.boostfwk.policy.frame.FrameDecision;
import com.mediatek.boostfwk.utils.Config;
import com.mediatek.boostfwk.utils.LogUtil;
import com.mediatek.boostfwk.utils.Util;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

//T-HUB Core[SPD]:added for TranFrameTracker by wei.kang 20241017 start
import com.android.internal.jank.InteractionJankMonitor;
import android.content.Context;
import android.app.Activity;
import android.view.ViewRootImpl;
import android.view.View;
import android.util.Slog;
//T-HUB Core[SPD]:added for TranFrameTracker by wei.kang 20241017 end

/** @hide */
public final class ActivityInfo {

    private static final String TAG = "ActivityInfo";

    public static final int NO_CHECKED_STATUS = 0;
    public static final int NON_RENDER_THREAD_TID = -1;

    private WeakReference<Context> activityContext = null;
    private WindowManager.LayoutParams attrs;
    private float density = -1;
    private String packageName = null;
    private int mRenderThreadTid = Integer.MIN_VALUE;
    private int mWebviewRenderPid = Integer.MIN_VALUE;
    private int mFlutterRenderTid = Integer.MIN_VALUE;

    // For touch scroll boost
    private static final int DEFAULT_TOUCH_SLOP = 24;
    private static final int DEFAULT_MIN_FLING_VELOCITY = 500;
    private int mTouchSlop = DEFAULT_TOUCH_SLOP;
    private int mMinimumFlingVelocity = DEFAULT_MIN_FLING_VELOCITY;
    private ViewConfiguration mConfiguration;

    private ArrayList<ActivityChangeListener> activityChangeListeners = null;
    ActivityStateListener mActivityStateListener = null;

    private volatile int mActivityCount = 0;

    private int mPageType = NO_CHECKED_STATUS;
    // Activity page type define---------- begin
    // Android draw flow design
    public static final int PAGE_TYPE_AOSP_DESIGN = 1 << 0;
    // Special app design, not use android AOSP draw flow
    public static final int PAGE_TYPE_SPECIAL_DESIGN = 1 << 1;
    public static final int PAGE_TYPE_WEBVIEW = 1 << 2;
    public static final int PAGE_TYPE_FLUTTER = 1 << 3;
    public static final int PAGE_TYPE_WEBVIEW_APPBRAND = 1 << 4;
    // Special app design type webview for 60hz
    public static final int PAGE_TYPE_WEBVIEW_60FPS = 1 << 5;
    // Special app design and do nothing
    public static final int PAGE_TYPE_SPECIAL_DESIGN_FULLSCREEN_GLTHREAD = 1 << 6;
    public static final int PAGE_TYPE_SPECIAL_DESIGN_NO_ACTIVITY = 1 << 7;
    public static final int PAGE_TYPE_SPECIAL_DESIGN_MAP = 1 << 8;
    // Mark this page has mutil draw, will queue mutil buffer,
    // Lead to SFP always prefetcher-frame, then lead to jank, so mark it
    public static final int PAGE_TYPE_SPECIAL_DESIGN_MUTIL_DRAW = 1 << 9;
    // Mark this page has app self define, so we can reset input flag for it
    public static final int PAGE_TYPE_AOSP_DESIGN_COMPOSE = 1 << 10;
    public static final int PAGE_TYPE_SPECIAL_DESIGN_RO = 1 << 11;
    public static final int PAGE_TYPE_AOSP_DESIGN_MULTI_FRAME = 1 << 12;
    // Activity page type define---------- end

    private boolean mIsMutilDrawPage = false;

    //low 16bits refrence SBE_MASK,
    //high 16bits refrence [16 ~ 19]traversalCount [20 ~ 32] frameIdMask
    private static int mSBEMask = 0;
    public static final int SBE_MASK_RECUE_HINT = 1 << 0;
    public static final int SBE_MASK_FRAME_HINT = 1 << 1;
    public static final int SBE_MASK_SKIP_FRAME_END_HINT = 1 << 2;
    public static final int SBE_MASK_DEBUG_HINT = 1 << 3;
    public static final int SBE_MASK_BUFFER_COUNT_HINT = 1 << 4;

    public String mGLTaskName = null;

    private final int CAPACITY_DEFAULT_SIZE = 20;
    private LinkedList<Integer> mLatestFrameCapacity = new LinkedList<Integer>();

    private SparseArray<WeakReference<ThreadedRenderer>> mWeakThreadedRenderArray = null;
    private WeakReference<ThreadedRenderer> mWeakThreadedRender = null;
    private WeakReference<BLASTBufferQueue> mWeakBlastBufferQueue = null;

    private static volatile ActivityInfo instance = null;
    private static final Object LOCK = new Object();

    //non-hwui render and dep info
    private String mSpecificRenderStr = null;
    private int mSpecificRenderSize = 0;
    //Key: pageType
    private SparseArray<List<RenderInfo>> mRenderInfoArray = new SparseArray();
    private List<String> mFlutterRenderList = new ArrayList<>();
    private String mFlutterRenderNameList = null;

    private ActivityInfo() {
        activityChangeListeners = new ArrayList<>(10);
        mSpecificRenderStr = "JNISurfaceTextu,CrGpuMain,Chrome_InProcGp";
        mSpecificRenderSize = 3;
    }

    /* only holder current activityInfo for a process*/
    public static ActivityInfo getInstance() {
        if (null == instance) {
            synchronized (ActivityInfo.class) {
                if (null == instance) {
                    instance = new ActivityInfo();
                }
            }
        }
        return instance;
    }

    public Context getContext() {
        if (activityContext == null) {
            return null;
        }
        return activityContext.get();
    }

    /**
     * update Context when activity resumed
     */
    public void setContext(Context context) {
        if (context == null) {
            return;
        }

        if (activityContext == null ) {
            activityContext = new WeakReference<Context>(context);
            initialBasicInfo(context);
        } else if (!context.equals(activityContext.get())) {
            activityContext.clear();
            activityContext = new WeakReference<Context>(context);
            initialBasicInfo(context);
        }
        notifyActivityUpdate(context);

        //T-HUB Core[SPD]:added for TranFrameTracker by wei.kang 20241017 start
        if(context instanceof Activity) {
            updateView((Activity)context);
        }
        //T-HUB Core[SPD]:added for TranFrameTracker by wei.kang 20241017 end
    }

    private void initialBasicInfo(Context context) {
        attrs = null;
        density = context.getResources().getDisplayMetrics().density;
        packageName = context.getPackageName();
        if (mActivityStateListener == null) {
            mActivityStateListener = new ActivityStateListener();
            if (context instanceof Activity) {
                Application app = ((Activity)context).getApplication();
                app.registerActivityLifecycleCallbacks(mActivityStateListener);
                //first activity resume
                mActivityCount++;

                //get view configuration and touch slop
                mConfiguration = ViewConfiguration.get(context);
                if (mConfiguration != null) {
                    mTouchSlop = mConfiguration.getScaledTouchSlop();
                    mMinimumFlingVelocity = mConfiguration.getScaledMinimumFlingVelocity();
                } else {
                    mTouchSlop = ViewConfiguration.getTouchSlop();
                    mMinimumFlingVelocity = ViewConfiguration.getMinimumFlingVelocity();
                }
            }
            if (Config.getBoostFwkVersion() > Config.BOOST_FWK_VERSION_2) {
                //int once for ensure receive activity change
                FrameDecision.getInstance();
                FrameIdentify.getInstance();
            }
            if (Config.isEnableSBEMbrain()) {
                //get mbrain instance &  register callback
                ScrollMbrainSync.getInstance();
            }
        }

    }

    private void notifyActivityUpdate(Context context) {
        mPageType = NO_CHECKED_STATUS;
        mIsMutilDrawPage = false;
        Config.resetFeatureOption();
        ScrollState.registerDispalyCallbacks();
        for (ActivityChangeListener listener : activityChangeListeners) {
            listener.onChange(context);
        }
    }

    private void notifyAllActivityStop(Context context) {
        for (ActivityChangeListener listener : activityChangeListeners) {
            listener.onAllActivityStop(context);
        }
        ScrollState.unregisterDispalyCallbacks();
        if (Config.isEnableSBEMbrain()) {
            // mbrain unregister , must unregister or will leak
            ScrollMbrainSync.tryunRegisterCallback();
        }
    }

    private void notifyActivityPause(Context context) {
        for (ActivityChangeListener listener : activityChangeListeners) {
            listener.onActivityPaused(context);
        }
    }

    public WindowManager.LayoutParams getWindowLayoutAttr() {
        if (attrs != null) {
            return attrs;
        }
        if (activityContext == null) {
            return null;
        }
        Context context = activityContext.get();
        Window win = null;
        if (context != null && (context instanceof Activity)) {
            win = ((Activity)context).getWindow();
        }
        if (win != null) {
            attrs = win.getAttributes();
            return attrs;
        }
        return null;
    }

    public float getDensity() {
        return density;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setRenderThreadTid(int renderThreadTid) {
        mRenderThreadTid = renderThreadTid;
    }

    public int getRenderThreadTid() {
        if (mRenderThreadTid == Integer.MIN_VALUE) {
            mRenderThreadTid = TasksUtil.findRenderTheadTid(Process.myPid());
        }
        return mRenderThreadTid;
    }

    public interface ActivityChangeListener{
        public void onChange(Context c);

        default void onAllActivityStop(Context c) {
        }

        default void onActivityPaused(Context c) {
        }
    }

    public void registerActivityListener(ActivityChangeListener changeListener) {
        if (changeListener == null) {
            return;
        }
        activityChangeListeners.add(changeListener);
    }

    public class ActivityStateListener implements Application.ActivityLifecycleCallbacks {

        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

        @Override
        public void onActivityStarted(Activity activity) {
            mActivityCount++;
        }

        @Override
        public void onActivityResumed(Activity activity) {
        }

        @Override
        public void onActivityPaused(Activity activity) {
            notifyActivityPause(activity);
        }

        @Override
        public void onActivityStopped(Activity activity) {
            if (--mActivityCount == 0 && !activity.isChangingConfigurations()) {
                notifyAllActivityStop(activity);
            }
            setFling(false);
        }
    }

    /**
     * Using binary to represent a page type
     * Some app may has webview & flutter at same time,
     * in this case using binary for better performance.
     * */
    public int getPageType() {
        if (mPageType != NO_CHECKED_STATUS) {
            if (LogUtil.DEBUG) {
                LogUtil.traceAndMLogd(TAG,"pageType--> " + pageType2Str());
            }
            LogUtil.traceMessage("pageType " + mPageType);
            return mPageType;
        }
        Context context = getContext();
        //only support activity pages
        if (context == null) {
            mPageType = PAGE_TYPE_SPECIAL_DESIGN_NO_ACTIVITY;
            return mPageType;
        }

        mRenderInfoArray.clear();

        Map<String, Integer> threads = TasksUtil.getCurProcThreadNameMap();

        if (threads == null || threads.isEmpty()) {
            //no task found recheck next time
            mPageType = NO_CHECKED_STATUS;
            return mPageType;
        }

        if ((mGLTaskName = TasksUtil.hasMapTask(threads)) != null) {
            mPageType = PAGE_TYPE_SPECIAL_DESIGN_MAP | PAGE_TYPE_SPECIAL_DESIGN;
        }
        if (Util.IsFullScreen(getWindowLayoutAttr())
                && (isPage(PAGE_TYPE_SPECIAL_DESIGN_MAP) || TasksUtil.hasGLTask(threads))) {
            mPageType = PAGE_TYPE_SPECIAL_DESIGN_FULLSCREEN_GLTHREAD;
        } else {
            if (TasksUtil.isAppBrand()) {
                mPageType |= PAGE_TYPE_WEBVIEW_APPBRAND | PAGE_TYPE_SPECIAL_DESIGN;
            }
            if (TasksUtil.isFlutterApp(context)) {
                mPageType |= PAGE_TYPE_FLUTTER | PAGE_TYPE_SPECIAL_DESIGN;
                tryUpdateRenderInfo(PAGE_TYPE_FLUTTER, "\\d*.raster", "\\d*.ui", threads);
                updateFlutterRenderTaskInfo();
            }
            if (TasksUtil.isWebview(threads)) {
                if (Config.getBoostFwkVersion() <= Config.BOOST_FWK_VERSION_2 &&
                        !isPage(PAGE_TYPE_FLUTTER | PAGE_TYPE_WEBVIEW_APPBRAND) &&
                        ScrollState.getRefreshRate() == ScrollState.REFRESHRATE_60) {
                    mPageType |= PAGE_TYPE_WEBVIEW_60FPS | PAGE_TYPE_SPECIAL_DESIGN;
                } else {
                    mPageType |= PAGE_TYPE_WEBVIEW | PAGE_TYPE_SPECIAL_DESIGN;
                }
                mWebviewRenderPid = Integer.MIN_VALUE;
            }

            if (packageName != null
                        && Config.GAME_FILTER_LIST.contains(packageName)) {
                mPageType |= PAGE_TYPE_SPECIAL_DESIGN_RO | PAGE_TYPE_SPECIAL_DESIGN;
                tryUpdateRenderInfo(PAGE_TYPE_SPECIAL_DESIGN_RO, "Thread-\\d*", "", threads);
            }

            if (!isSpecialPageDesign()) {
                mPageType = PAGE_TYPE_AOSP_DESIGN;
                if (TasksUtil.hasCompose(context.getClass().getClassLoader())) {
                    mPageType |= PAGE_TYPE_AOSP_DESIGN_COMPOSE;
                }
            }
            //SPF: add identify composer view have webview thread by sifeng.tian 20250207 start
            if (isPage(PAGE_TYPE_SPECIAL_DESIGN) && isPage(PAGE_TYPE_WEBVIEW)) {
                if (TasksUtil.hasCompose(context.getClass().getClassLoader())) {
                    mPageType = PAGE_TYPE_AOSP_DESIGN;
                    mPageType |= PAGE_TYPE_AOSP_DESIGN_COMPOSE;
                }
            }
            //SPF: add identify composer view have webview thread by sifeng.tian 20250207 end
        }
        if (LogUtil.DEBUG) {
            LogUtil.traceAndMLogd(TAG, getPackageName() + " finally pageType--> " + pageType2Str());
        }
        return mPageType;
    }

    public void markAsMutilDrawPage() {
        if (mPageType != NO_CHECKED_STATUS) {
            mPageType |= PAGE_TYPE_SPECIAL_DESIGN_MUTIL_DRAW;
        } else {
            mIsMutilDrawPage = true;
        }
    }

    private void addOrRemovePageType(boolean add, int pageType) {
        if (mPageType == NO_CHECKED_STATUS) {
            return;
        }

        if (add) {
            mPageType |= pageType;
        } else {
            mPageType &= (~pageType);
        }
    }

    public void mutilFramePage(boolean add) {
        addOrRemovePageType(add, PAGE_TYPE_AOSP_DESIGN_MULTI_FRAME);
    }

    public void clearMutilDrawPage() {
        if (mPageType != NO_CHECKED_STATUS) {
            mPageType &= (~PAGE_TYPE_SPECIAL_DESIGN_MUTIL_DRAW);
        }
        mIsMutilDrawPage = false;
    }

    public boolean isMutilDrawPage() {
        return isPage(PAGE_TYPE_SPECIAL_DESIGN_MUTIL_DRAW);
    }

    public boolean isSpecialPageDesign() {
        return isPage(PAGE_TYPE_SPECIAL_DESIGN);
    }

    public boolean isAOSPPageDesign() {
        return isPage(PAGE_TYPE_AOSP_DESIGN);
    }

    public boolean isPage(int pageType) {
        return (mPageType & pageType) != 0;
    }

    public String pageType2Str() {
        if (!LogUtil.DEBUG) {
            return "";
        }
        int pageType = mPageType;
        if (mPageType == NO_CHECKED_STATUS) {
            return "NO_CHECKED_STATUS";
        }
        String result;

        if (isPage(PAGE_TYPE_SPECIAL_DESIGN_NO_ACTIVITY)) {
            result = "PAGE_TYPE_SPECIAL_DESIGN_NO_ACTIVITY";
        } else if (isPage(PAGE_TYPE_AOSP_DESIGN)) {
            result = "PAGE_TYPE_AOSP_DESIGN";
            if (isPage(PAGE_TYPE_AOSP_DESIGN_COMPOSE)) {
                result += " & PAGE_TYPE_AOSP_DESIGN_COMPOSE";
            }
        } else if(isPage(PAGE_TYPE_SPECIAL_DESIGN_FULLSCREEN_GLTHREAD)){
            result = "PAGE_TYPE_SPECIAL_DESIGN_FULLSCREEN_GLTHREAD";
        } else {
            result = "PAGE_TYPE_SPECIAL_DESIGN";
            if (isPage(PAGE_TYPE_WEBVIEW)) {
                result += " & PAGE_TYPE_WEBVIEW";
            }
            if (isPage(PAGE_TYPE_FLUTTER)) {
                result += " & PAGE_TYPE_FLUTTER";
            }
            if (isPage(PAGE_TYPE_WEBVIEW_60FPS)) {
                result += " & PAGE_TYPE_WEBVIEW_60FPS";
            }
            if (isPage(PAGE_TYPE_WEBVIEW_APPBRAND)) {
                result += " & PAGE_TYPE_WEBVIEW_APPBRAND";
            }
            if (isPage(PAGE_TYPE_SPECIAL_DESIGN_MAP)) {
                result += " & PAGE_TYPE_SPECIAL_DESIGN_MAP";
            }
            if (isPage(PAGE_TYPE_SPECIAL_DESIGN_MUTIL_DRAW)) {
                result += " & PAGE_TYPE_SPECIAL_DESIGN_MUTIL_DRAW";
            }
        }
        return result;
    }

    private void tryUpdateRenderInfo(int pageType, String renderRegex, String depRegex,
            Map<String, Integer> threads) {
        if (renderRegex == null || depRegex == null) {
            return;
        }

        List<RenderInfo> renders = mRenderInfoArray.get(pageType);
        if (renders != null) {
            renders.clear();
        }
        List<String> depList = new ArrayList<>();
        List<Integer> depTidList = new ArrayList<>();

        Set<String> theadNames = threads.keySet();

        Pattern renderP = null;
        Pattern depP = null;

        if (renderRegex.length() > 0) {
            renderP = Pattern.compile(renderRegex);
        }
        if (depRegex.length() > 0) {
            depP = Pattern.compile(depRegex);
        }

        for (String taskName : theadNames) {
            if(renderP != null
                    && taskName != null
                    && renderP.matcher(taskName).matches()) {
                RenderInfo render = new RenderInfo();
                render.renderName = taskName;
                render.renderTid = threads.get(taskName);

                //add render
                if (renders == null) {
                    renders = new ArrayList<>();
                    renders.add(render);
                } else  if (!renders.contains(render)) {
                    renders.add(render);
                }
            }

            if (depP != null
                    && taskName != null
                    && depP.matcher(taskName).matches()) {
                depList.add(taskName);
                depTidList.add(threads.get(taskName));
            }
        }

        //add renders to array
        if (renders != null && renders.size() > 0) {
            for (RenderInfo render : renders) {
                render.knownDepNameList.addAll(depList);
                render.knownDepTidList.addAll(depTidList);
                if (LogUtil.DEBUG) {
                    LogUtil.traceAndMLogd(TAG, "add a new render for page "
                            + pageType + " render->" + render);
                }

                //add renderName to specific render.
                if (pageType != PAGE_TYPE_AOSP_DESIGN
                        && !mSpecificRenderStr.contains(render.renderName)) {
                    mSpecificRenderStr += "," + render.renderName;
                    mSpecificRenderSize++;
                }
            }
            mRenderInfoArray.put(pageType, renders);
        }

        if (pageType != PAGE_TYPE_AOSP_DESIGN && depList.size() > 0) {
            //add all dep to specific render
            for (String depName : depList) {
                //add dep to specific render.
                if (!mSpecificRenderStr.contains(depName)) {
                    mSpecificRenderStr += "," + depName;
                    mSpecificRenderSize++;
                }
            }
            if (LogUtil.DEBUG) {
                LogUtil.traceAndMLogd(TAG, "final SpecificRender="
                        + mSpecificRenderStr + " size=" + mSpecificRenderSize);
            }
        }
    }

    public int updateSpecialPageTypeIfNecessary(boolean isRunning, int pageType) {
        if (isRunning) {
            if (pageType != PAGE_TYPE_AOSP_DESIGN
                    && mPageType == PAGE_TYPE_AOSP_DESIGN) {
                mPageType = PAGE_TYPE_SPECIAL_DESIGN;
            }
            mPageType |= pageType;
        } else {
            mPageType &= ~pageType;
        }

//T-HUB Core[SPD]:added for updateSpecialPageType for chrome by dewang.tan 20251009 start    
        if (mPageType == PAGE_TYPE_SPECIAL_DESIGN && !"com.android.chrome".equals(getPackageName())) {
//T-HUB Core[SPD]:added for updateSpecialPageType for chrome by dewang.tan 20251009 end      
            mPageType = PAGE_TYPE_AOSP_DESIGN;
        }

        if (LogUtil.DEBUG) {
            LogUtil.traceAndLog(TAG, "updateSpecialPageTypeIfNecessary "
                    + " mPageType = " + mPageType);
        }
        return mPageType;
    }

    public int getSpecificRenderSize() {
        return mSpecificRenderSize;
    }

    public String getSpecificRenderStr() {
        return mSpecificRenderStr;
    }

    public List<RenderInfo> getRendersByPageType(int pageType) {
        return mRenderInfoArray.get(pageType);
    }

    public void tryUpdateWebViewRenderPid() {
        if (LogUtil.DEBUG) {
            LogUtil.mLogd(TAG, "tryUpdateWebViewRenderPid");
        }

        List<RenderInfo> renders = mRenderInfoArray.get(PAGE_TYPE_WEBVIEW);
        if (renders == null) {
            renders = new ArrayList<RenderInfo>();
        } else {
            renders.clear();
        }

        Context context = getContext();
        if (context != null) {
            ActivityManager activityManager
                    = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager == null) {
                mWebviewRenderPid = NON_RENDER_THREAD_TID;
                return;
            }

            List<ActivityManager.RunningAppProcessInfo> processInfos
                    = activityManager.getRunningAppProcesses();
            if (processInfos == null) {
                mWebviewRenderPid = NON_RENDER_THREAD_TID;
                return;
            }

            int l = TasksUtil.BLINK_WEBVIEW_PROCESS.length;
            for (ActivityManager.RunningAppProcessInfo process : processInfos) {
                if (process.processName == null) continue;

                for (String blinkWebviewProcess : TasksUtil.BLINK_WEBVIEW_PROCESS) {
                    if (process.processName.contains(blinkWebviewProcess)) {
                        int renderPid = process.pid;
                        renders.add(new RenderInfo(TasksUtil.BLINK_WEBVIEW_RENDER_NAME, renderPid));
                        renders.add(new RenderInfo(TasksUtil.UC_WEBVIEW_RENDER_NAME, renderPid));
                        if (LogUtil.DEBUG) {
                            LogUtil.mLogd(TAG, "findGPUProcessPid pid=" + renderPid);
                        }
                    }
                }
            }
            mRenderInfoArray.put(PAGE_TYPE_WEBVIEW,renders);
        }
    }

    public int getWebViewRenderPid() {
        if (mWebviewRenderPid != Integer.MIN_VALUE) {
            return mWebviewRenderPid;
        }
        Context context = getContext();
        if (context != null) {
            ActivityManager activityManager
                    = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager == null) {
                mWebviewRenderPid = NON_RENDER_THREAD_TID;
                return mWebviewRenderPid;
            }

            List<ActivityManager.RunningAppProcessInfo> processInfos
                    = activityManager.getRunningAppProcesses();
            if (processInfos == null) {
                mWebviewRenderPid = NON_RENDER_THREAD_TID;
                return mWebviewRenderPid;
            }

            int l = TasksUtil.BLINK_WEBVIEW_PROCESS.length;
            for (ActivityManager.RunningAppProcessInfo p : processInfos) {
                String pName = p.processName;
                if (pName == null) {
                    continue;
                }
                for (int i = 0; i < l; i++) {
                    if(pName.contains(TasksUtil.BLINK_WEBVIEW_PROCESS[i])) {
                        mWebviewRenderPid = p.pid;
                        if (LogUtil.DEBUG) {
                            LogUtil.mLogd(TAG, "findGPUProcessPid pid=" + mWebviewRenderPid);
                        }
                        return mWebviewRenderPid;
                    }
                }
            }
        }
        //only find once
        mWebviewRenderPid = NON_RENDER_THREAD_TID;
        return mWebviewRenderPid;
    }


    public int getFlutterRenderTid(){
        if (mFlutterRenderTid != Integer.MIN_VALUE) {
            return mFlutterRenderTid;
        }
        mFlutterRenderTid = TasksUtil.findFlutterRenderTidForPid();
        return mFlutterRenderTid;
    }

    public void updateFlutterRenderTaskInfo(){
        StringBuilder renderTaskBuilder = new StringBuilder();
        mFlutterRenderList.clear();
        mFlutterRenderList.add(TasksUtil.FLUTTER_TASKS);
        renderTaskBuilder.append(TasksUtil.FLUTTER_TASKS);
        for (int i = 0; i < TasksUtil.FLUTTER_RENDER_TASK.length; i++) {
            String renderString = TasksUtil.FLUTTER_RENDER_TASK[i];
            if (!mFlutterRenderList.contains(renderString)){
                mFlutterRenderList.add(renderString);
                renderTaskBuilder.append(",").append(renderString);
            }
        }

        //render find dynamic
        List<RenderInfo> renders = mRenderInfoArray.get(ActivityInfo.PAGE_TYPE_FLUTTER);
        if (renders != null && renders.size() > 0) {
            for (RenderInfo render : renders) {
                String renderName = render.getRenderName();
                if (renderName != null && !mFlutterRenderList.contains(renderName)){
                    mFlutterRenderList.add(renderName);
                    renderTaskBuilder.append(",").append(renderName);
                }
            }
        }
        mFlutterRenderNameList = renderTaskBuilder.toString();

        if (LogUtil.DEBUG) {
            LogUtil.mLogd(TAG, "updateFlutterRenderTaskInfo->"
                + mFlutterRenderList.toString() + " mFlutterRenderNameList->"
                + mFlutterRenderNameList);
        }
    }

    public String getFlutterRenderTask() {
        return mFlutterRenderNameList == null ? "" : mFlutterRenderNameList;
    }

    public int getFlutterRenderTaskNum() {
        if (mFlutterRenderNameList != null && mFlutterRenderList != null) {
            return mFlutterRenderList.size();
        }
        return 0;
    }

    public void setBufferQueue(BLASTBufferQueue blastBufferQueue) {
        final BLASTBufferQueue queue = blastBufferQueue;
        if (queue == null) {
            return;
        }
        mWeakBlastBufferQueue = new WeakReference<BLASTBufferQueue>(queue);
        if (LogUtil.DEBUG) {
            LogUtil.mLogd(TAG, "add new bufferqueue = " + queue);
        }
    }

    public BLASTBufferQueue getBLASTBufferQueue() {
        if (mWeakBlastBufferQueue == null) {
            return null;
        }
        return mWeakBlastBufferQueue.get();
    }

    public void setThreadedRenderer(ThreadedRenderer threadedRenderer) {
        final ThreadedRenderer render = threadedRenderer;
        if (render == null) {
            return;
        }

        synchronized (LOCK) {
            if (mWeakThreadedRenderArray == null) {
                mWeakThreadedRenderArray = new SparseArray();
            }

            int index = render.hashCode();
            if (mWeakThreadedRenderArray.contains(index)) {
                //already added.
                return;
            }

            WeakReference<ThreadedRenderer> weakThreadedRender =
                    new WeakReference<ThreadedRenderer>(render);
            mWeakThreadedRenderArray.put(index, weakThreadedRender);
            if (LogUtil.DEBUG) {
                LogUtil.mLogd(TAG, "add new render = " + threadedRenderer);
            }
        }
    }

    public ThreadedRenderer getThreadedRenderer(boolean checkSurface) {
        synchronized (LOCK) {
            if (mWeakThreadedRenderArray == null) {
                return null;
            }
        }

        ThreadedRenderer render = null;
        Surface surface = null;
        //using cache first
        if (mWeakThreadedRender != null &&
                (render = mWeakThreadedRender.get()) != null) {
            if (Config.getBoostFwkVersion() > Config.BOOST_FWK_VERSION_2 && checkSurface) {
                // after version 3, if surface not valid anymore, get a new one.
                surface = render.getSurface();
                if (LogUtil.DEBUG) {
                    LogUtil.mLogd(TAG, "check cache render= "
                            + render + " enable=" + (surface != null && surface.isValid()));
                }
                if (surface != null && surface.isValid()) {
                    return render;
                }
            } else {
                return render;
            }
        }

        synchronized (LOCK) {
            int size = mWeakThreadedRenderArray.size();
            if (size == 0) {
                return null;
            }
            //find first not null & surface valid render
            int key;
            WeakReference<ThreadedRenderer> weakThreadedRender;
            for (int i = size - 1; i >= 0; i--) {
                key = mWeakThreadedRenderArray.keyAt(i);
                weakThreadedRender = mWeakThreadedRenderArray.get(key);
                if (weakThreadedRender != null &&
                        (render = weakThreadedRender.get()) != null) {
                    if (Config.getBoostFwkVersion() > Config.BOOST_FWK_VERSION_2 && checkSurface) {
                        surface = render.getSurface();
                        if (surface == null || !surface.isValid()) {
                            if (LogUtil.DEBUG) {
                                LogUtil.mLogd(TAG, "check render= " + render
                                        + " enable=" + (surface != null && surface.isValid()));
                            }
                            continue;
                        }
                    }
                    //find not null render, cache it.
                    mWeakThreadedRender = weakThreadedRender;
                    return render;
                }
            }
            //all render is null, clear it.
            mWeakThreadedRenderArray.clear();
        }
        return null;
    }

    public ThreadedRenderer getThreadedRenderer() {
        return getThreadedRenderer(false);
    }

    public synchronized int getBufferCountFromProducer() {
        BLASTBufferQueue queue = getBLASTBufferQueue();
        return queue == null ? 0 : (int)queue.getBufferCountFromProducer();
    }

    public static synchronized int updateSBEMask(int mask, boolean add) {
        if (add) {
            mSBEMask |= mask;
        } else {
            mSBEMask &= (~mask);
        }
        if (LogUtil.DEBUG) {
            LogUtil.traceAndMLogd(TAG, "updateSBEMask to " + mSBEMask);
        }
        return mSBEMask;
    }

    public static void clearFrameInfo() {
        mSBEMask = (mSBEMask & 0xFFFF);
    }

    public static boolean containMask(int mask) {
        return (mSBEMask & mask) != 0;
    }

    public void recordLastFrameCapacity(int capacity) {
        if (capacity <= 0) {
            return;
        }
        if (mLatestFrameCapacity.size() == CAPACITY_DEFAULT_SIZE) {
            mLatestFrameCapacity.removeFirst();
        }
        mLatestFrameCapacity.addLast(capacity);
    }

    public int getLastFrameCapacity() {
        Integer result = mLatestFrameCapacity.peekLast();
        if (result == null || result.intValue() <= 0) {
            return -1;
        }
        return result.intValue();
    }

    public int getScaledTouchSlop() {
        return mTouchSlop;
    }

    public int getMinimumFlingVelocity() {
        return mMinimumFlingVelocity;
    }

    //T-HUB Core[SPD]:added for TranFrameTracker by wei.kang 20241017 start
    private static final int TRAN_CUJ_SCROLL = 10001;
    private WeakReference<View> mViewReference = null;
    private boolean mIsBeginInteraction = false;

    public void setFling(boolean isFling) {
        LogUtil.mLogd(TAG, "setFling isFling:" + isFling);
        if(isFling){
            beginInteractionJankMonitor();
        } else {
            endInteractionJankMonitor();
        }
    }

    private void beginInteractionJankMonitor() {
        if(mViewReference == null) {
            return;
        }
        View view = mViewReference.get();
        LogUtil.mLogd(TAG, "beginInteractionJankMonitor view=" + view);
        if (view != null) {
            mIsBeginInteraction = true;
            InteractionJankMonitor.getInstance().begin(view, TRAN_CUJ_SCROLL);
        }
    }

    public void endInteractionJankMonitor() {
        if(mIsBeginInteraction) {
            LogUtil.mLogd(TAG, "endInteractionJankMonitor");
            mIsBeginInteraction = false;
            InteractionJankMonitor.getInstance().end(TRAN_CUJ_SCROLL);
        }
    }

    private void updateView(Activity activity) {
        if(activity == null || activity.getWindow() == null) {
            return;
        }
        Window window = activity.getWindow();
        View decorView = window.getDecorView();
        if(decorView != null) {
            decorView.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        ViewRootImpl viewRootImpl = decorView.getViewRootImpl();
                        View currView = null;
                        if(viewRootImpl != null) {
                            currView = viewRootImpl.getView();
                        }
                        if(currView != null) {
                            mViewReference = new WeakReference<>(currView);
                            LogUtil.mLogd(TAG, "updateView view=" + currView);
                        } else {
                            mViewReference = null;
                        }
                    } catch (Exception e) {
                        mViewReference = null;
                    }
                }
            });
        } else {
            mViewReference = null;
        }
    }
    //T-HUB Core[SPD]:added for TranFrameTracker by wei.kang 20241017 end

}
