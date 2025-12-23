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

#define LOG_TAG "libPowerHal"

#include <stdio.h>
#include <stdint.h>
#include <unistd.h>
#include <string.h>
#include <sys/resource.h>
#include <sys/stat.h>
#include <dirent.h>
#include <ctype.h>
#include <cutils/properties.h>
#include <utils/Log.h>
#include <utils/RefBase.h>
#include <dlfcn.h>
#include <string.h>

#include "perfservice.h"
#include "utility_thermal.h"
#include "utility_thermal_policy.h"
#include "utility_fps.h"
#include "tran_common.h"
#include "common.h"
#include <stdlib.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <errno.h>

using namespace std;
//BSP:add for thermal policy switch by jian.li at 20240424 start
#include <vector>
using std::vector;
extern int load_thm_api(int idx, bool start);
extern int change_policy_by_socket(char *conf_name, int enable);

#define MAX_THERMAL_POLICY_SIZE 999
#define TRAN_THERMAL_POLICY_SWITCH_SUPPORT     "1"
#define TRAN_THERMAL_POLICY_SWITCH_RO    "ro.vendor.tran_thermal_switch.support"
#define TRAN_THERMAL_POLICY_ID_IN_MULTIWINDOW  "ro.vendor.tran_thermal_multiwindow_id"
#define TRAN_THERMAL_POLICY_FOLDED_STATE 1
#define TRAN_THERMAL_POLICY_MULTIWINDOW_STATE 1
#define TRAN_THERMAL_POLICY_INVALID_MULTIWINDOW_ID -1
typedef struct {
    char pkgName[PACK_NAME_MAX];
    int gameMode;
    int ambientTempThreshold;
    int foldedState;
    int orgThermalIdx;
    int newThermalIdx;
} thermal_policy_manager;
vector<thermal_policy_manager> g_thermal_policy_vector;
int g_current_ambientTemp = 0;
int g_thermal_idx = -1;
int g_thermal_start = 0;
int g_thermal_switch_support = 0;
int g_game_mode = 0;
int g_fold_state = 0;
extern char g_current_foreground_pack[PACK_NAME_MAX];
extern char g_current_paused_pack[PACK_NAME_MAX];
char g_started_foreground_pack[PACK_NAME_MAX] = {0};
int g_multiwindow_state = 0;
int g_multiwindow_thermal_idx = -1;
int g_multiwindow_thermal_start = 0;
int g_multiwindow_default_thermal_idx = get_property_value(TRAN_THERMAL_POLICY_ID_IN_MULTIWINDOW);

int load_thm_api_by_app_internal_switch(int idx){
    if (idx > 0) {
        if (g_thermal_idx > 0) {
            load_thm_api(g_thermal_idx, 0);//reset the privous state.
        }
        load_thm_api(idx, 1);//reassign new thermal conf.
    }
    return 0;
}

bool get_threshold_with_thmIdx(int threshold_with_thmIdx, int& threshold, int& thmIdx){
    //4501 -> 45 is temp threshold, 01 is thmIdx
    //5012 -> 50 is temp threshold, 12 is thmIdx
    //threshold_with_thmIdx should be 4 bit
    if (threshold_with_thmIdx < 100 || threshold_with_thmIdx > 9999){
        return false;
    }
    threshold = threshold_with_thmIdx/100;
    thmIdx = threshold_with_thmIdx%100;
    LOG_I("get_threshold_with_thmIdx threshold_with_thmIdx:%d, threshold:%d, thmIdx:%d.", threshold_with_thmIdx, threshold, thmIdx);
    if (threshold <= 0){
        return false;
    }
    return true;
}

int change_thm_policy_by_ambient_temperature(int threshold_with_thmIdx, void *scn){
    LOG_I("change_thm_policy_by_ambient_temperature threshold_with_thmIdx:%d, g_thermal_switch_support:%d g_thermal_policy_vector.size():%lu", threshold_with_thmIdx, g_thermal_switch_support, g_thermal_policy_vector.size());
    if (g_thermal_switch_support == 0 || g_thermal_policy_vector.size() >= MAX_THERMAL_POLICY_SIZE){
        return -1;
    }
    int i = 0;
    bool found = false;
    int threshold = 0;
    int thmIdx = 0;
    int index = g_thermal_policy_vector.size();

    bool ret = get_threshold_with_thmIdx(threshold_with_thmIdx, threshold, thmIdx);
    if (!ret){
        LOG_I("get_threshold_with_thmIdx threshold_with_thmIdx:%d, it is invalid, the format should like 4502.", threshold_with_thmIdx);
        return -1;
    }

    for (i = 0; i < g_thermal_policy_vector.size(); i++){
        if (strncmp(g_current_foreground_pack, g_thermal_policy_vector[i].pkgName, PACK_NAME_MAX - 1) == 0){
            found = true;
            index = i;
            break;
        }
    }
    if (found == false) {
        thermal_policy_manager* thermal_policy = (thermal_policy_manager*)malloc(sizeof(thermal_policy_manager));
        if (thermal_policy == NULL) {
            LOG_E("out of memory\n");
            return -1;
        }
        memset(thermal_policy, 0, sizeof(thermal_policy_manager));
        g_thermal_policy_vector.push_back(*thermal_policy);
    }
    strncpy(g_thermal_policy_vector[index].pkgName, g_current_foreground_pack, PACK_NAME_MAX - 1);
    g_thermal_policy_vector[index].ambientTempThreshold = threshold;
    g_thermal_policy_vector[index].newThermalIdx = thmIdx;

    for (i = 0; i < g_thermal_policy_vector.size(); i++){
        LOG_D("change_thm_policy_by_temperature g_thermal_policy_vector[%d].pkgName:%s gameMode:%d ambientTempThreshold:%d orgThermalIdx:%d newThermalIdx:%d",
            i, g_thermal_policy_vector[i].pkgName, g_thermal_policy_vector[i].gameMode, g_thermal_policy_vector[i].ambientTempThreshold, g_thermal_policy_vector[i].orgThermalIdx, g_thermal_policy_vector[i].newThermalIdx);
    }
    if (found == false ) {//Fix the first time to launcher the policy
        if (g_current_ambientTemp >= threshold && g_thermal_idx != thmIdx) {
            LOG_D("load_thm_api_by_app_internal_switch in change_thm_policy_by_ambient_temperature");
            load_thm_api_by_app_internal_switch(thmIdx);
        }
    }
    LOG_I("change_thm_policy_by_temperature threshold_with_thmIdx:%d, index:%d, threshold:%d, thmIdx:%d, g_current_foreground_pack:%s.",
        threshold_with_thmIdx, index, threshold, thmIdx, g_current_foreground_pack);
    return 0;
}

