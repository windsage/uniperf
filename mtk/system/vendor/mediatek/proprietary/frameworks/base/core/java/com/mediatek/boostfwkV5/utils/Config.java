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

import android.os.SystemProperties;

import java.util.ArrayList;

/** @hide */
public final class Config {

    private static final String BOOST_FWK_VERSION = "vendor.boostfwk.version";
    private static final String ENHANCE_LOG_PROPERTY_NAME = "vendor.boostfwk.log.enable";
    private static final String DISABLE_BOOST_FWK_PROPERTY_NAME = "vendor.boostfwk.option";
    private static final String FRAME_RESCUE_PROPERTY_NAME = "vendor.boostfwk.rescue";
    private static final String SCROLL_COMMON_POLICY_PROPERTY_NAME
            = "vendor.boostfwk.scroll.common.policy";
    private static final String DISABLE_SCROLL_IDENTIFY_PROPERTY_NAME
            = "vendor.boostfwk.scrollidentify.option";
    private static final String DISABLE_FRAME_IDENTIFY_PROPERTY_NAME
            = "vendor.boostfwk.frameidentify.option";
    private static final String ENABLE_BOOST_DISPLAY_60_PROPERTY_NAME
            = "vendor.boostfwk.display60";
    private static final String DISABLE_BOOST_FWK_FRAME_PREFETCHER_NAME
            = "vendor.boostfwk.frameprefetcher";
    private static final String DISABLE_BOOST_FWK_PRE_ANIMATION_NAME
            = "vendor.boostfwk.preanimation";
    private static final String BOOST_FWK_TOUCH_PROPERTY_NAME
            = "vendor.boostfwk.touch";
    private static final String FRAME_RESCUE_CHECK_POINT_PROPERTY_NAME
            = "vendor.boostfwk.rescue.checkpoint";
    private static final String FRAME_RESCUE_TRAVERSAL_CHECK_POINT_PROPERTY_NAME
            = "vendor.boostfwk.rescue.checkpoint.traversal";
    private static final String FRAME_RESCUE_TRAVERSAL_THRESHOLD_PROPERTY_NAME
            = "vendor.boostfwk.rescue.threshold.traversal";
    private static final String FRAME_SBE_HORIZONTAL_SCROLL_DURATION
            = "vendor.boostfwk.scroll.duration.h";
    private static final String FRAME_SBE_VERTICAL_SCROLL_DURATION
            = "vendor.boostfwk.scroll.duration.v";
    private static final String FRAME_SBE_SBB_ENABLE_LATER
            = "vendor.boostfwk.sbb.touch.duration";
    private static final String FRAME_SBE_SCROLL_FREQ_FLOOR
            = "vendor.boostfwk.scroll.floor";
    private static final String FRAME_SBE_SCROLL_MOVE_BOOST_THRESHOLD
            = "vendor.boostfwk.scroll.move.threshold";
    private static final String BOOST_FWK_FRAME_DECISION_PROPERTY_NAME
            = "vendor.boostfwk.frame.decision";
    private static final String FRAME_RUNNING_RESCUE_PROPERTY_NAME
            = "vendor.boostfwk.frame.running.rescue";
    private static final String FRAME_RUNNING_FILTER_PROPERTY_NAME
            = "vendor.boostfwk.frame.running.filter";
    private static final String MTK_SCROLL_POLICY_MODE_OPTION
            = "vendor.boostfwk.scroll.policy.mode";
    private static final String MTK_SCROLL_DISBALE_TOUCH_TIME
            = "vendor.boostfwk.scroll.disable.touch";
    private static final String BOOST_FWK_DYNAMIC_RESCUE_MODE_OPTION
            = "vendor.boostfwk.dy.rescue.mode";
    private static final String BOOST_FWK_SCROLL_COMMON_MODE_HWUI_OPTION
            = "vendor.boostfwk.scroll.common.hwui";
    private static final String BOOST_FWK_SCROLL_COMMON_MODE_NON_HWUI_OPTION
            = "vendor.boostfwk.scroll.common.nonhwui";

    //feature switch: scroll send scrolling data to MBrain
    private static final String MTK_SBE_MBrain_PROPERITY
            = "vendor.boostfwk.scroll.mbrain.mode";

    private static final String MTK_SBE_SCROLL_TOUCH_MOVING
            = "vendor.boostfwk.scroll.moving";


    ///M:HWUI vulkan prop, copy from /frameworks/base/libs/hwui/Properties.cpp#49
    private static final String HWUI_PROP_USE_VULKAN = "ro.hwui.use_vulkan";
    private static final String BOOST_FWK_FRAME_PREFETCHER_INCREASE_BUFFER
            = "vendor.boostfwk.frameprefetcher.increase.buffer";
    private static final String MTK_BOOST_FWK_FRAME_PREFETCHER_DROP_BUFFER
            = "vendor.boostfwk.frameprefetcher.drop.buffer";

