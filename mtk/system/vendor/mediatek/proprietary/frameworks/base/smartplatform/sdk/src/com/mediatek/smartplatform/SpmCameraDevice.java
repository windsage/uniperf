package com.mediatek.smartplatform;


import java.lang.RuntimeException;
import java.lang.IllegalStateException;
import java.nio.ByteBuffer;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;

import android.annotation.NonNull;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.media.CamcorderProfile;
import android.media.MediaCodec;
import android.media.MediaRecorder;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageWriter;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.ServiceSpecificException;
import android.os.SystemProperties;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.mediatek.smartplatform.ICarCamDeviceUser;


/**
 * This class is used to manager the cameras in car. The design is reference
 * with both {@link android.hardware.camera2} and
 * {@link android.media.MediaRecorder}. It combines car customized preview,
 * record and other camera features, and more easy to use.
 *
 * @see android.hardware.camera2
 * @see android.media.MediaRecorder
 */
public abstract class SpmCameraDevice {
    private String TAG = "SpmCameraDevice";
    public static final boolean DEBUG = Build.IS_ENG || SystemProperties.getBoolean("persist.vendor.log.spmsdk", false);



    private static final boolean mUseDefaultMR = SystemProperties.getBoolean("persist.mtk.spm.d_dmr", false);

    private static final boolean ALLOW_SPLIT = SystemProperties.getBoolean("persist.mtk.cam_split", true);


    static final int EVENT_MSG_RELEASE_PICTURETAKEN = 0x001;
    static final int EVENT_MSG_RESULT_METADATA = 0x002;
    static final int EVENT_MSG_SOUND_INIT = 0x003;
    static final int EVENT_MSG_SOUND_RELEASE = 0x004;
    static final int EVENT_MSG_TAKE_PICTURE_TIMEOUT = 0x005;
    static final int EVENT_MSG_TAKE_PICTURE_OK= 0x006;

    long mTakePictureTimeoutMs = 3000; //3s

    // ref  CameraSurfaceUsage
    public static final int STATE_FAIL            = -1;
    public static final int STATE_IDLE            = 0;
    public static final int STATE_PREVIEWS_0      = 0x1;
    public static final int STATE_PREVIEWS_1      = 0x2;
    public static final int STATE_PREVIEWS_2      = 0x4;
    public static final int STATE_PREVIEWS_3      = 0x8;
    public static final int STATE_PREVIEWS_4      = 0x10;
    public static final int STATE_RECORD_0        = 0x20;
    public static final int STATE_RECORD_1        = 0x40;
    public static final int STATE_RECORD_2        = 0x80;
    public static final int STATE_RECORD_3        = 0x100;
    public static final int STATE_RECORD_4        = 0x200;
    public static final int STATE_EXTERNAL_0      = 0x400;
    public static final int STATE_EXTERNAL_1      = 0x800;
    public static final int STATE_EXTERNAL_2      = 0x1000;
    public static final int STATE_EXTERNAL_3      = 0x2000;
    public static final int STATE_EXTERNAL_4      = 0x4000;
    public static final int STATE_PICTURE_0       = 0x8000;
    public static final int STATE_ADAS_0          = 0x10000;

    /**
        * Interface definition for a callback to be invoked when a recording video
        * completed.
        */
    public interface VideoCallback {
        public static final int VIDEO_EVENT_ADD_FILE_IN_GALLERY = 0;
        public static final int VIDEO_EVENT_DELETE_FILE_IN_GALLERY = 1;
        public static final int VIDEO_EVENT_SDCARD_FULL = 2;
        public static final int VIDEO_EVENT_RECORD_STATUS_START = 3;
        public static final int VIDEO_EVENT_RECORD_STATUS_STOP = 4;
        public static final int VIDEO_EVENT_RECORD_RECORDING_ERROR = 5;
        public static final int VIDEO_EVENT_RECORD_SDCARD_DAMAGED = 6;
        public static final int VIDEO_EVENT_LOWRES_KEYPOINT_START = 7;
        public static final int VIDEO_EVENT_LOWRES_KEYPOINT_STOP  = 8;
        public static final int VIDEO_EVENT_KEYPOINT_START  = 9;
        public static final int VIDEO_EVENT_KEYPOINT_STOP  = 10;
        /**
                * Notify the callback that the recording video is completed.
                *
                * @param VideoInfo
                *  the file name of the recording video
                */
        void onVideoTaken(VideoInfoMap videoInfoMap);

        /**
                * Notify the video frame
                *
                * @param data
                *  video frame data
                * @param size
                *  the size of video frame data
                */
        void onVideoFrame(byte[] data, int dataType, int size, String cameraId, int recoderSource);
    }

    /**
     * Interface definition for a callback to be invoked when audio data decode
     * completed.
     */
    public interface AudioCallback {
        /**
         * Notify the audio frame
        *
        * @param data
        *		   audio frame data
        * @param size
        *		   the size of audio frame data
        */
        void onAudioFrame(byte[] data, int dataType, int size, String cameraId, int recoderSource);

    }

