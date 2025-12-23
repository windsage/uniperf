/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.mediatek.mbrainlocalservice;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.RemoteException;
import android.os.Trace;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;

import vendor.mediatek.hardware.mbrain.IMBrain;
import vendor.mediatek.hardware.mbrain.IMBrainCallbacks;
import vendor.mediatek.hardware.mbrain.MBrain_CallbackType;
import vendor.mediatek.hardware.mbrain.MBrain_Parcelable;
import vendor.mediatek.hardware.mbrain.MBrain_CallbackDataType;
/**
 * On service registration, {@link #onRegistration} is called, which registers {@code this}, an
 * {@link IHealthInfoCallback}, to the health service.
 *
 * <p>When the health service has updates to health info via {@link IHealthInfoCallback}, {@link
 * HealthInfoCallback#update} is called.
 *
 * @hide
 */
// It is made public so Mockito can access this class. It should have been package private if not
// for testing.
@VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
public class MBrainRegCallbackAidl {
    private static final String TAG = "MBrainRegCallbackAidl";
    private final MBrainInfoCallback mServiceInfoCallback;
    private final IMBrainCallbacks mHalInfoCallback = new HalInfoCallback();

    MBrainRegCallbackAidl(@Nullable MBrainInfoCallback mbrainInfoCallback) {
        mServiceInfoCallback = mbrainInfoCallback;
    }

    /**
     * Called when the service manager sees {@code newService} replacing {@code oldService}.
     * This unregisters the health info callback from the old service (ignoring errors), then
     * registers the health info callback to the new service.
     *
     * @param oldService the old IHealth service
     * @param newService the new IHealth service
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void onRegistration(@Nullable IMBrain oldService, @NonNull IMBrain newService) {
        if (mServiceInfoCallback == null) return;

        Trace.traceBegin(Trace.TRACE_TAG_SYSTEM_SERVER, "MBrainUnregisterCallbackAidl");
        try {
            unregisterCallback(oldService, mHalInfoCallback);
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_SYSTEM_SERVER);
        }

        Trace.traceBegin(Trace.TRACE_TAG_SYSTEM_SERVER, "MBrainRegisterCallbackAidl");
        try {
            registerCallback(newService, mHalInfoCallback);
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_SYSTEM_SERVER);
        }
    }

    private static void unregisterCallback(@Nullable IMBrain oldService, IMBrainCallbacks cb) {
        if (oldService == null) return;
        try {
            oldService.UnRegisterCallback(MBrain_CallbackType.MB_TYPE_MBRAIN_JAVASERVICE, cb);
        } catch (RemoteException e) {
            // Ignore errors. The service might have died.
            Slog.w(
                    TAG,
                    "MBrain: cannot unregister previous callback (transaction error): "
                            + e.getMessage());
        }
    }

    private static void registerCallback(@NonNull IMBrain newService, IMBrainCallbacks cb) {
        try {
            newService.RegisterCallback(MBrain_CallbackType.MB_TYPE_MBRAIN_JAVASERVICE, cb);
        } catch (RemoteException e) {
            Slog.e(
                    TAG,
                    "MBrain: cannot register callback, framework may cease to"
                            + " receive updates on mbrain info!",
                    e);
            return;
        }
    }

    private class HalInfoCallback extends IMBrainCallbacks.Stub {
        @Override
        public boolean notifyToClient(String callbackData) throws RemoteException {
            return mServiceInfoCallback.notifyToClient(callbackData);
        }
        @Override
        public boolean getMessgeFromClient(MBrain_Parcelable inputData) throws RemoteException {
            return mServiceInfoCallback.getMessgeFromClient(inputData);
        }
        @Override
        public boolean getDataFromClient(MBrain_CallbackDataType cbData) throws RemoteException {
            return true;
        }
        @Override
        public String getInterfaceHash() {
            return IMBrainCallbacks.HASH;
        }
        @Override
        public int getInterfaceVersion() {
            return IMBrainCallbacks.VERSION;
        }
    }
}
