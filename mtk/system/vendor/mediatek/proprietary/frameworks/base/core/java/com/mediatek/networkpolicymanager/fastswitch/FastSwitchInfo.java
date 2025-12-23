/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2023. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */

package com.mediatek.networkpolicymanager.fastswitch;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Map;
/**
 * Information about a FastSwitch.
 *
 * Constants in this file correspond to the constants in vendor/mediatek/proprietary/hardware
 * /interfaces/nps/aidl/vendor/mediatek/hardware/nps/nos/fastswitch/FastSwitchInfoConst.aidl.
 *
 * ACTION_REQ_GEN_ACT & ACTION_REPORT_GEN and their sub actions correspond to vendor/mediatek
 * /proprietary/hardware/NetworkObservationSystem/fastswitch/FastSwitch.h (Don't update aidl
 * due to special reasons this time)
 *
 * Variables some constants in this file correspond to the Variables in vendor/mediatek/proprietary
 * /hardware/interfaces/nps/aidl/vendor/mediatek/hardware/nps/nos/fastswitch/FastSwitchInfo.aidl.
 *
 * BK_PARM_XX & BV_PARM_XX correspond to vendor/mediatek/proprietary/hardware
 * /NetworkObservationSystem/fastswitch/FastSwitch.h (Don't update aidl due to special reasons
 * this time)
 *
 * Please modify them together when update.
 *
 * Note: This feature has SDK for 3rd party customer.
 *
 *       So:
 *       You mustn't modify the current variables and their types.
 *       You can add new variable.
 *       The newly added variable must be at end of writeToParcel and readFromParcel.
 *       You can delete the existed variable, but you must keep the variable field for it
 *       in functions writeToParcel and readFromParcel.
 *
 *       For delete case, strongly suggest you update SDK and publish to customer in time
 *       because delete case is not backward compatibility.
 *
 *       Special note:
 *           please add mark to describe for your deleted variable, especially last one.
 *
 *       Example
 *           2023.01.01, version 1
 *           Variable: int a = 0;
 *           Variable: String b = 1; it is just the last variable
 *           @Override
 *           public void writeToParcel(Parcel out, int flags) {
 *               out.writeInt(a);
 *               out.writeString(b);
 *           }
 *
 *           private void readFromParcel(Parcel in) {
 *               a = in.readInt();
 *               b = in.readString();
 *           }
 *
 *           2023.03.01, version 2
 *           Variable: int a = 0;
 *           (Variable: // String b = 1; it is just the last variable)
 *           @Override
 *           public void writeToParcel(Parcel out, int flags) {
 *               out.writeInt(a);
 *               out.writeString("");
 *           }
 *
 *           private void readFromParcel(Parcel in) {
 *               a = in.readInt();
 *               in.readString();
 *           }
 *
 *           2023.04.01, version 3
 *           Variable: int a = 0;
 *           (Variable: // String b = 1; it is just the last variable)
 *           Variable: int c = 0;
 *           @Override
 *           public void writeToParcel(Parcel out, int flags) {
 *               out.writeInt(a);
 *               out.writeString("");
 *               out.writeInt(c);
 *           }
 *
 *           private void readFromParcel(Parcel in) {
 *               a = in.readInt();
 *               in.readString();
 *               c = in.readInt();
 *           }
 *
 *       So:
 *       You mustn't modify the current constant variables and their values.
 *       You must set new value for your newly added constant variable.
 *       The newly added constant variable value must be increasing.
 *       The newly added constant variable value mustn't duplicate the existing one.
 *       You can delete the existed constant variable, but you must keep the constant variable
 *       value for it.
 *       The newly added constant variable value mustn't duplicate the keeped constant variable
 *       value for deleted constant variable.
 *
 *       For delete case, strongly suggest you update SDK and publish to customer in time
 *       because delete case is not backward compatibility.
 *
 *       Special note:
 *           please add mark to describe for your deleted constant variable, especially last one.
 *
 *       Example
 *           2023.01.01, version 1
 *           public static final int ACTION_REQ_SET_LTR     = ACTION_REQ_BASE + 1;
 *           // It is just the last constant variable
 *           public static final int ACTION_REQ_REM_LTR     = ACTION_REQ_BASE + 2;
 *
 *           2023.03.01, version 2
 *           public static final int ACTION_REQ_SET_LTR     = ACTION_REQ_BASE + 1;
 *           // keep the deleted constant variable here
 *           // It is just the last constant variable
 *           // public static final int ACTION_REQ_REM_LTR  = ACTION_REQ_BASE + 2;
 *
 *           2023.04.01, version 3
 *           public static final int ACTION_REQ_SET_LTR     = ACTION_REQ_BASE + 1;
 *           // keep the deleted constant variable here
 *           // It is just the last constant variable
 *           // public static final int ACTION_REQ_REM_LTR  = ACTION_REQ_BASE + 2;
 *           public static final int ACTION_REQ_STR_MNR     = ACTION_REQ_BASE + 3;
 *
 * @hide
 */
public class FastSwitchInfo implements Parcelable {

    /**
     * FastSwitch base version, non function, that is to say it is unsupported.
     */
    public static final int FASTSWITCH_VERSION_BASE = 0;

    /**
     * FastSwitch version constant, for underlying function upgrages.
     */
    public static final int FASTSWITCH_VERSION_1             = FASTSWITCH_VERSION_BASE + 1;

