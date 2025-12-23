package com.mediatek.smartplatform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import android.annotation.CallbackExecutor;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;

//import android.hardware.camera2.utils.BinderHolder;
import android.hardware.Camera;
import android.hardware.CameraStatus;
import android.hardware.ICameraService;
import android.hardware.ICameraServiceListener;

import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.impl.CameraMetadataNative;

import android.graphics.Point;

import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.ServiceSpecificException;
import android.os.SystemProperties;

import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.Size;
import android.util.SparseArray;

import android.view.Display;
import android.view.Surface;
import android.view.IWindowManager;

import com.mediatek.smartplatform.ICarCamDeviceUser;


/**
 * <p>
 * A manager for detecting, characterizing, and connecting to smartplatform service
 * that run in smartplatformserver and register in {@link ServiceManager}. This call
 * is used to manager car related devices and events, such as cameras in car,
 * collision detection, reverse gear event and so on.
 * </p>
 *
 * <p>
 * You can get the singleton instance of this class by calling {@link #get()}
 * </p>
 *
 *
 */
public final class SmartPlatformManager {

    private static final String SMARTPLATFORM_SERVICE_BINDER_NAME = "mtk.smartplatform";
    private static final String CAMERA_SERVICE_BINDER_NAME = "media.camera";
    private static final String WINDOW_SERVICE_BINDER_NAME = "window";
    private static final String TAG = "SmartPlatformManager";
    private static final boolean DEBUG = Build.IS_ENG || SystemProperties.getBoolean("persist.vendor.log.spmsdk", false);

    private IpodProxy mIpodProxy = null;
    private CollisionProxy mCollisionProxy = null;
    private CarEventProxy mCarEventProxy = null;

    private Object mLock = new Object();
    private Object mEventListenerLock = new Object();
    private Object mArrayMapLock = new Object();
    private Object mCameraServiceLock = new Object();
    private Object mServiceDiedLock = new Object();
    private Object mCameraDeviceLock = new Object();
    private Object mAvailableCallbackLock = new Object();
    private ISmartPlatformService mSmartPlatformService = null;
    private ICameraService mCameraService = null;
    private IWindowManager mWindowManager = null;
    public static final boolean sCameraServiceDisabled =
        SystemProperties.getBoolean("config.disable_cameraservice", false);

    private final ArraySet<ServiceDeathCallback> mServiceDeathCallbacks = new ArraySet<ServiceDeathCallback>();
    private final ArraySet<CameraAvailableCallback> mAvailableCallbacks = new ArraySet<CameraAvailableCallback>();

    //private final SparseArray<SpmCameraDevice> mCameraDevices = new SparseArray<>();
    private final ArrayMap<String, SpmCameraDevice> mCameraDevices = new ArrayMap<String, SpmCameraDevice>();

    private final ArrayMap<SpmCameraDevice, CameraDeviceRefCount> mCameraDevicesRefMap =
        new ArrayMap<SpmCameraDevice, CameraDeviceRefCount>();

    // Camera ID -> Status map
    private final ArrayMap<String, Integer> mDeviceStatus = new ArrayMap<String, Integer>();

    public static final int EVENT_TEST = 100;

    //context is null, so do not use openCamera.
    //private final CameraManager mCameraManager = new CameraManager(null);

    /**
     * Interface definition for a callback to be invoked when SmartPlatform Service is dead.
     */
    public interface ServiceDeathCallback {

        /**
              * Called when carcorder service is dead
              *
              * @param arg1 reserved
              * @param arg2 Service name
              */
        void onDeath(int arg1, String arg2);
    };

    /**
     * Interface definition for a callback to be invoked when a camera was added
     * or removed.
     */
    public interface CameraAvailableCallback {
        public static final int STATUS_CAMERA_REMOVED = 0;
        public static final int STATUS_CAMERA_ADDED = 1;

        /**
         * Called when a camera was added or removed.
         *
         * @param cameraid
         *            the camera id
         * @param status
         *            camera status
         *            <ul>
         *            <li>{@link #STATUS_CAMERA_ADDED}
         *            <li>{@link #STATUS_CAMERA_REMOVED}
         *            </ul>
         * @param extra
         *            an extra code, specific to the error type
         */
        void onAvailable(String cameraid, int status);
    }


    // Singleton instance
    private static final SmartPlatformManager gSmartPlatformManager = new SmartPlatformManager();

