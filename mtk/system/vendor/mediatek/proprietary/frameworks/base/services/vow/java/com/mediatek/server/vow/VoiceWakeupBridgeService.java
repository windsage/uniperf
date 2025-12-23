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
 * MediaTek Inc. (C) 2010. All rights reserved.
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

/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mediatek.server.vow;

import android.app.ActivityThread;
import android.content.Context;
import android.hardware.soundtrigger.IRecognitionStatusCallback;
import android.hardware.soundtrigger.SoundTrigger.KeyphraseSoundModel;
import android.hardware.soundtrigger.SoundTrigger.ModuleProperties;
import android.hardware.soundtrigger.SoundTrigger.RecognitionConfig;
import android.media.permission.Identity;
import android.media.permission.PermissionUtil;
import android.media.permission.SafeCloseable;
import android.os.Binder;
import android.os.IBinder;
import android.os.Process;
import android.util.Slog;

import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.SoundTriggerInternal;
import com.mediatek.vow.IVoiceWakeupBridge;

import java.util.List;

/**
 * A bridge from voice wakeup API to SoundTriggerInternal class
 * 
 * @hide
 */
public class VoiceWakeupBridgeService extends SystemService {

    private static final String TAG = "VoiceWakeupBridgeService";
    private static final String BINDER_TAG = "vow_bridge";

    private final Context mContext;
    private final VoiceWakeupBridgeStub mServiceStub;
    private SoundTriggerInternal.Session mSoundTriggerInternalSession;
    private IBinder mBinder;

    public VoiceWakeupBridgeService(Context context) {
        super(context);
        mContext = context;
        mServiceStub = new VoiceWakeupBridgeStub();
    }

    @Override
    public void onStart() {
        Slog.d(TAG, "onStart");
        publishBinderService(BINDER_TAG, mServiceStub);
    }

    @Override
    public void onBootPhase(int phase) {
        Slog.d(TAG, "onBootPhase: " + phase);
        if (PHASE_THIRD_PARTY_APPS_CAN_START == phase) {
            Slog.d(TAG, "onBootPhase: mServiceStub:"+mServiceStub);
            try {
                SoundTriggerInternal mSoundTriggerInternal =
                                 LocalServices.getService(SoundTriggerInternal.class);

                mBinder = getBinderService(BINDER_TAG);
                Slog.d(TAG, "onBootPhase: mBinder:"+mBinder);

                Identity identity = new Identity();
                identity.packageName = ActivityThread.currentOpPackageName();

                SafeCloseable ignored = PermissionUtil.establishIdentityDirect(identity);
                Slog.d(TAG, "onBootPhase: SafeCloseable ignored:"+ignored);

               //if (ignored) {
                   List<ModuleProperties> modulePropertiesList = mSoundTriggerInternal
                            .listModuleProperties(identity);
                   if (!modulePropertiesList.isEmpty()) {
                       mSoundTriggerInternalSession = mSoundTriggerInternal.
                               attach(mBinder, modulePropertiesList.get(0), true);
                       Slog.d(TAG, "onBootPhase: mSoundTriggerInternalSession:"
                               + mSoundTriggerInternalSession);
                   }
               //}
            } catch (Throwable e) {
                Slog.d(TAG, "onBootPhase: Error:"+e.getMessage());
                Slog.e(TAG, "onBootPhaseError", e);
                e.printStackTrace();
           }
        }
    }
 /* 
    @Override
     public boolean isUserSupported(TargetUser user) {
         Slog.d(TAG, "isUserSupported(" + user + ")");
         return user.isFull();
    }

    @Override
     public void onUserStarting(TargetUser user) {
         Slog.d(TAG, "onUserStarting(" + user + ")");
    }

    @Override
     public void onUserUnlocking(TargetUser user) {
         Slog.d(TAG, "onUserUnlocking(" + user + ")");
    }

    @Override
    public void onUserSwitching(TargetUser from, TargetUser to) {
         Slog.d(TAG, "onSwitchUser(" + from + " > " + to + ")");
    }
*/
    class VoiceWakeupBridgeStub extends IVoiceWakeupBridge.Stub {
        @Override
        public int startRecognition(int keyphraseId,
                KeyphraseSoundModel soundModel,
                IRecognitionStatusCallback listener,
                RecognitionConfig recognitionConfig) {
            enforceCallingPermission();
            Slog.d(TAG, "startRecognition");
            if (mSoundTriggerInternalSession != null){
                Slog.w(TAG, "startRecognition mSoundTriggerInternalSession");
                return mSoundTriggerInternalSession.startRecognition(keyphraseId,
                        soundModel, listener, recognitionConfig,false);
            } else {
                Slog.w(TAG, "startRecognition mSoundTriggerInternalSession null obj");
                return 0;
            }
        }

        @Override
        public int stopRecognition(int keyphraseId,
                IRecognitionStatusCallback listener) {
            enforceCallingPermission();
            Slog.d(TAG, "stopRecognition");
            if (mSoundTriggerInternalSession != null){
                Slog.w(TAG, "stopRecognition mSoundTriggerInternalSession");
                return mSoundTriggerInternalSession.stopRecognition(keyphraseId, listener);
            } else {
                Slog.w(TAG, "stopRecognition mSoundTriggerInternalSession null obj");
                return 0;
            }
        }

        @Override
        public int unloadKeyphraseModel(int keyphaseId) {
            enforceCallingPermission();
            Slog.d(TAG, "unloadKeyphraseModel");
            if (mSoundTriggerInternalSession != null){
                Slog.w(TAG, "unloadKeyphraseModel mSoundTriggerInternalSession");
                return mSoundTriggerInternalSession.unloadKeyphraseModel(keyphaseId);
            } else {
                Slog.w(TAG, "unloadKeyphraseModel mSoundTriggerInternalSession null obj");
                return 0;
            }
        }
    }

    public void enforceCallingPermission() {
        if (Binder.getCallingPid() == Process.myPid()) {
            return;
        }
        mContext.enforcePermission(android.Manifest.permission.MANAGE_SOUND_TRIGGER,
                Binder.getCallingPid(), Binder.getCallingUid(), null);
    }

}
