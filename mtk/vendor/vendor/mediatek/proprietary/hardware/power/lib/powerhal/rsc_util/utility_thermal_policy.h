/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */

#ifndef ANDROID_UTILITY_THERMAL_POLICY_H
#define ANDROID_UTILITY_THERMAL_POLICY_H

#include <string>

using namespace std;

//BSP:add for thermal policy switch by jian.li at 20240424 start
extern int load_thm_api_policy(int idx, bool start);
extern int load_thm_api_policy_set_multiwindow(int idx, bool start);
extern int load_thm_api_policy_set_foreground_package(int idx, bool start, int next_index);
extern int load_thm_api_policy_start(int idx, void *scn);
extern int load_thm_api_policy_stop(int idx, void *scn);
extern int change_thm_policy_by_ambient_temperature(int threshold_with_thm_idx, void *scn);
extern int unchange_thm_policy_by_ambient_temperature(int threshold_with_thm_idx, void *scn);
extern int change_current_ambient_temperature(int temperature, void *scn);
extern int unchange_current_ambient_temperature(int temperature, void *scn);
extern int change_thm_policy_by_game_mode(int game_mode_with_thm_idx, void *scn);
extern int unchange_thm_policy_by_game_mode(int game_mode_with_thm_idx, void *scn);
extern int set_game_mode(int game_mode, void *scn);
extern int unset_game_mode(int game_mode, void *scn);
extern int set_fold_state(int fold_state, void *scn);
extern int unset_fold_state(int fold_state, void *scn);
extern int change_thm_policy_by_folded(int fold_state, void *scn);
extern int unchange_thm_policy_by_folded(int fold_state, void *scn);
extern int set_multiwindow_state(int multiwindow_state, void *scn);
extern int unset_multiwindow_state(int multiwindow_state, void *scn);
extern int close_thm_policy_by_engineer_mode(int close_state, void *scn);
extern int unclose_thm_policy_by_engineer_mode(int close_state, void *scn);
//BSP:add for thermal policy switch by jian.li at 20240424 end
//BSP:add for thermal feature by jian.li at 20251113 start
extern int setCPUFreqTranThermalCluster0(int value, void *scn);
extern int setCPUFreqTranThermalCluster1(int value, void *scn);
extern int setCPUFreqTranThermalCluster2(int value, void *scn);
extern int unsetCPUFreqTranThermalCluster0(int value, void *scn);
extern int unsetCPUFreqTranThermalCluster1(int value, void *scn);
extern int unsetCPUFreqTranThermalCluster2(int value, void *scn);
//BSP:add for thermal feature  by jian.li at 20251113 end
#endif // ANDROID_UTILITY_THERMAL_POLICY_H