    public interface KeypointCallback {
        /**
         * Notify the keypoint data
        * @param data
        * 		   keypoint data,include video and audio
        * @param size
        * 		   the size of keypoint data
        */
        void onKeypointFrame(byte[] data, int dataType, int size, String cameraId, int recoderSource);

    }


    public interface YUVCallback {
        /**
         * Notify the YUV data
        * @param data
        * 		   YUV data,
        * @param size
        * 		   the size of YUV data
        */
        void onYuvCbFrame(byte[] data, int dataType, int size, String cameraId, int recoderSource);

    }

    /**
     * Interface definition for a record status,it will notify  onRecordStatusChanged if
     * record status had changed.
     */
    public interface RecordStatusCallback {
        public static final int RECORD_STATUS_START		  = 0;
        public static final int RECORD_STATUS_STOP		  = 1;
        public static final int RECORD_STATUS_RECODING	  = 2;

        void onRecordStatusChanged(int status, String cameraId, int recorderSource);
    }

    public interface ErrorCallback{
        //ICameraDeviceCallbacks  Error codes  start
        public static final int ERROR_CAMERA_INVALID_ERROR = -1; // To indicate all invalid error codes
        public static final int ERROR_CAMERA_DISCONNECTED = 0;
        public static final int ERROR_CAMERA_DEVICE = 1;
        public static final int ERROR_CAMERA_SERVICE = 2;
        public static final int ERROR_CAMERA_REQUEST = 3;
        public static final int ERROR_CAMERA_RESULT = 4;
        public static final int ERROR_CAMERA_BUFFER = 5;
        public static final int ERROR_CAMERA_DISABLED = 6;
        //ICameraDeviceCallbacks  Error codes  end

        // other Error codes
        public static final int ERROR_CAMERA_UNAVAILABLE =  12;
        public static final int ERROR_CAMERA_HW_EXCEPTION =  13;

       /**
        * Notify to app when use cameradevice error occured 
        *
        * @param error
        *          the type of error
        * @param cameraId
        *          the id of camera
        * @param camera
        *          the instance of CameraDevice
        */
        void onError(int error, String cameraId, SpmCameraDevice camera);
    }

    /**
     * Interface definition for a callback to be invoked to get the AeLv after queryAeLv() API is invoked 
     */
    public interface AeStatusCallback {
        void onStatusCallback(int value);
        void onStatusCallbackArray(int[] value, int size);
    }


    /**
     * Callback interface used to signal the moment of actual image capture.
     *
     */
    public interface ShutterCallback {
        /**
         * Called as near as possible to the moment when a photo is captured
         * from the sensor. This is a good opportunity to play a shutter sound
         * or give other feedback of camera operation. This may be some time
         * after the photo was triggered, but some time before the actual data
         * is available.
         */
        void onShutter();
    }


    /**
     * Callback interface used to supply JPEG pathname.
     *
     */
    public interface CamPictureCallback {

        public static final int PICTURE_TAKEN_FAIL = -1;
        public static final int PICTURE_TAKEN_SAVING =  0;
        public static final int PICTURE_TAKEN_SUCCESS = 1;
        /**
         * Called when image data is available after a picture is taken.
         *
         * @param status
         *             PICTURE_TAKEN_FAIL,PICTURE_TAKEN_SAVING or PICTURE_TAKEN_SUCCESS
         * @param cameraId
         *            the id of camera
         * @param path
         *            the taken JPEG pathname
         */
        void onPictureTaken(int status, String cameraId, String path);
    };

    /**
     * Interface definition for a callback to be invoked when camera post ADAS
     * message.
     */
    public interface ADASCallback {
        /**
         * Notify the listener of the detected ADAS info in the preview frame.
         *
         * @param info
         *            the detected adas info
         */
        void onADASCallback(ADASInfo info);
    }

    public interface ImageDataCallback {
        public static final int IMAGE_FORMAT_YUV_420_888 = ImageFormat.YUV_420_888;
        public static final int IMAGE_FORMAT_JPEG = ImageFormat.JPEG;
        public static final int IMAGE_FORMAT_NV21 = ImageFormat.NV21;
        public static final int IMAGE_FORMAT_YV12 = ImageFormat.YV12;
        public static final int IMAGE_FORMAT_RGB_888 = PixelFormat.RGB_888;
        public static final int IMAGE_FORMAT_YUY2 = ImageFormat.YUY2;

        public static final int IMAGE_DATA_RAW    = 0;
        public static final int IMAGE_DATA_FILE   = 1;
        public static final int IMAGE_DATA_BUFFER = 2;
        public static final int IMAGE_DATA_IMAGE  = 3;

        public static final int IMAGE_STATUS_SUCCEEDED = 0;
        public static final int IMAGE_STATUS_FAILED = -1;

        void onImageAvailable(ImageDataCallbackInfo info);
    };

    public abstract boolean release();
    public abstract String getCameraId();

    public abstract boolean setPreviewSurface(Surface surface, @PreviewSource.Format int previewSource);
    public abstract int startPreview();

    public abstract int stopPreview();

