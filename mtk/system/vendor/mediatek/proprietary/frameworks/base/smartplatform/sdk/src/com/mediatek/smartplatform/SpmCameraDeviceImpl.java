package com.mediatek.smartplatform;


import java.lang.RuntimeException;
import java.lang.IllegalStateException;
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
import java.util.Arrays;

import android.annotation.NonNull;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.impl.CameraMetadataNative;
import android.hardware.camera2.impl.CaptureResultExtras;
import android.hardware.camera2.impl.PhysicalCaptureResultInfo;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.media.MediaActionSound;
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
import android.util.ArrayMap;
import android.util.Log;
import android.util.Size;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.mediatek.smartplatform.ICarCamDeviceUser;
import com.mediatek.smartplatform.ICameraDeviceListener;
import com.mediatek.smartplatform.ISpmRecordListener;
import com.mediatek.smartplatform.ImageReaderEx.ImageCallback;
/**
 * This class is used to manager the cameras in car. The design is reference
 * with both {@link android.hardware.camera2} and
 * {@link android.media.MediaRecorder}. It combines car customized preview,
 * record and other camera features, and more easy to use.
 *
 * @see android.hardware.camera2
 * @see android.media.MediaRecorder
 */
public class SpmCameraDeviceImpl extends SpmCameraDevice implements IBinder.DeathRecipient {
    private String TAG = "SpmCameraDeviceImpl";
    private static final boolean DEBUG = Build.IS_ENG || SystemProperties.getBoolean("persist.vendor.log.spmsdk", false);
    private static final boolean mPreSetPictureSurface = SystemProperties.getBoolean("persist.vendor.spm.presetpic", false);

    private ICarCamDeviceUser mRemoteDevice;
    private Object mRemoteDeviceLock = new Object();

    private SmartPlatformManager mManager;

    private Object mLock = new Object();

    private String mCameraId = null;

    private boolean mIsAvmDevice = false;
    private boolean mEnableRawSensorJpeg = false;
    private int mState = 0;

    private boolean mRelease = false;

    private ErrorCallback mErrorCallback = null;
    private AeStatusCallback mAeStatusCallback = null;
    private ADASCallback mADASCallback = null;

    private MediaActionSound mSound;
    private boolean mShutterSoundEnable = true;
    private final SparseBooleanArray mRecordSoundEnableArray = new SparseBooleanArray();
    private final SparseBooleanArray mPictureSoundEnableArray = new SparseBooleanArray();

    private final SparseBooleanArray mSurfaceUsageArray = new SparseBooleanArray();

    private ArrayList mRecordSourceList = new ArrayList<>();
    private Size mPictureSize = null;
    private boolean isTakingPicture = false;
    private Object mTakingPictureLock = new Object();
    private Handler mMamagerHandler = null;

    public SpmCameraDeviceImpl(SmartPlatformManager manager, Handler handler, String cameraId) {
        mManager = manager;
        mMamagerHandler = handler;
        mCameraId = cameraId;
        TAG = TAG + "[" + mCameraId + "]";
        if (DEBUG) {
            Log.d(TAG,"Instance, Camera id: " + mCameraId  + "; CameraDevice: " + this);
        }

        mEventThread = new HandlerThread(TAG + "CameraDeviceEvent");
        mEventThread.start();
        mEventHandler= new EventHandler(this, mEventThread.getLooper());
        Message m = mEventHandler.obtainMessage(EVENT_MSG_SOUND_INIT);
        mEventHandler.sendMessage(m);
    }

    public String getCameraId() {
        return mCameraId;
    }

    private HandlerThread mEventThread = null;
    private EventHandler mEventHandler = null;

    private class EventHandler extends Handler {
        private SpmCameraDeviceImpl mCameraDevice;

        public EventHandler(SpmCameraDeviceImpl cd, Looper looper) {
            super(looper);
            mCameraDevice = cd;
        }

        @Override
        public void handleMessage(Message msg) {
            if (DEBUG) Log.i(TAG, "handleMessage: " + msg.what);
            switch(msg.what) {
            case EVENT_MSG_RELEASE_PICTURETAKEN:
                ImageReaderEx pictureTaken = (ImageReaderEx)msg.obj;
                boolean needRelease = true;
                if(mPreSetPictureSurface){
                    needRelease = false;
                }
                mCameraDevice.stopTakePicture(pictureTaken, needRelease);
                return;
            case EVENT_MSG_RESULT_METADATA:
                mCameraDevice.handleResultMetadata();
                return;
            case EVENT_MSG_SOUND_INIT:
                initSound();
                return;
            case EVENT_MSG_SOUND_RELEASE:
                releaseSound();
                return;
            case EVENT_MSG_TAKE_PICTURE_TIMEOUT:
                CamPictureCallback callback = (CamPictureCallback)msg.obj;
                takePictureFail(callback);
                return;
            case EVENT_MSG_TAKE_PICTURE_OK:
                removeTakePictureTimeoutMessage();
                return;
            default:
                Log.e(TAG,"Unknow message type " + msg.what);
                return;
            }
        }

    }

    public static final int CAMERA_STATUS_ERROR = 1;
    public static final int CAMERA_STATUS_SHUTTER = 2;
    public static final int CAMERA_STATUS_VIDEO = 3;
    public static final int CAMERA_STATUS_MOTION = 4;
    public static final int CAMERA_PICTURE_TAKEN = 5;
    public static final int RECORD_STATUS_CHANGED = 6;
    public static final int CAMERA_STATUS_AE = 7;
    public static final int CAMERA_ERROR_OCCURED = 8;
    public static final int CAMERA_ADAS_NOTIFY = 9;
    public static final int CAMERA_AVM_CALIBBRATION = 10;

    private CameraMetadataNative resultMetadata = null;

    private SpmCamDeviceListener mSpmCamDeviceListener = null;

    private class SpmCamDeviceListener extends ICameraDeviceListener.Stub {
        @Override
        public void onStatusChanged(int event, int arg1, String arg2, int arg3)
        throws RemoteException {
        }

        @Override
        public void onMiscDataCallback(int fd,int datatype, int size) throws RemoteException {

        }

        @Override
        public void onVideoFrame(int fd,int dataType, int size) throws RemoteException {

        }

        @Override
        public void onAudioFrame(int fd,int dataType, int size) throws RemoteException {
        }

        @Override
        public void onADASCallback(ADASInfo info)throws RemoteException {
            synchronized (mADASCallbackLock) {
                if (mADASCallback != null) {
                    mADASCallback.onADASCallback(info);
                }
            }

        }

        @Override
        public void onResultReceived(CameraMetadataNative result,
                CaptureResultExtras resultExtras, PhysicalCaptureResultInfo physicalResults[])
                throws RemoteException {
            Log.i(TAG,"onResultReceived ");
            resultMetadata = new CameraMetadataNative(result);
            if(mCurrentPara != null) {
                mCurrentPara.setResultMetadata(resultMetadata);
            }
            if(mEventHandler != null) {
                Message m = mEventHandler.obtainMessage(EVENT_MSG_RESULT_METADATA);
                mEventHandler.sendMessage(m);
            }
            if(DEBUG) {
                resultMetadata.dumpToLog();
            }
        }

        @Override
        public void onDeviceError(final int errorCode, CaptureResultExtras resultExtras){
            Log.i(TAG,"onDeviceError : " + errorCode);
            synchronized (mLock) {
                synchronized (mDeviceErrorLock) {
                    if (mErrorCallback != null) {
                        mErrorCallback.onError(errorCode, mCameraId, SpmCameraDeviceImpl.this);
                    } else {
                        Log.i(TAG,"mErrorCallback is null");
                        if(errorCode == ErrorCallback.ERROR_CAMERA_DISCONNECTED){
                            Message msg = Message.obtain(mMamagerHandler, SmartPlatformManager.EVENT_DEVICE_DISCONNECTED, mCameraId);
                            mMamagerHandler.sendMessage(msg);
                        }
                    }
                }
            }
        }

    };


    int openCamera() {
        int ret = 0;

        synchronized (mRemoteDeviceLock) {
            try {
                if (mRemoteDevice == null) {
                    Log.e(TAG,"remote Device is null, this device maybe release already !!! ");
                    return -1;
                }
                mRemoteDevice.open();
            } catch (RemoteException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                ret = -1;
            } catch (ServiceSpecificException e) {
                e.printStackTrace();
                ret = -1;
            }
        }

        synchronized (mLock) {		
            if(ret == 0) {
                mRelease = false;
            }
        }
        return ret;
    }