    private ISmartPlatformEventListener mEventListener = new ISmartPlatformEventListener.Stub() {

        //public static final int EVENT_WEBCAM_HOTPLUG = 0;
        // Should keep the same definition with the one in carReverHandler.h
        public static final int EVENT_CAR_REVERSE = 1;
        public static final int EVENT_COLLISION = 2;
        public static final int EVENT_CAR_ENGINE_CHANGED = 3;
        public static final int EVENT_CAR_CUSTOMIZATION1 = 1000;
        public static final int EVENT_CAR_CUSTOMIZATION2 = 2000;

        @Override
        public void onEventChanged(int event, int arg1, int arg2)
        throws RemoteException {
            if (DEBUG) Log.i(TAG,"onEventChanged: event" +event + "  arg1 = " + arg1 + "   arg2 =" + arg2 );
            synchronized (mEventListenerLock) {
                switch (event) {
                case EVENT_TEST:
                    break;
                case EVENT_COLLISION:
                    if (mCollisionProxy != null) {
                        mCollisionProxy.onCollison(arg1, arg2);
                    }
                    break;
                case EVENT_CAR_REVERSE:
                case EVENT_CAR_ENGINE_CHANGED:
                case EVENT_CAR_CUSTOMIZATION1:
                case EVENT_CAR_CUSTOMIZATION2:
                    if (mCarEventProxy != null) {
                        mCarEventProxy.handEvent(event, arg1, arg2);
                    }
                    break;
                default:
                    break;
                }
            }
        }
    };

    private ICameraServiceListener mCameraListener = new ICameraServiceListener.Stub() {

        /**
        * Callback from camera service notifying the process about camera availability changes
        */
        @Override
        public void onStatusChanged(int status, String cameraId) throws RemoteException {
            synchronized(mCameraServiceLock) {
                onStatusChangedLocked(status, cameraId);
            }
        }

        @Override
        public void onPhysicalCameraStatusChanged(int status, String cameraId, String physicalCameraId) throws RemoteException {
            synchronized (mCameraServiceLock) {
                //onPhysicalCameraStatusChanged();
            }
        }

        @Override
        public void onTorchStatusChanged(int status, String cameraId) throws RemoteException {
            synchronized (mCameraServiceLock) {
                //onTorchStatusChanged(status, cameraId);
            }
        }

        @Override
        public void onTorchStrengthLevelChanged(String cameraId, int newTorchStrength) throws RemoteException {
            synchronized (mCameraServiceLock) {
                //onTorchStrengthLevelChanged(status, cameraId);
            }
        }

        @Override
        public void onCameraAccessPrioritiesChanged() throws RemoteException {
            synchronized (mCameraServiceLock) {
                //onCameraAccessPrioritiesChanged();
            }
        }

        @Override
        public void onCameraOpened(String cameraId, String clientPackageId) throws RemoteException {
            synchronized (mCameraServiceLock) {
                //onCameraOpened();
            }
        }

        @Override
        public void onCameraClosed(String cameraId) throws RemoteException {
            synchronized (mCameraServiceLock) {
                //onCameraClosed();
            }
        }
    };

    private void onStatusChangedLocked(int status, String id) {
        if (DEBUG) {
            Log.v(TAG,
                    String.format("Camera id %s has status changed to 0x%x", id, status));
        }

        if (!validStatus(status)) {
            Log.e(TAG, String.format("Ignoring invalid device %s status 0x%x", id,
                            status));
            return;
        }

        Integer oldStatus = mDeviceStatus.put(id, status);
		Log.i(TAG,"onStatusChangedLocked mDeviceStatus.size:" + mDeviceStatus.size());

        if (oldStatus != null && oldStatus == status) {
            if (DEBUG) {
                Log.v(TAG, String.format(
                    "Device status changed to 0x%x, which is what it already was",
                    status));
            }
            return;
        }

        Log.i(TAG, String.format("Camera id %s: oldStatus 0x%x changed to status 0x%x", id, oldStatus, status));

        synchronized (mAvailableCallbackLock) {
            for (CameraAvailableCallback callback : mAvailableCallbacks) {
                if (callback != null) {
                    callback.onAvailable(id, status);
                }
            }
        }
    } // onStatusChangedLocked

    private boolean validStatus(int status) {
        switch (status) {
            case ICameraServiceListener.STATUS_NOT_PRESENT:
            case ICameraServiceListener.STATUS_PRESENT:
            case ICameraServiceListener.STATUS_ENUMERATING:
            case ICameraServiceListener.STATUS_NOT_AVAILABLE:
                return true;
            default:
                return false;
        }
    }


    class DeathNotifer implements IBinder.DeathRecipient {
        private String mServiceName;

        DeathNotifer(String name) {
            mServiceName = name;
        }