    // ConsistencyEngine config
    // 0:off
    // 1:on + use predict capability(predict with targettime)
    // 2:on + use default capability(dont predict with targettime)
    private static final String ENHANCE_CONSYSTENCY_PROPERTY_NAME
            = "vendor.boostfwk.consis.enable";
    private static final String CONSISTENCY_CAPBILITY_PROPERTY_NAME
            = "vendor.boostfwk.consis.capability";
    private static final String CONSISTENCY_RESPONSE_PROPERTY_NAME
            = "vendor.boostfwk.consis.response";
    // End of ConsistencyEngine config

    public static final String USER_CONFIG_DEFAULT_VALUE = "-1";
    public static final String USER_CONFIG_DEFAULT_TYPE = "1";

    private static final String ENABLE_ENHANCE_LOG = "1" ;
    private static final String DISABLE_SCROLL_IDENTIFY = "0" ;
    private static final String DISABLE_FRAME_IDENTIFY = "0" ;
    private static final String DISABLE_BOOST_FWK = "0" ;
    private static final String ENABLE_BOOST_DISPLAY_60 = "0" ;
    private static final String DISABLE_BOOST_FWK_FRAME_PREFETCHER = "0" ;
    private static final String DISABLE_BOOST_FWK_PRE_ANIMATION = "0" ;
    private static final String DISABLE_BOOST_FWK_TOUCH = "0" ;
    private static final String DISABLE_FRAME_RESCUE = "0" ;
    private static final String DISABLE_SCROLL_COMMON_POLICY = "0" ;
    private static final String EANBLE_BOOST_FWK_MBRAIN = "1" ;

    private static final boolean BOOLEAN_ENABLE_LOG
            = ENABLE_ENHANCE_LOG.equals(SystemProperties.get(ENHANCE_LOG_PROPERTY_NAME));
    private static final boolean BOOLEAN_DISABLE_SCROLL_IDENTIFY
            = !DISABLE_SCROLL_IDENTIFY.equals(
                SystemProperties.get(DISABLE_SCROLL_IDENTIFY_PROPERTY_NAME));
    private static final boolean BOOLEAN_DISABLE_FRAME_IDENTIFY
            = DISABLE_FRAME_IDENTIFY.equals(
                SystemProperties.get(DISABLE_FRAME_IDENTIFY_PROPERTY_NAME));
    private static final boolean BOOLEAN_DISABLE_BOOST_FWK
            = DISABLE_BOOST_FWK.equals(SystemProperties.get(DISABLE_BOOST_FWK_PROPERTY_NAME));
    private static final boolean BOOLEAN_ENABLE_BOOST_DISPLAY_60
            = !ENABLE_BOOST_DISPLAY_60.equals(
                SystemProperties.get(ENABLE_BOOST_DISPLAY_60_PROPERTY_NAME));

    private static final boolean BOOLEAN_DISABLE_BOOST_FWK_FRAME_PREFETCHER
            = !DISABLE_BOOST_FWK_FRAME_PREFETCHER.equals(
                SystemProperties.get(DISABLE_BOOST_FWK_FRAME_PREFETCHER_NAME));
    private static final boolean BOOLEAN_BOOST_FWK_PRE_ANIMATION
            = !DISABLE_BOOST_FWK_PRE_ANIMATION.equals(
                SystemProperties.get(DISABLE_BOOST_FWK_PRE_ANIMATION_NAME));
    private static final boolean BOOLEAN_ENABLE_FRAME_RESCUE = !DISABLE_FRAME_RESCUE.equals(
                    SystemProperties.get(FRAME_RESCUE_PROPERTY_NAME));
    private static final boolean BOOLEAN_ENABLE_BOOST_FWK_TOUCH = !DISABLE_BOOST_FWK_TOUCH.equals(
                    SystemProperties.get(BOOST_FWK_TOUCH_PROPERTY_NAME));
    private static final boolean BOOLEAN_ENABLE_SCROLL_COMMON_POLICY
            = !DISABLE_SCROLL_COMMON_POLICY.equals(
                SystemProperties.get(SCROLL_COMMON_POLICY_PROPERTY_NAME));
    private static final boolean BOOLEAN_DISABLE_FRAME_DECISION_WHEN_SCROLL
            = "0".equals(SystemProperties.get(BOOST_FWK_FRAME_DECISION_PROPERTY_NAME));

    private static final boolean BOOLEAN_BOOST_FWK_MBRAIN
            = EANBLE_BOOST_FWK_MBRAIN.equals(SystemProperties.get(MTK_SBE_MBrain_PROPERITY));

    private static final boolean BOOLEAN_ENABLE_CONSISTENCY
            = !"0".equals(SystemProperties.get(ENHANCE_CONSYSTENCY_PROPERTY_NAME));
    private static final String ENABLE_CONSISTENCY_MODE
            = SystemProperties.get(ENHANCE_CONSYSTENCY_PROPERTY_NAME);


