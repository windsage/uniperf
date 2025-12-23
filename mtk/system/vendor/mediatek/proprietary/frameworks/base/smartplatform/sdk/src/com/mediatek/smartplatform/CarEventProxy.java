package com.mediatek.smartplatform;

import java.util.HashSet;

import android.os.Build;
import android.os.RemoteException;
import android.os.SystemProperties;

import android.util.Log;


public class CarEventProxy {
    private static final String TAG = "CarEventProxy";
    private static final boolean DEBUG = Build.IS_ENG || SystemProperties.getBoolean("persist.vendor.log.spmsdk", false);
    private static SmartPlatformManager mSmartPlatformManager;
    private static CarEventProxy mCarEventProxy;

    private HashSet<CarReverseCallback> mCarReverseCallbacks = new HashSet<CarReverseCallback>();
    private HashSet<CarEngineChangedCallback> mCarEngineChangedCallbacks = new HashSet<CarEngineChangedCallback>();
    private HashSet<CarCustomizationCallback> mCarCustomizationCallbacks = new HashSet<CarCustomizationCallback>();

    private Object mLock = new Object();

    /*
     * the flag that car motion or engine in unknow state
     */
    public static final int CAR_STATE_UNKNOWN=-1;

    /*
     * the flag that car is reversing
     */
    public static final int CAR_MOTION_REVERSE=0;

    /*
    * the flag that car is driving normally
    */
    public static final int CAR_MOTION_NORMAL=1;

    /*
     * the flag that car engine is flameout
     */
    public static final int CAR_ENGINE_FLAMEOUT=0;

    /*
    * the flag that car engine is working
    */
    public static final int CAR_ENGINE_WORKING=1;



    private static final int COMMAND_QUERY_CAR_STATUS = 1;//queryCarStatus
    private static final int COMMAND_QUERY_CAR_MOTION_STATE = 2; //queryCarMotionState
    private static final int COMMAND_QUERY_CAR_ENGINE_STATE = 3;//queryCarEngineState
    private static final int COMMAND_SET_ACC_OFF_BEHAVIOR = 4;  //setDefaultAccOffBehavior
    private static final int COMMAND_QUERY_CUSTOMIZATION1_STATE = 0x1000; //queryCustomizationState
    private static final int COMMAND_QUERY_CUSTOMIZATION2_STATE = 0x2000;


    private CarEventProxy() {   }

    public synchronized static CarEventProxy getInstance(SmartPlatformManager smartPlatformManager) {
        if(smartPlatformManager==null) { //we do not allow carcorderManager is null
            Log.e(TAG,"param carcorderManager is null.");
            return null;
        }

        if(mCarEventProxy==null) {
            mCarEventProxy=new CarEventProxy();
        }

        mSmartPlatformManager=smartPlatformManager;
        return mCarEventProxy;
    }