    /**
     * Network type.
     * Move to NetworkPolicyManager.java if other feature uses them later.
     * NOTE: Corresponding to the network type in INOSListener.aidl, should be modifed together.
     */
    public static final int NETWORK_TYPE_BASE = 1;
    public static final int NETWORK_TYPE_UNKNOWN              = NETWORK_TYPE_BASE << 0;
    public static final int NETWORK_TYPE_MOBILE               = NETWORK_TYPE_BASE << 1;
    public static final int NETWORK_TYPE_WIFI                 = NETWORK_TYPE_BASE << 2;
    public static final int NETWORK_TYPE_BLUETOOTH            = NETWORK_TYPE_BASE << 3;
    public static final int NETWORK_TYPE_ETHERNET             = NETWORK_TYPE_BASE << 4;
    public static final int NETWORK_TYPE_MAX                  = NETWORK_TYPE_BASE << 5;

    /**
     * Actions:
     *     REQ = Request, REPORT = EVENT.
     *     GET = Get, SET = Set, REM = Remove.
     *     STR = Star, PAU = Pause, RES = Resume, STP = Stop.
     */
    public static final int ACTION_REQ_BASE = 0;
    /* Add info listener */
    public static final int ACTION_REQ_SET_LTR                 = ACTION_REQ_BASE + 1;
    public static final int ACTION_REQ_REM_LTR                 = ACTION_REQ_BASE + 2;
    /* Start/pause/resume/stop monitor */
    public static final int ACTION_REQ_STR_MNR                 = ACTION_REQ_BASE + 3;
    public static final int ACTION_REQ_PAU_MNR                 = ACTION_REQ_BASE + 4;
    public static final int ACTION_REQ_RES_MNR                 = ACTION_REQ_BASE + 5;
    public static final int ACTION_REQ_STP_MNR                 = ACTION_REQ_BASE + 6;
    /* Get monitor state */
    public static final int ACTION_REQ_GET_STA                 = ACTION_REQ_BASE + 7;
    /* Set/get monitor sensitivity */
    public static final int ACTION_REQ_SET_MS                  = ACTION_REQ_BASE + 8;
    public static final int ACTION_REQ_GET_MS                  = ACTION_REQ_BASE + 9;
    /* Set/Remove/Remove all UID/LINKINFO */
    public static final int ACTION_REQ_SET_UID                 = ACTION_REQ_BASE + 10;
    public static final int ACTION_REQ_REM_UID                 = ACTION_REQ_BASE + 11;
    public static final int ACTION_REQ_REM_ALL_UID             = ACTION_REQ_BASE + 12;
    public static final int ACTION_REQ_SET_LIN                 = ACTION_REQ_BASE + 13;
    public static final int ACTION_REQ_REM_LIN                 = ACTION_REQ_BASE + 14;
    public static final int ACTION_REQ_REM_ALL_LIN             = ACTION_REQ_BASE + 15;
    /* Get local link quality */
    public static final int ACTION_REQ_GET_LLQ                 = ACTION_REQ_BASE + 16;
    /* Get full link quality */
    public static final int ACTION_REQ_GET_FLQ                 = ACTION_REQ_BASE + 17;
    /* Get signal strength */
    public static final int ACTION_REQ_GET_SS                  = ACTION_REQ_BASE + 18;
    /* Get NOS service capability */
    public static final int ACTION_REQ_GET_NSC                 = ACTION_REQ_BASE + 19;
    /* General action used to set/get */
    public static final int ACTION_REQ_GEN_ACT                 = ACTION_REQ_BASE + 20;

    // { Sub actions of ACTION_REQ_GEN_ACT, don't occupy main action sequence number.
    // Why design sub action? The reason is that this requirement is just a customer tune request.
    // Not a real feedback design or function requirement. Must contain set/get/getsvr
    public static final int ACTION_SUB_REQ_BASE                  = 0;
    /* Used to set / get L2 RTT threshold */
    public static final int ACTION_SUB_REQ_SET_L2RTT_THR           = ACTION_SUB_REQ_BASE + 1;
    public static final int ACTION_SUB_REQ_GET_L2RTT_THR           = ACTION_SUB_REQ_BASE + 2;
    /* Used to get Suggested Value Range (SVR) of L2 RTT threshold */
    public static final int ACTION_SUB_REQ_GET_L2RTT_THR_SVR       = ACTION_SUB_REQ_BASE + 3;
    /* Used to set / get L2 RTT low/high interference environment (LIE/HIE) threshold */
    public static final int ACTION_SUB_REQ_SET_L2RTT_IE_THR        = ACTION_SUB_REQ_BASE + 4;
    public static final int ACTION_SUB_REQ_GET_L2RTT_IE_THR        = ACTION_SUB_REQ_BASE + 5;
    /* Used to get Suggested Value Range (SVR) of L2 RTT LIE/HIE threshold */
    public static final int ACTION_SUB_REQ_GET_L2RTT_IE_THR_SVR    = ACTION_SUB_REQ_BASE + 6;
    /* Used to set / get L4 RTT threshold */
    public static final int ACTION_SUB_REQ_SET_L4RTT_THR           = ACTION_SUB_REQ_BASE + 7;
    public static final int ACTION_SUB_REQ_GET_L4RTT_THR           = ACTION_SUB_REQ_BASE + 8;
    /* Used to get Suggested Value Range (SVR) of L4 RTT IE threshold */
    public static final int ACTION_SUB_REQ_GET_L4RTT_THR_SVR       = ACTION_SUB_REQ_BASE + 9;
    /* Used to set / get RSSI threshold */
    public static final int ACTION_SUB_REQ_SET_RSSI_THR            = ACTION_SUB_REQ_BASE + 10;
    public static final int ACTION_SUB_REQ_GET_RSSI_THR            = ACTION_SUB_REQ_BASE + 11;
    /* Used to get Suggested Value Range (SVR) of RSSI threshold */
    public static final int ACTION_SUB_REQ_GET_RSSI_THR_SVR        = ACTION_SUB_REQ_BASE + 12;
    /* Used to set / get RSSI large weight (LWEI) threshold */
    public static final int ACTION_SUB_REQ_SET_RSSI_LWEI_THR       = ACTION_SUB_REQ_BASE + 13;
    public static final int ACTION_SUB_REQ_GET_RSSI_LWEI_THR       = ACTION_SUB_REQ_BASE + 14;
    /* Used to get Suggested Value Range (SVR) of RSSI LWEI threshold */
    public static final int ACTION_SUB_REQ_GET_RSSI_LWEI_THR_SVR   = ACTION_SUB_REQ_BASE + 15;
    /* Used to set / get reconfirm (REC) threshold */
    public static final int ACTION_SUB_REQ_SET_REC_THR             = ACTION_SUB_REQ_BASE + 16;
    public static final int ACTION_SUB_REQ_GET_REC_THR             = ACTION_SUB_REQ_BASE + 17;
    /* Used to get Suggested Value Range (SVR) of REC threshold */
    public static final int ACTION_SUB_REQ_GET_REC_THR_SVR         = ACTION_SUB_REQ_BASE + 18;
    /* Used to debug or pre-research project */
    public static final int ACTION_SUB_REQ_DBG_BASE                = 500;
    /* Used to set/get debug parameter into/from fastswitch */
    public static final int ACTION_SUB_REQ_DBG_SET_FS              = ACTION_SUB_REQ_DBG_BASE + 1;
    public static final int ACTION_SUB_REQ_DBG_GET_FS              = ACTION_SUB_REQ_DBG_BASE + 2;
    /* Used to get Suggested Value Range (SVR) of debug parameter from fastswitch */
    public static final int ACTION_SUB_REQ_DBG_GET_FS_SVR          = ACTION_SUB_REQ_DBG_BASE + 3;
    // }