        @Override
        public void binderDied() {
            Log.e(TAG, mServiceName + " died " );

            if (SMARTPLATFORM_SERVICE_BINDER_NAME.equals(mServiceName))
                mSmartPlatformService = null;

            if (CAMERA_SERVICE_BINDER_NAME.equals(mServiceName))
                mCameraService = null;

            if (WINDOW_SERVICE_BINDER_NAME.equals(mWindowManager))
                mWindowManager = null;

            synchronized (mServiceDiedLock) {
                for (ServiceDeathCallback callback : mServiceDeathCallbacks) {
                    if (callback != null) {
                        callback.onDeath(0, mServiceName);
                    }
                }
            }
        }


    }

    private final class CameraDeviceRefCount {
        public int refCout;
        CameraDeviceRefCount(int count) {
            refCout = count;
        }
    }

    private HandlerThread mEventThread = null;
    private SpmEventHandler mEventHandler = null;

    static final int EVENT_DEVICE_DISCONNECTED = 0x001;

    private class SpmEventHandler extends Handler {
        private SmartPlatformManager mManager;

        public SpmEventHandler(SmartPlatformManager manager, Looper looper) {
            super(looper);
            mManager = manager;
        }

        @Override
        public void handleMessage(Message msg) {
            if (DEBUG) Log.i(TAG, "handleMessage: " + msg.what);
            switch(msg.what) {
            case EVENT_DEVICE_DISCONNECTED:
                String cameraId = (String)msg.obj;
                Log.w(TAG, "Device Disconnected, no ErrorCallbck, close ; " + cameraId);
                mManager.closeCameraDevice(cameraId);
                return;
            default:
                Log.e(TAG,"Unknow message type " + msg.what);
                return;
            }
        }

    }

    // Singleton, don't allow construction
    private SmartPlatformManager() {
        getSmartPlatformService();
        getCameraService();
    }

    /**
     * @return the SmartPlatformManager singleton instance
     */
    public static SmartPlatformManager get() {
        return gSmartPlatformManager;
    }