int unchange_thm_policy_by_ambient_temperature(int threshold_with_thmIdx, void *scn){
    LOG_I("unchange_thm_policy_by_ambient_temperature threshold_with_thmIdx:%d", threshold_with_thmIdx);
    return 0;
}

int change_current_ambient_temperature(int temperature, void *scn){
    LOG_I("change_current_ambient_temperature temperature:%d, g_thermal_switch_support:%d g_thermal_policy_vector.size():%lu", temperature, g_thermal_switch_support, g_thermal_policy_vector.size());
    if (g_thermal_switch_support == 0 || g_thermal_policy_vector.size() >= MAX_THERMAL_POLICY_SIZE){
        LOG_I("change_current_ambient_temperature g_thermal_switch_support == 0 || g_thermal_policy_vector.size() >= MAX_THERMAL_POLICY_SIZE");
        return -1;
    }
    if (temperature != g_current_ambientTemp){
        g_current_ambientTemp = temperature;
        int i = 0;
        int vectSize = g_thermal_policy_vector.size();
        if(vectSize > 0) {
            LOG_I("change_current_ambient_temperature g_thermal_policy_vector[0].pkgName:%s g_thermal_start:%d g_current_foreground_pack:%s",
                g_thermal_policy_vector[0].pkgName, g_thermal_start, g_current_foreground_pack);
        }
        for (i = 0; i < vectSize; i++){
            if (strncmp(g_current_foreground_pack, g_thermal_policy_vector[i].pkgName, PACK_NAME_MAX - 1) == 0 && g_thermal_policy_vector[i].ambientTempThreshold > 0){
                if (g_current_ambientTemp >= g_thermal_policy_vector[i].ambientTempThreshold){
                    if (g_thermal_idx != g_thermal_policy_vector[i].newThermalIdx){
                        //change the current newThermalIdx by high temperature
                        load_thm_api_by_app_internal_switch(g_thermal_policy_vector[i].newThermalIdx);
                    }
                } else {
                    if (g_thermal_idx != g_thermal_policy_vector[i].orgThermalIdx && g_thermal_policy_vector[i].orgThermalIdx > 0){
                        load_thm_api_by_app_internal_switch(g_thermal_policy_vector[i].orgThermalIdx);
                    }
                }
            }
        }
    }
    return 0;
}

int unchange_current_ambient_temperature(int temperature, void *scn){
    LOG_I("unchange_current_ambient_temperature temperature:%d", temperature);
    return 0;
}

bool splitNineDigitToThreeThree(int num, int& firstThreeDigits, int& middleThreeDigits, int& lastThreeDigits) {
    if (num < 100000000 || num > 999999999) {
        LOG_I("input nub isn't nine digit");
        return false;
    }
    firstThreeDigits = num / 1000000;
    middleThreeDigits = (num / 1000) % 1000;
    lastThreeDigits = num % 1000;
    LOG_I("splitNineDigitToThreeThree result:%03d, %03d, %03d\n", firstThreeDigits, middleThreeDigits, lastThreeDigits);
    return true;
}

bool splitSixDigitToTwoThree(int num, int& firstThreeDigits, int& lastThreeDigits) {
    if(num < 100000 || num > 999999) {
        LOG_I("input nub isn't six digit");
        return false;
    }
    firstThreeDigits = num / 1000;
    lastThreeDigits = num % 1000;

    LOG_I("splitSixDigitToTwoThree result:%03d, %03d\n", firstThreeDigits, lastThreeDigits);
    return true;
}

