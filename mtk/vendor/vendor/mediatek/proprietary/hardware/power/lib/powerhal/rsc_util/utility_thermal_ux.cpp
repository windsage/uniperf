#define LOG_TAG "libPowerHal_thermal_ux"
#define ATRACE_TAG (ATRACE_TAG_POWER | ATRACE_TAG_HAL)

#include <utils/Log.h>
#include <cutils/properties.h>
#include <cutils/trace.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <dirent.h>

#include "common.h"
#include "perfservice.h"
#include "utility_thermal_ux.h"

using std::string;
using std::vector;

#define TEMPERATURE_AP_PATH_1        "/proc/mtktz/mtktsAP"
#define PPM_THERMAL_UX               "/proc/ppm/policy/thermal_ux"
#define THERMAL_VALUE_MIN_PROP       "persist.vendor.powerhal.thermal_ux_temp_min"
#define THERMAL_VALUE_MAX_PROP       "persist.vendor.powerhal.thermal_ux_temp_max"
#define THERMAL_VALUE_MIN_DEF 40000
#define THERMAL_VALUE_MAX_DEF 50000

extern int nClusterNum;
extern int CpuFreqReady;
extern int cpufreqHardNeedUpdate;
extern tClusterInfo *ptClusterTbl;
extern void setClusterHardFreq(int cluster);

static char thermal_temp_patch[128];

static const int thermalcore_version = []() {
    int prop_value = getPpmSupport();
    if (prop_value == 1) {
        return 1;
    } else if (prop_value == 0) {
        return 2;
    } else {
        return 2;
    }
}();
static int thermal_ux_min_temp = get_property_value(THERMAL_VALUE_MIN_PROP) > 0 ? get_property_value(THERMAL_VALUE_MIN_PROP) : THERMAL_VALUE_MIN_DEF;
static int thermal_ux_max_temp = get_property_value(THERMAL_VALUE_MAX_PROP) > 0 ? get_property_value(THERMAL_VALUE_MAX_PROP) : THERMAL_VALUE_MAX_DEF;
static int *pre_thermal_ux_freq = NULL;
static bool thermal_ux_ready = false;
extern int getFreqHardMaxNowByPowerMode(int cluster, int freq);

static int get_current_temp()
{
    int temp;
    char line[256];
    if (thermalcore_version == 1) 
    {  //for thermalcore1.0(PPM)
        char temp_buf[128];
        FILE *file = fopen(TEMPERATURE_AP_PATH_1, "r");
        if (file != NULL) {
            fgets(temp_buf, sizeof(temp_buf), file);
            fclose(file);
            temp = atoi(temp_buf);
        }else {
            ALOGE("[thermalux] thermalcore version 1 can`t read ap thermal temp");
            return -1;
        }
    }else if (thermalcore_version == 2) 
    {    //for thermalcore2.0(read thermal zone)
        if (strlen(thermal_temp_patch) > 0) 
        {
            FILE* file = NULL;
            file = fopen(thermal_temp_patch, "r");
            if (file) {
                char *str = NULL;
                if (fgets(line, sizeof(line), file)) {
                    temp = atoi(line);
                }
                if(fclose(file) == EOF)
                    ALOGE("[thermalux] fclose errno:%d", errno);
            }
        } else {  //search thermal zone find thermal temp patch
            struct dirent *ptr;
            DIR *dir;
            string PATH = "/sys/class/thermal/";
            dir = opendir(PATH.c_str());
            vector<string> filePaths;
            FILE* file = NULL;
            char line[256];

            while ((ptr=readdir(dir))!=NULL)
            {
                if (ptr->d_name[0] == '.')
                    continue;

                filePaths.push_back(PATH + ptr->d_name);
            }
            closedir(dir);

            for (int i = 0; i < filePaths.size(); i++)
            {
                bool match = false;
                if (strstr(filePaths[i].c_str(), "cooling_device") != NULL) {
                    continue;
                }

                file = fopen((filePaths[i] + "/type").c_str(), "r");
                if (file) {
                    if (fgets(line, sizeof(line), file)) {
                        if (strncmp(line, "mtktsAP", 7) == 0 || strncmp(line, "ap_ntc", 6) == 0) {
                            strcpy(thermal_temp_patch, (filePaths[i] + "/temp").c_str());
                            match = true;
                        }
                    }

                    if(fclose(file) == EOF)
                        ALOGE("[thermalux] fclose errno:%d", errno);
                    if (match)
                        break;
                }
            }
            if (file != NULL)
                fclose(file);

            ALOGD("[thermalux] thermal_temp_patch :%s", thermal_temp_patch);
            if (strlen(thermal_temp_patch) < 18) {
                ALOGE("[thermalux] thermalcore version 2 can`t find ap thermal sys");
                return -1;
            }else {
                file = fopen(thermal_temp_patch, "r");
                if (file) {
                    char *str = NULL;
                    if (fgets(line, sizeof(line), file)) {
                        temp = atoi(line);
                    }
                    if(fclose(file) == EOF)
                        ALOGE("[thermalux] fclose errno:%d", errno);
                }else {
                    ALOGE("[thermalux] thermalcore version 2 can`t read ap thermal temp");
                    return -1;
                }
            }
        }
    }else 
    {
        ALOGE("[thermalux] thermal core version not support");
        return -1;
    }
    return temp;
}

