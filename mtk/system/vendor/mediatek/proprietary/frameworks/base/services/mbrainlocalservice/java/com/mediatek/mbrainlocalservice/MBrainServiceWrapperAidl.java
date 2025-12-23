package com.mediatek.mbrainlocalservice;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.IServiceCallback;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.ServiceSpecificException;
import android.os.SystemProperties;
import android.os.Trace;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.mediatek.mbrainlocalservice.helper.Utils;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import vendor.mediatek.hardware.mbrain.IMBrain;
import vendor.mediatek.hardware.mbrain.MBrain_Event;
import vendor.mediatek.hardware.mbrain.MBrain_Parcelable;

final public class MBrainServiceWrapperAidl {
    private static final String TAG = "MBrainServiceWrapperAidl";
    @VisibleForTesting static final String SERVICE_NAME = IMBrain.DESCRIPTOR + "/default";
    private final HandlerThread mHandlerThread = new HandlerThread("MBrainServiceBinder");
    private final AtomicReference<IMBrain> mLastService = new AtomicReference<>();
    private final IServiceCallback mServiceCallback = new ServiceCallback();
    private final MBrainRegCallbackAidl mRegCallback;

    private final int MB_JAVA_DOZE_MODE_CHANGE = 120;
    private final int MB_JAVA_AUDIO_MODE_CHANGE = 121;
    private final int MB_JAVA_APP_LIFE_CYCLE = 130;

    /** Stub interface into {@link ServiceManager} for testing. */
    interface ServiceManagerStub {
        default @Nullable IMBrain getService(@NonNull String name) {
            return IMBrain.Stub.asInterface(ServiceManager.checkService(name));
        }

        default void registerForNotifications(
                @NonNull String name, @NonNull IServiceCallback callback) throws RemoteException {
            ServiceManager.registerForNotifications(name, callback);
        }
    }

    MBrainServiceWrapperAidl(
            @Nullable MBrainRegCallbackAidl regCallback, @NonNull ServiceManagerStub serviceManager)
            throws RemoteException, NoSuchElementException {

        traceBegin("MBrainInitGetServiceAidl");
        IMBrain newService;
        try {
            newService = serviceManager.getService(SERVICE_NAME);
        } finally {
            traceEnd();
        }

        if (newService == null) {
            throw new NoSuchElementException(
                    "IMBrain service instance isn't available. Perhaps no permission?");
        }
        mLastService.set(newService);
        mRegCallback = regCallback;
        if (mRegCallback != null) {
            mRegCallback.onRegistration(null /* oldService */, newService);
        }

        traceBegin("MBrainInitRegisterNotificationAidl");
        mHandlerThread.start();
        try {
            serviceManager.registerForNotifications(SERVICE_NAME, mServiceCallback);
        } finally {
            traceEnd();
        }
        //Slog.d(TAG, "MBrain: MBrainServiceWrapper listening to AIDL");
    }

    @VisibleForTesting
    public HandlerThread getHandlerThread() {
        return mHandlerThread;
    }

    public void sendMessageToService(String message) throws RemoteException {
        //Slog.d(TAG, "MBrain: MBrainServiceWrapper sendMessageToService");
        if (checkMBrainAvailable()) {
            try {
                IMBrain service = mLastService.get();
                if (service == null) {
                    Slog.i(TAG, "no mBrain service");
                    return;
                }
                byte passEvent = MBrain_Event.MB_DEFAULT;
                MBrain_Parcelable passData = new MBrain_Parcelable();
                passData.privData = message;
                service.Notify(passEvent, passData);
            } catch (RemoteException | ServiceSpecificException ex) {
                Slog.e(TAG, "Cannot call update on mbrain AIDL", ex);
            } finally {
                Slog.d(TAG, "MBrain: MBrainServiceWrapper sendMessageToService end");
                traceEnd();
            }
        }
    }

    public void notifyAppSwitchToService(String message) throws RemoteException {
        //Slog.d(TAG, "MBrainServiceWrapper notifyAppSwitchToService");
        notifyEventToNativeService(MBrain_Event.MB_JAVA_APP_SWITCH, message);
    }

    public void notifyChargeChangeToService(String message) throws RemoteException {
        //Slog.d(TAG, "MBrainServiceWrapper notifyChargeChangeToService");
        notifyEventToNativeService(MBrain_Event.MB_JAVA_CHARGE_CHANGE, message);
    }

    public void notifyNetworkChangeToService(String message) throws RemoteException {
        //Slog.d(TAG, "MBrainServiceWrapper notifyNetworkChangeToService");
        notifyEventToNativeService(MBrain_Event.MB_JAVA_NETWORK_CHANGE, message);
    }