    public static final int FEATURE_OPTION_NON_CHECK = -100;
    public static final int FEATURE_OPTION_ON = 1;
    public static final int FEATURE_OPTION_OFF = 0;
    //scroll policy mode define
    public static final int MTK_SCROLL_POLICY_MODE_DEFAULT = 1;
    public static final int MTK_SCROLL_POLICY_MODE_EAS = 2;
    //scroll default ctrl duration define
    public static final int SCROLLING_FING_HORIZONTAL_HINT_DURATION = 500;
    public static final int SCROLLING_FING_VERTICAL_HINT_DURATION = 3000;
    public static final int TOUCH_HINT_DURATION_DEFAULT = 1000;
    //default rescue strength 50
    public static final int FRAME_HINT_RESCUE_STRENGTH = 50;
    public static final int FRAME_HINT_RESCUE_CHECK_POINT = 50;

    /**--------------------------SBE Version define -----------------**/
    //default version is 1, if systemprop exsit follow prop define;
    private static int mBoostFwkVersion = 0;
    public static final int BOOST_FWK_VERSION_1 = 1;
    public static final int BOOST_FWK_VERSION_2 = 2;
    public static final int BOOST_FWK_VERSION_3 = 3;
    public static final int BOOST_FWK_VERSION_4 = 4;
    public static final int BOOST_FWK_VERSION_5 = 5;

    /**--------------------------Frame Decision Mode define-----------------**/
    public static final int FRAME_DECISION_MODE_DISABLE = 0;
    //enbale frame decision for scrolling and non-scorlling
    public static final int FRAME_DECISION_MODE_FOR_ALL = 1;
    //enbale frame decision for scorlling
    public static final int FRAME_DECISION_MODE_FOR_SCROLLING = 2;
    //touch boost duration , need sync with prop, and align to POWERHAL Touch boost Config
    public static final int TOUCH_BOOST_DURATION = 103;
    //touch move boost duration
    public static final int TOUCH_MOVE_BOOST_DURATION = 90;

    /**--------------------------Consistency engine define-----------------**/
    //Consistency default cpu capbility
    public static final int CONSISTENCY_CAPBILITY_MIN = 0;
    public static final int CONSISTENCY_CAPBILITY_MAX = 1024;
    public static final int CONSISTENCY_CAPBILITY_DEFAULT = 675;
    //Consistency default app launch response time
    public static final int CONSISTENCY_RESPONSE_MIN = 0;
    public static final int CONSISTENCY_RESPONSE_MAX = 200;
    public static final int CONSISTENCY_RESPONSE_DEFAULT = 76;
    public static final String CONSISTENCY_DISENABLE = "0";
    public static final String CONSISTENCY_ENABLE = "1";
    public static final String CONSISTENCY_ENABLE_WITHOUT_PREDICT = "2";

    /** -----------------------MSync3 Config Start  -------------------**/
    // Msync3 config
    private static final String ENABLE_MSYNC3_NAME = "vendor.msync3.enable";
    // MSync3 smooth fling config
    private static final String ENABLE_MSYNC3_SMOOTHFLING_NAME = "vendor.msync3.smoothfling";
    // MSync3 customer config
    private static final String ENABLE_MSYNC3_CUSTOMERCONFIG_NAME = "vendor.msync3.customerconfig";
    // e.g : 120,90,60,30
    private static final String MSYNC3_FLING_SUPPORT_REFRESHRATE_NAME
            = "vendor.msync3.flingrefreshrate";
    // e.g : 1.82,1.365,0.91,0 ,Number must math  MSYNC3_FLING_SUPPORT_REFRESHRATE_NAME
    private static final String MSYNC3_FLING_REFRESHRATE_GAT_NAME
            = "vendor.msync3.flingrefreshrategap";
    // e.g.: if set 90fps, this means if touch scroll speed is low , set fps to 90 fps
    public static final String MSYNC3_TOUCHSCROLL_REFRESHRATE_SPEED_LOW_NAME
            = "vendor.msync3.touchscrolllow";
    // e.g.: if set 90fps, this means if list fling with video, set floor fps to 90 fps
    public static final String MSYNC3_VIDEO_FLOOR_FPS_NAME
            = "vendor.msync3.videofloor";
    // e.g.: if set 90fps, this means if list fling with video, set floor fps to 90 fps
    public static final String MSYNC3_TOUCH_SCROLL_VELOCITY_NAME
            = "vendor.msync3.touchscrollvelocity";
    public static final String MSYNC3_TOUCH_SCROLL_NAME
            = "vendor.msync3.touchscroll";
    public static final String CONFIG_FPSGO_RELEASE_TIME
            = "vendor.boostfwk.scroll.fpsgo.release";

    private static final String ENABLE_MSYNC3 = "1" ;
    private static final String ENABLE_MSYNC3_SMOOTHFLING = "1" ;