int change_thm_policy_by_game_mode(int game_mode_with_thmIdx, void *scn){
    LOG_I("change_thm_policy_by_game_mode game_mode_with_thmIdx:%d, g_thermal_switch_support:%d g_thermal_policy_vector.size():%lu begin", game_mode_with_thmIdx, g_thermal_switch_support, g_thermal_policy_vector.size());
    if (g_thermal_switch_support == 0 || g_thermal_policy_vector.size() >= MAX_THERMAL_POLICY_SIZE){//if it isn't thermal core 2.0, don't support it.
        LOG_I("change_thm_policy_by_game_mode g_thermal_switch_support == 0 || g_thermal_policy_vector.size() >= MAX_THERMAL_POLICY_SIZE");
        return -1;
    }
    int i = 0, j = 0;
    bool found = false;
    int gameMode = 0;
    int thmIdx = 0;
    int gameModes[3] = {0};
    int thmIdxes[3] = {0};
    int gameModeCount = 0;
    int index = 0;
    int vectSize = 0;

    if (game_mode_with_thmIdx < 100 && game_mode_with_thmIdx > 999999999){
        return -1;
    }
    if (game_mode_with_thmIdx > 99 && game_mode_with_thmIdx <= 999){
        gameModeCount = 1;
        if (get_threshold_with_thmIdx(game_mode_with_thmIdx, gameModes[0], thmIdxes[0]) == false){
            return -1;
        }
    } else if (game_mode_with_thmIdx > 999 && game_mode_with_thmIdx <= 999999){
        int firstThreeDigits = 0;
        int lastThreeDigits = 0;
        gameModeCount = 2;
        if (splitSixDigitToTwoThree(game_mode_with_thmIdx, firstThreeDigits, lastThreeDigits) == false){
            return -1;
        }
        if (get_threshold_with_thmIdx(firstThreeDigits, gameModes[0], thmIdxes[0]) == false){
            return -1;
        }
        if (get_threshold_with_thmIdx(lastThreeDigits, gameModes[1], thmIdxes[1]) == false){
            return -1;
        }
    } else if (game_mode_with_thmIdx > 999999 && game_mode_with_thmIdx <= 999999999){
        int firstThreeDigits = 0;
        int middleThreeDigits  = 0;
        int lastThreeDigits = 0;
        gameModeCount = 3;
        if (splitNineDigitToThreeThree(game_mode_with_thmIdx, firstThreeDigits, middleThreeDigits, lastThreeDigits) == false){
            return -1;
        }
        if (get_threshold_with_thmIdx(firstThreeDigits, gameModes[0], thmIdxes[0]) == false){
            return -1;
        }
        if (get_threshold_with_thmIdx(middleThreeDigits, gameModes[1], thmIdxes[1]) == false){
            return -1;
        }
        if (get_threshold_with_thmIdx(lastThreeDigits, gameModes[2], thmIdxes[2]) == false){
            return -1;
        }
    }
    LOG_I("change_thm_policy_by_game_mode 1 gameModeCount:%d g_thermal_policy_vector.size():%d", gameModeCount, g_thermal_policy_vector.size());
    for (i = 0; i < gameModeCount; i++){
        gameMode = gameModes[i];
        thmIdx = thmIdxes[i];
        index = g_thermal_policy_vector.size();
        vectSize = g_thermal_policy_vector.size();
        found = false;
        for (j = 0; j < vectSize; j++){
            if (strncmp(g_current_foreground_pack, g_thermal_policy_vector[j].pkgName, PACK_NAME_MAX - 1) == 0 && g_thermal_policy_vector[j].gameMode == gameMode){
                found = true;
                index = j;
                break;
            }
        }
        if (found == false) {
            thermal_policy_manager* thermal_policy = (thermal_policy_manager*)malloc(sizeof(thermal_policy_manager));
            if (thermal_policy == NULL) {
                LOG_E("out of memory\n");
                return -1;
            }
            memset(thermal_policy, 0, sizeof(thermal_policy_manager));
            g_thermal_policy_vector.push_back(*thermal_policy);
        }
        strncpy(g_thermal_policy_vector[index].pkgName, g_current_foreground_pack, PACK_NAME_MAX - 1);
        g_thermal_policy_vector[index].gameMode = gameMode;
        g_thermal_policy_vector[index].newThermalIdx = thmIdx;

    }
    LOG_I("change_thm_policy_by_game_mode 2 gameModeCount:%d g_thermal_policy_vector.size():%d", gameModeCount, g_thermal_policy_vector.size());
    for (i = 0; i < g_thermal_policy_vector.size(); i++){
        LOG_D("change_thm_policy_by_game_mode g_thermal_policy_vector[%d].pkgName:%s gameMode:%d ambientTempThreshold:%d orgThermalIdx:%d newThermalIdx:%d",
            i, g_thermal_policy_vector[i].pkgName, g_thermal_policy_vector[i].gameMode, g_thermal_policy_vector[i].ambientTempThreshold, g_thermal_policy_vector[i].orgThermalIdx, g_thermal_policy_vector[i].newThermalIdx);
    }
    LOG_I("change_thm_policy_by_temperature game_mode_with_thmIdx:%d, index:%d, gameMode:%d, thmIdx:%d, g_current_foreground_pack:%s, g_thermal_policy_vector.size():%lu end",
        game_mode_with_thmIdx, index, gameMode, thmIdx, g_current_foreground_pack, g_thermal_policy_vector.size());
    return 0;
}

int unchange_thm_policy_by_game_mode(int game_mode_with_thmIdx, void *scn){
    LOG_I("unchange_thm_policy_by_game_mode game_mode_with_thmIdx:%d", game_mode_with_thmIdx);
    return 0;
}

