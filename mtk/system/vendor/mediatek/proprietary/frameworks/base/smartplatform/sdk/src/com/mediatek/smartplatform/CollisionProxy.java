package com.mediatek.smartplatform;

import java.util.HashSet;


import android.os.RemoteException;

import android.util.Log;


public class CollisionProxy {

    private static final String TAG = "CollisionProxy";
    private static SmartPlatformManager mSmartPlatformManager;
    private static CollisionProxy mCollisionProxy;

    private HashSet<CollisionCallback> mCollisionCallbacks = new HashSet<CollisionCallback>();
    private Object mLock = new Object();


    private static final int COLLISION = 0;
    private static final int COLLISION_ENABLE_CALLBACK = 1;
    private static final int COLLISION_DISABLE_CALLBACK = 2;
    private static final int COLLISION_SET_NORMAL_STATUS = 3;
    private static final int COLLISION_SET_SUSPEND_STATUS = 4;
    private static final int COLLISION_SET_NORMAL_SENSITY = 5;
    private static final int COLLISION_SET_SUSPEND_SENSITY = 6;
    private static final int COLLISION_GET_NORMAL_STATUS = 7;
    private static final int COLLISION_GET_SUSPEND_STATUS = 8;
    private static final int COLLISION_GET_NORMAL_SENSITY = 9;
    private static final int COLLISION_GET_SUSPEND_SENSITY = 10;
    private static final int COLLISION_SET_NORMAL_THRESHOLD = 11;
    private static final int COLLISION_GET_NORMAL_THRESHOLD = 12;
    private static final int COLLISION_SET_SUSPEND_THRESHOLD = 13;
    private static final int COLLISION_GET_SUSPEND_THRESHOLD = 14;
    private static final int COLLISION_SET_NORMAL_GSENSOR_EVENTRATE   = 15;
    private static final int COLLISION_GET_NORMAL_GSENSOR_EVENTRATE   = 16;
    private static final int COLLISION_ENTER_IPOD_OFF = 17;
    private static final int COLLISION_ENTER_IPOD_BOOT = 18;

    public final class CollisionType {
        private CollisionType() {}
        public static final int COLLISION_NORMAL = 0;
        public static final int COllision_SUSPEND = 1;
    }

    public final class CollisionPara {
        private CollisionPara() {}
        public static final int THRESHOLD = 0;
        public static final int SENSITY = 1;
        public static final int EVENTRATE =2;
    }


    private CollisionProxy() {   }

    public synchronized static CollisionProxy getInstance(SmartPlatformManager smartPlatformManager) {
        if(smartPlatformManager==null) { //we do not allow carcorderManager is null
            Log.e(TAG,"param carcorderManager is null.");
            return null;
        }

        if(mCollisionProxy==null) {
            mCollisionProxy=new CollisionProxy();
        }

        mSmartPlatformManager=smartPlatformManager;
        return mCollisionProxy;
    }


    /**
     * Interface definition for a callback to be invoked when car collision
     * happened.
     */
    public interface CollisionCallback {

        public static final int COLLISION_UNRELIABLE = 0;
        public static final int COLLISION_LOW = 1;
        public static final int COLLISION_MEDIUM = 2;
        public static final int COLLISION_HIGH = 3;

        /**
         * Called when car collision happened.
         *
         * @param COLLISION
         *            <ul>
         *            <li>{@link #COLLISION_LOW}
         *            <li>{@link #COLLISION_MEDIUM}
         *            <li>{@link #COLLISION_HIGH}
         *            </ul>
         * @status status reserved
         */
        void onCollison(int COLLISION, int status);

    }