    private ISmartPlatformService getSmartPlatformService() {
        if (mSmartPlatformService == null) {
            Log.i(TAG,"mSmartPlatformService is null, try to get it");

            IBinder smartPlatformServiceBinder = ServiceManager
                                                 .getService(SMARTPLATFORM_SERVICE_BINDER_NAME);

            if (smartPlatformServiceBinder == null) {
                Log.e(TAG, SMARTPLATFORM_SERVICE_BINDER_NAME + " is null");
                return null;
            }

            try {
                smartPlatformServiceBinder.linkToDeath(new DeathNotifer(SMARTPLATFORM_SERVICE_BINDER_NAME), 0);
            } catch (RemoteException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            mSmartPlatformService = ISmartPlatformService.Stub
                                    .asInterface(smartPlatformServiceBinder);

            try {
                mSmartPlatformService.addListener(mEventListener);
            } catch (RemoteException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        return mSmartPlatformService;
    }

    private IWindowManager getWindowManagerService() {
        if (mWindowManager == null) {
            Log.i(TAG,"mWindowManager is null, try to get it");

            IBinder windowServiceBinder = ServiceManager
                                          .getService(WINDOW_SERVICE_BINDER_NAME);

            if (windowServiceBinder == null) {
                Log.e(TAG, WINDOW_SERVICE_BINDER_NAME + " is null");
                return null;
            }

            try {
                windowServiceBinder.linkToDeath(new DeathNotifer(WINDOW_SERVICE_BINDER_NAME), 0);
            } catch (RemoteException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            mWindowManager = IWindowManager.Stub
                             .asInterface(windowServiceBinder);


        }

        return mWindowManager;
    }

    /**
     * Return a best-effort ICameraService.
     *
     * <p>This will be null if the camera service is not currently available. If the camera
     * service has died since the last use of the camera service, will try to reconnect to the
     * service.</p>
     */
    private ICameraService getCameraService() {
        synchronized(mLock) {
            connectCameraServiceLocked();

            if (mCameraService == null && !sCameraServiceDisabled) {
                Log.e(TAG, "Camera service is unavailable");
            }

            return mCameraService;
        }
    }


    /**
     * Connect to the camera service if it's available, and set up listeners.
     * If the service is already connected, do nothing.
     *
     * <p>Sets mCameraService to a valid pointer or null if the connection does not succeed.</p>
     */
    private void connectCameraServiceLocked() {
        // Only reconnect if necessary
        if (mCameraService != null || sCameraServiceDisabled) return;

        Log.i(TAG, "Connecting to camera service");

        IBinder cameraServiceBinder = ServiceManager.getService(CAMERA_SERVICE_BINDER_NAME);

        if (cameraServiceBinder == null) {
            Log.i(TAG, "camera service null");
            // Camera service is now down, leave mCameraService as null
            return;
        }

        try {
            cameraServiceBinder.linkToDeath(new DeathNotifer(CAMERA_SERVICE_BINDER_NAME), /*flags*/ 0);
        } catch (RemoteException e) {
            // Camera service is now down, leave mCameraService as null
            Log.i(TAG, "camera service linkToDeath fail");
            return;
        }

        ICameraService cameraService = ICameraService.Stub.asInterface(cameraServiceBinder);

        try {
            CameraMetadataNative.setupGlobalVendorTagDescriptor();
        } catch (ServiceSpecificException e) {
            Log.i(TAG, " set global vendor tag failed");
            return;
        }

        try {
            if (getSmartPlatformService() == null) {
                Log.e(TAG,"Smartplatform Service is not alive");
                return;
            }

            //CameraStatus[] cameraStatuses = cameraService.addListener(mCameraListener);
            CameraStatus[] cameraStatuses = getSmartPlatformService().addCameraListener(mCameraListener);
            if(cameraStatuses != null){
                for (CameraStatus c : cameraStatuses) {
                    onStatusChangedLocked(c.status, c.cameraId);
                }
            }else{
                Log.i(TAG, "camera status null");
            }
            mCameraService = cameraService;
        } catch(ServiceSpecificException e) {
            // Unexpected failure
            throw new IllegalStateException("Failed to register a camera service listener", e);
        } catch (RemoteException e) {
            // Camera service is now down, leave mCameraService as null
            Log.w(TAG, "Connecting to camera service error occurred");
        }
    }



    /**
      * should check SmartPlatform and Camera service are alive or not . if these service are not
      * alive ,do not call any funtion of SmartPlatformManager.
      *
      * @return if service(mtk.spm) is alive,return true,otherwise false
      */
    public boolean isServiceAlive() {
        return getSmartPlatformService() != null && getCameraService() != null;
    }

    public boolean isSmartPlatformServiceAlive() {
        return getSmartPlatformService() != null;
    }

    private Size getDisplaySize() {
        Size ret = new Size(0, 0);

        try {
            IWindowManager windowManager = getWindowManagerService();
            if (windowManager == null) {
                Log.e(TAG, "windowManager is null!");
                return null;
            }
            Point initialSize = new Point();

            windowManager.getInitialDisplaySize(Display.DEFAULT_DISPLAY, initialSize);

            int width = initialSize.x;
            int height = initialSize.y;
            int temp;
            if (height > width) {
                temp = height;
                height = width;
                width = temp;
            }

            ret = new Size(width, height);
        } catch (Exception e) {
            Log.e(TAG, "getDisplaySize Failed. " + e.toString());
        }

        return ret;
    }

    @NonNull
    public CameraCharacteristics getCameraCharacteristics(@NonNull String cameraId)
    throws CameraAccessException {
        CameraCharacteristics characteristics = null;

        synchronized (mLock) {

            try {
                if (getSmartPlatformService() == null) {
                    Log.e(TAG,"Smartplatform Service is not alive");
                    return null;
                }

                CameraMetadataNative info = getSmartPlatformService().getCameraCharacteristics(cameraId);
                if (info == null) {
                    Log.e(TAG, "Failed to getCameraCharacteristics camera Id " + cameraId + " to integer");
                    return null;
                }
                Size displaySize = getDisplaySize();

                try {
                    info.setCameraId(Integer.parseInt(cameraId));
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Failed to parse camera Id " + cameraId + " to integer");
                }
                info.setDisplaySize(displaySize);

                characteristics = new CameraCharacteristics(info);

            } catch (ServiceSpecificException e) {

            } catch (RemoteException e) {
                // Camera service died - act as if the camera was disconnected
                throw new CameraAccessException(CameraAccessException.CAMERA_DISCONNECTED,
                                                "Camera service is currently unavailable", e);
            }
        }
        return characteristics;
    }

    /**
        * Returns the number of available cameras. Since the CVBS or USB camera
        * device can be removed, the return value will change when camera be added
        * or removed.
        *
        * @return the number of available cameras.
        */
    public int getNumberOfCameras() {
        String[] ids = getCameraIdList();
        if(ids != null) {
            return ids.length;
        } else {
            return -1;
        }
    }

    /**
         * get avm camera id
         * return camera id
	  */
    public String getAvmCameraId() {
         if (getSmartPlatformService() == null) {
             Log.e(TAG,"Smartplatform Service is not alive");
             return null;
         }
         try{
             synchronized(mLock) {
                 connectCameraServiceLocked();
             }
             String cameraId = getSmartPlatformService().getAvmCameraId();
             Log.i(TAG,"get avm camera id: " + cameraId);
             return cameraId;
         }  catch (RemoteException e) {
             e.printStackTrace();
             return null;
         }
    }

    /**
         * Get a list of all camera IDs.
         */
    public String[] getCameraIdList() {
        String[] cameraIds = null;
        synchronized(mLock) {
            // Try to make sure we have an up-to-date list of camera devices.
            connectCameraServiceLocked();

            int idCount = 0;
            cameraIds = new String[mDeviceStatus.size()];

            for (int i = 0; i < mDeviceStatus.size(); i++) {
                cameraIds[idCount] = mDeviceStatus.keyAt(i);
                idCount++;
            }
        }

        // The sort logic must match the logic in
        // libcameraservice/common/CameraProviderManager.cpp::getAPI1CompatibleCameraDeviceIds
        Arrays.sort(cameraIds, new Comparator<String>() {
                @Override
                public int compare(String s1, String s2) {
                    int s1Int = 0, s2Int = 0;
                    try {
                        s1Int = Integer.parseInt(s1);
                    } catch (NumberFormatException e) {
                        s1Int = -1;
                    }

                    try {
                        s2Int = Integer.parseInt(s2);
                    } catch (NumberFormatException e) {
                        s2Int = -1;
                    }

                    // Uint device IDs first
                    if (s1Int >= 0 && s2Int >= 0) {
                        return s1Int - s2Int;
                    } else if (s1Int >= 0) {
                        return -1;
                    } else if (s2Int >= 0) {
                        return 1;
                    } else {
                        // Simple string compare if both id are not uint
                        return s1.compareTo(s2);
                    }
                }});
        return cameraIds;
    }

    public int getCameraStatus(String cameraId) {
        // Try to make sure we have an up-to-date list of camera devices.
        connectCameraServiceLocked();
        Integer ret = mDeviceStatus.get(cameraId);
        if(ret == null)
            return ICameraServiceListener.STATUS_UNKNOWN;
        else
            return ret;
    }


    /**
     * Register a callback to be invoked when smart platform service is dead.
     *
     * @param callback
     *            the callback that will be run
     */
    public void addServiceDeathCallback(ServiceDeathCallback callback) {
        Log.i(TAG,"add Service Death Callback");

        synchronized (mServiceDiedLock) {
            if (callback != null) {
                mServiceDeathCallbacks.add(callback);
            }
        }
    }

    /**
     * Unregister the installed callback
     *
     * @param callback
     *            the callback that will no longer be invoked
     *
     */
    public void removeServiceDeathCallback(ServiceDeathCallback callback) {
        Log.i(TAG,"remove Service Death Callback");

        synchronized(mServiceDiedLock) {
            if (callback != null) {
                mServiceDeathCallbacks.remove(callback);
            }
        }
    }

    /**
     * Register a callback to be invoked when a camera was added or removed.
     *
     * @param callback
     *            the callback that will be run
     */
    public void addCameraAvailableCallback(
            final CameraAvailableCallback callback) {
            Log.i(TAG, "cyt, addCameraAvailableCallback: " + callback);
        synchronized (mAvailableCallbackLock) {
            if (callback != null) {
                mAvailableCallbacks.add(callback);

                for (int i = 0; i < mDeviceStatus.size(); i++) {
                    callback.onAvailable(mDeviceStatus.keyAt(i), mDeviceStatus.valueAt(i));
                }
                Log.i(TAG, "camera status: " + mDeviceStatus.toString());
            }
        }
    }

    /**
     * Unregister the camera available callback
     *
     * @param callback
     *            the callback that will no longer be invoked
     *
     */
    public void removeCameraAvailableCallback(
            final CameraAvailableCallback callback) {
        synchronized (mAvailableCallbackLock) {
            mAvailableCallbacks.remove(callback);
        }
    }

    protected int notifyCameraDeviceError(int error, SpmCameraDevice cameraDevice) {
        Log.i(TAG, "Camera [" + cameraDevice.getCameraId() + "] Error: " + error);
        int ret = 0;
        synchronized (mServiceDiedLock) {
            for (ServiceDeathCallback callback : mServiceDeathCallbacks) {
                if (callback != null) {
                    callback.onDeath(0, "SpmCameraDevice");
                    ret ++;
                }
            }
        }
        return ret;
    }

    /**
     * Helper for openning a connection to a camera with the given ID.
     *
     * @param cameraId
     *            The unique identifier of the camera device to open
     * @return A handle to the newly-created camera device.
     */
    public SpmCameraDevice openCameraDevice(@NonNull String cameraIdStr) {
        synchronized(mCameraDeviceLock) {
            if (DEBUG) {
                Log.d(TAG, "openCameraDevice cameraId : " + cameraIdStr, new Throwable("here"));
            } else {
                Log.i(TAG, "openCameraDevice cameraId : " + cameraIdStr);
            }

            if(mEventHandler == null){
                mEventThread = new HandlerThread(TAG + "SmartPlatformManager");
                mEventThread.start();
                mEventHandler= new SpmEventHandler(this, mEventThread.getLooper());
            }

            int cameraId = Integer.parseInt(cameraIdStr);
            int  mNumberOfCameras = getNumberOfCameras();
            SpmCameraDevice device;
            SpmCameraDeviceImpl deviceImpl;
            CameraDeviceRefCount crc;

            if (!isServiceAlive()) {
                Log.e(TAG,"Camera or Smartplatform Service is not alive");
                return null;
            }
            if (mCameraDevices.indexOfKey(cameraIdStr) < 0) {
                Log.i(TAG, "new a SpmCameraDevice");
                deviceImpl = new SpmCameraDeviceImpl(gSmartPlatformManager, mEventHandler, cameraIdStr);
                try {
                    ICarCamDeviceUser remoteDevice = getSmartPlatformService().connectDevice(cameraIdStr);
                    deviceImpl.setRemoteDevice(remoteDevice);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                int ret = deviceImpl.openCamera();
                if (ret != 0) {
                    Log.e(TAG,"Camera open failed.");
                    device = null;
                    return device;
                }
                device = deviceImpl;
                crc = new CameraDeviceRefCount(0);
                synchronized (mArrayMapLock) {
                    mCameraDevices.put(cameraIdStr, device);
                    mCameraDevicesRefMap.put(device, crc);
                }
            }
            device = mCameraDevices.get(cameraIdStr);
            crc = mCameraDevicesRefMap.get(device);
            crc.refCout += 1;
            if (DEBUG) {
                Log.d(TAG, "cameraId : " + cameraIdStr  + "  refCount : " + crc.refCout);
            }
            return device;
        }
    }

    /**
     * Helper for openning a connection to a camera with the given ID.
     *
     * @param cameraId
     *            The unique identifier of the camera device to open
     * @param deviceType
     *            0: normal camera, 1: avm camera
     * @return A handle to the newly-created camera device.
     */
    public SpmCameraDevice openAvmCameraDevice() {
        synchronized(mCameraDeviceLock) {
            String cameraIdStr = getAvmCameraId();
            if (DEBUG) {
                Log.d(TAG, "openAvmCameraDevice cameraId : " + cameraIdStr, new Throwable("here"));
            } else {
                Log.i(TAG, "openAvmCameraDevice cameraId : " + cameraIdStr);
            }
            if (cameraIdStr == null) {
                Log.e(TAG,"can not get avm cameraId!");
                return null;
            }

            if(mEventHandler == null){
                mEventThread = new HandlerThread(TAG + "SmartPlatformManager");
                mEventThread.start();
                mEventHandler= new SpmEventHandler(this, mEventThread.getLooper());
            }

            int cameraId = Integer.parseInt(cameraIdStr);
            int  mNumberOfCameras = getNumberOfCameras();
            SpmCameraDevice device;
            SpmCameraDeviceImpl deviceImpl;
            CameraDeviceRefCount crc;

            if (getSmartPlatformService() == null) {
                Log.e(TAG,"Smartplatform Service is not alive");
                return null;
            }
            if (mCameraDevices.indexOfKey(cameraIdStr) < 0) {
                Log.i(TAG, "new a SpmCameraDevice");
                deviceImpl = new SpmCameraDeviceImpl(gSmartPlatformManager, mEventHandler, cameraIdStr);
                try {
                    ICarCamDeviceUser remoteDevice;
                    remoteDevice = getSmartPlatformService().connectAvmDevice(cameraIdStr);
                    if(remoteDevice == null){
                        return null;
                    }
                    deviceImpl.setRemoteDevice(remoteDevice);
                    deviceImpl.setAvmDeviceType();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                int ret = deviceImpl.openCamera();
                if (ret != 0) {
                    Log.e(TAG,"Camera open failed.");
                    device = null;
                    return device;
                }
                device = deviceImpl;
                crc = new CameraDeviceRefCount(0);
                synchronized (mArrayMapLock) {
                    mCameraDevices.put(cameraIdStr, device);
                    mCameraDevicesRefMap.put(device, crc);
                }
            }
            device = mCameraDevices.get(cameraIdStr);
            crc = mCameraDevicesRefMap.get(device);
            crc.refCout += 1;
            if (DEBUG) {
                Log.d(TAG, "cameraId : " + cameraIdStr  + "  refCount : " + crc.refCout);
            }
            return device;
        }
    }

    /**
     * Disconnect camera device with the given camera.
     * if ref is not 0, will not release camera.
     * @param cameraId
     *            The opened camera id
     */
    public void closeCameraDevice(SpmCameraDevice camera) {
        synchronized(mCameraDeviceLock) {
            if (DEBUG) {
                Log.d(TAG, "close SpmCameraDevice camera : " + camera, new Throwable("here"));
            } else {
                Log.i(TAG, "close SpmCameraDevice camera : " + camera);
            }

            if(!mCameraDevices.containsValue(camera)) {
                Log.w(TAG,"Already closed !!");
                return;
            }
            CameraDeviceRefCount crc = mCameraDevicesRefMap.get(camera);
            crc.refCout -= 1;
            String cameraId = mCameraDevices.keyAt(mCameraDevices.indexOfValue(camera));
            if(DEBUG) {
                Log.d(TAG,"camera: " + cameraId + " refCout:  " + crc.refCout);
            }
            if(crc.refCout == 0) {
                Log.i(TAG,"release SpmCameraDevice ");

                camera.release();
                synchronized (mArrayMapLock) {
                    mCameraDevices.removeAt(mCameraDevices.indexOfValue(camera));
                    mCameraDevicesRefMap.remove(camera);
                }
                camera = null;

                try {
                    if (isSmartPlatformServiceAlive()) {
                        getSmartPlatformService().disconnectDevice(cameraId);
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }

                if(mCameraDevices.size() == 0) {
                    if(mEventThread != null) {
                        mEventThread.quitSafely();
                        mEventHandler = null;
                        mEventThread = null;
                    }
                }

            }
        }
    }


    public void closeCameraDevice(String cameraId) {
        synchronized(mCameraDeviceLock) {
            if(mCameraDevices.indexOfKey(cameraId) < 0) {
                Log.w(TAG,"Already closed !!");
                return;
            }

            if (DEBUG) {
                Log.d(TAG, "close SpmCameraDevice cameraId : " + cameraId, new Throwable("here"));
            } else {
                Log.i(TAG, "close SpmCameraDevice cameraId : " + cameraId);
            }

            SpmCameraDevice camera = mCameraDevices.get(cameraId);
            camera.release();
            synchronized (mArrayMapLock) {
                mCameraDevices.remove(cameraId);
                mCameraDevicesRefMap.remove(camera);
            }
            camera = null;
            try {
                if (isSmartPlatformServiceAlive()) {
                    getSmartPlatformService().disconnectDevice(cameraId);
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }

            if(mCameraDevices.size() == 0) {
                if(mEventThread != null) {
                    mEventThread.quitSafely();
                    mEventHandler = null;
                    mEventThread = null;
                }
            }
        }

    }

    public void closeAvmCameraDevice() {
        synchronized(mCameraDeviceLock) {
          String cameraId = getAvmCameraId();
          if(mCameraDevices.indexOfKey(cameraId) < 0) {
              Log.w(TAG,"Already closed !!");
              return;
          }

          if (DEBUG) {
              Log.d(TAG, "close SpmCameraDevice cameraId : " + cameraId, new Throwable("here"));
          } else {
              Log.i(TAG, "close SpmCameraDevice cameraId : " + cameraId);
          }

          SpmCameraDevice camera = mCameraDevices.get(cameraId);
          camera.release();
          synchronized (mArrayMapLock) {
              mCameraDevices.remove(cameraId);
              mCameraDevicesRefMap.remove(camera);
          }
          camera = null;
          try {
              if (isSmartPlatformServiceAlive()) {
                  getSmartPlatformService().disconnectAvmDevice();
              }
          } catch (RemoteException e) {
              e.printStackTrace();
          }

          if(mCameraDevices.size() == 0) {
              if(mEventThread != null) {
                  mEventThread.quitSafely();
                  mEventHandler = null;
                  mEventThread = null;
              }
          }
        }
    }

    public AvmCameraInfo getAvmCameraInfo() {
        AvmCameraInfo avmInfo = new AvmCameraInfo();
        try {
           if (isSmartPlatformServiceAlive()) {
               List<String> avmInfoArray = getSmartPlatformService().getAvmCameraInfo();
               if (avmInfoArray != null && avmInfoArray.size() >= 2) {
                   String[] info = new String[avmInfoArray.size()];
                   avmInfo.setWidth(Integer.parseInt(info[0]));
                   avmInfo.setHeight(Integer.parseInt(info[1]));
                   Log.i(TAG, "get avm camera w x h : cameraId : " + info[0] + " x " + info[1]);

                   return avmInfo;
               }
           }
        } catch (RemoteException e) {
           e.printStackTrace();
        }

        return null;
    }

    /**
     *  get a IpodProxy,we can set param to ipod by this proxy
     */
    public IpodProxy getIpodProxy() {

        mIpodProxy = IpodProxy.getInstance(gSmartPlatformManager);
        return mIpodProxy;
    }


    /**
       *  send cmd to ipod ,it could like to call ipod function
       */
    protected int postIpodCommand(String cmd,String params) {
        try {
            if (isSmartPlatformServiceAlive()) {
                return getSmartPlatformService().postIpodCommand(cmd, params);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        return -1;
    }


    /**
     *  get a CarEventProxy, we can get event from this proxy
     */
    public CarEventProxy getCarEventProxy() {

        mCarEventProxy = CarEventProxy.getInstance(gSmartPlatformManager);

        return mCarEventProxy;
    }

    protected void sendCareventCommand(SimpleCommand cmd) throws RemoteException {
        if(DEBUG) Log.d(TAG,"sendCareventCommand: " + cmd);
        try {
            if (isSmartPlatformServiceAlive()) {
                getSmartPlatformService().sendCarCommand(cmd);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
            throw e;
        }
    }


    /**
     *  get a IpodProxy,we can set param to ipod by this proxy
     */
    public CollisionProxy getCollisionProxy() {

        mCollisionProxy = CollisionProxy.getInstance(gSmartPlatformManager);

        return mCollisionProxy;
    }

    protected void sendCollisionCommand(SimpleCommand cmd) throws RemoteException {
        if(DEBUG) Log.d(TAG,"sendCollisionCommand: " + cmd);
        try {
            if (isSmartPlatformServiceAlive()) {
                getSmartPlatformService().sendCollisionCommand(cmd);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
            throw e;
        }
    }

    public static final int DEVICE_TYPE_COMMON          = 1;
    public static final int DEVICE_TYPE_POWERKEY_BY_ACC = 2;
    public static final int DEVICE_TYPE_VCDT            = 3;

    public int[] getSupportedDeviceType() {
        int[] deviceTypeList = {DEVICE_TYPE_COMMON, DEVICE_TYPE_POWERKEY_BY_ACC, DEVICE_TYPE_VCDT};
        return deviceTypeList;
    }

    /**
       * Get the current device type. The property, ro.boot.devicetype,  is initialized by init process
       */
    public int getCurrentDeviceType() {
        String deviceType = SystemProperties.get("ro.boot.devicetype");
        if (deviceType.equals("common")) {
            return DEVICE_TYPE_COMMON;
        } else if (deviceType.equals("sp_powerkey_by_acc")) {
            return DEVICE_TYPE_POWERKEY_BY_ACC;
        } else if (deviceType.equals("sp_vcdt")) {
            return DEVICE_TYPE_VCDT;
        } else {
            Log.w(TAG, "Wrong device type: " + deviceType);
            return DEVICE_TYPE_COMMON;
        }
    }


    protected int setJpegHwEncState(int state) {
        int result = -1;
        try {
            result = getSmartPlatformService().setJpegHwEncState(state);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        return result;
    }

    protected int getJpegHwEncState() {
        int result = -1;
        try {
            if (isSmartPlatformServiceAlive()) {
                result = getSmartPlatformService().getJpegHwEncState();
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        return result;
    }

    /**
      * Set the device type for sharing the one load among the different platforms
      * Should reboot the device after invoking this API
      */
    public int setDeviceType(int deviceType) {
        int result = -1;
        try {
            if (isSmartPlatformServiceAlive()) {
                result = getSmartPlatformService().setDeviceType(deviceType);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        return result;
    }

    public void test () {
        if (DEBUG) Log.d(TAG,"test");

        try {
            if (isSmartPlatformServiceAlive()) {
                getSmartPlatformService().sendCommand(EVENT_TEST, -1, -1);
            }
        } catch (RemoteException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
    }
}