    /** -----------------------Customer Default Config Start  -------------------**/
    private static final String CUSTOMER_DEFAULT_CONFIG_ENABLE_MSYNC_MSYNC3 = "0";
    private static final String CUSTOMER_DEFAULT_CONFIG_MSYNC3_CUSTOMERCONFIG = "-1";
    private static final String CUSTOMER_DEFAULT_CONFIG_ENABLE_MSYNC_SMOOTHFLING = "0";
    private static final String CUSTOMER_DEFAULT_CONFIG_FLING_SUPPORT_REFRESHRATE = "";
    private static final String CUSTOMER_DEFAULT_CONFIG_FLING_REFRESHRATE_CHANGE_GAP = "";
    private static final String CUSTOMER_DEFAULT_CONFIG_TOUCHSCROLL_REFRESHRATE_SPEED_LOW = "";
    private static final String CUSTOMER_DEFAULT_CONFIG_VIDEO_FLOOR_FPS = "60";
    private static final String CUSTOMER_DEFAULT_CONFIG_TOUCH_SCROLL_VELOCITY = "";
    private static final String CUSTOMER_DEFAULT_CONFIG_TOUCH_SCROLL_ENABLE = "0";
    /** -----------------------Customer Default Config End  -------------------**/

    private static final boolean BOOLEAN_ENABLE_MSYNC_MSYNC3 =
            ENABLE_MSYNC3.equals(SystemProperties.get(ENABLE_MSYNC3_NAME,
                CUSTOMER_DEFAULT_CONFIG_ENABLE_MSYNC_MSYNC3));
    private static final boolean BOOLEAN_ENABLE_MSYNC_MSYNC3_TOUCH_SCROLL =
            ENABLE_MSYNC3.equals(SystemProperties.get(MSYNC3_TOUCH_SCROLL_NAME,
                CUSTOMER_DEFAULT_CONFIG_TOUCH_SCROLL_ENABLE));
    private static final boolean BOOLEAN_ENABLE_MSYNC_SMOOTHFLING_MSYNC3 =
            ENABLE_MSYNC3_SMOOTHFLING.equals(SystemProperties.get(
                ENABLE_MSYNC3_SMOOTHFLING_NAME, CUSTOMER_DEFAULT_CONFIG_ENABLE_MSYNC_SMOOTHFLING));
    public static final String MSYNC3_FLING_SUPPORT_REFRESHRATE =
            SystemProperties.get(MSYNC3_FLING_SUPPORT_REFRESHRATE_NAME,
                CUSTOMER_DEFAULT_CONFIG_FLING_SUPPORT_REFRESHRATE);
    public static final String MSYNC3_FLING_REFRESHRATE_CHANGE_GAP =
            SystemProperties.get(MSYNC3_FLING_REFRESHRATE_GAT_NAME,
                CUSTOMER_DEFAULT_CONFIG_FLING_REFRESHRATE_CHANGE_GAP);
    public static final String MSYNC3_TOUCHSCROLL_REFRESHRATE_SPEED_LOW =
            SystemProperties.get(MSYNC3_TOUCHSCROLL_REFRESHRATE_SPEED_LOW_NAME,
                CUSTOMER_DEFAULT_CONFIG_TOUCHSCROLL_REFRESHRATE_SPEED_LOW);
    public static final String MSYNC3_VIDEO_FLOOR_FPS =
            SystemProperties.get(MSYNC3_VIDEO_FLOOR_FPS_NAME,
                CUSTOMER_DEFAULT_CONFIG_VIDEO_FLOOR_FPS);
    public static final String MSYNC3_TOUCH_SCROLL_VELOCITY =
            SystemProperties.get(MSYNC3_TOUCH_SCROLL_VELOCITY_NAME,
                CUSTOMER_DEFAULT_CONFIG_TOUCH_SCROLL_VELOCITY);
    public static final String MSYNC3_MSYNC3_CUSTOMERCONFIG =
            SystemProperties.get(ENABLE_MSYNC3_CUSTOMERCONFIG_NAME,
                CUSTOMER_DEFAULT_CONFIG_MSYNC3_CUSTOMERCONFIG);
    public static final float FLING_DEFAULT_GAP_CHANGE_REFRESHRATE = 1.83f;
    /** --------------------MSync3 Config Start  -----------------------**/

    /** --------------------Feature option for V3  -----------------------**/
    private static int mFreqFloorStatus = FEATURE_OPTION_NON_CHECK;
    public static boolean enableScrollFloor = false;
    private static int mFrameDecisionMode = FEATURE_OPTION_NON_CHECK;
    private static int mDisableTouchTime = FEATURE_OPTION_NON_CHECK;
    private static int mSFPDropBuffer = FEATURE_OPTION_NON_CHECK;
    private static int mTouchHintDuration = FEATURE_OPTION_NON_CHECK;
    private static int mHorizontalScrollDuration = FEATURE_OPTION_NON_CHECK;
    private static int mVerticalScrollDuration = FEATURE_OPTION_NON_CHECK;
    private static int mMoveThreshold = FEATURE_OPTION_NON_CHECK;
    private static int mScrollPolicyType = FEATURE_OPTION_NON_CHECK;
    private static int mRunningRescueFilterCount = 100;
    private static float mCheckPoint = FEATURE_OPTION_NON_CHECK;
    private static float mTraversalCheckPoint = FEATURE_OPTION_NON_CHECK;
    private static float mTraversalThreshold = FEATURE_OPTION_NON_CHECK;
    public static boolean dealyReleaseFPSGO = true;
    public static boolean mIncreaseBuffer = true;
    public static boolean checkCommonPolicyForALL = true;
    public static boolean preRunningRescue = false;
    private static boolean mEnableTouchMoveBoost = false;