int set_game_mode(int game_mode, void *scn){
    LOG_I("set_game_mode game_mode:%d g_game_mode:%d g_thermal_switch_support:%d g_thermal_policy_vector.size():%lu ",
        game_mode, g_game_mode, g_thermal_switch_support, g_thermal_policy_vector.size());
    if (g_thermal_switch_support == 0 || g_thermal_policy_vector.size() >= MAX_THERMAL_POLICY_SIZE){//if it isn't thermal core 2.0, don't support it.
        return 0;
    }
    if (game_mode > 0 && game_mode != g_game_mode){
        g_game_mode = game_mode;
        int i = 0;
        int vectSize = g_thermal_policy_vector.size();
        if (vectSize > 0){
            LOG_I("set_game_mode g_thermal_policy_vector[0].pkgName:%s g_thermal_start:%d g_current_foreground_pack:%s",
                g_thermal_policy_vector[0].pkgName, g_thermal_start, g_current_foreground_pack);
        }
        for (i = 0; i < vectSize; i++){
            if (strncmp(g_current_foreground_pack, g_thermal_policy_vector[i].pkgName, PACK_NAME_MAX - 1) == 0 && g_thermal_policy_vector[i].gameMode > 0){
                if (g_game_mode == g_thermal_policy_vector[i].gameMode){
                    if (g_thermal_idx != g_thermal_policy_vector[i].newThermalIdx){
                        //change the current newThermalIdx by high temperature
                        load_thm_api_by_app_internal_switch(g_thermal_policy_vector[i].newThermalIdx);
                    }
                } else {
                    if (g_thermal_idx != g_thermal_policy_vector[i].orgThermalIdx && g_thermal_policy_vector[i].orgThermalIdx > 0){
                        load_thm_api_by_app_internal_switch(g_thermal_policy_vector[i].orgThermalIdx);
                    }
                }
            }
        }
    }
    return 0;
}

int unset_game_mode(int game_mode, void *scn){
    //LOG_I("unset_game_mode game_mode:%d", game_mode);
    return 0;
}

int change_thm_policy_by_folded(int foldedThermalIdx, void *scn){
    LOG_I("change_thm_policy_by_folded foldedThermalIdx:%d, g_thermal_switch_support:%d g_thermal_policy_vector.size():%lu", foldedThermalIdx, g_thermal_switch_support, g_thermal_policy_vector.size());
    if (g_thermal_switch_support == 0 || g_thermal_policy_vector.size() >= MAX_THERMAL_POLICY_SIZE){
        return -1;
    }
    int i = 0;
    bool found = false;
    int index = g_thermal_policy_vector.size();

    for (i = 0; i < g_thermal_policy_vector.size(); i++){
        if (strncmp(g_current_foreground_pack, g_thermal_policy_vector[i].pkgName, PACK_NAME_MAX - 1) == 0){
            found = true;
            index = i;
            break;
        }
    }
    if (found == false) {
        thermal_policy_manager* thermal_policy = (thermal_policy_manager*)malloc(sizeof(thermal_policy_manager));
        if (thermal_policy == NULL) {
            LOG_E("out of memory\n");
            return -1;
        }
        memset(thermal_policy, 0, sizeof(thermal_policy_manager));
        g_thermal_policy_vector.push_back(*thermal_policy);
    }
    strncpy(g_thermal_policy_vector[index].pkgName, g_current_foreground_pack, PACK_NAME_MAX - 1);
    g_thermal_policy_vector[index].foldedState = TRAN_THERMAL_POLICY_FOLDED_STATE;
    g_thermal_policy_vector[index].newThermalIdx = foldedThermalIdx;

    for (i = 0; i < g_thermal_policy_vector.size(); i++){
        LOG_D("change_thm_policy_by_folded g_thermal_policy_vector[%d].pkgName:%s foldedState:%d orgThermalIdx:%d newThermalIdx:%d",
            i, g_thermal_policy_vector[i].pkgName, g_thermal_policy_vector[i].foldedState, g_thermal_policy_vector[i].orgThermalIdx, g_thermal_policy_vector[i].newThermalIdx);
    }
    if (found == false ) {//Fix the first time to launcher the policy
        if (g_fold_state == TRAN_THERMAL_POLICY_FOLDED_STATE && g_thermal_idx != foldedThermalIdx) {
            LOG_D("load_thm_api_by_app_internal_switch in change_thm_policy_by_folded");
            load_thm_api_by_app_internal_switch(foldedThermalIdx);
        }
    }
    LOG_I("change_thm_policy_by_folded foldedThermalIdx:%d, index:%d, g_current_foreground_pack:%s.",
        foldedThermalIdx, index, g_current_foreground_pack);
    return 0;
}

int unchange_thm_policy_by_folded(int foldedThermalIdx, void *scn){
    //LOG_I("unchange_thm_policy_by_folded foldedThermalIdx:%d", foldedThermalIdx);
    return 0;
}

int set_fold_state(int fold_state, void *scn){
    LOG_I("set_fold_state fold_state:%d, g_fold_state:%d, g_thermal_switch_support:%d g_thermal_policy_vector.size():%lu", fold_state, g_fold_state, g_thermal_switch_support, g_thermal_policy_vector.size());
    if (g_thermal_switch_support == 0 || g_thermal_policy_vector.size() >= MAX_THERMAL_POLICY_SIZE){
        LOG_I("set_fold_state g_thermal_switch_support == 0 || g_thermal_policy_vector.size() >= MAX_THERMAL_POLICY_SIZE");
        return -1;
    }
    if (fold_state != g_fold_state){
        g_fold_state = fold_state;
        int i = 0;
        int vectSize = g_thermal_policy_vector.size();
        if (vectSize > 0) {
            LOG_I("set_fold_state g_thermal_policy_vector[0].pkgName:%s g_thermal_idx:%d g_thermal_start:%d g_current_foreground_pack:%s",
                g_thermal_policy_vector[0].pkgName, g_thermal_idx, g_thermal_start, g_current_foreground_pack);
        }
        for (i = 0; i < vectSize; i++){
            if (strncmp(g_current_foreground_pack, g_thermal_policy_vector[i].pkgName, PACK_NAME_MAX - 1) == 0){
                if (g_fold_state == TRAN_THERMAL_POLICY_FOLDED_STATE){
                    if (g_thermal_policy_vector[i].foldedState == TRAN_THERMAL_POLICY_FOLDED_STATE &&
                        g_thermal_idx != g_thermal_policy_vector[i].newThermalIdx){
                        //change the current newThermalIdx by fold_state
                        load_thm_api_by_app_internal_switch(g_thermal_policy_vector[i].newThermalIdx);
                    }
                } else {
                    if (g_thermal_idx != g_thermal_policy_vector[i].orgThermalIdx && g_thermal_policy_vector[i].orgThermalIdx > 0){
                        load_thm_api_by_app_internal_switch(g_thermal_policy_vector[i].orgThermalIdx);
                    }
                }
            }
        }
    }
    return 0;
}