    public abstract int startRecord(@RecordSource.Format int recordSource, @NonNull RecordConfiguration recordConfig);

    public abstract int stopRecord(@RecordSource.Format int recordSource) ;

    public abstract int submitRequest(boolean streaming);
    public abstract void updateRequest();

    public abstract int clearRequest();
    public abstract CaptureRequest.Builder createCaptureRequest(int templateType);

    public SpmCameraDevice() {
    }

    public abstract void lockRecordingVideo(int duration, String protectedType, @RecordSource.Format int recordSource);
    public abstract void unlockRecordingVideo(@NonNull String filename, @RecordSource.Format int recordSource);
    public abstract void setVideoRotateDuration(int duration_ms, @RecordSource.Format int recordSource );
    public abstract void setVideoBitrateDyn(int bitrate, int bAdjust, @RecordSource.Format int recordSource);
    public abstract void setRecordingMuteAudio(boolean isMuteAudio, @RecordSource.Format int recordSource);
    public abstract void setRecordMicChanged(boolean isMicChanged, @RecordSource.Format int recordSource);
    public abstract void setProtectRecording(boolean ismotiondetect, boolean isrecordingstatus,int duration_ms, @RecordSource.Format int recordSource);
    public abstract void setRecordingSdcardPath(@NonNull String path, @RecordSource.Format int recordSource);
    public abstract void flushCurRecFile(@RecordSource.Format int recordSource);
    public abstract void setLockFilePath(@NonNull String path, @RecordSource.Format int recordSource);
    public abstract void enableEncryption(boolean enable, @RecordSource.Format int recordSource);
    public abstract int startPictureSequence(@PictureSequenceSource.Format int source, @NonNull PictureConfiguration config);
    public abstract int stopPictureSequence(@PictureSequenceSource.Format int source);
    public abstract FileDescriptor enableShareBuffer(@PictureSequenceSource.Format int source, boolean enable);
    public abstract FileDescriptor enableShareBuffer(@PictureSequenceSource.Format int source, boolean enableSharebuffer, boolean enableCallback);
    public abstract int takePicture(String fileName, ShutterCallback shutter, CamPictureCallback jpeg);
    public abstract int takePicture(String fileName, ShutterCallback shutter, CamPictureCallback jpeg, android.util.Size pictureSize);
    @PictureSequenceSource.TakePictureResult
    public abstract int takePicture(List<String> filePath, android.util.Size pictureSize, @PictureSequenceSource.Format int source, int quality);
    public abstract int setPictureSize(android.util.Size pictureSize);
    public abstract int setPictureSurface(android.util.Size pictureSize);
    public abstract int clearPictureSurface();
    public abstract SpmCamParameters getParameters();
    public abstract void setParameters(SpmCamParameters params);

    public abstract CameraCharacteristics getCameraCharacteristics();
    public abstract void setVideoFrameMode(@RecordConfiguration.VideoFrameMode int mode, @RecordSource.Format int recordSource);
    /**
     * Starts ADAS (Advanced driver assistance systems)
     */
    public abstract int startADAS();
    /**
     * Stops ADAS
     */
    public abstract int stopADAS();

    public abstract void setADASCallback(ADASCallback callback);

    /**
     * Enable shutter sound
     *
     * @param shutter
     *            if false, the shutter sound will disabled, the default shutter
     *            sound is enabled.
     */
    public abstract void enableShutterSound(boolean shutter);
    public abstract void enableShutterSound(boolean shutter, @PictureSequenceSource.Format int pictureSource);
    public abstract void enableRecordSound(boolean enable, @RecordSource.Format int recordSource);

    public abstract void queryAeBlock();
    public abstract void setAeStatusCallback(AeStatusCallback callback);
    /**
    *  Gets the state of camera device
    *  @return STATE_IDLE, STATE_PREVIEW_[0-4], STATE_RECORD_[0-4]
    *  return STATE_FAIL if happend  Exception
    */
    public abstract int getState();

    /**
    * Registers a callback to be notified when using camera occure error
    */
    public abstract void setErrorCallback(ErrorCallback callback);


    public static final int STREAM_TYPE_YUV = 0;
    public static final int STREAM_TYPE_RECORD = 1;
    public static final int STREAM_TYPE_PREVIEW = 2;
    /*
    * @param streamType:
    *   STREAM_TYPE_YUV,
    *   STREAM_TYPE_RECORD,
    *   STREAM_TYPE_PREVIEW,
    * @param streamSource:
    *   see @PictureSequenceSource.Format
    *   see @RecordSource.Format
    *   see @PreviewSource.Format
    * @param streamPolicy: the interval is that you want to skip  frame.
    *   if param == 0, not drop frame;
    *   if param > 0,  drop one frame each param;
    *   if param < 0,  drop param frames each one frame;
    */
    public abstract int setDropStreamFrame(int streamType, int streamSource, int streamPolicy);

    /**
    * app notify watermark info
    * @param enable
    *            true open watermark, false close watermark
    * @param watermark
    *            watermark vehicle params, if param enable is false, this param could be "" string
    */
    public abstract void updateWatermark(boolean enable, String watermark);

}