    /* Only used by platform to enable/disable (switch) or query fastswitch status */
    public static final int ACTION_REQ_FEA_OPS                 = ACTION_REQ_BASE + 21;

    // Add new request here please and MODIFY the below NUM at same time.
    public static final int ACTION_REQ_NUM = ACTION_REQ_FEA_OPS;

    public static final int ACTION_REPORT_BASE = 100;
    /* Report monitor state */
    public static final int ACTION_REPORT_STA                 = ACTION_REPORT_BASE + 1;
    /* Report monitor sensitivity */
    public static final int ACTION_REPORT_MS                  = ACTION_REPORT_BASE + 2;
    /* Report local link quality */
    public static final int ACTION_REPORT_LLQ                 = ACTION_REPORT_BASE + 3;
    /* Report full link quality */
    public static final int ACTION_REPORT_FLQ                 = ACTION_REPORT_BASE + 4;
    /* Report signal strength */
    public static final int ACTION_REPORT_SS                  = ACTION_REPORT_BASE + 5;
    /* General report used to callback */
    public static final int ACTION_REPORT_GEN                 = ACTION_REPORT_BASE + 6;

    // { Sub actions of ACTION_REPORT_GEN, don't occupy main action sequence number.
    // Why design sub action? The reason is that this requirement is just a customer tune request.
    // Not a real feedback or function requirement.
    public static final int ACTION_SUB_REPORT_BASE                 = 0;
    /* Report L2 RTT threshold (SVR) */
    public static final int ACTION_SUB_REPORT_L2RTT_THR            = ACTION_SUB_REPORT_BASE + 1;
    public static final int ACTION_SUB_REPORT_L2RTT_THR_SVR        = ACTION_SUB_REPORT_BASE + 2;
    /* Report L2 RTT low/high interference environment (LIE/HIE) threshold (SVR) */
    public static final int ACTION_SUB_REPORT_L2RTT_IE_THR         = ACTION_SUB_REPORT_BASE + 3;
    public static final int ACTION_SUB_REPORT_L2RTT_IE_THR_SVR     = ACTION_SUB_REPORT_BASE + 4;
    /* Report L4 RTT threshold (SVR) */
    public static final int ACTION_SUB_REPORT_L4RTT_THR            = ACTION_SUB_REPORT_BASE + 5;
    public static final int ACTION_SUB_REPORT_L4RTT_THR_SRV        = ACTION_SUB_REPORT_BASE + 6;
    /* Report RSSI threshold (SVR) */
    public static final int ACTION_SUB_REPORT_RSSI_THR             = ACTION_SUB_REPORT_BASE + 7;
    public static final int ACTION_SUB_REPORT_RSSI_THR_SRV         = ACTION_SUB_REPORT_BASE + 8;
    /* Report RSSI large weight (LWEI) threshold (SVR) */
    public static final int ACTION_SUB_REPORT_RSSI_LWEI_THR        = ACTION_SUB_REPORT_BASE + 9;
    public static final int ACTION_SUB_REPORT_RSSI_LWEI_THR_SRV    = ACTION_SUB_REPORT_BASE + 10;
    /* Report reconfirm (REC) threshold (SVR) */
    public static final int ACTION_SUB_REPORT_REC_THR              = ACTION_SUB_REPORT_BASE + 11;
    public static final int ACTION_SUB_REPORT_REC_THR_SRV          = ACTION_SUB_REPORT_BASE + 12;
    /* Used to debug or pre-research project */
    public static final int ACTION_SUB_REP_DBG_BASE                = 500;
    /* Report debug information from fastswitch */
    public static final int ACTION_SUB_REP_DBG_FS_INFO             = ACTION_SUB_REP_DBG_BASE + 1;
    public static final int ACTION_SUB_REP_DBG_FS_INFO_SRV         = ACTION_SUB_REP_DBG_BASE + 2;
    // }