/*thermal ux for thermalcore1.0(PPM) */
static void thermal_ux_update_ppm_info(int cluster, int freq)
{
    int cur_temp = 0;
    char str[64] = "";
    char final_str[128] = "";

    cur_temp = get_current_temp();
    if (cur_temp < 0) {
        ALOGE("[thermalux]get current temp failed");
        return;
    }
    if (cur_temp < thermal_ux_min_temp || cur_temp > thermal_ux_max_temp) {
        ALOGD("[thermalux]current temp %d not in range %d ~ %d", cur_temp, thermal_ux_min_temp, thermal_ux_max_temp);
        return;
    }
    if(sprintf(str, "%d %d", cluster, freq) < 0) {
        return;
    }

    strncat(final_str, str, strlen(str));
    set_value(PPM_THERMAL_UX, final_str);
    LOG_D("[thermalux] echo %s to %s(%d %d %d)\n", final_str, PPM_THERMAL_UX, cur_temp, thermal_ux_min_temp, thermal_ux_max_temp);
}

/*thermal ux for thermalcore2.0(HARDLIMIT) */
static void thermal_ux_update_hardlimit_info(int cluster, int freq)
{
    cpufreqHardNeedUpdate = 1;
    int cur_temp = 0;
    cur_temp = get_current_temp();
    if (cur_temp < 0) {
        ALOGE("[thermalux]get current temp failed");
        return;
    }
    if (cur_temp < thermal_ux_min_temp || cur_temp > thermal_ux_max_temp) {
        ALOGD("[thermalux] current temp %d not in range %d ~ %d", cur_temp, thermal_ux_min_temp, thermal_ux_max_temp);
        return;
    }
    if (freq != -1) {//update
        pre_thermal_ux_freq[cluster] = ptClusterTbl[cluster].freqHardMaxNow;
        ptClusterTbl[cluster].freqHardMaxNow = (freq >= ptClusterTbl[cluster].freqMax) ? ptClusterTbl[cluster].freqMax : freq;
    } else {//reset
        ptClusterTbl[cluster].freqHardMaxNow = getFreqHardMaxNowByPowerMode(cluster, pre_thermal_ux_freq[cluster]);
    }

    setClusterHardFreq(cluster);
}

void thermalUxInit(bool is_first)
{
    if (is_first) {
        pre_thermal_ux_freq = (int *)calloc(nClusterNum, sizeof(int));
        if (pre_thermal_ux_freq == NULL) {
            return;
        }

        for (int i = 0; i < nClusterNum; i++) {
            pre_thermal_ux_freq[i] = -1;
        }
        thermal_ux_ready = true;
    } else {
        thermal_ux_ready = false;
        thermal_ux_min_temp = get_property_value(THERMAL_VALUE_MAX_PROP) > 0 ? get_property_value(THERMAL_VALUE_MAX_PROP) : THERMAL_VALUE_MAX_DEF;
        thermal_ux_max_temp = get_property_value(THERMAL_VALUE_MIN_PROP) > 0 ? get_property_value(THERMAL_VALUE_MIN_PROP) : THERMAL_VALUE_MIN_DEF;
        thermal_ux_ready = true;
    }
}


#define SET_CPU_FREQ_THERMAL_UX(cluster) \
int setCPUFreqThermalUxCluster##cluster(int freq, void *scn) { \
    LOG_D("[thermalux] set cluster %d freq: %d", cluster, freq); \
    if(thermal_ux_ready == true && CpuFreqReady == 1 && cluster < nClusterNum) { \
        if (thermalcore_version == 1 && getPpmSupport()) { \
            thermal_ux_update_ppm_info(cluster, freq); \
        } \
        else if (thermalcore_version == 2) { \
            thermal_ux_update_hardlimit_info(cluster, freq); \
        } \
    } \
    return 0; \
}

SET_CPU_FREQ_THERMAL_UX(0);
SET_CPU_FREQ_THERMAL_UX(1);
SET_CPU_FREQ_THERMAL_UX(2);




