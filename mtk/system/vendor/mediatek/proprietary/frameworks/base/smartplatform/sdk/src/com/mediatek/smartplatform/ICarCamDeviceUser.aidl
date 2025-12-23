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
import com.mediatek.smartplatform.ICameraDeviceListener;
import com.mediatek.smartplatform.ISpmRecordListener;
import com.mediatek.smartplatform.SimpleCommand;
import android.view.Surface;
import android.hardware.camera2.impl.CameraMetadataNative;

/** @hide */
interface ICarCamDeviceUser {
    int addListener(ICameraDeviceListener listener);
    int removeListener(ICameraDeviceListener listener);
    int addSpmRecordListener(ISpmRecordListener listener, int usage);
    int removeSpmRecordListener(ISpmRecordListener listener, int usage);
    int startADAS();
    int stopADAS();
    Surface getADASSurface();

    int setPreviewSurface(in @nullable Surface surface);
    int setSurfaceDisplay(in @nullable Surface surface, int usage);
    int startPreview();
    int stopPreview();
    int startRecord(String config, int usage);
    int stopRecord(String config, int usage);
    void takePicture(String path);
    int capture(in List<String> path, int w, int h, int source, int quality);
    int open();
    int release();
    String getParameters();
    void setParameters(String params);
    int getState(int stateType);
    void setState(int stateType, int state);
    oneway void notifyEvent(int eventId, int arg1, int arg2, in String params);
    oneway void notifyRecordEvent(int recorder, int eventId, int arg1, int arg2, in String params);

    void sendSimpleCommand(inout SimpleCommand command);
    int setRequestConfig(in @nullable CameraMetadataNative metadata, int usage);
    int submitRequest(boolean streaming);
    int clearRequest();
    CameraMetadataNative createDefaultRequest(int templateId);
    int queryAeBlock(in @nullable CameraMetadataNative metadata);
    void updateWatermark(boolean enable, String watermark);
}