    // Add new report/event here please and MODIFY the below NUM at same time.
    public static final int ACTION_REPORT_NUM = ACTION_REPORT_GEN - ACTION_REPORT_BASE;

    // Only for aligning Native layer.
    public static final int ACTION_NF_BASE = 200;
    /* Notify screen state */
    public static final int ACTION_NF_SCR_STA                 = ACTION_NF_BASE + 1;

    /**
     * Signal strength level. For WIFI and modem
     * Move to NetworkPolicyManager.java if other feature uses them later.
     * WIFI, based on RSSI
     *
     * MODEM, based on RSRP, maybe including RSSI, SINR or CQI indices.
     *      GREAT:     RSRP in (-85dBm  ~        ]
     *      GOOD:      RSRP in [-95dBm  ~  -85dBm)
     *      MODERATE:  RSRP in [-105dBm ~  -95dBm)
     *      POOR:      RSRP in [-115dBm ~ -105dBm)
     *      WORSE:     RSRP in [        ~ -115dBm)
     * NOTE: Corresponding to the signal strength in INOSListener.aidl, should be modifed together.
     */
    public static final int SIGNAL_STRENGTH_BASE = 0;
    public static final int SIGNAL_STRENGTH_NONE_OR_UNKNOWN   = SIGNAL_STRENGTH_BASE + 1;
    public static final int SIGNAL_STRENGTH_WORSE             = SIGNAL_STRENGTH_BASE + 2;
    public static final int SIGNAL_STRENGTH_POOR              = SIGNAL_STRENGTH_BASE + 3;
    public static final int SIGNAL_STRENGTH_MODERATE          = SIGNAL_STRENGTH_BASE + 4;
    public static final int SIGNAL_STRENGTH_GOOD              = SIGNAL_STRENGTH_BASE + 5;
    public static final int SIGNAL_STRENGTH_GREAT             = SIGNAL_STRENGTH_BASE + 6;
    public static final int SIGNAL_STRENGTH_NUM = SIGNAL_STRENGTH_GREAT;

    /**
     * Monitor Sensitivity. It is a comprehensive index, including but not limited to RTT/RSSI.
     * Move to NetworkPolicyManager.java if other feature uses them later.
     * NOTE: Corresponding to the sensitivity in INOSListener.aidl, should be modifed together.
     */
    public static final int SENSITIVITY_LEVEL_BASE = 0;
    public static final int SENSITIVITY_LEVEL_UNKNOWN         = SENSITIVITY_LEVEL_BASE + 1;
    public static final int SENSITIVITY_LEVEL_LOW             = SENSITIVITY_LEVEL_BASE + 2;
    public static final int SENSITIVITY_LEVEL_MEDIUM          = SENSITIVITY_LEVEL_BASE + 3;
    public static final int SENSITIVITY_LEVEL_HIGH            = SENSITIVITY_LEVEL_BASE + 4;
    public static final int SENSITIVITY_LEVEL_NUM = SENSITIVITY_LEVEL_HIGH;

    /**
     * Local/Full link quality. It is a comprehensive index, including but not limited to RTT.
     * Move to NetworkPolicyManager.java if other feature uses them later.
     * NOTE: Corresponding to the link quality in INOSListener.aidl, should be modifed together.
     */
    public static final int LINKQUALITY_LEVEL_BASE = 0;
    public static final int LINKQUALITY_LEVEL_UNKNOWN         = LINKQUALITY_LEVEL_BASE + 1;
    public static final int LINKQUALITY_LEVEL_WORSE           = LINKQUALITY_LEVEL_BASE + 2;
    public static final int LINKQUALITY_LEVEL_POOR            = LINKQUALITY_LEVEL_BASE + 3;
    public static final int LINKQUALITY_LEVEL_MODERATE        = LINKQUALITY_LEVEL_BASE + 4;
    public static final int LINKQUALITY_LEVEL_GOOD            = LINKQUALITY_LEVEL_BASE + 5;
    public static final int LINKQUALITY_LEVEL_GREAT           = LINKQUALITY_LEVEL_BASE + 6;
    public static final int LINKQUALITY_LEVEL_NUM = LINKQUALITY_LEVEL_GREAT;

    /**
     * Monitor state, pause will enter IDLE, resume will enter RUNNING.
     * StateMachine: NotStart -> Running -> Idle(PAUSE) ->Running -> NotStart(Stoped)
     * NOTE: Corresponding to the state in INOSListener.aidl, should be modifed together.
     */
    public static final int MONITOR_STATE_BASE = 0;
    public static final int MONITOR_STATE_UNKNOWN             = MONITOR_STATE_BASE + 1;
    public static final int MONITOR_STATE_NOTSTART            = MONITOR_STATE_BASE + 2;
    public static final int MONITOR_STATE_RUNNING             = MONITOR_STATE_BASE + 3;
    public static final int MONITOR_STATE_IDLE                = MONITOR_STATE_BASE + 4;
    public static final int MONITOR_STATE_NUM = MONITOR_STATE_IDLE;