    public void notifyAppStateChangeToService(String message) throws RemoteException {
        //Slog.d(TAG, "MBrainServiceWrapper notifyAppStateChangeToService");
        notifyEventToNativeService(MBrain_Event.MB_JAVA_APP_STATE_CHANGE, message);
    }

    public void notifyUsbChangeToService(String message) throws RemoteException {
        //Slog.d(TAG, "MBrainServiceWrapper notifyUsbChangeToService");
        notifyEventToNativeService(MBrain_Event.MB_JAVA_USB_CHANGE, message);
    }

    public void notifyScreenChangeToService(String message) throws RemoteException {
        //Slog.d(TAG, "MBrainServiceWrapper notifyScreenChangeToService");
        notifyEventToNativeService(MBrain_Event.MB_JAVA_SCREEN_CHANGE, message);
    }

    public void notifyControlMsgToService(String message) throws RemoteException {
        //Slog.d(TAG, "MBrainServiceWrapper notifyControlMsgToService");
        notifyEventToNativeService(MBrain_Event.MB_CONTROL_MSG, message);
    }

    public void notifyTimeChangeToService(String message) throws RemoteException {
        //Slog.d(TAG, "MBrainServiceWrapper notifyTimeChangeToService");
        notifyEventToNativeService(MBrain_Event.MB_JAVA_TIMESYS_CHANGE, message);
    }

    public void notifyDeviceShutDownToService(String message) throws RemoteException {
        //Slog.d(TAG, "MBrainServiceWrapper notifyDeviceShutDownToService");
        notifyEventToNativeService(MBrain_Event.MB_JAVA_SHUTDOWN_MSG, message);
    }

    public void notifyAudioRouteChangeToService(String message) throws RemoteException {
        //Slog.d(TAG, "MBrainServiceWrapper notifyAudioRouteChangeToService");
        notifyEventToNativeService(MBrain_Event.MB_JAVA_AUDIO_ROUTE_CHANGE, message);
    }

    public void notifyTelephonyStateChangeToService(String message) throws RemoteException {
        //Slog.d(TAG, "MBrainServiceWrapper notifyTelephonyStateChangeToService");
        notifyEventToNativeService(MBrain_Event.MB_JAVA_TELEPHONY_EVENT_NOTIFY, message);
    }

    public void notifyDozeModeChangeToService(String message) throws RemoteException {
        //Slog.d(TAG, "MBrainServiceWrapper notifyDozeModeChangeToService");
        notifyExtEventToNativeService(MB_JAVA_DOZE_MODE_CHANGE, message);
    }

    public void notifyAudioModeChangeToService(String message) throws RemoteException {
        //Slog.d(TAG, "MBrainServiceWrapper notifyAudioModeChangeToService");
        notifyExtEventToNativeService(MB_JAVA_AUDIO_MODE_CHANGE, message);
    }

    public void notifyAppLifecycleToService(String message) throws RemoteException {
        //Slog.d(TAG, "MBrainServiceWrapper notifyAppLifecycleToService");
        notifyExtEventToNativeService(MB_JAVA_APP_LIFE_CYCLE, message);
    }

    public void notifyAPIGetDataToService(String message) throws RemoteException {
        //Slog.d(TAG, "MBrainServiceWrapper notifyAPIGetDataToService");
        notifyApiGetDataToNativeService(MBrain_Event.MB_API_GET_CUSTOMIZE_INFO, message);
    }

    public void notifyEventToNativeService(byte event, String message) throws RemoteException {
        getHandlerThread()
                .getThreadHandler()
                .post(new Runnable() {
            @Override
            public void run() {
                if (checkMBrainAvailable() || Utils.isPassThroughMode(message)) {
                    try {
                        IMBrain service = mLastService.get();
                        if (service == null) {
                            Slog.d(TAG, "no mBrain service");
                            return;
                        }
                        byte mBrainEvent = event;
                        MBrain_Parcelable mBrainData = new MBrain_Parcelable();
                        mBrainData.privData = message;
                        service.Notify(mBrainEvent, mBrainData);
                    } catch (RemoteException | ServiceSpecificException ex) {
                        Slog.e(TAG, "Cannot call update on mbrain AIDL", ex);
                    } catch (Exception ex) {
                        Slog.e(TAG, "unknown mbrain AIDL exception", ex);
                    } finally {
                        Slog.d(TAG, "MBrainServiceWrapper notifyEventToNativeService end");
                    }
                }
            }
        });
    }