    /** --------------------Feature option for V4  -----------------------**/
    private static int mDyRescueMode = FEATURE_OPTION_NON_CHECK;
    private static final int DY_RESCUE_MODE_BUFFER_COUNT_FILTER = 1 << 0;
    private static final int DY_RESCUE_MODE_TRAVERSAL_DYNAMIC_CHECKPOINT = 1 << 1;
    private static final int DY_RESCUE_MODE_OBTIANVIEW_INFLATE_CHECKPOINT = 1 << 2;
    private static final int DY_RESCUE_MODE_SECOND_RESCUE = 1 << 3;
    private static final int DY_RESCUE_MODE_MAX = 1 << 10;
    private static int mScrollCommonModeHwui = FEATURE_OPTION_NON_CHECK;
    private static int mScrollCommonModeNonHwui = FEATURE_OPTION_NON_CHECK;
    public static final int SCROLLING_COMMON_MODE_60 = 1 << 0;
    public static final int SCROLLING_COMMON_MODE_90 = 1 << 1;
    public static final int SCROLLING_COMMON_MODE_120 = 1 << 2;
    public static final int SCROLLING_COMMON_MODE_MAX = 1 << 10;
    public static boolean isSecondRescue = false;
    public static boolean isOICheckpoint = false;
    public static boolean isBufferCountFilterSBE = false;
    public static boolean isBufferCountFilterFpsgo = false;
    public static boolean isTraversalDynamicCheckpoint = false;

    /** --------------------Feature option for V5  -----------------------**/
    private static int mConsisCap = FEATURE_OPTION_NON_CHECK;
    private static int mConsisResponse = FEATURE_OPTION_NON_CHECK;
    private static int mTouchMovingPolicy = FEATURE_OPTION_NON_CHECK;

    /** ----------Feature option for migration chip based V2/3/4... -------------**/
    private static int mScrollDisableTouchBoost = FEATURE_OPTION_NON_CHECK;
    private static final String MTK_SCROLL_INTERRUPT_TOUCHBOOST
            = "vendor.boostfwk.scroll.interrupt.touchboost";


    public static final ArrayList<String> SYSTEM_PACKAGE_ARRAY = new ArrayList<String>() {
        {
            add("android");
        }
    };

    //using frame decision for non-activity applications
    public static final ArrayList<String> FRAME_TRACKING_LIST = new ArrayList<String>() {
        {
            add("com.android.systemui");
        }
    };

    public static final ArrayList<String> UX_POLICY_FILTER_LIST = new ArrayList<String>() {
        {
            add("com.android.systemui");
            add("com.android.launcher3");
        }
    };

    public static final ArrayList<String> GAME_FILTER_LIST = new ArrayList<String>() {
        {
            add("com.roblox.client");
        }
    };

    static {
        //update default feature option for diff boostfwk
        generateDefalutFeatureForDiffVersion();
    }

    public static void resetFeatureOption() {
        if (getBoostFwkVersion() > BOOST_FWK_VERSION_2) {
            mFrameDecisionMode = FEATURE_OPTION_NON_CHECK;
            mTouchHintDuration = FEATURE_OPTION_NON_CHECK;
            mHorizontalScrollDuration = FEATURE_OPTION_NON_CHECK;
            mVerticalScrollDuration = FEATURE_OPTION_NON_CHECK;
            mCheckPoint = FEATURE_OPTION_NON_CHECK;
            mTraversalCheckPoint = FEATURE_OPTION_NON_CHECK;
            mTraversalThreshold = FEATURE_OPTION_NON_CHECK;
            mFreqFloorStatus = FEATURE_OPTION_NON_CHECK;
        }
    }

    public static int getIntFromPropWithCheck(String prop, int defalutVal, int min, int max) {
        int result = SystemProperties.getInt(prop, defalutVal);
        if (result < min || result > max) {
            result = defalutVal;
        }
        return result;
    }

    public static boolean isBoostFwkLogEnable() {
        return BOOLEAN_ENABLE_LOG;
    }

    public static boolean isBoostFwkScrollIdentify() {
        return BOOLEAN_DISABLE_SCROLL_IDENTIFY;
    }

    public static boolean disableFrameIdentify() {
        return BOOLEAN_DISABLE_FRAME_IDENTIFY;
    }

    public static boolean disableSBE() {
        return BOOLEAN_DISABLE_BOOST_FWK;
    }

    public static boolean isBoostFwkScrollIdentifyOn60hz() {
        return BOOLEAN_ENABLE_BOOST_DISPLAY_60;
    }