    /**
     * FastSwitch Bundle key constant.
     */
    // the following Bundle keys own string values
    public static final String BK_IF_NAME             = "interfacename";
    public static final String BK_IF_INDEX            = "interfaceindex";
    public static final String BK_NW_TYPE             = "networktype";
    // used by APK requests and NOS server report
    // APK request: only one pid is allowed, provide it to NOS server to avoid duplicating action.
    // NOS server report: must be one or more than one pid with delimiter comma(,)
    public static final String BK_APK_PID             = "apkpid";
    // used by APK requests
    // APK transfers it to NOS server at startMonitor to let NOS server sort out list of
    // applications's maintained in NOS server to make sure NOS server runs when it is indeed
    // necessary.
    // In short, NOS server should sort out the maintained capability/pid/uid tables to
    // clean map relationship for the exited APK that didn't call stopMonitor regularly.
    // Why need UID? because pid can be reused, but UID is unique.
    // Why use PID for APK request? because UID can be shared but PID is unique at same time.
    // Notice: Neither PID nor UID can resist malicious use.
    public static final String BK_APK_UID             = "apkuid";
    // used by APK requests
    public static final String BK_APK_CAP             = "apkcapability";
    // used by NOS server report
    public static final String BK_PF_CAP              = "platformcapability";
    // used by NOS server report to explain the reason of unexpected error.
    // Using simple phrase with no space, like "duplicatedStartMonitor" or "errno1".
    // Mustn't include colon(:) and comma(,)
    public static final String BK_ERR_REASON          = "errorreason";
    // Bundle keys for parameter operation(THR:threshold, IE: interference environment).
    // Extended parameter operation actions. Value format ACT
    // ACT is from sub-action (EXT: extension action) of ACTION_REQ_GEN_ACT.
    public static final String BK_PARM_ACT_EXT        = "parmactext";
    // Network quality factor. Value format THR.
    // L2 RTT >= THR will be thought as poor network, maybe really poor or affected by disturbance.
    public static final String BK_PARM_L2RTT_THR      = "parml2rttthr"; // default 5ms
    // Network quality factor. Value format LIE/MIE/HIE/SIE. Default: 10/20/40/90.
    // Count of (L2 RTT >= THR) per 10s will be thought as poor network, otherwise good.
    // They correspond low interference environment (LIE), medium interference environment (MIE),
    // high interference environment (HIE) and strongest interference environment (SIE).
    // Value can be "JXNDEF/20/40/80" that means 10/20/40/80.
    public static final String BK_PARM_L2RTT_IE_THR   = "parml2rttiethr";
    // Network quality factor. Value format LEVEL|LOW/MID/HIG/TOP.
    // Supported levels: level0(sensitive mode) and level1(normal mode)
    // Network state: =< LOW: great, LOW < =< MID: good, MID < =< HIG: moderate,
    //                HIG < =< TOP: poor, > TOP: worst.
    // Default: level0|10/30/50/80 and level1|20/50/60/80.
    // Value can be "JXNDEF|10/30/50/80" that mean to use level1|10/30/50/80.
    public static final String BK_PARM_L4RTT_THR      = "parml4rttthr";
    // Signal quality factor. Value format BAD/LOW/MID/HIG.
    // Signal quality: =< BAD: worse, BAD < =< LOW: poor, LOW < =< MID: fair, MID < =< HIG: good,
    //                 > HIG: excellent.
    // Default: -88/-77/-66/-55dBm.
    // Value can be "JXNDEF/-77/-66/-50" that mean -88/-77/-66/-50.
    public static final String BK_PARM_RSSI_THR       = "parmrssithr";
    // Signal quality factor. Value format S2D/D2S. (LWEI: large weight) default: -66/-75dBm.
    // S2D(single to dual): Large weight signal quality factor from single link to dual link.
    // D2S(dual to single): Large weight signal quality factor from dual link to single link.
    // Value can be "JXNDEF/-50" that mean -66/-50
    public static final String BK_PARM_RSSI_LWEI_THR  = "parmrssilweithr";
    // Signal quality reconfirm factor. Value format THR. (REC: reconfirm) default: 1000ms.
    // The time interval between preparing and reconfirm whether switch to single link in dual link
    // Value can be "JXNDEF" that mean 1000.
    public static final String BK_PARM_REC_THR        = "parmreconfirmthr";
    // General parameter key used by debug or pre-research project.
    // Value format key|value. Only one key|value for one request.
    public static final String BK_PARM_DEBUG          = "parmdebug";
    // The longest time "GET" sub-actions of general action wait. Default 500ms.
    // Value format value. Only framework/SDK need this key.
    public static final String BK_TIMEOUT_INMS        = "TIMEOUTINMS";
    // Add new BK_xx before here and add it into MAP_BKS_SUBACT at same time to avoid modifying
    // convertJavaInfoToNative in NPSFastSwitch.java.

    // Feature control key. Only used by framework.
    // -1 for disable, 1 for enable.
    public static final String BK_FEATURE_CTRL        = "featurecontroller";

    // Default value indicator of all bundle parameter key(nos service set default for this).
    public static final String BV_PARM_DEF            = "JXNDEF"; // indicator of default
    // BV for Query operation. Return value format x/y/z/...
    // Sample: bundle.putString(BK_PARM_DEBUG, BV_PARM_QRY) to query value of non-subkey item.
    // Sample: bundle.putString(BK_PARM_DEBUG, BV_PARM_QRY + "|" + subkey) to query value of subkey
    public static final String BV_PARM_QRY            = "JXNQRY";
    // BV for Query range operation. Return value format f~c/f~c/f~c/... (f:floor, c:ceil).
    // Sample: bundle.putString(BK_PARM_DEBUG, BV_PARM_SVR) to query scope of non-subkey item.
    // Sample: bundle.putString(BK_PARM_DEBUG, BV_PARM_SVR + "|" + subkey) to query scope of subkey
    public static final String BV_PARM_SVR            = "JXNSVR";

