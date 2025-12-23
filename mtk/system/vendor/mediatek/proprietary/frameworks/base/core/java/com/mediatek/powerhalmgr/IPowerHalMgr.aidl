package com.mediatek.powerhalmgr;

import android.os.IRemoteCallback;
import com.mediatek.powerhalmgr.DupLinkInfo;
import com.mediatek.powerhalmgr.BoosterInfo;

/** @hide */
interface IPowerHalMgr {
    int scnReg();
    oneway void scnConfig(int handle, int cmd, int param_1, int param_2, int param_3, int param_4);
    oneway void scnUnreg(int handle);
    oneway void scnEnable(int handle, int timeout);
    oneway void scnDisable(int handle);
    oneway void scnUltraCfg(int handle, int ultracmd, int param_1, int param_2, int param_3, int param_4);
    oneway void mtkCusPowerHint(int hint, int data);
    oneway void getCpuCap();
    oneway void getGpuCap();
    oneway void getGpuRTInfo();
    oneway void getCpuRTInfo();
    oneway void UpdateManagementPkt(int type, String packet);
    oneway void setForegroundSports();
    oneway void setSysInfo(int type, String data);

    // M: DPP @{
    boolean startDuplicatePacketPrediction();
    boolean stopDuplicatePacketPrediction();
    boolean isDupPacketPredictionStarted();
    boolean registerDuplicatePacketPredictionEvent(in IRemoteCallback listener);
    boolean unregisterDuplicatePacketPredictionEvent(in IRemoteCallback listener);
    boolean updateMultiDuplicatePacketLink(in DupLinkInfo[] linkList);
    // @}

    oneway void setPredictInfo(String pack_name, int uid);
    int perfLockAcquire(int handle, int duration, in int[] list);
    oneway void perfLockRelease(int handle);
    int querySysInfo(int cmd, int param);
    oneway void mtkPowerHint(int hint, int data);
    int perfCusLockHint(int hint, int duration);
    int setSysInfoSync(int type, String data);

    // M: vip channel sdk @{
    boolean setPriorityByUid(in int action, in int uid);
    boolean setPriorityByLinkinfo(in int action, in DupLinkInfo linkinfo);
    boolean flushPriorityRules(in int type);
    // @}

    // APK Booster @{
    boolean configBoosterInfo(in BoosterInfo info);
    // @}
}
