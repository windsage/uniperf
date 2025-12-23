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
import com.mediatek.smartplatform.ADASInfo;
import android.hardware.camera2.impl.CameraMetadataNative;
import android.hardware.camera2.impl.CaptureResultExtras;
import android.hardware.camera2.impl.PhysicalCaptureResultInfo;

/** @hide */
interface ICameraDeviceListener {
    void onStatusChanged(int status, int arg1, String arg2, int arg3);
    void onADASCallback(in ADASInfo info);
    void onVideoFrame(int fd, int dataType,int size);
    void onAudioFrame(int fd, int dataType,int size);
    void onMiscDataCallback(int fd, int datatype, int size);
    oneway void onDeviceError(int errorCode, in CaptureResultExtras resultExtras);
    oneway void onResultReceived(in CameraMetadataNative result,
                                 in CaptureResultExtras resultExtras,
                                 in PhysicalCaptureResultInfo[] physicalCaptureResultInfos);
}