    // Used to distinguish caller source.
    // "platform" and "sdk" are the values for NPM APK.
    // "platformprotect" and "sdkprotect" are the values for NPM protection.
    // "fwservice" is the value for NPS.
    public static final String BV_REQ_SOURCE_P        = "platform";
    public static final String BV_REQ_SOURCE_PP       = "platformprotect";
    public static final String BV_REQ_SOURCE_S        = "sdk";
    public static final String BV_REQ_SOURCE_SP       = "sdkprotect";
    public static final String BV_REQ_SOURCE_F        = "fwservice";
    public static final String BK_REQ_SOURCE          = "requestsource";
    // Used to notify NOS server screen state.
    public static final String BV_SCR_STA_ON          = "screenon";
    public static final String BV_SCR_STA_OFF         = "screenoff";
    public static final String BK_SCR_STA             = "screenstate";

    public static final Map<String, Integer> MAP_BKS_SUBACT = Map.ofEntries(
        Map.entry(BK_PARM_ACT_EXT, ACTION_SUB_REQ_BASE),
        Map.entry(BK_PARM_L2RTT_THR, ACTION_SUB_REQ_SET_L2RTT_THR),
        Map.entry(BK_PARM_L2RTT_IE_THR, ACTION_SUB_REQ_SET_L2RTT_IE_THR),
        Map.entry(BK_PARM_L4RTT_THR, ACTION_SUB_REQ_SET_L4RTT_THR),
        Map.entry(BK_PARM_RSSI_THR, ACTION_SUB_REQ_SET_RSSI_THR),
        Map.entry(BK_PARM_RSSI_LWEI_THR, ACTION_SUB_REQ_SET_RSSI_LWEI_THR),
        Map.entry(BK_PARM_REC_THR, ACTION_SUB_REQ_SET_REC_THR),
        Map.entry(BK_PARM_DEBUG, ACTION_SUB_REQ_DBG_SET_FS)
    );

    public static final Map<String, String> MAP_SUBACT_SUBREP = Map.ofEntries(
        Map.entry(String.valueOf(ACTION_SUB_REQ_GET_L2RTT_THR),
                  String.valueOf(ACTION_SUB_REPORT_L2RTT_THR)),
        Map.entry(String.valueOf(ACTION_SUB_REQ_GET_L2RTT_THR_SVR),
                  String.valueOf(ACTION_SUB_REPORT_L2RTT_THR_SVR)),
        Map.entry(String.valueOf(ACTION_SUB_REQ_GET_L2RTT_IE_THR),
                  String.valueOf(ACTION_SUB_REPORT_L2RTT_IE_THR)),
        Map.entry(String.valueOf(ACTION_SUB_REQ_GET_L2RTT_IE_THR_SVR),
                  String.valueOf(ACTION_SUB_REPORT_L2RTT_IE_THR_SVR)),
        Map.entry(String.valueOf(ACTION_SUB_REQ_GET_L4RTT_THR),
                  String.valueOf(ACTION_SUB_REPORT_L4RTT_THR)),
        Map.entry(String.valueOf(ACTION_SUB_REQ_GET_L4RTT_THR_SVR),
                  String.valueOf(ACTION_SUB_REPORT_L4RTT_THR_SRV)),
        Map.entry(String.valueOf(ACTION_SUB_REQ_GET_RSSI_THR),
                  String.valueOf(ACTION_SUB_REPORT_RSSI_THR)),
        Map.entry(String.valueOf(ACTION_SUB_REQ_GET_RSSI_THR_SVR),
                  String.valueOf(ACTION_SUB_REPORT_RSSI_THR_SRV)),
        Map.entry(String.valueOf(ACTION_SUB_REQ_GET_RSSI_LWEI_THR),
                  String.valueOf(ACTION_SUB_REPORT_RSSI_LWEI_THR)),
        Map.entry(String.valueOf(ACTION_SUB_REQ_GET_RSSI_LWEI_THR_SVR),
                  String.valueOf(ACTION_SUB_REPORT_RSSI_LWEI_THR_SRV)),
        Map.entry(String.valueOf(ACTION_SUB_REQ_GET_REC_THR),
                  String.valueOf(ACTION_SUB_REPORT_REC_THR)),
        Map.entry(String.valueOf(ACTION_SUB_REQ_GET_REC_THR_SVR),
                  String.valueOf(ACTION_SUB_REPORT_REC_THR_SRV)),
        Map.entry(String.valueOf(ACTION_SUB_REQ_DBG_GET_FS),
                  String.valueOf(ACTION_SUB_REP_DBG_FS_INFO)),
        Map.entry(String.valueOf(ACTION_SUB_REQ_DBG_GET_FS_SVR),
                  String.valueOf(ACTION_SUB_REP_DBG_FS_INFO_SRV))
    );

    /**
     * Which network type the action will happens on.
     * It is a required parameter(NotNull) for both set/get request(function call) and
     * report(listener callback).
     * Used to specify operation network type.
     */
    private int mNetworkType = -1;

    /**
     * Which action to do. Related to the corresponding function implement.
     * It is a required parameter(NotNull) for both set/get request(function call) and
     * report(listener callback).
     * Used to specify operation.
     */
    private int mAction = -1;

    /**
     * The signal strength for the special network type.
     * It is a required parameter(NotNull) for both get request(function call) and report(listener
     * callback) when you do signal strength related action.
     * Used to indicate network signal strength.
     */
    private int mSignalStrength = -1;  // RSSI for WIFI and RSRP for modem

    /**
     * The monitor sensitivity for the special network type.
     * It is a required parameter(NotNull) for both set/get request(function call) and
     * report(listener callback) when you do monitor sensitivity related action.
     * Used to design monitor algorithm.
     */
    private int mMonitorSensitivity = -1;