int unset_fold_state(int fold_state, void *scn){
    //LOG_I("unset_fold_state fold_state:%d", fold_state);
    return 0;
}

int set_multiwindow_state(int multiwindow_state, void *scn){
    LOG_I("set_multiwindow_state multiwindow_state:%d, g_multiwindow_state:%d, g_multiwindow_default_thermal_idx:%d g_multiwindow_thermal_idx:%d g_multiwindow_thermal_start:%d",
        multiwindow_state, g_multiwindow_state, g_multiwindow_default_thermal_idx, g_multiwindow_thermal_idx, g_multiwindow_thermal_start);
    if (g_thermal_policy_vector.size() >= MAX_THERMAL_POLICY_SIZE){
        LOG_I("set_multiwindow_state g_thermal_switch_support == 0 || g_thermal_policy_vector.size() >= MAX_THERMAL_POLICY_SIZE");
        return -1;
    }

    if (multiwindow_state != g_multiwindow_state && g_multiwindow_default_thermal_idx > 0){
        g_multiwindow_state = multiwindow_state;
        g_current_paused_pack[0] = '\0';
        g_started_foreground_pack[0] = '\0';
        LOG_I("set_multiwindow_state 1 g_multiwindow_state:%d", g_multiwindow_state);
        if (multiwindow_state == TRAN_THERMAL_POLICY_MULTIWINDOW_STATE) {
            if (g_multiwindow_thermal_idx == -1) {
                g_multiwindow_thermal_idx = g_multiwindow_default_thermal_idx;
                g_multiwindow_thermal_start = 1;
            }
            LOG_I("set_multiwindow_state 2 multiwindow_state:%d", multiwindow_state);
            load_thm_api_by_app_internal_switch(g_multiwindow_default_thermal_idx);
        } else {
            if (g_multiwindow_thermal_idx == -1) {
                LOG_I("set_multiwindow_state 3 g_multiwindow_thermal_idx:%d", g_multiwindow_thermal_idx);
                g_multiwindow_thermal_start = 0;
                load_thm_api(g_multiwindow_default_thermal_idx, g_multiwindow_thermal_start);//reassign new thermal conf.
            }
            else {
                LOG_I("set_multiwindow_state 4 g_multiwindow_thermal_idx:%d", g_multiwindow_thermal_idx);
                if (g_multiwindow_thermal_start == 0) {
                    LOG_I("set_multiwindow_state 5 g_multiwindow_thermal_start:%d", g_multiwindow_thermal_start);
                    load_thm_api(g_multiwindow_thermal_idx, g_multiwindow_thermal_start);//reassign new thermal conf.
                } else {
                    LOG_I("set_multiwindow_state 6 g_multiwindow_default_thermal_idx:%d g_multiwindow_thermal_idx:%d", g_multiwindow_default_thermal_idx, g_multiwindow_thermal_idx);
                    if (g_multiwindow_default_thermal_idx == g_multiwindow_thermal_idx){
                        LOG_I("set_multiwindow_state 7 g_multiwindow_thermal_start:%d", g_multiwindow_thermal_start);
                        g_multiwindow_thermal_start = 0;
                        load_thm_api(g_multiwindow_default_thermal_idx, g_multiwindow_thermal_start);//reassign new thermal conf.
                    } else {
                        LOG_I("set_multiwindow_state 8 g_multiwindow_thermal_idx:%d", g_multiwindow_thermal_idx);
                        load_thm_api_by_app_internal_switch(g_multiwindow_thermal_idx);
                    }
                }
            }
        }
        g_current_paused_pack[0] = '\0';
        g_started_foreground_pack[0] = '\0';
    }
    return 0;
}