    public  CameraCharacteristics getCameraCharacteristics() {
        CameraCharacteristics p = null ;
        try {
            p = mManager.getCameraCharacteristics(mCameraId);
        } catch(Exception e) {
            Log.e(TAG, "could not getCameraCharacteristics");
        }
        return p;
    }

    /**
     * Set remote device, which triggers initial onOpened/onUnconfigured callbacks
     *
     * <p>This function may post onDisconnected and throw CAMERA_DISCONNECTED if remoteDevice dies
     * during setup.</p>
     *
     */
    void setRemoteDevice(ICarCamDeviceUser remoteDevice) {

        synchronized (mRemoteDeviceLock) {

            if (remoteDevice == null) {
                Log.e(TAG,"remote Device is null");
                return ;
            }
            mRemoteDevice = remoteDevice;

            IBinder remoteDeviceBinder = remoteDevice.asBinder();
            if (remoteDeviceBinder != null) {
                try {
                    remoteDeviceBinder.linkToDeath(this, /*flag*/ 0);
                } catch (RemoteException e) {

                }
            }
            try {
                mSpmCamDeviceListener = new SpmCamDeviceListener();
                mRemoteDevice.addListener(mSpmCamDeviceListener);
            } catch (RemoteException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    /**
     * Set remote device, which triggers initial onOpened/onUnconfigured callbacks
     *
     * <p>This function may post onDisconnected and throw CAMERA_DISCONNECTED if remoteDevice dies
     * during setup.</p>
     *
     */
    void setAvmDeviceType() {
        mIsAvmDevice = true;
    }

    void enableRawSensorJpeg() {
        mEnableRawSensorJpeg = true;
    }
    /**
     * Listener for binder death.
     *
     * <p> Handle binder death for ICarCamDeviceUser. Trigger onError.</p>
     */
    @Override
    public void binderDied() {
        Log.w(TAG, "CameraDevice " + mCameraId + " died unexpectedly");
        synchronized (mRemoteDeviceLock) {
            if (mRemoteDevice == null) {
                return; // Camera already closed
            }
        }
    }


    /**
    *  Gets the state of camera device
    *  @return  STATE_IDLE, STATE_PREVIEW_[0-4], STATE_RECORD_[0-4]
    *  return STATE_FAIL if happend  Exception
    */
    public int getState() {
        synchronized (mRemoteDeviceLock) {
            if (mRemoteDevice == null) {
                Log.e(TAG,"remote Device is null, this device maybe release already !!! ");
                return STATE_FAIL;
            }

            try {
                return mRemoteDevice.getState(0);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        return STATE_FAIL;
    }

    /**
    *  Sets the state of camera device
    */
    private void setState(int state) {

        synchronized (mRemoteDeviceLock) {
            if (mRemoteDevice == null) {
                Log.e(TAG,"remote Device is null, this device maybe release already !!! ");
                return ;
            }
            try {
                mRemoteDevice.setState(0, state);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean checkState(int state) {
        int sState = getState();
        if (DEBUG) Log.d(TAG,"checkState, sState = " + sState + "state = " + state);
        if(( sState & state) != 0) {
            //Log.w(TAG, "checkState, already ");
            return false;
        }
        return true;
    }


    private void printParameter(String parameters) {
        if (DEBUG) {
            int maxLength = 1000;
            long length = parameters.length();
            if (length <= maxLength) {
                Log.d(TAG, parameters);
            } else {
                for (int i = 0; i < parameters.length(); i += maxLength) {
                    if (i + maxLength < parameters.length()) {
                        Log.d(TAG, parameters.substring(i, i + maxLength));
                    } else {
                        Log.d(TAG, parameters.substring(i, parameters.length()));
                    }
                }
            }
        }
    }


    public boolean setPreviewSurface(Surface surface, @PreviewSource.Format int previewSource) {

        synchronized (mLock) {

            int previewSurfaceId = PreviewSource.getSurfaceUsage(previewSource);
            if(!CameraSurfaceUsage.isValid(previewSurfaceId)) {
                Log.e(TAG, "do not support this previewSource");
                return false;
            }

            int stateValue = CameraSurfaceUsage.getSurfaceState(previewSurfaceId);
            if(!checkState(stateValue) && surface != null) {
                Log.e(TAG,"This camera usage(" + previewSurfaceId + ") has been used, please check again...");
                return false;
            }

            Log.i(TAG,"setPreviewSurface : " + surface + " previewSurfaceId: " + previewSurfaceId);
            if (!mIsAvmDevice) {
                if(mPreSetPictureSurface) {
                    if(surface == null) {
                        stopTakePicture(mPictureTaken, true);
                    } else {
                        if(!mSurfaceUsageArray.get(CameraSurfaceUsage.PICTURE_0, false)) {
                            Log.i(TAG, "no Picture surface, set default");
                            setPictureSize(null);
                        }
                    }
                }
            }
            if(surface == null) {
                mState &= ~stateValue;
            } else {
                mState |= stateValue;
            }

            synchronized (mRemoteDeviceLock) {
                if (mRemoteDevice == null) {
                    Log.e(TAG,"remote Device is null, this device maybe release already !!! ");
                    return false;
                }

                int ret = setSurfaceDisplay(surface, previewSurfaceId, CameraDevice.TEMPLATE_PREVIEW);
                if (ret != 0) {
                    Log.w(TAG,"setSurfaceDisplay return fail!");
                    return false;
                }
                setState(mState);

            }
        }

        return true;
    }

    /**
     * Sets the {@link Surface} to be used for live preview.
     *
     * <p>
     * The surface can be surface view or surface texture. During the preview
     * process, if the preview surface is destroyed, this method can be called
     * to set a new surface, but the dimensions cannot change until
     * {@link #stopPreview()} be called.
     *
     * @param surface
     *            used to display preview pictures
     *
     */

    public int startPreview() {
        synchronized (mLock) {
            Log.i(TAG,"startPreview ");
            synchronized (mRemoteDeviceLock) {
                if (mRemoteDevice == null) {
                    Log.e(TAG,"remote Device is null, this device maybe release already !!! ");
                    return -1;
                }
                try {
                    mRemoteDevice.startPreview();
                } catch (RemoteException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
        return 0;
    }


    public int stopPreview() {
        synchronized (mLock) {

            Log.i(TAG,"stopPreview ");

            synchronized (mRemoteDeviceLock) {
                if (mRemoteDevice == null) {
                    Log.e(TAG,"remote Device is null, this device maybe release already !!! ");
                    return -1;
                }
                try {
                    mRemoteDevice.stopPreview();
                } catch (RemoteException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }

        return 0;
    }

    private  int setSurfaceDisplay(Surface surface, int usage, int requestTemplate) {

        Log.i(TAG,"setSurfaceDisplay: surface: " + surface + "  usage: " + usage);
        synchronized (mRemoteDeviceLock) {
            int ret = 0;
            if (mRemoteDevice == null) {
                Log.e(TAG,"remote Device is null, this device maybe release already !!! ");
                return -1;
            }

            /*try{
                if(surface != null) {
                    CameraMetadataNative requestMetadata= mRemoteDevice.createDefaultRequest(requestTemplate);
                    mCurrentPara.updateMetadata(requestMetadata, usage);
                    mRemoteDevice.setRequestConfig(requestMetadata, usage);
                }
            } catch(Exception e) {
                Log.e(TAG, "set request config fail");
                e.printStackTrace();
            }*/

            try {
                ret = mRemoteDevice.setSurfaceDisplay(surface, usage);
            } catch (RemoteException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
				ret = -1;
            } catch (ServiceSpecificException e) {
                e.printStackTrace();
                ret = -1;
            }
            Log.i(TAG,"setSurfaceDisplay return ret:" + ret);			
            return ret;
        }


    }

    private SpmCamParameters mCurrentPara = null;

    /**
      * Returns the current settings for this Camera service.
      * If modifications are made to the returned Parameters, they must be passed
      * to {@link #setParameters(Camera.Parameters)} to take effect.
      *
      * @throws RuntimeException if reading parameters fails; usually this would
      *    be because of a hardware or other low-level error, or because
      *    release() has been called on this Camera instance.
      * @see #setParameters(Camera.Parameters)
      */
    public SpmCamParameters getParameters() {
        Log.v(TAG,"getParameters");
		synchronized (mRemoteDeviceLock) {
            if (mRemoteDevice == null) {
                Log.e(TAG,"remote Device is null, please open device.");
                return null;
            }
        }
        if(mCurrentPara == null) {

            SpmCamParameters p = new SpmCamParameters();
            Log.i(TAG,"new SpmCamParameters");
            CameraCharacteristics cameraCharacteristics = getCameraCharacteristics();

            if(DEBUG) {
                Log.d(TAG,"cameraCharacteristics:" + cameraCharacteristics);
                if(cameraCharacteristics != null) cameraCharacteristics.getNativeCopy().dumpToLog();
            }

            p.setCameraCharacteristics(cameraCharacteristics);

            mCurrentPara = p;
            synchronized (mRemoteDeviceLock) {
                try {
                    CameraMetadataNative requestMetadata= mRemoteDevice.createDefaultRequest(CameraDevice.TEMPLATE_RECORD);
                    p.setRequestMetadata(requestMetadata);
                } catch (Exception  e) {
                    Log.e(TAG, "get request metadata fail");
                    mCurrentPara = null;
                    e.printStackTrace();
                }
            }

        }
        return mCurrentPara;
    }

    /**
     * Changes the settings for this Camera service.
     *
     * @param params
     *            the Parameters to use for this Camera service
     * @see #getParameters()
     */
    public void setParameters(SpmCamParameters params) {
        Log.v(TAG," setParameters ");
        synchronized (mRemoteDeviceLock) {
            if (mRemoteDevice == null) {
                Log.e(TAG,"remote Device is null, please open device.");
                return ;
            }
        }
        mCurrentPara = params;
        if (!mIsAvmDevice)
            updateRequestConfig(CameraSurfaceUsage.END, CameraDevice.TEMPLATE_RECORD);

    }

    public int startRecord(@RecordSource.Format int recordSource, @NonNull RecordConfiguration recordConfig) {
        synchronized (mLock) {

            Log.i(TAG,"startRecord, recordSource = " + recordSource);

            int recordSurfaceId = RecordSource.getSurfaceUsage(recordSource);

            if(!CameraSurfaceUsage.isValid(recordSurfaceId)) {
                Log.e(TAG, "do not support this recordSource");
                return RECORD_START_ERROR_INVALID_RECORD_SOURCE;
            }

            int stateValue = CameraSurfaceUsage.getSurfaceState(recordSurfaceId);
            if(!checkState(stateValue)) {
                Log.e(TAG,"This camera usage has been used, please check again...");
                return RECORD_START_ERROR_SURFACE_USED;
            }

            String recorderId = RecordSource.getRecordId(recordSource, mCameraId);

            if(recorderId == null) {
                Log.e(TAG, "do not support this recordSource");
                return RECORD_START_ERROR_INVALID_RECORD_SOURCE;
            }

            MediaRecorderListener recorderListener;


            recorderListener = mRecorderListenerArray.get(recorderId);
            if(null == recorderListener) {
                recorderListener = new MediaRecorderListener();
                mRecorderListenerArray.put(recorderId, recorderListener);
            }

            SpmCamParameters para = getParameters();
            if (para == null) {
                Log.e(TAG, "do not support this recordSource");
                return RECORD_START_ERROR_INVALID_RECORD_SOURCE;
            }
            para.set(SpmCamParameters.KEY_RECORD_ID, recorderId);

            para.setCameraId(mCameraId);

            para.setFromRecordConfig(recordConfig);



            String listenerName;
            if(RecordSource.isSubRecord(recordSource)) {
                listenerName= "_SRL";

            } else {
                listenerName= "_MRL";
            }

            //int videoFrameMode = recordConfig.mVideoFrameMode;

            recorderListener.setInfo(listenerName,
                                     recordConfig.mVideoCallback, recordConfig.mAudioCallback,
                                     recordConfig.mRecordStatusCallback, recordConfig.mKeypointCallback);

            recorderListener.setRecorderSource(recordSource);

            synchronized (mRemoteDeviceLock) {
                if (mRemoteDevice == null) {
                    Log.e(TAG,"remote Device is null, this device maybe release already !!! ");
                    return -1;
                }

                /*try{
                    CameraMetadataNative requestMetadata= mRemoteDevice.createDefaultRequest(CameraDevice.TEMPLATE_RECORD);
                    mCurrentPara.updateMetadata(requestMetadata, recordSurfaceId);
                    mRemoteDevice.setRequestConfig(requestMetadata, recordSurfaceId);
                } catch(Exception e) {
                    Log.e(TAG, "set request config fail");
                    e.printStackTrace();
                }*/

                try {
                    mRemoteDevice.addSpmRecordListener(recorderListener, recordSurfaceId);
                    mRemoteDevice.startRecord(para.flatten(), recordSurfaceId);
                    mRecordSourceList.add(recordSource);
                   // setDropStreamFrame(STREAM_TYPE_RECORD, recordSource, 2);
                    boolean recordSound = mRecordSoundEnableArray.get(recordSurfaceId, true);
                    if(recordSound) {
                        playSound(MediaActionSound.START_VIDEO_RECORDING);
                    }
                    mState |= stateValue;
                    setState(mState);
                } catch (RemoteException | ServiceSpecificException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                    return RECORD_START_ERROR_REMOTE_FAIL;
                }
            }

            //submitRequest();

            return RECORD_START_OK;
        }

    }


    public int stopRecord(@RecordSource.Format int recordSource) {

        synchronized (mLock) {
            Log.i(TAG,"stopRecord, recordSource = " + recordSource);

            int recordSurfaceId = RecordSource.getSurfaceUsage(recordSource);

            if(!CameraSurfaceUsage.isValid(recordSurfaceId)) {
                Log.e(TAG, "do not support this recordSource");
                return RECORD_START_ERROR_INVALID_RECORD_SOURCE;
            }

            int stateValue = CameraSurfaceUsage.getSurfaceState(recordSurfaceId);

            if(checkState(stateValue)){
                Log.i(TAG, "already stop: " + recordSource);
                return 0;
            }

            String recorderId = RecordSource.getRecordId(recordSource, mCameraId);
            if(recorderId == null) {
                Log.e(TAG, "do not support this recordSource");
                return RECORD_START_ERROR_INVALID_RECORD_SOURCE;
            }

            MediaRecorderListener recorderListener;
            recorderListener = mRecorderListenerArray.get(recorderId);

            synchronized (mRemoteDeviceLock) {
                if (mRemoteDevice == null) {
                    Log.e(TAG,"remote Device is null, this device maybe release already !!! ");
                    return -1;
                }
                try {
                    boolean recordSound = mRecordSoundEnableArray.get(recordSurfaceId, true);
                    if(recordSound) {
                        playSound(MediaActionSound.STOP_VIDEO_RECORDING);
                    }
                    mRecordSoundEnableArray.delete(recordSurfaceId);

                    mRemoteDevice.stopRecord(recorderId, recordSurfaceId);
                    if(recorderListener != null) {
                        mRemoteDevice.removeSpmRecordListener(recorderListener, recordSurfaceId);
                    }
                    for (int i = mRecordSourceList.size() - 1; i >= 0; i --) {
                        if ((int)mRecordSourceList.get(i) == recordSource) {
                            mRecordSourceList.remove(i);
                        }
                    }
                } catch (RemoteException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }

            mRecorderListenerArray.remove(recorderId);

            //startPreview();
            mState &= ~stateValue;
            setState(mState);

        }
        return 0;
    }

    public CaptureRequest.Builder createCaptureRequest(int templateType) {
        synchronized(mRemoteDeviceLock) {
            if (mRemoteDevice == null) {
                Log.e(TAG,"remote Device is null, this device maybe release already !!! ");
                return null;
            }

            CameraMetadataNative templatedRequest = null;
            try {
                templatedRequest = mRemoteDevice.createDefaultRequest(templateType);
            } catch (RemoteException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            if (templatedRequest == null) {
                Log.e(TAG,"templatedRequest is null, this device maybe release already !!! ");
                return null;
            }        
            CaptureRequest.Builder builder = new CaptureRequest.Builder(
                    templatedRequest, /*reprocess*/false, CameraCaptureSession.SESSION_ID_NONE,
                    getCameraId(), /*physicalCameraIdSet*/ null);

            return builder;
        }
    }

    public int submitRequest(boolean streaming) {
        synchronized (mLock) {
            synchronized (mRemoteDeviceLock) {
                if (mRemoteDevice == null) {
                    Log.e(TAG,"remote Device is null, this device maybe release already !!! ");
                    return -1;
                }
                try {
                    mRemoteDevice.submitRequest(streaming);
                } catch (RemoteException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            return 0;
        }
    }

    public int clearRequest() {
        synchronized (mLock) {
            synchronized (mRemoteDeviceLock) {
                if (mRemoteDevice == null) {
                    Log.e(TAG,"remote Device is null, this device maybe release already !!! ");
                    return -1;
                }
                try {
                    mRemoteDevice.clearRequest();
                } catch (RemoteException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            return 0;
        }
    }

    public void updateRequest() {
        synchronized (mLock) {
            synchronized (mRemoteDeviceLock) {
                if (mRemoteDevice == null) {
                    Log.e(TAG,"remote Device is null, this device maybe release already !!! ");
                    return ;
                }

                if (mIsAvmDevice) {
                    Log.e(TAG,"avm device no not support this API.");
                    return ;
                }

                updateRequestConfig(CameraSurfaceUsage.END, CameraDevice.TEMPLATE_RECORD);

                try {
                    mRemoteDevice.submitRequest(true);
                } catch (RemoteException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    }

    private void updateRequestConfig(int usage, int requestTemplate){
        try{
           CameraMetadataNative requestMetadata= mCurrentPara.getRequestMetadata();
           mCurrentPara.updateMetadata(requestMetadata, usage);
           synchronized (mRemoteDeviceLock) {
                mRemoteDevice.setRequestConfig(requestMetadata, usage);
           }
       } catch(Exception e) {
           Log.e(TAG, "set request config fail");
           e.printStackTrace();
       }
    }

    private boolean setPictureRequestConfig(){
        int pictureTemplate = CameraDevice.TEMPLATE_STILL_CAPTURE;
        int recordState = CameraSurfaceUsage.getRecordState();
        if(!checkState(recordState)) {
            pictureTemplate = CameraDevice.TEMPLATE_VIDEO_SNAPSHOT;
        }

        synchronized (mRemoteDeviceLock) {
            try {
                if (mRemoteDevice == null) {
                   Log.e(TAG,"remote Device is null, setPictureRequestConfig !!!");
                   return false;
                }
                CameraMetadataNative requestMetadata= mRemoteDevice.createDefaultRequest(pictureTemplate);
                mCurrentPara.updateMetadata(requestMetadata, CameraSurfaceUsage.PICTURE_0);
                mRemoteDevice.setRequestConfig(requestMetadata, CameraSurfaceUsage.PICTURE_0);
            } catch (Exception  e) {
                Log.e(TAG, "setPictureRequestConfig fail");
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }

    private static final int EVENT_LOCK_VIDEO                   = 0;
    private static final int EVENT_SET_VIDEO_ROTATE_DURATION    = 1;
    private static final int EVENT_SET_RECORDING_MUTE_AUDIO     = 2;
    private static final int EVENT_SET_RECORDING_SDCARD_PATH    = 3;
    private static final int EVENT_SET_MOTIO_DETECT             = 4;
    private static final int EVENT_ENABLE_SHUTTER_SOUND         = 5;
    private static final int EVENT_SET_BITRATE_DYN              = 6;
    private static final int EVENT_SET_RECORD_MIC_CHANGED       = 7;
    private static final int EVENT_ENABLE_RECORD_SOUND          = 8;
    private static final int EVENT_FLUSH_CUR_REC_FILE           = 9;
    private static final int EVENT_SET_RECORDING_LOCK_PATH      = 10;
    private static final int EVENT_SET_RECORDING_ENCRYPT_MODE   = 11;
    private static final int EVENT_SET_VIDEO_FRAME_MODE         = 12;

    private void notifyRecordEvent(int eventId, int arg1, int arg2, String params, @RecordSource.Format int recordSource) {
        synchronized (mLock) {
            int recordSurfaceId = RecordSource.getSurfaceUsage(recordSource);

            if (params == null) {
                params = "";
            }
            if (DEBUG) {
                Log.d(TAG,"notifyRecordEvent, eventId: " + eventId + " arg1:" + arg1
                      + " arg2:" + arg2 + " param:(" + params + ")" + "recorderId :" + recordSurfaceId);
            }

            synchronized (mRemoteDeviceLock) {
                try {
                    if (mRemoteDevice == null) {
                        Log.e(TAG,"remote Device is null, this device maybe release already !!! ");
                        return;
                    }
                    mRemoteDevice.notifyRecordEvent(recordSurfaceId, eventId, arg1, arg2, params);;
                } catch (RemoteException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    }

    public void lockRecordingVideo(int duration, String protectedType, @RecordSource.Format int recordSource) {
        notifyRecordEvent(EVENT_LOCK_VIDEO, 1, duration, protectedType, recordSource);
    }

    public void unlockRecordingVideo(@NonNull String filename, @RecordSource.Format int recordSource) {
        notifyRecordEvent(EVENT_LOCK_VIDEO, 0, 0, filename, recordSource);
    }

    public void setVideoRotateDuration(int duration_ms, @RecordSource.Format int recordSource ) {
        notifyRecordEvent(EVENT_SET_VIDEO_ROTATE_DURATION, duration_ms, 0, null, recordSource);
    }

    public void setVideoBitrateDyn(int bitrate, int bAdjust, @RecordSource.Format int recordSource) {
        notifyRecordEvent(EVENT_SET_BITRATE_DYN, bitrate, bAdjust, null, recordSource);
    }

    public void setRecordingMuteAudio(boolean isMuteAudio, @RecordSource.Format int recordSource) {
        if (isMuteAudio) {
            notifyRecordEvent(EVENT_SET_RECORDING_MUTE_AUDIO, 1, 0, null, recordSource);
        } else {
            notifyRecordEvent(EVENT_SET_RECORDING_MUTE_AUDIO, 0, 0, null, recordSource);
        }
    }

    public void setRecordMicChanged(boolean isMicChanged, @RecordSource.Format int recordSource) {
        if (isMicChanged) {
            notifyRecordEvent(EVENT_SET_RECORD_MIC_CHANGED, 1, 0, null, recordSource);
        } else {
            notifyRecordEvent(EVENT_SET_RECORD_MIC_CHANGED, 0, 0, null, recordSource);
        }
    }


    public void setProtectRecording(boolean ismotiondetect, boolean isrecordingstatus,int duration_ms, @RecordSource.Format int recordSource) {
        if (ismotiondetect) {
            notifyRecordEvent(EVENT_SET_MOTIO_DETECT, 1, isrecordingstatus? 1:0,
                              Integer.toString(duration_ms), recordSource);
        } else {
            notifyRecordEvent(EVENT_SET_MOTIO_DETECT, 0, isrecordingstatus? 1:0,
                              Integer.toString(duration_ms), recordSource);
        }
    }


    public void setRecordingSdcardPath(@NonNull String path, @RecordSource.Format int recordSource) {
        if (path == null) {
            Log.w(TAG, "Invalid argument: path=" + path
                  + " in setRecordingSdcardPath");
            return;
        }
        notifyRecordEvent(EVENT_SET_RECORDING_SDCARD_PATH, 0, 0, path, recordSource);
    }

    public void flushCurRecFile(@RecordSource.Format int recordSource){
        notifyRecordEvent(EVENT_FLUSH_CUR_REC_FILE, 0, 0, null, recordSource);
    }

    public void setLockFilePath(@NonNull String path, @RecordSource.Format int recordSource) { 
        if (path == null) {
            Log.w(TAG, "Invalid argument: path=" + path
                  + " in setLockFilepath");
            return;
        }
        notifyRecordEvent(EVENT_SET_RECORDING_LOCK_PATH, 0, 0, path, recordSource);
    }

    public void enableEncryption(boolean enable, @RecordSource.Format int recordSource) {
        notifyRecordEvent(EVENT_SET_RECORDING_ENCRYPT_MODE, enable ? 1 : 0, 0, null, recordSource);
    }

    public void setVideoFrameMode(@RecordConfiguration.VideoFrameMode int mode, @RecordSource.Format int recordSource) {
        if(RecordConfiguration.VideoFrameMode.VIDEO_FRAME_MODE_SOURCE != mode && 
            RecordConfiguration.VideoFrameMode.VIDEO_FRAME_MODE_PACKET!= mode)
            notifyRecordEvent(EVENT_SET_VIDEO_FRAME_MODE, mode, 0, null,recordSource);
    }

    public int startPictureSequence(@PictureSequenceSource.Format int source, @NonNull PictureConfiguration config) {
        synchronized (mLock) {
            Log.i(TAG,"startPictureSequence :" + source);
            String path = config.mPath;
            int imageFormat = config.mImageFormat;
            ImageReaderEx.ImageCallback cb = config.mImageCallback;
            int dataType = config.mDataType;
            boolean jpegHwEnc = config.mJpegHwEnc;
            int picWidth = config.mPicWidth;
            int picHeight = config.mPicHeight;

            Log.i(TAG, "startPictureSequence,  path = " + path + "; imageFormat = " + imageFormat + "; type = " + dataType + "; jpegHwenc = " + jpegHwEnc);


            int pictureSurface = PictureSequenceSource.getSurfaceUsage(source);
            if(pictureSurface < 0) {
                Log.e(TAG, "can not startPictureSequence, not support this source: " + pictureSurface);
                return -1;
            }

            String pictureSequenceId = PictureSequenceSource.getPictureSequenceId(source);

            int stateValue = CameraSurfaceUsage.getSurfaceState(pictureSurface);


            int extId = CameraSurfaceUsage.getSurfaceExId(pictureSurface);
            if (extId < 0) {
                Log.e(TAG, "startPictureSequence, wrong pictureSurface id = " + pictureSurface + "; extId = " + extId);
                return -1;
            }
            Log.i(TAG, "startPictureSequence, pictureSurface id = " + pictureSurface + "; extId = " + extId);

            if(!checkState(stateValue)) {
                Log.e(TAG,"can not startPictureSequence, already in State");
                return -1;
            }

            int pictureWidth = 1280;
            int pictureHeight = 720;

            ImageReaderEx mPictureSequence = new ImageReaderEx();

            if(picWidth >0 && picHeight > 0) {
                pictureWidth = picWidth;
                pictureHeight = picHeight;
            }


            Log.i(TAG,"startPictureSequence Picture Size (" + pictureWidth + "x" + pictureHeight + ")");


            mPictureSequence.setImageCallback(cb);
            mPictureSequence.setImageDataCallback(config.mImageDataCallback);
            mPictureSequence.setOutImageFormat(imageFormat, dataType);
            mPictureSequence.setOutImageDir(path);

            mPictureSequence.init(pictureWidth, pictureHeight, imageFormat , "_" + mCameraId, mCameraId, true);

            if(jpegHwEnc && (mManager != null && 0 == mManager.getJpegHwEncState())) {
                mPictureSequence.setJpegHwEnc(true);
                mManager.setJpegHwEncState(1);
            } else {
                if(jpegHwEnc) {
                    Log.w(TAG,"jpeg hw enc used by another or not support, set jpeg sw enc");
                }
                mPictureSequence.setJpegHwEnc(false);
            }
            if (mManager == null) {
                Log.e(TAG, "SmartPlatformManager is null");
                return -1;
            }

            Surface mPictureSequenceSurface = mPictureSequence.getSurface();

            SpmCamParameters para = getParameters();
            if (para == null) {
                Log.e(TAG, "para is null");
                return -1;
            }
            if(extId >= 0 &&CameraSurfaceUsage.IsExSurface(pictureSurface)) {
                para.setExtSurfaceSize(extId, pictureWidth, pictureHeight);
                para.setExtSurfaceFormat(extId, "yuv420p");
            }

            setParameters(para);

            setSurfaceDisplay(mPictureSequenceSurface, pictureSurface, CameraDevice.TEMPLATE_PREVIEW);
            submitRequest(true);
            mPictureSequence.start();

            mPictureSequenceArray.put(pictureSequenceId, mPictureSequence);

            mState |= stateValue;
            setState(mState);

            return 0;
        }
    }


    public int stopPictureSequence(@PictureSequenceSource.Format int source) {
        synchronized (mLock) {
            Log.i(TAG, " stopPictureSequence: " + source);
            int pictureSurface = PictureSequenceSource.getSurfaceUsage(source);
            if(pictureSurface < 0) {
                Log.e(TAG, "can not stopPictureSequence, not support this source or this source was already stopped: " + pictureSurface);
                return -1;
            }

            String pictureSequenceId = PictureSequenceSource.getPictureSequenceId(source);

            ImageReaderEx mPictureSequence = mPictureSequenceArray.get(pictureSequenceId);

            int stateValue = CameraSurfaceUsage.getSurfaceState(pictureSurface);

            if(checkState(stateValue)){
                Log.i(TAG, "already stop: " + source);
                return 0;
            }

            try {
                setSurfaceDisplay(null, pictureSurface, CameraDevice.TEMPLATE_PREVIEW);
                submitRequest(true);
                if(mPictureSequence != null) {
                    mPictureSequence.stop();
                }
            } catch(Exception e) {
                Log.e(TAG, " stop Picture Sequence error " + e);
            } finally {
                if(mPictureSequence != null && mPictureSequence.getJpegHwEnc()) {
                    if(mManager != null) {
                        mManager.setJpegHwEncState(0);
                    }
                }
                mPictureSequenceArray.remove(pictureSequenceId);
            }

            mState &= ~stateValue;
            setState(mState);

            mPictureSequence = null;

            return 0;
        }
    }

    public FileDescriptor enableShareBuffer(@PictureSequenceSource.Format int source, boolean enableSharebuffer) {
        String pictureSequenceId = PictureSequenceSource.getPictureSequenceId(source);
        ImageReaderEx mPictureSequence = mPictureSequenceArray.get(pictureSequenceId);

        if(mPictureSequence != null) {
            return mPictureSequence.enableShareBuffer(enableSharebuffer, false);
        }
        return null;
    }

    public FileDescriptor enableShareBuffer(@PictureSequenceSource.Format int source,
                                                            boolean enableSharebuffer, boolean enableCallback) {
        String pictureSequenceId = PictureSequenceSource.getPictureSequenceId(source);
        ImageReaderEx mPictureSequence = mPictureSequenceArray.get(pictureSequenceId);

        if(mPictureSequence != null) {
            return mPictureSequence.enableShareBuffer(enableSharebuffer, enableCallback);
        }
        return null;
    }

    public int setDropStreamFrame(int streamType, int streamSource, int streamPolicy) {
        int usage = -1;
        switch(streamType) {
        case STREAM_TYPE_YUV:
            usage = PictureSequenceSource.getSurfaceUsage(streamSource);
            break;
        case STREAM_TYPE_RECORD:
            usage = RecordSource.getSurfaceUsage(streamSource);
            break;
        case STREAM_TYPE_PREVIEW:
            usage = PreviewSource.getSurfaceUsage(streamSource);
            break;
        default:
            Log.e(TAG, "setDropStreamFrame: streamType error" + streamType);
            return -1;
        }

        SpmCamParameters para = getParameters();
        if (para == null) {
            Log.e(TAG, "getParameters return null");
            return -1;
        }
        para.setDropStreamFrame(streamPolicy, usage);
        updateRequest();
        return 0;
    }

    private final ArrayMap<String, MediaRecorderListener> mRecorderListenerArray = new ArrayMap<>();
    private final ArrayMap<String, ImageReaderEx> mPictureSequenceArray = new ArrayMap<>();
    private ImageReaderEx mPictureTaken;

    public static final int RECORD_START_OK = 0;
    public static final int RECORD_START_ERROR_INVALID_RECORD_SOURCE = 1;
    public static final int RECORD_START_ERROR_SURFACE_USED = 2;
    public static final int RECORD_START_ERROR_PREPARE_FAIL = 3;
    public static final int RECORD_START_ERROR_REMOTE_FAIL = 4;


    public static final int DATA_TYPE_VIDEO = 501; //callback H264 video data
    public static final int DATA_TYPE_KEYPOINT = 502; //callback H264 ts packet data
    public static final int DATA_TYPE_VIDEO_FRAME_YUV = 503;  //callback video YUV data;
    private Object mVideoCallbackLock = new Object();
    private Object mADASCallbackLock = new Object();
    private Object mAudioCallbackLock = new Object();
    private Object mRecordCbLock = new Object();
    private Object mAeStatusCallbackLock = new Object();
    private Object mDeviceErrorLock = new Object();
    private byte[] mVideoData=new byte[1];  //need to initiation
    private byte[] mAudioData=new byte[1];
    //private byte[] mYuvData=new byte[1];  //need to initiation

    public boolean release() {
        synchronized (mLock) {

            Log.i(TAG,"release ");
            if(mRelease) {
                Log.w(TAG,"already release, skip this release");
                return true;
            }

            if(mEventHandler != null) {
                Message m = mEventHandler.obtainMessage(EVENT_MSG_SOUND_RELEASE);
                mEventHandler.sendMessage(m);
            }

            synchronized (mRemoteDeviceLock) {
                if(mRemoteDevice != null) {
                    try {
                        if(mPreSetPictureSurface) {
                            if (mSurfaceUsageArray.get(CameraSurfaceUsage.PICTURE_0, false)) {
                                Log.i(TAG,"release free picture surface");
                                stopTakePicture(mPictureTaken, true);
                            }
                        }
                        mRemoteDevice.removeListener(mSpmCamDeviceListener);
                        mRemoteDevice.release();
                        IBinder remoteDeviceBinder = mRemoteDevice.asBinder();
                        if (remoteDeviceBinder != null) {
                            remoteDeviceBinder.unlinkToDeath(this, /*flag*/ 0);
                        }
                        mRemoteDevice = null;
                        mSpmCamDeviceListener = null;
                    } catch (RemoteException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }

            if(mEventThread != null) {
                mEventThread.quitSafely();
                mEventHandler = null;
                mEventThread = null;
            }

            mRelease = true;

            mManager = null;
            synchronized (mDeviceErrorLock) {
                mMamagerHandler = null;
                mErrorCallback = null;
            }
            synchronized (mADASCallbackLock) {
                mADASCallback = null;
            }
            synchronized (mAeStatusCallbackLock) {
                mAeStatusCallback = null;
            }
            //mADASCallbackLock = null;
            //mAeStatusCallbackLock = null;

            synchronized (mAudioCallbackLock) {			
                //mAudioCallbackLock = null;
                mAudioData = null;
            }
            return true;
        }
    }



    private static final int EVENT_RECORDER_STATUS_CHANGE = 1;
    private static final int EVENT_RECORDER_VIDEO_FRAME = 2;
    private static final int EVENT_RECORDER_AUDIO_FRAME = 3;

    private class MediaRecorderListener extends ISpmRecordListener.Stub {

        @Override
        public void onStatusChanged(int event, int arg1, String arg2, int arg3)
        throws RemoteException {
            if (DEBUG) {
                Log.d(TAG, "onStatusChanged is invoked: event " + event + ", arg1 "
                      + arg1 + ", arg2 " + arg2 + ", arg3 " + arg3 );
            }

            onRecorderStatusChanged(event, arg1, arg2, arg3);

        }

        @Override
        public void onVideoFrame(ParcelFileDescriptor fd,int dataType, int size) throws RemoteException {
            onRecorderVideoFrame(fd, dataType, size);
        }

        @Override
        public void onAudioFrame(ParcelFileDescriptor fd,int dataType, int size) throws RemoteException {
            onRecorderAudioFrame(fd, dataType, size);
        }

        private void onRecorderStatusChanged(int event, int arg1, String arg2, int arg3) {
            if(DEBUG)
                Log.d(TAG + mName, "onRecorderStatusChanged is invoked: event " + event + ", arg1 "
                      + arg1 + ", arg2 " + arg2 + ", arg3 " + arg3 );
            switch (event) {
            case CAMERA_STATUS_VIDEO:
                synchronized (mRecordCbLock) {
                    if (this.mVideoCallback != null) {
                        VideoInfoMap videoInfoMap = new VideoInfoMap();
                        videoInfoMap.unflatten(arg2);
                        videoInfoMap.set(VideoInfoMap.KEY_RECORDER_CAMERA_ID, mCameraId);
                        videoInfoMap.set(VideoInfoMap.KEY_RECORDER_EVENT_TYPE, arg1);
                        videoInfoMap.set(VideoInfoMap.KEY_RECORDER_RECORDER_SOURCE, this.mRecorderSource);
                        this.mVideoCallback.onVideoTaken(videoInfoMap);
                    } else {
                        Log.w(TAG, "mVideoCallback is null ");
                    }
                }
                break;
            case CAMERA_STATUS_MOTION:
            case CAMERA_STATUS_SHUTTER:
            case CAMERA_PICTURE_TAKEN:
                break;
            case RECORD_STATUS_CHANGED:
                synchronized (mRecordCbLock) {
                    if (this.mRecordStatusCallback != null) {
                        this.mRecordStatusCallback.onRecordStatusChanged(arg1, mCameraId, this.mRecorderSource);
                    }
                }
                break;
            case CAMERA_STATUS_AE:
            case CAMERA_ERROR_OCCURED:
            case CAMERA_ADAS_NOTIFY:
            default:
                break;
            }
        }

        public void onRecorderVideoFrame(ParcelFileDescriptor fd, int dataType, int size) {
            synchronized (mVideoCallbackLock) {
                if (DEBUG) Log.i(TAG, "fd = " + fd + " ,dataType = " + dataType + ", size = " + size + ", preview length = " + mVideoData.length );

                //ParcelFileDescriptor fd = null;
                try {
                    //fd = ParcelFileDescriptor.fromFd(iFd);

                    switch(dataType) {
                    case DATA_TYPE_KEYPOINT:
                        if (this.mKeypointCallback != null) {
                            //if (mVideoData.length < size) {
                            //    mVideoData = new byte[size];
                            //}
                            // do not resue the buffer, app may use it in other thread.
                            mVideoData = new byte[size];
                            if (size == readAshmemBytes(fd, size, mVideoData)) {
                                this.mKeypointCallback.onKeypointFrame(mVideoData, dataType, size, mCameraId, this.mRecorderSource);
                            }
                        } else {
                            Log.w(TAG + mName, "mKeypointCallback is null");
                        }
                        break;
                    default:
                        if (this.mVideoCallback != null) {
                            //if (mVideoData.length < size) {
                            //    mVideoData = new byte[size];
                            //}
                            // do not resue the buffer, app may use it in other thread.
                            mVideoData = new byte[size];
                            if (size == readAshmemBytes(fd, size, mVideoData)) {
                                this.mVideoCallback.onVideoFrame(mVideoData, dataType, size, mCameraId, this.mRecorderSource);
                            } else {
                                Log.e(TAG, " readAshmemBytes fail ");
                            }
                        } else {
                            Log.w(TAG + mName, "mVideoCallback is null");
                        }
                        break;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e(TAG + mName, "Recorder Video Frame failed");
                } finally {
                    closeFd(fd);
                }
            }
        }

        public void onRecorderAudioFrame(ParcelFileDescriptor fd, int dataType, int size) {
            synchronized (mAudioCallbackLock) {
                if (DEBUG) Log.i(TAG, "fd = " + fd + " ,dataType = " + dataType + ", size = " + size + ", preview length = " + mAudioData.length );

                //ParcelFileDescriptor fd = null;
                try {
                    //fd = ParcelFileDescriptor.fromFd(iFd);

                    if (this.mAudioCallback != null) {
                        //if (mAudioData.length < size) {
                        //    mAudioData = new byte[size];
                        //}

                        // do not resue the buffer, app may use it in other thread.
                        mAudioData = new byte[size];
                        if (size == readAshmemBytes(fd, size, mAudioData)) {
                            this.mAudioCallback.onAudioFrame(mAudioData, dataType, size, mCameraId, this.mRecorderSource);
                        }
                    } else {
                        Log.w(TAG + mName, "mAudioCallback is null");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e(TAG + mName, "Recorder Audio Frame failed");
                } finally {
                    closeFd(fd);
                }
            }
        }

        private int readAshmemBytes(ParcelFileDescriptor fd, int size, byte[] data) {

            SmartPlatformMemoryFile client = null;
            int length = 0;
            try {
                client = new SmartPlatformMemoryFile(fd.getFileDescriptor(), size);
                if (client != null) {
                    length = client.readBytes(data, 0, 0, size);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                closeMemoryFile(client);
                client = null;
            }
            return length;
        }
        private void closeFd(ParcelFileDescriptor fd) {
            try {
                if(fd!=null) {
                    fd.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        private void closeMemoryFile(SmartPlatformMemoryFile client) {
            try {
                if(client!=null) {
                    client.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private String mName;
        private VideoCallback mVideoCallback;
        private AudioCallback mAudioCallback;
        private RecordStatusCallback mRecordStatusCallback;
        private KeypointCallback mKeypointCallback;
        private int mRecorderSource;
        private int mRecorderType;

        public void setInfo(String name,
                            VideoCallback videoCallback,
                            AudioCallback audioCallback,
                            RecordStatusCallback recordStatusCallback,
                            KeypointCallback keypointCallback) {
            mName = name;
            this.mVideoCallback = videoCallback;
            synchronized (mAudioCallbackLock) {
                this.mAudioCallback = audioCallback;
            }
            synchronized (mRecordCbLock) {
                this.mRecordStatusCallback = recordStatusCallback;
            }
            synchronized (mVideoCallbackLock) {
                this.mKeypointCallback = keypointCallback;
            }
        }

        public void setRecorderSource(int recordSource) {
            mRecorderSource = recordSource;
        }

        public void setRecorderType(int recordType) {
            mRecorderType = recordType;
        }

    };


    private CamPictureCallback mCamPictureCallback = null;
    private ShutterCallback mShutterCallback = null;



    private Size returnLargestSize(Size[] sizes) {
        Size largestSize = null;
        int area = 0;
        for (int j = 0; j < sizes.length; j++) {
            if (DEBUG) {
                Log.d(TAG, "support size " + sizes[j]) ;
            }

            if (sizes[j].getHeight() * sizes[j].getWidth() > area) {
                area = sizes[j].getHeight() * sizes[j].getWidth();
                largestSize = sizes[j];
            }
        }
        return largestSize;
    }
    public  int setPictureSurface(android.util.Size pictureSize) {
        synchronized (mLock) {
            int ret = setPictureSize(pictureSize);
            return ret;
        }
    }
    public  int clearPictureSurface(){
        synchronized (mLock) {
            stopTakePicture(mPictureTaken, true);
            return 0;
        }
    }

    public int setPictureSize(android.util.Size pictureSize) {
        synchronized (mLock) {
            Log.i(TAG,"setPictureSurface");

            int ret = 0;
            int stateValue = CameraSurfaceUsage.getSurfaceState(CameraSurfaceUsage.PICTURE_0);

            if(!checkState(stateValue)) {
                Log.i(TAG,"already in State, clear old Picture surface");
                stopTakePicture(mPictureTaken, true);
            }


            int pictureFormat;
            int pictureWidth, pictureHeight;

            if(mEnableRawSensorJpeg && !(mCurrentPara.isWatermarkEnable(CameraSurfaceUsage.PICTURE_0))){
                pictureFormat = ImageFormat.JPEG;
            } else {
                pictureFormat = ImageFormat.YV12;
            }

            if(pictureSize == null && mPictureSize == null) {
                CameraCharacteristics cameraCharacteristics = getCameraCharacteristics();
                if (cameraCharacteristics == null) {
                    Log.e(TAG,"cameraCharacteristics is null");
                    return -1;
				}
                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                int[] formats = map.getOutputFormats();

                for (int i = 0; i < formats.length; i++) {
                    if (formats[i] == pictureFormat) {
                        mPictureSize = returnLargestSize(map.getOutputSizes(formats[i]));
                        break;
                    }
                }

                if (mPictureSize == null) {
                    mPictureSize = new Size(1280, 720);
                    Log.i(TAG, "setPictureSurface set default size: " + mPictureSize);
                } else {
                    Log.i(TAG, "setPictureSurface size use largestsize" + mPictureSize + ", inFormat = " + pictureFormat);
                }

            } else if (pictureSize != null){
                mPictureSize = new Size(pictureSize.getWidth(), pictureSize.getHeight());

                Log.i(TAG, "setPictureSize size use appset" + pictureSize + ", inFormat = " + pictureFormat);
            } else {
                Log.i(TAG, "setPictureSize size use previous" + mPictureSize + ", inFormat = " + pictureFormat);
            }

            pictureWidth = mPictureSize.getWidth();
            pictureHeight = mPictureSize.getHeight();


            Surface mPictureTakenSurface;
            synchronized (mTakingPictureLock) {
                mPictureTaken = new ImageReaderEx();

                mPictureTaken.init(pictureWidth, pictureHeight, pictureFormat, "_" + mCameraId, mCameraId, false);

                mPictureTakenSurface = mPictureTaken.getSurface();
            }

            ret = setSurfaceDisplay(mPictureTakenSurface, CameraSurfaceUsage.PICTURE_0, 0);
            submitRequest(true);

            mSurfaceUsageArray.put(CameraSurfaceUsage.PICTURE_0, true);

            mState |= stateValue;
            setState(mState);

            return ret;
        }
    }

    /**
     * Triggers an asynchronous image capture.
     *
     * @param path
     *            the following JPEG pathname, or null that the pathname is
     *            auto-generate with timestamp
     * @param shutter
     *            the callback for image capture moment, or null
     * @param jpeg
     *            the callback for JPEG pathname, or null
     *
     */

    public int takePicture(String fileName, ShutterCallback shutter, CamPictureCallback jpeg) {
        return takePicture(fileName, shutter, jpeg, null);
    }

    public int takePicture(String fileName, ShutterCallback shutter,
                            CamPictureCallback jpeg, android.util.Size pictureSize) {

        Log.i(TAG,"takePicture");
        synchronized (mTakingPictureLock) {
            if(isTakingPicture) {
                Log.i(TAG, "already taking picture, please wait...");
                return -1;
            }
        }
        synchronized (mLock) {
            mShutterCallback = shutter;
            mCamPictureCallback = jpeg;
        }
        if(!mSurfaceUsageArray.get(CameraSurfaceUsage.PICTURE_0, false)) {
            int ret = setPictureSize(pictureSize);
            if(ret != 0) {
                Log.e(TAG,"can not takePicture, already in State");
                mCamPictureCallback.onPictureTaken(CamPictureCallback.PICTURE_TAKEN_FAIL, mCameraId, null);
                return -1;
            }
        } else {
            Log.i(TAG, "use default pictureSize or the size in setPictureSize");
        }

        if (!setPictureRequestConfig()) {
            Log.e(TAG,"setPictureRequestConfig fail!");
            return PictureSequenceSource.TAKE_PICTURE_FAIL;
        }
        synchronized (mRemoteDeviceLock) {
            try {
               if (mRemoteDevice == null) {
                  Log.e(TAG,"remote Device is null, can not takePicture !!! ");
                  return PictureSequenceSource.TAKE_PICTURE_FAIL;
               }
               mRemoteDevice.takePicture(fileName);
            } catch (RemoteException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        synchronized (mTakingPictureLock) {
            if(mPictureTaken != null){
                mPictureTaken.setCameraDeviceHandler(mEventHandler);
                mPictureTaken.setCamPictureCallback(jpeg);
                mPictureTaken.setOutImageFormat(ImageCallback.IMAGE_FORMAT_JPEG, ImageCallback.IMAGE_DATA_FILE);
                mPictureTaken.setOutImageName(fileName);
                mPictureTaken.setJpegHwEnc(false);
            }else{
                return -1;
            }
            mPictureTaken.start();
            isTakingPicture = true;
        }

        setTakePictureTimeoutMessage(jpeg);
        if(mShutterSoundEnable) {
            playSound(MediaActionSound.SHUTTER_CLICK);
        }

        return 0;
    }

    @PictureSequenceSource.TakePictureResult
    public int takePicture(List<String> filePath, android.util.Size pictureSize, @PictureSequenceSource.Format int source, int quality) {
        Log.i(TAG,"takePicture");
        int ret = PictureSequenceSource.TAKE_PICTURE_FAIL;

        if (PictureSequenceSource.AVM_ALL != source
            && PictureSequenceSource.AVM_ALL_EX != source
            && PictureSequenceSource.AVM_FRONT != source
            && PictureSequenceSource.AVM_LEFT != source
            && PictureSequenceSource.AVM_RIGHT != source
            && PictureSequenceSource.AVM_BACK != source) {
            Log.e(TAG,"can not capture wrong source:" + source);
            return PictureSequenceSource.TAKE_PICTURE_FAIL;
        }

        if (quality < 0 || quality > 100) {
            Log.e(TAG,"can not capture wrong quality:" + quality);
            return PictureSequenceSource.TAKE_PICTURE_FAIL;
        }

        synchronized (mRemoteDeviceLock) {
            if (mRemoteDevice == null) {
                Log.e(TAG,"remote Device is null, this device maybe release already !!! ");
                return PictureSequenceSource.TAKE_PICTURE_FAIL;
            }
            try {
               ret = mRemoteDevice.capture(filePath, pictureSize.getWidth(), pictureSize.getHeight(), source, quality);
               int pictureSurfaceId = PictureSequenceSource.getSurfaceUsage(source);
               boolean takePicSound = mPictureSoundEnableArray.get(pictureSurfaceId, true);
               if (takePicSound) {
                   playSound(MediaActionSound.SHUTTER_CLICK);
               }
               mPictureSoundEnableArray.delete(pictureSurfaceId);
               if (ret == 0)
                   return PictureSequenceSource.TAKE_PICTURE_SUCCESS;
               else
                   return PictureSequenceSource.TAKE_PICTURE_FAIL;
            } catch (RemoteException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        return ret;
    }
    private void stopTakePicture(ImageReaderEx pictureTaken, boolean release) {

        Log.i(TAG,"stopTakePicture, release: " + release);

        synchronized (mTakingPictureLock) {
            isTakingPicture = false;
        if (release && pictureTaken != null) {
            pictureTaken.stop();
            mPictureTaken = null;
            }
        }
        if (release) {
            mSurfaceUsageArray.put(CameraSurfaceUsage.PICTURE_0, false);
            setSurfaceDisplay(null, CameraSurfaceUsage.PICTURE_0, CameraDevice.TEMPLATE_PREVIEW);
            submitRequest(true);
            synchronized (mLock) {
                int stateValue = CameraSurfaceUsage.getSurfaceState(CameraSurfaceUsage.PICTURE_0);
                mState &= ~stateValue;
				setState(mState);
			}
        }

    }

    private void setTakePictureTimeoutMessage(CamPictureCallback callback){
        if(DEBUG){
            Log.i(TAG,"set takePicture timeout ");
        }
        Message m = mEventHandler.obtainMessage(EVENT_MSG_TAKE_PICTURE_TIMEOUT, callback);
        mEventHandler.sendMessageDelayed(m, mTakePictureTimeoutMs);
    }

    private void removeTakePictureTimeoutMessage(){
        if(DEBUG){
            Log.i(TAG,"remove takePicture timeout ");
        }
        mEventHandler.removeMessages(EVENT_MSG_TAKE_PICTURE_TIMEOUT);
    }

    private void takePictureFail(CamPictureCallback callback){
         new Thread() {
         public void run() {
             Log.i(TAG, "take Picture timeout");
             if(callback != null) {
                 callback.onPictureTaken(CamPictureCallback.PICTURE_TAKEN_FAIL, mCameraId, null);
             }
             synchronized (mTakingPictureLock) {
                 isTakingPicture = false;
             }
         }
        }.start();
    }

    /**
     * Registers a callback to be notified when using camera occure error
     */
    public void setErrorCallback(ErrorCallback callback){
         Log.i(TAG,"setErrorCallback");
         synchronized (mDeviceErrorLock) {
             mErrorCallback = callback;
         }
    }

    /**
     * Registers a callback to receive ae state .
     */
    public void setAeStatusCallback(AeStatusCallback callback) {
        synchronized (mAeStatusCallbackLock) {
            mAeStatusCallback = callback;
        }
    }

    public void queryAeBlock() {
        try {
            CameraMetadataNative requestMetadata= mCurrentPara.getRequestMetadata();
            mCurrentPara.updateMetadata(requestMetadata, CameraSurfaceUsage.DISPLAY_0);
            mCurrentPara.mapAeBlockMode(requestMetadata);
            synchronized (mRemoteDeviceLock) {
                mRemoteDevice.queryAeBlock(requestMetadata);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void updateWatermark(boolean enable, String watermark) {
        synchronized (mRemoteDeviceLock) {
            if (mRemoteDevice == null) {
                Log.e(TAG,"remote Device is null, this device maybe release already !!! ");
                return;
            }
            try {
                mRemoteDevice.updateWatermark(enable, watermark);
            } catch (RemoteException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
    private void handleResultMetadata() {
        synchronized (mAeStatusCallbackLock) {
            if(mAeStatusCallback != null) {
                int [ ] value = mCurrentPara.getResultAeBlockValue();
                    mAeStatusCallback.onStatusCallbackArray(value, SpmCamParameters.AeBlockValueSize);
                if(DEBUG) Log.d(TAG,"AeStatusCallback:  " + value);
            }
        }
    }



    protected Surface getADASSurface() throws RemoteException {

        Log.i(TAG,"getADASSurface");

        Surface input = null;

        synchronized (mRemoteDeviceLock) {
            if (mRemoteDevice == null) {
                Log.e(TAG,"remote Device is null, this device maybe release already !!! ");
                return null;
            }
            try {
                input = mRemoteDevice.getADASSurface();
            } catch (RemoteException e) {
                e.printStackTrace();
                throw e;
            }
        }
        return input;
    }

    /**
     * Registers a callback to be notified about the ADAS info detected in
     * preview frame.
     *
     * @param callback
     *            the callback to notify
     */
    public void setADASCallback(ADASCallback callback) {
        synchronized (mADASCallbackLock) {
            mADASCallback = callback;
        }
    }

    public int startADAS() {
          synchronized (mLock) {
              Log.i(TAG,"startADAS");
              int adasState = CameraSurfaceUsage.getSurfaceState(CameraSurfaceUsage.ADAS_0);
              if(!checkState(adasState)) {
                  Log.e(TAG,"can not startADAS");
                  return -1;
              }
              try {
                  synchronized (mRemoteDeviceLock) {
                      if (mRemoteDevice == null) {
                          Log.e(TAG,"remote Device is null, this device maybe release already !!! ");
                          return -1;
                      }
                      setSurfaceDisplay(getADASSurface(), CameraSurfaceUsage.ADAS_0, CameraDevice.TEMPLATE_PREVIEW);
                      mRemoteDevice.startADAS();
                      mState |= adasState;
                      setState(mState);
                  }
              } catch (RemoteException e) {
                  // TODO Auto-generated catch block
                  e.printStackTrace();
              }
              return 0;
         }
      }


    public int stopADAS() {
        synchronized (mLock) {
            Log.i(TAG,"stopADAS");
            try {
                int adasState = CameraSurfaceUsage.getSurfaceState(CameraSurfaceUsage.ADAS_0);

                synchronized (mRemoteDeviceLock) {
                    if (mRemoteDevice == null) {
                        Log.e(TAG,"remote Device is null, this device maybe release already !!! ");
                        return -1;
                    }
                    setSurfaceDisplay(null, CameraSurfaceUsage.ADAS_0, CameraDevice.TEMPLATE_PREVIEW);
                    mRemoteDevice.stopADAS();
                    mState &= ~adasState;
                    setState(mState);
                }
            } catch (RemoteException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            return 0;
        }
    }

    public void enableShutterSound(boolean shutter) {
        Log.i(TAG, "enableShutterSound:" + shutter);
        mShutterSoundEnable = shutter;
    }

    public void enableShutterSound(boolean shutter, @PictureSequenceSource.Format int pictureSource) {
        Log.i(TAG, "enableRecordSound:" + shutter + ", pictureSource : " + pictureSource);
        int pictureSurfaceId = PictureSequenceSource.getSurfaceUsage(pictureSource);
        mPictureSoundEnableArray.put(pictureSurfaceId, shutter);
    }

    public void enableRecordSound(boolean enable, @RecordSource.Format int recordSource) {
        Log.i(TAG, "enableRecordSound:" + enable + ", recordSource : " + recordSource);
        int recordSurfaceId = RecordSource.getSurfaceUsage(recordSource);
        mRecordSoundEnableArray.put(recordSurfaceId, enable);
    }

    private void initSound() {
        mSound = new MediaActionSound();
        mSound.load(MediaActionSound.START_VIDEO_RECORDING);
        mSound.load(MediaActionSound.STOP_VIDEO_RECORDING);
        mSound.load(MediaActionSound.FOCUS_COMPLETE);
        mSound.load(MediaActionSound.SHUTTER_CLICK);
    }

    private void releaseSound() {
        if(mSound != null) {
            mSound.release();
            mSound = null;
        }
    }
    private synchronized void playSound(int action) {
        if (mSound == null) {
            Log.e(TAG, "[play] mSound is null");
            return;
        }
        switch (action) {
            case MediaActionSound.FOCUS_COMPLETE:
                mSound.play(MediaActionSound.FOCUS_COMPLETE);
                break;
            case MediaActionSound.START_VIDEO_RECORDING:
                mSound.play(MediaActionSound.START_VIDEO_RECORDING);
                break;
            case MediaActionSound.STOP_VIDEO_RECORDING:
                mSound.play(MediaActionSound.STOP_VIDEO_RECORDING);
                break;
            case MediaActionSound.SHUTTER_CLICK:
                mSound.play(MediaActionSound.SHUTTER_CLICK);
                break;
            default:
                Log.w(TAG, "Unrecognized action:" + action);
        }
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        if (mPreSetPictureSurface) {
            if (mSurfaceUsageArray.get(CameraSurfaceUsage.PICTURE_0, false)) {
                Log.i(TAG,"finalize free picture surface");
                stopTakePicture(mPictureTaken, true);
            }
        }
        Log.i(TAG, "finalize stop record list: " + mRecordSourceList.size());
        for (int i = mRecordSourceList.size() - 1; i >= 0; i --) {
            int recordSource = (int)mRecordSourceList.get(i);
            stopRecord(recordSource);
        }
        synchronized (mRemoteDeviceLock) {
            if(mRemoteDevice != null) {
                mRemoteDevice.removeListener(mSpmCamDeviceListener);
                mRemoteDevice.release();
            }
        }
    }

}