    /**
     * The Local/Full link quality for the special network type.
     * It is a required parameter(NotNull) for both get request(function call) and
     * report(listener callback) when you do link quality related action.
     * Difference:
     *     Local is UE <-> [WIFI AP, MOBILE BS], Full is UE <-> [WIFI AP, MOBILE BS] <-> REMOTE UE.
     *     Good local link quality doesn't represent full link quality must be good.
     * Used to indicate network link quality.
     */
    private int mLocalLinkQuality = -1;
    private int mFullLinkQuality = -1;

    /**
     * The monitor work state for the special network type.
     * It is a required parameter(NotNull) for both get request(function call) and
     * report(listener callback) when you do monitor state related action.
     * Used to indicate monitor work state.
     */
    private int mMonitorState = -1;

    /**
     * For extension.
     * It can be used in both set/get request(function call) and report(listener callback) when
     * service upgrate with only need more parameter support.
     *
     * Used to indicate more information.
     * Usage:
     *     Bundle b = new Bundle();
     *     b.putString("key1", "value1");
     *     b.putInt("key2", "value2");
     *     b.putX("key3", "value3");   // Suggest basic data type now.
     *
     *     b.containsKey("key1");      // it is better before use it.
     *     b.getString("key1", null);  // default null.
     *     b.getInt("key2", -1);       // default -1.
     *     b.getX("key3", x);          // default x.
     */
    private Bundle mBundle = null;

    /**
     * The uid of application to be monitored for the special network type.
     * It is a required parameter(NotNull) for both set request(function call) and
     * report(listener callback) when you do uid monitor related.
     * Difference:
     *     Local is UE <-> [WIFI AP, MOBILE BS], Full is UE <-> [WIFI AP, MOBILE BS] <-> REMOTE UE.
     *     Good local link quality doesn't represent full link quality must be good.
     * Used to indicate uid of application.
     */
    private int mUid = -1;

    /**
     * mSrcIp and mDstIp should be the same IP version.
     * 0.0.0.0/0 (ipv4) and ::/0 (ipv6) mean to match all IP address.
     */
    private String mSrcIp = null;
    private String mDstIp = null;

    /**
     * Only be valid for tcp and udp of mProto, -1 for any port.
     */
    private int mSrcPort = -1;
    private int mDstPort = -1;

    /**
     * 1: tcp, 2: udp, -1: any.
     */
    private int mProto = -1;

    /**
     * FastSwitch copy constructor.
     * @param info to be configured to input.
     */
    public FastSwitchInfo(FastSwitchInfo info) {
        this(info.mNetworkType, info.mAction, info.mSignalStrength, info.mMonitorSensitivity,
                info.mLocalLinkQuality, info.mFullLinkQuality, info.mMonitorState, info.mBundle,
                info.mUid, info.mSrcIp, info.mDstIp, info.mSrcPort, info.mDstPort, info.mProto);
    }

    /**
     * FastSwitch constructor.
     *
     * Applicable to the functions corresponding to the following actions:
     *     ACTION_REQ_STR_MNR, ACTION_REQ_PAU_MNR, ACTION_REQ_RES_MNR, ACTION_REQ_STP_MNR.
     *     ACTION_REQ_GET_STA, ACTION_REQ_GET_MS, ACTION_REQ_REM_ALL_UID, ACTION_REQ_REM_ALL_LIN.
     *     ACTION_REQ_GET_LLQ, ACTION_REQ_GET_FLQ, ACTION_REQ_GET_SS, ACTION_REQ_GET_NSC
     *
     * @param type which network type to operated on.
     * @param action what to do.
     *     MUST for configFastSwitchInfo & configFastSwitchInfoWithIntResult.
     *     -1 for other function please.
     * @param b bundle object. null for non-extension. For extension, see note of variable bundle.
     */
    public FastSwitchInfo(int type, int action, Bundle b) {
        this(type, action, -1, -1, -1, -1, -1, b, -1, null, null, -1, -1, -1);
    }

    /**
     * FastSwitch constructor.
     *
     * Applicable to the functions corresponding to the following actions:
     *     ACTION_REQ_SET_MS, ACTION_REPORT_MS    => parameter p indicates monitor sensitivity.
     *     ACTION_REQ_SET_UID, ACTION_REQ_REM_UID => parameter p indicates uid.
     *     ACTION_REPORT_STA                      => parameter p indicates monitor work state.
     *     ACTION_REPORT_LLQ, ACTION_REPORT_FLQ   => parameter p indicates local/full link quality.
     *     ACTION_REPORT_SS                       => parameter p indicates signal strength.
     *
     * NOTE: only the corresponding variable is valid for the fixed action.
     *       one constructor for different action due to same data type for these actions.
     *
     * @param type which network type to operated on.
     * @param action what to do.
     *     MUST for configFastSwitchInfo & onFastSwitchInfoNotify.
     *     -1 for other function please.
     * @param p different meanings for different action.
     * @param b bundle object. null for non-extension. For extension, see note of variable bundle.
     */
    public FastSwitchInfo(int type, int action, int p, Bundle b) {
        this(type, action, p, p, p, p, p, b, p, null, null, -1, -1, -1);
    }

    /**
     * FastSwitch constructor.
     *
     * Applicable to the functions corresponding to the following actions:
     *     ACTION_REQ_SET_LIN, ACTION_REQ_REM_LIN
     *
     * @param type which network type to operated on.
     * @param action what to do.
     *     MUST for configFastSwitchInfo.
     *     -1 for other function please.
     * @param srcIp source ip, 0.0.0.0/0 and ::/0 to bypass it.
     * @param dstIp destination ip, 0.0.0.0/0 and ::/0 to bypass it.
     * @param srcPort source port, -1 for any and only valid for protocl with 1 or 2.
     * @param dstPort destination port, -1 for any and only valid for protocl with 1 or 2.
     * @param proto protocol, 1 for tcp, 2 for udp and -1 for any.
     * @param b bundle object. null for non-extension. For extension, see note of variable bundle.
     */
    public FastSwitchInfo(int type, int action, String srcIp, String dstIp, int srcPort,
            int dstPort, int proto, Bundle b) {
        this(type, action, -1, -1, -1, -1, -1, b, -1, srcIp, dstIp, srcPort, dstPort, proto);
    }