int unset_multiwindow_state(int multiwindow_state, void *scn){
    //LOG_I("unset_multiwindow_state multiwindow_state:%d", fold_state);
    return 0;
}
int get_current_thermal_idx(const int idx){
    int i = 0;
    int new_idx = idx;
    int vectSize = g_thermal_policy_vector.size();
    if (vectSize > 0) {
        LOG_I("get_current_thermal_idx idx:%d g_thermal_policy_vector[0].pkgName:%s ambientTempThreshold:%d orgThermalIdx:%d newThermalIdx:%d gameMode:%d g_thermal_idx:%d, g_thermal_start:%d "
        "g_current_foreground_pack:%s g_game_mode:%d, g_current_ambientTemp:%d, g_fold_state:%d",
            idx, g_thermal_policy_vector[0].pkgName, g_thermal_policy_vector[0].ambientTempThreshold, g_thermal_policy_vector[0].orgThermalIdx, g_thermal_policy_vector[0].newThermalIdx,
            g_thermal_policy_vector[0].gameMode, g_thermal_idx, g_thermal_start, g_current_foreground_pack, g_game_mode, g_current_ambientTemp, g_fold_state);
    }
    if (g_multiwindow_state == TRAN_THERMAL_POLICY_MULTIWINDOW_STATE && g_multiwindow_default_thermal_idx > 0){
        return g_multiwindow_default_thermal_idx;
    }
    for (i = 0; i < vectSize; i++){
        if (strncmp(g_current_foreground_pack, g_thermal_policy_vector[i].pkgName, PACK_NAME_MAX - 1) == 0){
            if (g_thermal_policy_vector[i].orgThermalIdx == 0){//save the orginal thermal idx for the pkgname.
                g_thermal_policy_vector[i].orgThermalIdx = idx;
            }
            if (g_thermal_policy_vector[i].ambientTempThreshold > 0){
                LOG_I("g_current_ambientTemp:%d, g_thermal_policy_vector[i].ambientTempThreshold:%d", g_current_ambientTemp, g_thermal_policy_vector[i].ambientTempThreshold);
                if (g_current_ambientTemp >= g_thermal_policy_vector[i].ambientTempThreshold){
                    if (idx != g_thermal_policy_vector[i].newThermalIdx){
                        //change the current newThermalIdx by high temperature
                        new_idx = g_thermal_policy_vector[i].newThermalIdx;
                    }
                }
            } else if (g_thermal_policy_vector[i].gameMode > 0){
                LOG_I("g_game_mode:%d, g_thermal_policy_vector[i].gameMode:%d", g_game_mode, g_thermal_policy_vector[i].gameMode);
                if (g_game_mode == g_thermal_policy_vector[i].gameMode){
                    if (idx != g_thermal_policy_vector[i].newThermalIdx){
                        //change the current newThermalIdx by game mode
                        new_idx = g_thermal_policy_vector[i].newThermalIdx;
                    }
                }
            } else if (g_thermal_policy_vector[i].foldedState > 0){
                LOG_I("g_fold_state:%d, g_thermal_policy_vector[i].foldedState:%d", g_fold_state, g_thermal_policy_vector[i].foldedState);
                if (g_fold_state == g_thermal_policy_vector[i].foldedState){
                    if (idx != g_thermal_policy_vector[i].newThermalIdx){
                        //change the current newThermalIdx by folded state
                        new_idx = g_thermal_policy_vector[i].newThermalIdx;
                    }
                }
            }
        }
    }
    return new_idx;
}
//BSP:add for thermal policy switch by jian.li at 20240424 end

int close_thm_policy_by_engineer_mode(int close_state, void *scn)
{
    int ret = 0;

    LOG_I("close_thm_policy, apply disable_thermal_temp.conf");

    ret = change_policy_by_socket("disable_thermal_temp", 1);
    return ret;
}

int unclose_thm_policy_by_engineer_mode(int close_state, void *scn)
{
    LOG_I("unclose_thm_policy");
    return 0;
}

int load_thm_api_policy_start(int idx, void *scn){
    //BSP:add for thermal policy switch by jian.li at 20240424 start
    if (g_thermal_switch_support == 1){
        if (g_multiwindow_state == TRAN_THERMAL_POLICY_MULTIWINDOW_STATE && g_multiwindow_default_thermal_idx > 0) {
            LOG_I("load_thm_api_start 1 idx=%d, g_thermal_start == 1", idx);
            int thermal_idx = g_multiwindow_thermal_idx;
            g_multiwindow_thermal_idx = idx;
            g_multiwindow_thermal_start = 1;
            g_started_foreground_pack[0] = '\0';
            if (g_thermal_idx > 0 && g_thermal_idx == thermal_idx){
                LOG_I("load_thm_api_start 2 g_thermal_idx == thermal_idx g_multiwindow_state == 1");
                return 0;
            }
        }
        g_multiwindow_thermal_idx = idx;
        g_multiwindow_thermal_start = 1;
        g_started_foreground_pack[0] = '\0';
        LOG_I("load_thm_api_start 3 g_thermal_idx=%d, g_thermal_start == 1", g_thermal_idx);
        if (g_thermal_start == 1 && g_thermal_idx > 0 && g_thermal_policy_vector.size() > 0) {
            load_thm_api(g_thermal_idx, 0);//first need to pop index from stack.
        }
        else if (g_thermal_start == 1 && g_thermal_idx > 0 && g_multiwindow_default_thermal_idx > 0) {
            load_thm_api(g_thermal_idx, 0);//first need to pop index from stack.
        }
    }
    //BSP:add for thermal policy switch by jian.li at 20240424 end
    return 1;
}

int load_thm_api_policy_stop(int idx, void *scn){
    //BSP:add for thermal policy switch by jian.li at 20240424 start
    if (g_thermal_switch_support == 1){
        if (g_multiwindow_default_thermal_idx > 0) {
            g_multiwindow_thermal_idx = idx;
            g_multiwindow_thermal_start = 0;
            //LOG_I("load_thm_api_stop g_multiwindow_thermal_idx:%d, g_multiwindow_thermal_start:%d", g_multiwindow_thermal_idx, g_multiwindow_thermal_start);
            if (g_multiwindow_state == TRAN_THERMAL_POLICY_MULTIWINDOW_STATE) {
                LOG_I("load_thm_api_stop 1 g_multiwindow_state:%d start == 0", g_multiwindow_state);
                return 0;
            }
        }
        //LOG_I("load_thm_api_stop . (g_started_foreground_pack:%s, g_current_paused_pack:%s)", g_started_foreground_pack, g_current_paused_pack);
        if (g_thermal_idx > 0 && g_thermal_policy_vector.size() > 0) {
            if (strncmp(g_started_foreground_pack, g_current_paused_pack, PACK_NAME_MAX-1) != 0 && g_current_paused_pack[0] != '\0'  && g_started_foreground_pack[0] != '\0'){
                LOG_I("load_thm_api_stop return 0. (g_started_foreground_pack:%s, g_current_paused_pack:%s)", g_started_foreground_pack, g_current_paused_pack);
                return 0;
            }
        }
    }
    //BSP:add for thermal policy switch by jian.li at 20240424 end
    return 1;
}

