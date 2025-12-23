/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.mediatek.smartplatform;

import com.mediatek.smartplatform.ISmartPlatformEventListener;
import com.mediatek.smartplatform.SimpleCommand;
import com.mediatek.smartplatform.ICarCamDeviceUser;
import android.view.Surface;
import android.hardware.camera2.impl.CameraMetadataNative;
import android.hardware.ICameraServiceListener;
import android.hardware.CameraInfo;
import android.hardware.CameraStatus;

/** @hide */
interface ISmartPlatformService {
    int addListener(ISmartPlatformEventListener listener);

    int removeListener(ISmartPlatformEventListener listener);

    /**
     * Notify the smartplatform service of a system event.  Should only be called from xxx.
     *
     * Callers require the android.permission.SMARTPLATFORM_SEND_SYSTEM_EVENTS permission.
     */
    void notifyListener(int event, int arg1, int arg2);

    int sendCommand(int cmd, int arg1, int arg2);

    void sendCollisionCommand(inout SimpleCommand command);

    void sendCarCommand(inout SimpleCommand command);

    int postIpodCommand(String cmd,String params);

    ICarCamDeviceUser connectDevice(String cameraId);

    ICarCamDeviceUser connectAvmDevice(String cameraId);

    int disconnectDevice(String cameraId);

    int disconnectAvmDevice();

    int setDeviceType(int deviceType);

    int setJpegHwEncState(int state);
    int getJpegHwEncState();
    List<String> getAvmCameraInfo();
    String getAvmCameraId();

    CameraMetadataNative getCameraCharacteristics(String cameraId);

    /**
     * Add listener for changes to camera device and flashlight state.
     *
     * Also returns the set of currently-known camera IDs and state of each device.
     * Adding a listener will trigger the torch status listener to fire for all
     * devices that have a flash unit.
     */
    CameraStatus[] addCameraListener(ICameraServiceListener listener);

    /**
     * Remove listener for changes to camera device and flashlight state.
     */
    void removeCameraListener(ICameraServiceListener listener);
}