    /**
     * FastSwitch constructor, all parameters.
     * @param type which network type the action will operate on.
     * @param act what to do. Related to the corresponding function implement.
     * @param ss the signal strength for the special network type.
     * @param ms which level of monitor sensitivity to run monitor based on.
     * @param llq the local link quality of the special network type.
     * @param flq the full link quality of the special network type.
     * @param sta the monitor work state of the special network type.
     * @param b bundle object. null for non-extension. For extension, see note of variable bundle.
     * @param uid application uid.
     * @param srcIp source ip, 0.0.0.0/0 and ::/0 to bypass it.
     * @param dstIp destination ip, 0.0.0.0/0 and ::/0 to bypass it.
     * @param srcPort source port, -1 for any and only valid for protocl with 1 or 2.
     * @param dstPort destination port, -1 for any and only valid for protocl with 1 or 2.
     * @param proto protocol, 1 for tcp, 2 for udp and -1 for any.
     */
    public FastSwitchInfo(int type, int act, int ss, int ms, int llq, int flq, int sta, Bundle b,
        int uid, String srcIp, String dstIp, int srcPort, int dstPort, int proto) {
        this.mNetworkType = type;
        this.mAction = act;
        this.mSignalStrength = ss;
        this.mMonitorSensitivity = ms;
        this.mLocalLinkQuality = llq;
        this.mFullLinkQuality = flq;
        this.mMonitorState = sta;
        this.mBundle = b;
        this.mUid = uid;
        this.mSrcIp = srcIp;
        this.mDstIp = dstIp;
        this.mSrcPort = srcPort;
        this.mDstPort = dstPort;
        this.mProto = proto;
    }

    private FastSwitchInfo(Parcel in) {
        this.readFromParcel(in);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public final int getNetworkType() {
        return mNetworkType;
    }

    public final int getAction() {
        return mAction;
    }

    public final int getSignalStrength() {
        return mSignalStrength;
    }

    public final int getMonitorSensitivity() {
        return mMonitorSensitivity;
    }

    public final int getLocalLinkQuality() {
        return mLocalLinkQuality;
    }

    public final int getFullLinkQuality() {
        return mFullLinkQuality;
    }

    public final int getMonitorState() {
        return mMonitorState;
    }

    public final Bundle getBundle() {
        return mBundle;
    }

    /**
     * Not suggest, but version control need it.
     * @param b to be reset.
     */
    public final void resetBundle(Bundle b) {
        mBundle = b;
    }

    public int getUid() {
        return mUid;
    }

    public String getSrcIp() {
        return mSrcIp;
    }

    public String getDstIp() {
        return mDstIp;
    }

    public int getSrcPort() {
        return mSrcPort;
    }

    public int getDstPort() {
        return mDstPort;
    }

    public int getProto() {
        return mProto;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mNetworkType);
        out.writeInt(mAction);
        out.writeInt(mSignalStrength);
        out.writeInt(mMonitorSensitivity);
        out.writeInt(mLocalLinkQuality);
        out.writeInt(mFullLinkQuality);
        out.writeInt(mMonitorState);
        out.writeBundle(mBundle);
        out.writeInt(mUid);
        out.writeString(mSrcIp);
        out.writeString(mDstIp);
        out.writeInt(mSrcPort);
        out.writeInt(mDstPort);
        out.writeInt(mProto);
    }

    private void readFromParcel(Parcel in) {
        mNetworkType        = in.readInt();
        mAction             = in.readInt();
        mSignalStrength     = in.readInt();
        mMonitorSensitivity = in.readInt();
        mLocalLinkQuality   = in.readInt();
        mFullLinkQuality    = in.readInt();
        mMonitorState       = in.readInt();
        mBundle             = in.readBundle();
        mUid                = in.readInt();
        mSrcIp              = in.readString();
        mDstIp              = in.readString();
        mSrcPort            = in.readInt();
        mDstPort            = in.readInt();
        mProto              = in.readInt();
    }

    public static final Parcelable.Creator<FastSwitchInfo> CREATOR =
            new Parcelable.Creator<FastSwitchInfo>() {
        @Override
        public FastSwitchInfo createFromParcel(Parcel in) {
            return new FastSwitchInfo(in);
        }

        @Override
        public FastSwitchInfo[] newArray(int size) {
            return new FastSwitchInfo[size];
        }
    };

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("FastSwitchInfo(");
        sb.append(mNetworkType);
        sb.append(",");
        sb.append(mAction);
        sb.append(",");
        sb.append(mSignalStrength);
        sb.append(",");
        sb.append(mMonitorSensitivity);
        sb.append(",");
        sb.append(mLocalLinkQuality);
        sb.append(",");
        sb.append(mFullLinkQuality);
        sb.append(",");
        sb.append(mMonitorState);
        sb.append(",");
        sb.append(mBundle.toString());
        sb.append(",");
        sb.append(mUid);
        sb.append(",");
        sb.append(mSrcIp);
        sb.append(",");
        sb.append(mDstIp);
        sb.append(",");
        sb.append(mSrcPort);
        sb.append(",");
        sb.append(mDstPort);
        sb.append(",");
        sb.append(mProto);
        sb.append(")");
        return sb.toString();
    }
}
