package com.mediatek.mbrainlocalservice;
import vendor.mediatek.hardware.mbrain.MBrain_Parcelable;

/**
 * A wrapper over AIDL IMBrainInfoCallback.
 *
 * @hide
 */
public interface MBrainInfoCallback {
    /**
     * Signals to the client that need to update info.
     *
     * @param event the new coming event.
     */
    boolean notifyToClient(String event);

    boolean getMessgeFromClient(MBrain_Parcelable input);
}