int load_thm_api_policy(int idx, bool start){
    //BSP:add for thermal policy switch by jian.li at 20240424 start
    if (g_thermal_switch_support == 0){
        char thermal_value[PROPERTY_VALUE_MAX] = "\0";
        property_get(TRAN_THERMAL_POLICY_SWITCH_RO, thermal_value, "1");
        if (atoi(thermal_value) == 1) {
            g_thermal_switch_support = 1;
        }
    }
    if (g_thermal_switch_support == 1){
        int new_idx = get_current_thermal_idx(idx);
        if (start == 0) {//it will reset the index to previous one
            if (g_thermal_idx > 0){
                new_idx = g_thermal_idx;//using previus index as the reset index.
                LOG_I("load_thm_api 1 new_idx:%d,g_thermal_idx:%d start:%d", idx, g_thermal_idx, start);
            }
        }
        idx = new_idx;
        g_thermal_idx = -1;
        LOG_I("load_thm_api new_idx:%d, g_thermal_idx:%d start:%d", idx, g_thermal_idx, start);
    }
    //BSP:add for thermal policy switch by jian.li at 20240424 end
    return 0;
}

int load_thm_api_policy_set_multiwindow(int idx, bool start){
    //BSP:add for thermal policy switch by jian.li at 20240424 start
    if (idx == g_multiwindow_default_thermal_idx) {
        g_multiwindow_default_thermal_idx = TRAN_THERMAL_POLICY_INVALID_MULTIWINDOW_ID;
    }
    //BSP:add for thermal policy switch by jian.li at 20240424 end
    return 0;
}

int load_thm_api_policy_set_foreground_package(int idx, bool start, int next_index){
    //BSP:add for thermal policy switch by jian.li at 20240424 begin
    if (start == 1) {
        g_thermal_idx = next_index;
        strncpy(g_started_foreground_pack, g_current_foreground_pack, PACK_NAME_MAX-1);
    }
    g_thermal_start = start;
    //BSP:add for thermal policy switch by jian.li at 20240424 end
    return 0;
}
//BSP:add for thermal policy switch by jian.li at 20240424 end

//BSP:add for thermal feature by jian.li at 20251113 start
// Define encrypted suffix constants and frequency range limits
#define TRAN_THERMAL_FREQ_SUFFIX 5      // Normal frequency limit mode suffix
#define TRAN_THERMAL_PT_ENABLE_SUFFIX 4 // PT_policy enable mode suffix
#define TRAN_THERMAL_SUFFIX_DIGITS 1     // Number of suffix digits
#define MAX_CPU_FREQUENCY 9999999        // Maximum CPU frequency (7 digits)
extern bool g_tran_thermal_engine_enabled;
extern bool g_tran_thermal_pt_policy_enabled;
extern int g_tran_thermal_hard_freq_max[CLUSTER_MAX];
int g_tran_thermal_freqTbl[CLUSTER_MAX] = {0};           // Minimum frequency set to 0 means using default value
// Define constants for mode types
#define TRAN_THERMAL_MODE_NORMAL 0       // Normal frequency limiting mode
#define TRAN_THERMAL_MODE_PT_ENABLE 1    // PT_policy enable mode

static void thermal_update_hardlimit_info(int cluster, int freq) {
    ALOGV("[tran_thermal_engine] cpu cluster %d freq: %d, g_tran_thermal_engine_enabled:%d", cluster, freq, g_tran_thermal_engine_enabled);
    if (g_tran_thermal_engine_enabled) {
        g_tran_thermal_hard_freq_max[cluster] = freq;
    } else {
        g_tran_thermal_hard_freq_max[cluster] = 0;
    }
}

/**
 * Parse encrypted frequency value
 * @param value Encrypted frequency value (max 9 digits, range 1-999999999)
 * @param realFreq Output parameter used to store the parsed actual frequency value (max 8 digits, 0-99999999)
 * @return TRAN_THERMAL_MODE_PT_ENABLE indicates PT_policy enable mode (suffix 4),
 *         TRAN_THERMAL_MODE_NORMAL indicates normal frequency limit mode (suffix 5), -1 indicates failure
 * Failure cases: invalid format, mismatched suffix, or frequency value exceeding maximum limit (99999999)
 */
