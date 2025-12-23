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

package com.mediatek.wmsmsync;

import java.util.ArrayList;
import java.util.List;

/**
 * MSync3.0 MSyncCtrlBean
 */
public class MSyncCtrlBean {
    private String mPackageName;
    private List<ActivityBean> mActivityBeans;
    private boolean mSlideResponse;
    private float mFps;
    private float mImeFps;
    private float mVoiceFps;

    public String getPackageName() {
        return mPackageName;
    }

    public void setPackageName(String packageName) {
        this.mPackageName = packageName;
    }

    public List<ActivityBean> getActivityBeans() {
        if (null == mActivityBeans) {
            mActivityBeans = new ArrayList<>();
        }
        return mActivityBeans;
    }

    public void setActivityBeans(List<ActivityBean> activityBeans) {
        this.mActivityBeans = activityBeans;
    }

    public float getImeFps() {
        return mImeFps;
    }

    public void setImeFps(float imeFps) {
        this.mImeFps = imeFps;
    }

    public boolean isSlideResponse() {
        return mSlideResponse;
    }

    public void setSlideResponse(boolean slideResponse) {
        this.mSlideResponse = slideResponse;
    }

    public float getFps() {
        return mFps;
    }

    public void setFps(float defaultFps) {
        this.mFps = defaultFps;
    }

    public float getVoiceFps() {
        return mVoiceFps;
    }

    public void setVoiceFps(float voiceFps) {
        this.mVoiceFps = voiceFps;
    }

    public static class ActivityBean {
        @Override
        public String toString() {
            return "ActivityBean{" +
                    "name='" + mName + '\'' +
                    ", fps=" + mFps +
                    ", imeFps=" + mImeFps +
                    ", voiceFps=" + mVoiceFps +
                    '}';
        }

        private String mName;
        private float mFps;
        private float mImeFps;
        private float mVoiceFps;

        public float getImeFps() {
            return mImeFps;
        }

        public void setImeFps(float imeFps) {
            this.mImeFps = imeFps;
        }

        public String getName() {
            return mName;
        }

        public void setName(String name) {
            this.mName = name;
        }

        public float getFps() {
            return mFps;
        }

        public void setFps(float fps) {
            this.mFps = fps;
        }

        public float getVoiceFps() {
            return mVoiceFps;
        }

        public void setVoiceFps(float voiceFps) {
            this.mVoiceFps = voiceFps;
        }
    }

    @Override
    public String toString() {
        return "MSyncCtrlTableBean{" +
                "packageName='" + mPackageName + '\'' +
                ", activityBeans=" + mActivityBeans +
                ", slideResponse=" + mSlideResponse +
                ", defaultFps=" + mFps +
                ", imeFps=" + mImeFps +
                ", voiceFps=" + mVoiceFps +
                '}';
    }
}