    /*
       * Get the car status. Just sending the command to driver,
       * and the status is feedbacking in the {@link #onReverse(int)}
       *
       */
    public void queryCarStatus() {
        SimpleCommand cmd = new SimpleCommand(true/*get*/, COMMAND_QUERY_CAR_STATUS, -1, -1);
        try {
            mSmartPlatformManager.sendCareventCommand(cmd);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }



    /**
     * Get the car motion state
     *@return the status of car motion
     *
     **/
    public int queryCarMotionState() {
        SimpleCommand cmd = new SimpleCommand(true/*get*/, COMMAND_QUERY_CAR_MOTION_STATE, -1, 0/*return int*/);
        try {
            mSmartPlatformManager.sendCareventCommand(cmd);
            return cmd.getResultInt();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return CAR_STATE_UNKNOWN;
    }

    /**
     * Get the car engine state
     *@return the status of car engine
     **/
    public int queryCarEngineState() {
        SimpleCommand cmd = new SimpleCommand(true/*get*/, COMMAND_QUERY_CAR_ENGINE_STATE, -1, 0/*return int*/);
        try {
            mSmartPlatformManager.sendCareventCommand(cmd);
            return cmd.getResultInt();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return CAR_STATE_UNKNOWN;
    }

    /*
     * To control the default behavior when ACC is to off or is to On on special device.
     * The default behavior is that the carcorder service sends broadcast message to system ui when
     * Acc is to off or is to on on Special device
     *
     * @param enable
     *            true means the default behavior is working, and the {@link #onEngineChanged(int)} callback isn't available
     *            false means the default behavior isn't available and the {@link #onEngineChanged(int)} callback is working
      **/
    public void setDefaultAccOffBehavior(boolean enable) {
        SimpleCommand cmd = new SimpleCommand(false/*set*/, COMMAND_SET_ACC_OFF_BEHAVIOR, (enable ? 1 : 0), -1);
        try {
            mSmartPlatformManager.sendCareventCommand(cmd);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        return;
    }

    public void queryCustomization1State() {
        SimpleCommand cmd = new SimpleCommand(true/*get*/, COMMAND_QUERY_CUSTOMIZATION1_STATE, -1, -1);
        try {
            mSmartPlatformManager.sendCareventCommand(cmd);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        return;
    }

    public void queryCustomization2State() {
        SimpleCommand cmd = new SimpleCommand(true/*get*/, COMMAND_QUERY_CUSTOMIZATION2_STATE, -1, -1);
        try {
            mSmartPlatformManager.sendCareventCommand(cmd);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        return;
    }

    public interface CarReverseCallback {

        public static final int CAR_STATUS_UNKNOWN = -1;
        public static final int CAR_STATUS_NORMAL = 0;
        public static final int CAR_STATUS_LEFT =1;
        public static final int CAR_STATUS_RIGHT =2;
        public static final int CAR_STATUS_REVERSE = 3;

        public static final int CAR_STATUS_SOURCE_AVM = 1;
        public static final int CAR_STATUS_SOURCE_GPIO =2;

        /**
              * Called when car starts to reverse or starts to drive
              *
              * @param status
         *            <ul>
         *            <li>{@link #CAR_STATUS_REVERSE}
         *            <li>{@link #CAR_STATUS_NORMAL}
         *            </ul>
              */
        void onReverse(int status, int source);
    }

    public interface CarEngineChangedCallback {

        public static final int CAR_ENGINE_FLAMEOUT = 0;
        public static final int CAR_ENGINE_WORKING = 1;

        /**
              * Called when car's engine status is changed to flameout from working
              * or is changed to working from flameout
              *
              * This callback is available only when smart platform is special device and
              * {@link #setDefaultAccOffBehavior(boolean)} is invoked to disable the default ACC off behavior
              *
              * @param status
         *            <ul>
         *            <li>{@link #CAR_ENGINE_FLAMEOUT}
         *            <li>{@link #CAR_ENGINE_WORKING}
         *            </ul>
              */
        void onEngineChanged(int status);
    }


    /**
        * Customer can Customize car events callback
        *
        * Below are some hidden internal implementation details:
        *
        * query this information will send 0x1000 to the carEvent driver:
        *       CAR_CUSTOMIZATION_REQ_STATUS = 0x1000 // defined in native
        *       COMMAND_QUERY_CUSTOMIZATION_STATE = 0x1000
        *
        *       CAR_CUSTOMIZATION_REQ_STATUS = 0x2000 // defined in native
        *       COMMAND_QUERY_CUSTOMIZATION_STATE = 0x2000
        *
        *
        * Register to accept this information will send 0x1001 to the carEvent driver:
        *       CAR_CUSTOMIZATION1_SET_RCV = 0x1001 // defined in native
        *       CAR_CUSTOMIZATION2_SET_RCV = 0x2001
        *
        * driver should send event value(1000 or 2000) and  status value(need customer customization) to sdk(apk)
        *       struct car_status {     // defined in Carevent.h
	 *           int response_type;   // 1000 or 2000
	 *           int status;                // customize
        *       };
        */
    public interface CarCustomizationCallback {

        /**
                * @event
                * event value
                * <ul>
                * <li> {@link #EVENT_CAR_CUSTOMIZATION1}
                * <li> {@link #EVENT_CAR_CUSTOMIZATION2}
                * </ul>
                * @param status
                * The value sent by the carEvent driver
                */
        void onCustomizationChanged(int event, int status);
    }


    /**
       * Register a callback to be invoked when car starts to drive or start to reverse
       *
       * @param callback
       *            the callback that will be run
       */
    public void addCarReverseCallback(final CarReverseCallback callback) {
        synchronized(mLock) {
            if (callback != null) {
                mCarReverseCallbacks.add(callback);
            }
        }
    }


    /**
       * Unregister a callback to be invoked when car starts to drive or start to reverse
       *
       * @param callback
       *            the callback that will no longer be invoked
       */

    public void removeCarReverseCallback(final CarReverseCallback callback) {
        synchronized(mLock) {
            if (callback != null) {
                mCarReverseCallbacks.remove(callback);
            }
        }
    }

    /**
       * Register a callback to receive the status of car engine on special device
       *
       * @param callback
       *            the callback that will be run
       */
    public void addCarEngineChangedCallback(final CarEngineChangedCallback callback) {
        synchronized(mLock) {
            if (callback != null) {
                mCarEngineChangedCallbacks.add(callback);
            }
        }
    }


    /**
       * Unregister a callback of receiving the status of car engine on special device
       *
       * @param callback
       *            the callback that will no longer be invoked
       */

    public void removeCarEngineChangedCallback(final CarEngineChangedCallback callback) {
        synchronized(mLock) {
            if (callback != null) {
                mCarEngineChangedCallbacks.remove(callback);
            }
        }
    }


    public void addCarCustomizationCallback(final CarCustomizationCallback callback) {
        synchronized(mLock) {
            if (callback != null) {
                mCarCustomizationCallbacks.add(callback);
            }
        }
    }

    public void removeCustomizationCallback(final CarCustomizationCallback callback) {
        synchronized(mLock) {
            if (callback != null) {
                mCarCustomizationCallbacks.remove(callback);
            }
        }
    }



    //public static final int EVENT_WEBCAM_HOTPLUG = 0;
    // Should keep the same definition with the one in carReverHandler.h
    public static final int EVENT_CAR_REVERSE = 1;
    public static final int EVENT_COLLISION = 2;
    public static final int EVENT_CAR_ENGINE_CHANGED = 3;
    public static final int EVENT_CAR_CUSTOMIZATION1 = 1000;
    public static final int EVENT_CAR_CUSTOMIZATION2 = 2000;

    void handEvent(int event, int arg1, int arg2) {
        if (DEBUG) Log.d(TAG, " handEvent : " + event + ", arg1 = " + arg1 + ", arg2 = " + arg2);
        switch (event) {
        case EVENT_CAR_REVERSE:
            int status = -1;
            switch (arg2) {
            case CarReverseCallback.CAR_STATUS_SOURCE_GPIO:
                switch (arg1) {
                case 0:
                    status = 3;
                    break;
                case 1:
                    status = 0;
                    break;
                default:
                    Log.e(TAG, "unknown car reverse status of GPIO");
                    break;
                }
                break;
            case CarReverseCallback.CAR_STATUS_SOURCE_AVM:
                status = arg1;
                break;
            default:
                Log.e(TAG,"unknonw car status source......");
                break;
            }

            synchronized(mLock) {
                for (CarReverseCallback callback : mCarReverseCallbacks) {
                    if (callback != null) {
                        callback.onReverse(status, arg2);
                    }
                }
            }
            break;
        case EVENT_CAR_ENGINE_CHANGED:
            synchronized(mLock) {
                for (CarEngineChangedCallback callback : mCarEngineChangedCallbacks) {
                    if (callback != null) {
                        callback.onEngineChanged(arg1);
                    }
                }
            }
            break;
        case EVENT_CAR_CUSTOMIZATION1:
        case EVENT_CAR_CUSTOMIZATION2:
            synchronized(mLock) {
                for (CarCustomizationCallback callback : mCarCustomizationCallbacks) {
                    if (callback != null) {
                        callback.onCustomizationChanged(event, arg1);
                    }
                }
            }
            break;
        default:
            break;


        }

    }


}