    public void notifyExtEventToNativeService(int event, String message) throws RemoteException {
        getHandlerThread()
                .getThreadHandler()
                .post(new Runnable() {
            @Override
            public void run() {
                if (checkMBrainAvailable() || Utils.isPassThroughMode(message)) {
                    try {
                        IMBrain service = mLastService.get();
                        if (service == null) {
                            Slog.d(TAG, "no mBrain service");
                            return;
                        }
                        int mBrainEvent = event;
                        MBrain_Parcelable mBrainData = new MBrain_Parcelable();
                        mBrainData.privData = message;
                        service.NotifyExt(mBrainEvent, mBrainData);
                    } catch (RemoteException | ServiceSpecificException ex) {
                        Slog.e(TAG, "Cannot call update on mbrain AIDL", ex);
                    } catch (Exception ex) {
                        Slog.e(TAG, "unknown mbrain AIDL exception", ex);
                    } finally {
                        Slog.d(TAG, "MBrainServiceWrapper notifyEventToNativeService end");
                    }
                }
            }
        });
    }

    public void notifyApiGetDataToNativeService(byte event, String message) throws RemoteException {
        getHandlerThread()
                .getThreadHandler()
                .post(new Runnable() {
            @Override
            public void run() {
                if (checkMTKDBGAvailable()) {
                    try {
                        IMBrain service = mLastService.get();
                        if (service == null) {
                            Slog.d(TAG, "no MTK DBG service");
                            return;
                        }
                        byte mBrainEvent = event;
                        MBrain_Parcelable mBrainData = new MBrain_Parcelable();
                        mBrainData.privData = message;
                        service.Notify(mBrainEvent, mBrainData);
                    } catch (RemoteException | ServiceSpecificException ex) {
                        Slog.e(TAG, "Cannot call update on AIDL", ex);
                    } catch (Exception ex) {
                        Slog.e(TAG, "unknown AIDL exception", ex);
                    } finally {
                        //Slog.d(TAG, "MBrainServiceWrapper notifyEventToNativeService end");
                    }
                } else {
                    Slog.d(TAG, "checkMTKDBGAvailable is false");
                }
            }
        });
    }

    private static void traceBegin(String name) {
        Trace.traceBegin(Trace.TRACE_TAG_SYSTEM_SERVER, name);
    }

    private static void traceEnd() {
        Trace.traceEnd(Trace.TRACE_TAG_SYSTEM_SERVER);
    }

    private boolean checkMBrainAvailable() {
        if (!Utils.isVendorFreezeConditionPass()) {
            return false;
        }

        try {
            IMBrain service = mLastService.get();
            if (service == null) {
                Slog.i(TAG, "no mBrain service");
                return false;
            }
            boolean isMBrainSupport = (service.IsMBrainSupport() == 1);
            Utils.setMBrainSupport(isMBrainSupport);
            return isMBrainSupport;
        } catch (OutOfMemoryError ex) {
            Slog.i(TAG, "OutOfMemoryError", ex);
            return false;
        }  catch (RemoteException | ServiceSpecificException ex) {
            Slog.i(TAG, "Cannot call update on mbrain AIDL", ex);
            return false;
        } catch (Exception ex) {
            Slog.i(TAG, "unknown mbrain AIDL exception", ex);
            return false;
        }
    }

    private boolean checkMTKDBGAvailable() {
        if (!Utils.isVendorFreezeConditionPass()) {
            return false;
        }

        String mtkdbgvEnabled = SystemProperties.get("vendor.mtkdbgv.enabled", "0");
        String mtkdbgoEnabled = SystemProperties.get("vendor.mtkdbgo.enabled", "0");
        if ("1".equals(mtkdbgvEnabled) || "1".equals(mtkdbgoEnabled)) {
            try {
                IMBrain service = mLastService.get();
                if (service == null) {
                    Slog.i(TAG, "no MTK DBG service");
                    return false;
                }
                return true;
            } catch (ServiceSpecificException ex) {
                Slog.i(TAG, "Cannot call update on AIDL", ex);
                return false;
            } catch (Exception ex) {
                Slog.i(TAG, "unknown AIDL exception", ex);
                return false;
            }
        }

        return false;
    }

    private class ServiceCallback extends IServiceCallback.Stub {
        @Override
        public void onRegistration(String name, @NonNull final IBinder newBinder)
                throws RemoteException {
            if (!SERVICE_NAME.equals(name)) return;
            // This runnable only runs on mHandlerThread and ordering is ensured, hence
            // no locking is needed inside the runnable.
            getHandlerThread().getThreadHandler().post(
            () -> {
                IMBrain newService = IMBrain.Stub.asInterface(Binder.allowBlocking(newBinder));
                IMBrain oldService = mLastService.getAndSet(newService);
                IBinder oldBinder = oldService != null ? oldService.asBinder() : null;
                if (Objects.equals(newBinder, oldBinder)) return;

                if (mRegCallback != null && newService != null) {
                    mRegCallback.onRegistration(oldService, newService);
                }
            });
        }
    }
}
