/*===========================================================================
  TranThermalPolicy.cpp
  DESCRIPTION
  apply the tran thermal policy 
===========================================================================*/
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <errno.h>
#include <dlfcn.h>
#include <pthread.h>
/* thermal-lib header */
#include "thermal_client.h"
#include "Request.h"
#include "PerfLog.h"
#include "tran-thermal/TranThermalPolicy.h"
// Static variables to cache library handle and function pointer, avoiding frequent loading/unloading of dynamic library
#define LOG_TRAN_TAG "TRAN-THERMAL-CLIENT"
static void *thermal_lib_handle = NULL;
static int (*thermal_client_request_func)(char *, int) = NULL;
/* Define data validation password */
#define THERMAL_DATA_PASSWORD 5
/* Function to initialize thermalclient library, executes only once */
static void thermal_lib_init(void)
{
    char *error;
    /* Load thermalclient library */
    if (thermal_lib_handle == NULL) {
        thermal_lib_handle = dlopen("/vendor/lib64/libthermalclient.so", RTLD_NOW);
        if (!thermal_lib_handle) {
            error = (char *)dlerror();
            if (error != NULL) {
                QLOGE(LOG_TRAN_TAG, "[thermalux]Error: /vendor/lib64/libthermalclient.so isn't existed, error=%s\n", error);
            }
            return;
        }
    }
    /* Get thermal_client_request function handle */
    if (thermal_client_request_func == NULL) {
        thermal_client_request_func = (int (*)(char *, int))dlsym(thermal_lib_handle, "thermal_client_request");
        if (!thermal_client_request_func) {
            error = (char *)dlerror();
            if (error != NULL) {
                QLOGE(LOG_TRAN_TAG, "[thermalux]Error: thermal_client_request is null, error=%s\n", error);
            }
            dlclose(thermal_lib_handle);
            thermal_lib_handle = NULL;
        }
    }
}
/* Function to unload thermalclient library, can be called before program exit */
void thermal_lib_cleanup(void)
{
    if (thermal_lib_handle) {
        dlclose(thermal_lib_handle);
        thermal_lib_handle = NULL;
        thermal_client_request_func = NULL;
    }
}
/*
 * Callable function to send data to thermal-engine
 * Parameters:
 *   req_data - The request data value to send
 * Return value:
 *   0 on success, -1 on failure
 */
int tran_thermal_send_data(int req_data)
{
    int ret = 0;
    int rc = 0;
    int processed_data = 0;
    QLOGL(LOG_TAG, QLOG_L2, "[thermalux]send_thermal_data enter req_data:%d\n", req_data);
    /* Verify if req_data is a 4-digit number */
    if (req_data < THERMAL_DATA_PASSWORD || req_data > 9999) {
        QLOGE(LOG_TRAN_TAG, "[thermalux]Error: req_data must be a 4-digit number\n");
        return -2;
    }
    /* Verify if the last digit is THERMAL_DATA_PASSWORD (password) */
    if (req_data % 10 != THERMAL_DATA_PASSWORD) {
        QLOGE(LOG_TRAN_TAG, "[thermalux]Error: Invalid password. Last digit must be %d\n", THERMAL_DATA_PASSWORD);
        return -3;
    }
    /* Extract the first 3 digits */
    processed_data = req_data / 10;
    /* Use pthread_once to ensure library is initialized only once */
    thermal_lib_init();
    /* Check if library initialization was successful */
    if (!thermal_client_request_func) {
        QLOGE(LOG_TRAN_TAG, "[thermalux]Error: thermal_client_request function not initialized\n", stderr);
        return -1;
    }
    /* Use thermal_client_request to send processed data */
    QLOGL(LOG_TAG, QLOG_L2, "[thermalux]Sending processed data to thermal-engine: original=%d, processed=%d\n", 
           req_data, processed_data);
    rc = thermal_client_request_func("config_tran_boost", processed_data);
    if (rc < 0) {
        QLOGE(LOG_TRAN_TAG, "[thermalux]Failed to send data to thermal-engine, error code: %d\n", rc);
        ret = -1;
    } else {
        QLOGL(LOG_TAG, QLOG_L2, "[thermalux]Successfully sent data to thermal-engine\n");
    }
    //thermal_lib_cleanup();
    /* No longer closing the library on each call, keeping it open during program runtime */
    return ret;
}