    public static int getBoostFwkVersion() {
        if (mBoostFwkVersion == 0) {
            mBoostFwkVersion = getIntFromPropWithCheck(
                BOOST_FWK_VERSION,
                BOOST_FWK_VERSION_1,
                BOOST_FWK_VERSION_1,/**min */
                BOOST_FWK_VERSION_5/**max */);
        }
        return mBoostFwkVersion;
    }

    private static void generateDefalutFeatureForDiffVersion() {
        int version = getBoostFwkVersion();

        if (version <= BOOST_FWK_VERSION_2) {
            SYSTEM_PACKAGE_ARRAY.add("com.android.systemui");
            enableScrollFloor = true;
            mScrollPolicyType = MTK_SCROLL_POLICY_MODE_DEFAULT;
            mFrameDecisionMode = FRAME_DECISION_MODE_DISABLE;
            dealyReleaseFPSGO = false;
            mIncreaseBuffer = false;
            preRunningRescue = false;
            checkCommonPolicyForALL = false;
            mTouchHintDuration = TOUCH_HINT_DURATION_DEFAULT;
            mCheckPoint = FRAME_HINT_RESCUE_CHECK_POINT / 100.0f;
            mHorizontalScrollDuration = SCROLLING_FING_HORIZONTAL_HINT_DURATION;
            mVerticalScrollDuration = SCROLLING_FING_VERTICAL_HINT_DURATION;
            mEnableTouchMoveBoost = false;
        }

        if (version == BOOST_FWK_VERSION_3) {
            dealyReleaseFPSGO = true;
            mIncreaseBuffer = true;
            mFrameDecisionMode = FRAME_DECISION_MODE_FOR_SCROLLING;
            checkCommonPolicyForALL = true;
            preRunningRescue =  (FEATURE_OPTION_OFF != getIntFromPropWithCheck(
                FRAME_RUNNING_RESCUE_PROPERTY_NAME,
                FEATURE_OPTION_ON,
                FEATURE_OPTION_OFF,/**min */
                FEATURE_OPTION_ON/**max */));
            enableScrollFloor = FEATURE_OPTION_ON == getSrcollFreqFloorStatus();
            mEnableTouchMoveBoost = true;
            SYSTEM_PACKAGE_ARRAY.add("com.android.systemui");
        }

        if (version >= BOOST_FWK_VERSION_4) {
            dealyReleaseFPSGO = true;
            mIncreaseBuffer = true;
            mFrameDecisionMode = FRAME_DECISION_MODE_FOR_SCROLLING;
            checkCommonPolicyForALL = true;
            preRunningRescue =  (FEATURE_OPTION_OFF != getIntFromPropWithCheck(
                FRAME_RUNNING_RESCUE_PROPERTY_NAME,
                FEATURE_OPTION_ON,
                FEATURE_OPTION_OFF,/**min */
                FEATURE_OPTION_ON/**max */));
            enableScrollFloor = FEATURE_OPTION_ON == getSrcollFreqFloorStatus();

            getDyRescueMode();
            if ((mDyRescueMode & DY_RESCUE_MODE_BUFFER_COUNT_FILTER) != 0) {
                isBufferCountFilterFpsgo = true;
            }
            if ((mDyRescueMode & DY_RESCUE_MODE_SECOND_RESCUE) != 0) {
                isSecondRescue = true;
            }
            if ((mDyRescueMode & DY_RESCUE_MODE_OBTIANVIEW_INFLATE_CHECKPOINT) != 0) {
                isOICheckpoint = true;
            }
            if ((mDyRescueMode & DY_RESCUE_MODE_TRAVERSAL_DYNAMIC_CHECKPOINT) != 0) {
                isTraversalDynamicCheckpoint = true;
            }

            mEnableTouchMoveBoost = false;
        }

        if (version >= BOOST_FWK_VERSION_5) {
            mEnableTouchMoveBoost = true;
            mConsisCap = CONSISTENCY_CAPBILITY_DEFAULT;
            mConsisResponse = CONSISTENCY_RESPONSE_DEFAULT;
            mRunningRescueFilterCount = getIntFromPropWithCheck(
                FRAME_RUNNING_FILTER_PROPERTY_NAME,
                3,
                0,/**min */
                100/**max */);
        }
    }

    public static int getConsistencyCapability() {
        if (mConsisCap <= 0) {
            mConsisCap = getIntFromPropWithCheck(
                CONSISTENCY_CAPBILITY_PROPERTY_NAME,
                CONSISTENCY_CAPBILITY_DEFAULT,/**default */
                CONSISTENCY_CAPBILITY_MIN,/**min */
                CONSISTENCY_CAPBILITY_MAX/**max */);
        }
        return mConsisCap;
    }

    public static int getConsistencyResponse() {
        if (mConsisResponse <= 0) {
            mConsisResponse = getIntFromPropWithCheck(
                CONSISTENCY_RESPONSE_PROPERTY_NAME,
                CONSISTENCY_RESPONSE_DEFAULT,/**default */
                CONSISTENCY_RESPONSE_MIN,/**min */
                CONSISTENCY_RESPONSE_MAX/**max */);
        }
        return mConsisResponse;
    }

