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
package com.mediatek.view.impl;

import android.content.ContentResolver;
import android.content.Context;
import android.hardware.display.DisplayManagerGlobal;
import android.os.Handler;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.MotionEvent;

import com.mediatek.view.TouchSlopManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * View Touch Slop.
 */
public class TouchSlopManagerImpl extends TouchSlopManager {
    private final static boolean TOUCH_SLOP_LOG = SystemProperties.getBoolean(
            "debug.touchslop.log", false);
    private final static int TOUCH_SLOP_COORDINATE_ERROR_COUNT = SystemProperties.getInt(
            "debug.touchslop.coordinate_error_count", 3);
    private final static int TOUCH_TIME = SystemProperties.getInt(
            "debug.touchslop.touch_time", 400);
    private static final float TOUCH_SLOP_MIN = 3;
    private static final String TOUCHSLOP_LOG_TAG = "TouchSlop";
    private static final String TOUCHSLOP_SERVER_LOG_TAG = "TouchSlopServer";
    private static final String TOUCH_SLOP_LEVEL = "touch_slop_level";
    private static final String TOUCH_SLOP_DATA = "touch_slop_data";
    private int mDefaultTouchSlopPx;
    private int mCurrentTouchSlopDp;
    private float mCurrentTouchSlopPx = 0;
    private float mActualTouchSlopPx = 0;
    private boolean mBeginCollect = false;
    private boolean mNeedSend = false;
    private boolean mNeedChangeCoordinate = false;
    private int mCoordinateErrorCount = 0;
    private float mBeginx = 0;
    private float mBeginy = 0;
    private float mMaxDx = 0;
    private float mMaxDy = 0;
    private float mDensity;
    private int[] mTouchSlopData;
    private long mDownTime;

    /**
     * init.
     */
    public TouchSlopManagerImpl() {
        mTouchSlopServer = new TouchSlopServerImpl();
    }

    /**
     * localGetProp.
     *@return pixel
     *@param context Context
     *@param defaultTouchSlop pixel
     */
    public int localGetProp(Context context, int defaultTouchSlop) {
        int touchSlop = DisplayManagerGlobal.getInstance().getProp();
        mDefaultTouchSlopPx = defaultTouchSlop;
        mDensity = context.getResources().getDisplayMetrics().density;
        if (mTouchSlopData == null) {
            mTouchSlopData = new int[(int) Math.ceil(mDefaultTouchSlopPx / mDensity) + 1];
        }
        if (touchSlop == -1) {
            mCurrentTouchSlopPx = defaultTouchSlop;
            mCurrentTouchSlopDp = (int) Math.ceil((float) defaultTouchSlop / mDensity);
            mActualTouchSlopPx = mCurrentTouchSlopPx;
            return defaultTouchSlop;
        }
        mCurrentTouchSlopPx = (float) Math.ceil(touchSlop * mDensity);
        mCurrentTouchSlopDp = touchSlop;
        mActualTouchSlopPx = mCurrentTouchSlopPx;
        Log.d(TOUCHSLOP_LOG_TAG, "localGetProp mCurrentTouchSlopPx = " + mCurrentTouchSlopPx +
                "defaultTouchSlop = " + defaultTouchSlop);
        return (int) mCurrentTouchSlopPx;
    }

    /**
     * sendData.
     */
    public void sendData() {
        if (!mNeedSend) {
            if (TOUCH_SLOP_LOG) {
                Log.d(TOUCHSLOP_LOG_TAG, "sendData fail mTouchSlopData.isEmpty");
            }
            return;
        }
        if (TOUCH_SLOP_LOG) {
            Log.d(TOUCHSLOP_LOG_TAG, "sendData");
        }
        DisplayManagerGlobal.getInstance().sendData(mTouchSlopData);
        mTouchSlopData = new int[(int) Math.ceil(mDefaultTouchSlopPx / mDensity) + 1];
        mNeedSend = false;
    }