static int parseEncryptedFrequency(int value, int *realFreq) {
    // Special handling for case 0 (when frequency value is 0)
    if (value == TRAN_THERMAL_FREQ_SUFFIX || value == TRAN_THERMAL_PT_ENABLE_SUFFIX) {
        *realFreq = 0;
        return (value == TRAN_THERMAL_FREQ_SUFFIX) ? TRAN_THERMAL_MODE_NORMAL : TRAN_THERMAL_MODE_PT_ENABLE; // TRAN_THERMAL_FREQ_SUFFIX for normal mode, TRAN_THERMAL_PT_ENABLE_SUFFIX for PT mode
    }

    // Ensure the value is within the valid range, with a maximum value of 999999999 (9 digits)
    // Valid input range: 1-999999999
    int minValue = 1;
    int maxValue = 999999999; // Maximum 9 digits

    if (value >= minValue && value <= maxValue) {
        // Extract the last digit as password
        int suffix = value % 10;

        // Extract the actual frequency value by removing the last digit
        *realFreq = value / 10;

        // Verify the frequency value does not exceed the maximum (8 digits)
        const int NEW_MAX_CPU_FREQUENCY = 99999999; // Maximum 8 digits

        if (suffix == TRAN_THERMAL_FREQ_SUFFIX) {
            // Normal frequency limiting mode
            if (*realFreq <= NEW_MAX_CPU_FREQUENCY) {
                return TRAN_THERMAL_MODE_NORMAL; // Normal frequency limiting mode
            }
            return -1; // Frequency value exceeds range
        } else if (suffix == TRAN_THERMAL_PT_ENABLE_SUFFIX) {
            // PT_policy enable mode
            if (*realFreq <= NEW_MAX_CPU_FREQUENCY) {
                return TRAN_THERMAL_MODE_PT_ENABLE; // PT_policy enable mode
            }
            return -1; // Frequency value exceeds range
        }
    }

    return -1; // Invalid format or suffix
}

int setCPUFreqTranThermalCluster0(int value, void *scn) {
    int realFreq;
    int mode = parseEncryptedFrequency(value, &realFreq);
    int cluster = 0;

    if (mode == TRAN_THERMAL_MODE_NORMAL) {
       if (g_tran_thermal_pt_policy_enabled) {
            LOG_I("[tran_thermal_engine] PT_policy enabled, skip it.");
            return 0;
        }
        // Normal frequency limit mode (suffix 5)
        ALOGV("[tran_thermal_engine] Normal thermal limit mode for cluster 0: %d, real freq: %d", value, realFreq);
        thermal_update_hardlimit_info(cluster, realFreq);
        return 0;
    } else if (mode == TRAN_THERMAL_MODE_PT_ENABLE) {
        // PT_policy enable mode (suffix 4)
        g_tran_thermal_pt_policy_enabled = (realFreq > 0);
        ALOGV("[tran_thermal_engine] PT_policy enable mode for cluster 0: %d, real freq: %d", value, realFreq);
        thermal_update_hardlimit_info(cluster, realFreq);
        // Additional PT_policy specific processing logic can be added here
        return 0;
    } else {
        LOG_E("[tran_thermal_engine] Invalid encrypted freq format for cluster 0, value: %d", value);
        return -1;
    }
}

int unsetCPUFreqTranThermalCluster0(int value, void *scn){
    return 0;
}

int setCPUFreqTranThermalCluster1(int value, void *scn){
    int realFreq;
    int mode = parseEncryptedFrequency(value, &realFreq);
    int cluster = 1;

    if (mode == TRAN_THERMAL_MODE_NORMAL) {
        if (g_tran_thermal_pt_policy_enabled) {
            LOG_I("[tran_thermal_engine] PT_policy enabled, skip it.");
            return 0;
        }
        // Normal frequency limit mode (suffix 5)
        ALOGV("[tran_thermal_engine] Normal thermal limit mode for cluster 1: %d, real freq: %d", value, realFreq);
        thermal_update_hardlimit_info(cluster, realFreq);
        return 0;
    } else if (mode == TRAN_THERMAL_MODE_PT_ENABLE) {
        // PT_policy enable mode (suffix 4)
        g_tran_thermal_pt_policy_enabled = (realFreq > 0);
        ALOGV("[tran_thermal_engine] PT_policy enable mode for cluster 1: %d, real freq: %d", value, realFreq);
        thermal_update_hardlimit_info(cluster, realFreq);
        // Additional PT_policy specific processing logic can be added here
        return 0;
    } else {
        LOG_E("[tran_thermal_engine] Invalid encrypted freq format for cluster 1, value: %d", value);
        return -1;
    }
}

int unsetCPUFreqTranThermalCluster1(int value, void *scn){
    return 0;
}

int setCPUFreqTranThermalCluster2(int value, void *scn){
    int realFreq;
    int mode = parseEncryptedFrequency(value, &realFreq);
    int cluster = 2;

    if (mode == TRAN_THERMAL_MODE_NORMAL) {
        if (g_tran_thermal_pt_policy_enabled) {
            LOG_I("[tran_thermal_engine] PT_policy enabled, skip it.");
            return 0;
        }
        // Normal frequency limit mode (suffix 5)
        ALOGV("[tran_thermal_engine] Normal thermal limit mode for cluster 2: %d, real freq: %d", value, realFreq);
        thermal_update_hardlimit_info(cluster, realFreq);
        return 0;
    } else if (mode == TRAN_THERMAL_MODE_PT_ENABLE) {
        // PT_policy enable mode (suffix 4)
        g_tran_thermal_pt_policy_enabled = (realFreq > 0);
        ALOGV("[tran_thermal_engine] PT_policy enable mode for cluster 2: %d, real freq: %d", value, realFreq);
        thermal_update_hardlimit_info(cluster, realFreq);
        // Additional PT_policy specific processing logic can be added here
        return 0;
    } else {
        LOG_E("[tran_thermal_engine] Invalid encrypted freq format for cluster 2, value: %d", value);
        return -1;
    }
}

int unsetCPUFreqTranThermalCluster2(int value, void *scn){
    return 0;
}
//BSP:add for thermal feature by jian.li at 20251113 end