    private static int getDyRescueMode() {
        if (mDyRescueMode <= 0) {
            mDyRescueMode = getIntFromPropWithCheck(
                BOOST_FWK_DYNAMIC_RESCUE_MODE_OPTION,
                (DY_RESCUE_MODE_BUFFER_COUNT_FILTER
                | DY_RESCUE_MODE_OBTIANVIEW_INFLATE_CHECKPOINT
                | DY_RESCUE_MODE_TRAVERSAL_DYNAMIC_CHECKPOINT
                | DY_RESCUE_MODE_SECOND_RESCUE),
                0,/**min */
                DY_RESCUE_MODE_MAX/**max */);
        }
        return mScrollPolicyType;
    }

    public static int getScrollCommonHwuiMode() {
        if (mScrollCommonModeHwui <= 0) {
            mScrollCommonModeHwui = getIntFromPropWithCheck(
                BOOST_FWK_SCROLL_COMMON_MODE_HWUI_OPTION,
                0,
                0,/**min */
                SCROLLING_COMMON_MODE_MAX/**max */);
        }
        return mScrollCommonModeHwui;
    }

    public static int getScrollCommonNonHwuiMode() {
        if (mScrollCommonModeNonHwui <= 0) {
            mScrollCommonModeNonHwui = getIntFromPropWithCheck(
                BOOST_FWK_SCROLL_COMMON_MODE_NON_HWUI_OPTION,
                0,
                0,/**min */
                SCROLLING_COMMON_MODE_MAX/**max */);
        }
        return mScrollCommonModeNonHwui;
    }

    public static int getScollPolicyType() {
        if (mScrollPolicyType <= 0) {
            mScrollPolicyType = getIntFromPropWithCheck(
                MTK_SCROLL_POLICY_MODE_OPTION,
                MTK_SCROLL_POLICY_MODE_DEFAULT,
                MTK_SCROLL_POLICY_MODE_DEFAULT,/**min */
                MTK_SCROLL_POLICY_MODE_EAS/**max */);
        }
        return mScrollPolicyType;
    }

    public static boolean SFPCouldDropBuffer() {
        if (mSFPDropBuffer < 0) {
            mSFPDropBuffer = getIntFromPropWithCheck(
                MTK_BOOST_FWK_FRAME_PREFETCHER_DROP_BUFFER,
                FEATURE_OPTION_ON,
                FEATURE_OPTION_OFF,/**min */
                FEATURE_OPTION_ON/**max */);
        }
        return mSFPDropBuffer == FEATURE_OPTION_ON;
    }

    public static int getTouchDuration() {
        if (mTouchHintDuration <= 0) {
            mTouchHintDuration = getIntFromPropWithCheck(
                FRAME_SBE_SBB_ENABLE_LATER,
                TOUCH_HINT_DURATION_DEFAULT,
                0,/**min */
                100000/**max */);
        }
        return mTouchHintDuration;
    }

    public static int getMoveThreshold() {
        if (mMoveThreshold == FEATURE_OPTION_NON_CHECK) {
            mMoveThreshold = getIntFromPropWithCheck(
                FRAME_SBE_SCROLL_MOVE_BOOST_THRESHOLD,
                -1,
                -1,/**min */
                300/**max */);
        }
        return mMoveThreshold;
    }

    /* min check point is 0.1f */
    public static float getRescueCheckPoint() {
        if (mCheckPoint <= 0) {
            mCheckPoint = getIntFromPropWithCheck(
                FRAME_RESCUE_CHECK_POINT_PROPERTY_NAME,
                FRAME_HINT_RESCUE_CHECK_POINT,/**default */
                10,/**min */
                100/**max */) / 100.00f;
        }
        return mCheckPoint;
    }

    public static float getTraversalRescueCheckPoint() {
        if (mTraversalCheckPoint <= 0) {
            mTraversalCheckPoint = getIntFromPropWithCheck(
                FRAME_RESCUE_TRAVERSAL_CHECK_POINT_PROPERTY_NAME,
                100,/**default */
                10,/**min */
                100/**max */) / 100.00f;
        }
        return mTraversalCheckPoint;
    }

    public static float getTraversalThreshold() {
        if (mTraversalThreshold <= 0) {
            mTraversalThreshold = getIntFromPropWithCheck(
                FRAME_RESCUE_TRAVERSAL_THRESHOLD_PROPERTY_NAME,
                150,/**default */
                10,/**min */
                1000/**max */) / 100.00f;
        }
        return mTraversalThreshold;
    }

    /* horizontal min duration is 0ms, max is 100000ms*/
    public static int getHorizontalScrollDuration() {
        if (mHorizontalScrollDuration <= 0) {
            mHorizontalScrollDuration = getIntFromPropWithCheck(
                FRAME_SBE_HORIZONTAL_SCROLL_DURATION,
                SCROLLING_FING_HORIZONTAL_HINT_DURATION,
                0,/**min */
                100000/**max */);
        }
        return mHorizontalScrollDuration;
    }