    /**
     * collectData.
     * @param ev MotionEvent
     */
    public void collectData(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mDownTime = System.currentTimeMillis();
                mMaxDx = 0;
                mMaxDy = 0;
                mBeginx = ev.getX(0);
                mBeginy = ev.getY(0);
                mBeginCollect = true;
                break;
            case MotionEvent.ACTION_UP:
                long upTime = System.currentTimeMillis();
                if (mBeginCollect && upTime - mDownTime < TOUCH_TIME) {
                    int touchSlopDp = (int) Math.ceil(Math.max(mMaxDx, mMaxDy) / mDensity);
                    mTouchSlopData[touchSlopDp]++;
                    mBeginCollect = false;
                    mNeedSend = true;
                    if (TOUCH_SLOP_LOG) {
                        Log.d(TOUCHSLOP_LOG_TAG, "collectData touchSlopDp = " + touchSlopDp +
                                "mDefaultTouchSlopPx = " + mDefaultTouchSlopPx + "count = " +
                                 mTouchSlopData[touchSlopDp]);
                    }
                    if (mCurrentTouchSlopDp < touchSlopDp) {
                        mCurrentTouchSlopDp = touchSlopDp;
                        mCurrentTouchSlopPx = (float) Math.ceil(touchSlopDp * mDensity);
                        if (mCoordinateErrorCount == TOUCH_SLOP_COORDINATE_ERROR_COUNT) {
                            mNeedChangeCoordinate = true;
                        } else {
                            mCoordinateErrorCount++;
                        }
                    }
                }
                break;
            case MotionEvent.ACTION_MOVE:
                ev.setX(0);
                ev.setY(0);
                if (mBeginCollect) {
                    float dx = Math.abs(mBeginx - ev.getX(0));
                    float dy = Math.abs(mBeginy - ev.getY(0));
                    if (dx > mMaxDx) {
                        mMaxDx = dx;
                    }
                    if (dy > mMaxDy) {
                        mMaxDy = dy;
                    }
                    if (mMaxDx >= mDefaultTouchSlopPx || mMaxDy >= mDefaultTouchSlopPx) {
                        mBeginCollect = false;
                        break;
                    }
                    if (mNeedChangeCoordinate) {
                        if (dx > mActualTouchSlopPx && dx < mCurrentTouchSlopPx) {
                            if (mBeginx > ev.getX(0)) {
                                ev.setX(dx - mActualTouchSlopPx);
                            } else {
                                ev.setX(-(dx - mActualTouchSlopPx));
                            }
                        }
                        if (dy > mActualTouchSlopPx && dy < mCurrentTouchSlopPx) {
                            if (mBeginy > ev.getY(0)) {
                                ev.setY(dy - mActualTouchSlopPx);
                            } else {
                                ev.setY(-(dy - mActualTouchSlopPx));
                            }
                        }
                    }
                }
                break;
          default:
             break;
        }
    }

    /**
     * sparseIntArrayToString.
     */
    private  String sparseIntArrayToString(SparseIntArray sparseIntArray) {
        JSONArray jsonArray = new JSONArray();
        for (int i = 0; i < sparseIntArray.size(); i++) {
            int key = sparseIntArray.keyAt(i);
            int value = sparseIntArray.valueAt(i);
            JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.put("key", key);
                jsonObject.put("value", value);
                jsonArray.put(jsonObject);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return jsonArray.toString();
    }

    /**
     * stringToSparseIntArray.
     */
    private  SparseIntArray stringToSparseIntArray(String jsonString) {
        SparseIntArray sparseIntArray = new SparseIntArray();
        if (jsonString == null) {
            return sparseIntArray;
        }
        try {
            JSONArray jsonArray = new JSONArray(jsonString);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                int key = jsonObject.getInt("key");
                int value = jsonObject.getInt("value");
                sparseIntArray.put(key, value);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return sparseIntArray;
    }

    /**
     * abandonEvent.
     */
    public void abandonEvent () {
        mBeginCollect = false;
    }
    /**
     * TouchSlopServerImpl.
     */
    public class TouchSlopServerImpl extends TouchSlopServer {
        private static final int MAX_COUNT = SystemProperties.getInt(
            "debug.touchslop.maxcount", 10000);
        private static final int START_COUNT = SystemProperties.getInt(
            "debug.touchslop.startcount", 30);
        private static final int MAX_SEND = 100;
        private static final float CORRECT_RATE = 0.996f;
        private float mDefaultData = -2;
        private int mLocalTotal = 0;
        private int mTotalCount = -1;
        private SparseIntArray mCountArray = null;

        /**
         * getProp.
         *@return Default pixel
         *@param context Context
         */
        public int getProp(Context context) {
            if (mDefaultData == -2) {
                mDefaultData = Settings.Global.getInt(context.getContentResolver(),
                        TOUCH_SLOP_LEVEL, -1);
            }
            return (int) mDefaultData;
        }

        /**
         * setProp.
         *@param context Context
         *@param data app data
         *@param handler DMS handler
         */
        public void setProp(Context context, int[] data, Handler handler) {
            if (TOUCH_SLOP_LOG) {
                Log.d(TOUCHSLOP_SERVER_LOG_TAG, "setProp");
            }
            int totalCount = 0;
            int indexCount = 0;
            float max = -1;
            float writeData;
            // init arraylist
            if (mCountArray == null) {
                mCountArray = getData(context.getContentResolver());
            }
            // add data
            if (mTotalCount >= MAX_COUNT) {
                for (int i = 0; i < mCountArray.size(); i++) {
                    int key = mCountArray.keyAt(i);
                    int value = mCountArray.valueAt(i);
                    mCountArray.put(key, value / 2);
                }
            }
            // output result
            for (int i = 0; i < data.length; i++) {
                int newValue = mCountArray.get(i, 0) + data[i];
                mCountArray.put(i, newValue);
                mLocalTotal += data[i];
                totalCount += newValue;
                if (TOUCH_SLOP_LOG) {
                    Log.d(TOUCHSLOP_SERVER_LOG_TAG, "Number: " + i + ", Count: " + newValue);
                }
            }
            mTotalCount = totalCount;
            for (int i = 0; i < mCountArray.size(); i++) {
                if (mTotalCount != 0 && (float) indexCount / mTotalCount < CORRECT_RATE) {
                    max = i;
                }
                indexCount += mCountArray.get(i, 0);
            }
            writeData = caculateData(max);
            if (mDefaultData != writeData && mTotalCount > START_COUNT) {
                Log.d(TOUCHSLOP_SERVER_LOG_TAG, "setProp  writeData prop = " +
                        writeData + "mDensity=" + mDensity);
                Settings.Global.putInt(context.getContentResolver(), TOUCH_SLOP_LEVEL,
                        (int) writeData);
                setData(context.getContentResolver());
                mLocalTotal = 0;
                mDefaultData = writeData;
            } else if (mLocalTotal >= MAX_SEND) {  // sendmessage setdata
                setData(context.getContentResolver());
                mLocalTotal = 0;
            }
        }

        /**
         * caculateData.
         *@param max max data
         */
        private float caculateData(float max) {
            if (max < TOUCH_SLOP_MIN) {
                max = TOUCH_SLOP_MIN;
            } else if (max > mDefaultTouchSlopPx / mDensity) {
                max = mDefaultTouchSlopPx / mDensity;
            }
            return max;
        }

        /**
         * getData.
         *@param contentResolver ContentResolver
         */
        private SparseIntArray getData(ContentResolver contentResolver) {
            String data = Settings.Global.getString(contentResolver, TOUCH_SLOP_DATA);
            Log.d(TOUCHSLOP_SERVER_LOG_TAG, "getdata data = " + data);
            return stringToSparseIntArray(data);
        }

        /**
         * setData.
         *@param contentResolver ContentResolver
         */
        private void setData(ContentResolver contentResolver) {
            Settings.Global.putString(contentResolver, TOUCH_SLOP_DATA,
                    sparseIntArrayToString(mCountArray));
            Log.d(TOUCHSLOP_SERVER_LOG_TAG, "setdata mCountArray = "
                    + sparseIntArrayToString(mCountArray));
        }
    }
}