    /**
     * Register a callback to be invoked when car collision happened.
     *
     * @param callback
     *            the callback that will be run
     */
    public void addCollisionCallback(CollisionCallback callback) {
        SimpleCommand cmd = new SimpleCommand(false,COLLISION_ENABLE_CALLBACK,-1,-1);

        if (mCollisionCallbacks.isEmpty()) {
            try {
                mSmartPlatformManager.sendCollisionCommand(cmd);
            } catch (RemoteException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        synchronized (mLock) {
            if (callback != null) {
                mCollisionCallbacks.add(callback);
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
    public void removeCollisionCallback(CollisionCallback callback) {
        synchronized (mLock) {
            if (callback != null) {
                mCollisionCallbacks.remove(callback);
            }
        }

        SimpleCommand cmd = new SimpleCommand(false, COLLISION_DISABLE_CALLBACK, -1, -1);

        if (mCollisionCallbacks.isEmpty()) {
            try {
                mSmartPlatformManager.sendCollisionCommand(cmd);
            } catch (RemoteException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        }
    }



    /**
     * Enable or disable collision in normal situation
     *
     * @param fgEnable
     *            true means the collision function is enabled in normal case;
     *            otherwise, which means this function is disabled
     *
     */
    public void setNormalCollision(boolean fgEnable) {

        SimpleCommand cmd = new SimpleCommand(false,COLLISION_SET_NORMAL_STATUS, (fgEnable ? 1 : 0), -1);

        try {
            mSmartPlatformManager.sendCollisionCommand(cmd);
        } catch (RemoteException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
     * Enable or disable collision in suspend situation
     *
     * @param fgEnable
     *            true means the collision function is enabled in suspend case;
     *            otherwise, which means this function is disabled
     *
     */
    public void setSuspendCollision(boolean fgEnable) {
        SimpleCommand cmd = new SimpleCommand(false,COLLISION_SET_SUSPEND_STATUS, (fgEnable ? 1 : 0), -1);

        try {
            mSmartPlatformManager.sendCollisionCommand(cmd);
        } catch (RemoteException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
     * Set car collision threshold in normal case. The
     * {@link CollisionCallback#onCOLLISION(int, int)} callback only triggered
     * when the detected collision value >= threshold. If this method never
     * invoked, the default threshold is
     * {@link CollisionCallback#COLLISION_MEDIUM}
     *
     * @param threshold
     *            <ul>
     *            <li>{@link CollisionCallback#COLLISION_LOW}
     *            <li>{@link CollisionCallback#COLLISION_MEDIUM}
     *            <li>{@link CollisionCallback#COLLISION_HIGH}
     *            </ul>
     */
    public void setNormalCollisionSensity(int level) {
        SimpleCommand cmd = new SimpleCommand(false, COLLISION_SET_NORMAL_SENSITY, level, -1);

        try {
            mSmartPlatformManager.sendCollisionCommand(cmd);
        } catch (RemoteException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
     * Set car collision threshold in suspend case. The
     * {@link CollisionCallback#onCOLLISION(int, int)} callback only triggered
     * when the detected collision value >= threshold. If this method never
     * invoked, the default threshold is
     * {@link CollisionCallback#COLLISION_MEDIUM}
     *
     * @param threshold
     *            <ul>
     *            <li>{@link CollisionCallback#COLLISION_LOW}
     *            <li>{@link CollisionCallback#COLLISION_MEDIUM}
     *            <li>{@link CollisionCallback#COLLISION_HIGH}
     *            </ul>
     */
    public void setSuspendCollisionSensity(int level) {
        SimpleCommand cmd = new SimpleCommand(false, COLLISION_SET_SUSPEND_SENSITY, level, -1);

        try {
            mSmartPlatformManager.sendCollisionCommand(cmd);
        } catch (RemoteException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
     * Get car collision status in normal case.
     *
     * @return true means collision is enabled for normal case,
     *             otherwise, which means collision is disabled for normal case
     */
    public boolean getNormalCollision() {
        int status = 0;
        SimpleCommand cmd = new SimpleCommand(true, COLLISION_GET_NORMAL_STATUS, -1, 0);

        try {
            mSmartPlatformManager.sendCollisionCommand(cmd);
        } catch (RemoteException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        status = cmd.getResultInt();
        return (status == 1) ? true : false;
    }

    /**
     * Get car collision status in suspend case.
     *
     * @return true means collision is enabled for suspend case,
     *             otherwise, which means collision is disabled for suspend case
     */
    public boolean getSuspendCollision() {
        int status = 0;
        SimpleCommand cmd = new SimpleCommand(true, COLLISION_GET_SUSPEND_STATUS, -1, 0);

        try {
            mSmartPlatformManager.sendCollisionCommand(cmd);
        } catch (RemoteException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        status = cmd.getResultInt();
        return (status == 1) ? true : false;
    }

    /**
     * Get car collision sensity in normal case.
     *
     * @return the setting of collision sensity for normal case.
     *            <ul>
     *            <li>{@link CollisionCallback#COLLISION_LOW}
     *            <li>{@link CollisionCallback#COLLISION_MEDIUM}
     *            <li>{@link CollisionCallback#COLLISION_HIGH}
     *            </ul>
     */
    public int getNormalCollisionSensity() {
        int sensity = 0;
        SimpleCommand cmd = new SimpleCommand(true, COLLISION_GET_NORMAL_SENSITY, -1, 0);

        try {
            mSmartPlatformManager.sendCollisionCommand(cmd);
        } catch (RemoteException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        sensity = cmd.getResultInt();
        return sensity;
    }

    /**
     * Get car collision sensity in suspend case.
     *
     * @return the setting of collision sensity for suspend case.
     *            <ul>
     *            <li>{@link CollisionCallback#COLLISION_LOW}
     *            <li>{@link CollisionCallback#COLLISION_MEDIUM}
     *            <li>{@link CollisionCallback#COLLISION_HIGH}
     *            </ul>
     */
    public int getSuspendCollisionSensity() {
        int sensity = 0;
        SimpleCommand cmd = new SimpleCommand(true, COLLISION_GET_SUSPEND_SENSITY, -1, 0);

        try {
            mSmartPlatformManager.sendCollisionCommand(cmd);
        } catch (RemoteException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        sensity = cmd.getResultInt();
        return sensity;
    }

    /**
     * Set car collision threshold.
     * @param x   the threshold of Gx on the x-axis
       * @param y   the threshold of Gy on the y-axis
       * @param z   the threshold of Gz on the z-axis
       * the value of x,y,z must be greater than 0
       * @param level, like a flag,its value is the same as
       * {@link CollisionCallback#onCOLLISION(int COLLISION, int status)}  COLLISION
     * @return 0 if success,otherwise fail
     */
    public int setCollisionThreshold(float x,float y,float z,int level) {
        int status = -1;

        try {
            String params=Float.toString(x)+","+Float.toString(y)+","+Float.toString(z)+","+Integer.toString(level);
            SimpleCommand cmd = new SimpleCommand(false, COLLISION_SET_NORMAL_THRESHOLD, params, 0);
            mSmartPlatformManager.sendCollisionCommand(cmd);

            status = cmd.getResultInt();
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        return status;
    }

    /**
     * Get car collision threshold.
     * @return float[] values
     * values[0]: the threshold of Gx on the x-axis that be set
     * values[1]: the threshold of Gy on the y-axis that be set
     * values[2]: the threshold of Gz on the z-axis that be set
     * values will be null if getCollisionThreshold fail
     */
    public float[] getCollisionThreshold() {
        try {
            SimpleCommand cmd = new SimpleCommand(true, COLLISION_GET_NORMAL_THRESHOLD, -1, 1/*String*/);
            mSmartPlatformManager.sendCollisionCommand(cmd);

            String params = cmd.getResultString();

            if(params==null||params.equals("")) {
                Log.e(TAG,"params is null or empty");
                return null;
            }

            String str[]=params.split(",");

            if(str.length !=4) {
                Log.e(TAG,"str.length("+str.length+") is bad value.");
                return null;
            }

            float[] result=new float[3];
            result[0]=Float.parseFloat(str[0]);
            result[1]=Float.parseFloat(str[1]);
            result[2]=Float.parseFloat(str[2]);
            return result;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Set gsensor collision threshold.
     * @param threshold it must be between 1 and 100
     * @return 0 if success,otherwise fail
     */
    public int setGsensorThreshold(int threshold) {
        try {
            int status = -1;
            String params=Integer.toString(threshold);
            SimpleCommand cmd = new SimpleCommand(false/*set*/, COLLISION_SET_SUSPEND_THRESHOLD, params, 0/*int*/);

            mSmartPlatformManager.sendCollisionCommand(cmd);
            status = cmd.getResultInt();
            return status;
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        return -1;
    }

    /**
     * Get gsensor threshold
     *
     * @return the value of threshlod from gsensor driver
     *    will return -1 if getGsensorThreshold fail
     */
    public int getGsensorThreshold() {
        try {
            SimpleCommand cmd = new SimpleCommand(true/*get*/, COLLISION_GET_SUSPEND_THRESHOLD, -1, 1/*String*/);
            mSmartPlatformManager.sendCollisionCommand(cmd);

            String params = cmd.getResultString();

            if(params==null||params.equals("")) {
                Log.e(TAG,"getGsensorThreshold params is null or empty");
                return -1;
            }

            return Integer.parseInt(params);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        return -1;
    }

    /**
     * Set gsensor event report interval
     *
     * @param delayMs the delay time of two gsensor event
     * and unit is millisecond
     */
    public int setGsensorEventRate(int delayMs) {
        try {
            int status;
            String params=Integer.toString(delayMs);
            SimpleCommand cmd = new SimpleCommand(false/*set*/, COLLISION_SET_NORMAL_GSENSOR_EVENTRATE, params, 0/*int*/);
            mSmartPlatformManager.sendCollisionCommand(cmd);

            status = cmd.getResultInt();
            return status;
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        return -1;
    }
    /**
     * Get gsensor event report interval
     *
     * @return the interval of gsensor event,
     * the value should more than 0 if success
     */
    public int getGsensorEventRate() {
        try {
            SimpleCommand cmd = new SimpleCommand(true/*get*/, COLLISION_GET_NORMAL_GSENSOR_EVENTRATE, -1, 1/*String*/);
            mSmartPlatformManager.sendCollisionCommand(cmd);

            String params = cmd.getResultString();

            if(params==null||params.equals("")) {
                Log.e(TAG,"getGsensorEventRate params is null or empty");
                return -1;
            }

            return Integer.parseInt(params);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        return -1;
    }

    public void onCollison(int COLLISION, int status) {
        synchronized (mLock) {
            for (CollisionCallback callback : mCollisionCallbacks) {
                if (callback != null) {
                    callback.onCollison(COLLISION, status);
                }
            }
        }

    }


    /**
     * set  ipo  standby (on or off ) to collision
     *
     * @param fgEnable
     *            true means ipo shutdown ;
     *            otherwise, which means ipo boot .
     *
     */
    public void setIPOStandby(boolean on) {

        SimpleCommand cmd;
        if (on) {
            cmd = new SimpleCommand(false,COLLISION_ENTER_IPOD_OFF, (on ? 1 : 0), -1);
        } else {
            cmd = new SimpleCommand(false,COLLISION_ENTER_IPOD_BOOT, (on ? 1 : 0), -1);
        }

        try {
            mSmartPlatformManager.sendCollisionCommand(cmd);
        } catch (RemoteException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

}