    /* vertical min duration is 0ms, max is 100000ms*/
    public static int getVerticalScrollDuration() {
        if (mVerticalScrollDuration <= 0) {
            mVerticalScrollDuration = getIntFromPropWithCheck(
                FRAME_SBE_VERTICAL_SCROLL_DURATION,
                SCROLLING_FING_VERTICAL_HINT_DURATION,
                0,/**min */
                100000/**max */);
        }
        return mVerticalScrollDuration;
    }

    private static int getSrcollFreqFloorStatus() {
        if (mFreqFloorStatus < 0) {
            mFreqFloorStatus = getIntFromPropWithCheck(
                FRAME_SBE_SCROLL_FREQ_FLOOR,
                FEATURE_OPTION_OFF,
                FEATURE_OPTION_OFF,/**min */
                FEATURE_OPTION_ON/**max */);
        }
        return mFreqFloorStatus;
    }

    public static boolean getDisableTouchMovingMode() {
        if (mTouchMovingPolicy < 0) {
            mTouchMovingPolicy = getIntFromPropWithCheck(
                MTK_SBE_SCROLL_TOUCH_MOVING,
                FEATURE_OPTION_ON,
                FEATURE_OPTION_OFF,/**min */
                FEATURE_OPTION_ON/**max */);
        }
        return  mTouchMovingPolicy== FEATURE_OPTION_OFF;
    }

    public static int getFrameDecisionMode() {
        if (mFrameDecisionMode < 0) {
            mFrameDecisionMode = getIntFromPropWithCheck(
                BOOST_FWK_FRAME_DECISION_PROPERTY_NAME,
                FRAME_DECISION_MODE_FOR_SCROLLING,
                FRAME_DECISION_MODE_DISABLE,/**min */
                FRAME_DECISION_MODE_FOR_SCROLLING/**max */);
        }
        return mFrameDecisionMode;
    }

    /* disable touch boost delay, default value is 2 vsync time*/
    public static int getDisableTouchDelay() {
        if (mDisableTouchTime < 0) {
            mDisableTouchTime = getIntFromPropWithCheck(
                MTK_SCROLL_DISBALE_TOUCH_TIME,
                2,
                0,/**min */
                100/**max */);
        }
        return mDisableTouchTime;
    }

    /* enable touch move boost*/
    public static boolean isEnableMoveBoost() {
        return mEnableTouchMoveBoost;
    }

    public static int getFpsgoReleaseTime() {
        return SystemProperties.getInt(CONFIG_FPSGO_RELEASE_TIME, 50);
    }

    public static boolean isMSync3SmoothFlingEnabled() {
        return BOOLEAN_ENABLE_MSYNC_SMOOTHFLING_MSYNC3;
    }

    public static boolean isEnableFramePrefetcher() {
        return BOOLEAN_DISABLE_BOOST_FWK_FRAME_PREFETCHER;
    }

    public static boolean isEnablePreAnimation() {
        return BOOLEAN_BOOST_FWK_PRE_ANIMATION;
    }

    public static boolean isEnableFrameRescue() {
        return BOOLEAN_ENABLE_FRAME_RESCUE;
    }

    public static boolean isEnableScrollCommonPolicy() {
        return BOOLEAN_ENABLE_SCROLL_COMMON_POLICY;
    }

    public static boolean isEnableTouchPolicy() {
        return BOOLEAN_ENABLE_BOOST_FWK_TOUCH;
    }

    public static boolean isDisableFrameDecision() {
        getFrameDecisionMode();
        return FRAME_DECISION_MODE_DISABLE == mFrameDecisionMode;
    }

    public static boolean isFrameDecisionForScrolling() {
        getFrameDecisionMode();
        return FRAME_DECISION_MODE_FOR_SCROLLING == mFrameDecisionMode;
    }

    public static boolean isFrameDecisionForAll() {
        getFrameDecisionMode();
        return FRAME_DECISION_MODE_FOR_ALL == mFrameDecisionMode;
    }

    public static int isRunningRescueFilter() {
        return mRunningRescueFilterCount;
    }

    public static boolean isEnableSBEMbrain() {
        return  BOOLEAN_BOOST_FWK_MBRAIN;
    }

    public static int isInterruptTouchBoost() {
        if (mScrollDisableTouchBoost < FEATURE_OPTION_OFF) {
            mScrollDisableTouchBoost = getIntFromPropWithCheck(
                MTK_SCROLL_INTERRUPT_TOUCHBOOST,
                FEATURE_OPTION_ON,
                FEATURE_OPTION_OFF,/**min */
                FEATURE_OPTION_ON/**max */);
        }
        return mScrollDisableTouchBoost;
    }

    public static boolean isEnableConsistency() {
        return BOOLEAN_ENABLE_CONSISTENCY;
    }

    public static String modeWithConsistency() {
        return ENABLE_CONSISTENCY_MODE;
    }
}
