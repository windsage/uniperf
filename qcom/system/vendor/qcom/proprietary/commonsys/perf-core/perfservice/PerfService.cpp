/*
 * Copyright (c) 2018 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 *
 */

#include <fstream>
#include <sstream>
#include <binder/IServiceManager.h>
#include <binder/Parcel.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <log/log.h>
#include <utils/Log.h>
#include "mp-ctl.h"
#include "PerfService.h"

#undef LOG_TAG
#define LOG_TAG    "PerfService"
#define PERFD_LIB  "libqti-perfd-client_system.so"
#define IOPD_LIB   "libqti-iopd-client_system.so"

#define POLL_TIMER_MS       60000
#define INDEFINITE_DURATION 0

#define VENDOR_HINT_PERFORMANCE_MODE 0x00001091

namespace android {
PerfService::PerfService() {
    void *handle = NULL;
    handle = dlopen(PERFD_LIB, RTLD_NOW);
    void *handle_iop = NULL;
    handle_iop = dlopen(IOPD_LIB, RTLD_NOW);

    perf_lock_acq_rel = NULL;
    perf_lock_acq = NULL;
    perf_hint = NULL;
    perf_lock_rel = NULL;
    perf_ux_engine_events = NULL;
    perf_sync_request = NULL;
    perf_get_feedback_extn = NULL;
    perf_event = NULL;
    perf_hint_acq_rel = NULL;
    perf_hint_renew = NULL;
    get_perf_hal_ver = NULL;

    if (!handle) {
        ALOGE("unable to open %s: %s", PERFD_LIB, dlerror());
        return;
    } else {

        perf_lock_acq = (int (*)(int, int, int *, int))dlsym(handle, "perf_lock_acq");
        if (!perf_lock_acq) {
            ALOGE("couldn't get perf_lock_acq function handle.");
            return;
        }

        perf_hint = (int (*)(int, const char *, int, int))dlsym(handle, "perf_hint");
        if (!perf_hint) {
            ALOGE("couldn't get perf_hint function handle.");
            return;
        }

        perf_lock_rel = (int (*)(int))dlsym(handle, "perf_lock_rel");
        if (!perf_lock_rel) {
            ALOGE("couldn't get perf_hint function handle.");
            return;
        }

        perf_sync_request = (const char* (*)(int))dlsym(handle, "perf_sync_request");
        if (!perf_sync_request) {
            ALOGE("Couldn't get perf_sync_request function handle.");
            return;
        }

        perf_lock_acq_rel = (int (*)(int, int, int*, int, int))dlsym(handle, "perf_lock_acq_rel");
        if (!perf_lock_acq_rel) {
            ALOGE("couldn't get perf_lock_acq_rel function handle.");
            return;
        }

        perf_get_feedback_extn = (int (*)(int, const char *, int, int*))dlsym(handle, "perf_get_feedback_extn");
        if (!perf_get_feedback_extn) {
            ALOGE("couldn't get perf_get_feedback_extn function handle.");
            return;
        }

        perf_event = (void (*)(int, const char *, int, int*))dlsym(handle, "perf_event");
        if (!perf_event) {
            ALOGE("couldn't get perf_event function handle.");
            return;
        }

        perf_hint_acq_rel = (int (*)(int, int, const char *, int, int, int, int*))dlsym(handle, "perf_hint_acq_rel");
        if (!perf_hint_acq_rel) {
            ALOGE("couldn't get perf_hint_acq_rel function handle.");
            return;
        }

        perf_hint_renew = (int (*)(int, int, const char *, int, int, int, int*))dlsym(handle, "perf_hint_renew");
        if (!perf_hint_renew) {
            ALOGE("couldn't get perf_hint_renew function handle.");
            return;
        }

        get_perf_hal_ver = (double (*)())dlsym(handle, "get_perf_hal_ver");
        if (!get_perf_hal_ver) {
            ALOGE("couldn't get get_perf_hal_ver function handle.");
            return;
        }
    }
    if (!handle_iop) {
        ALOGE("unable to open %s: %s", IOPD_LIB, dlerror());
        return;
    } else {
        perf_ux_engine_events = (int (*)(int, int, const char *, int))dlsym(handle_iop, "perf_ux_engine_events");
        if (!perf_ux_engine_events) {
            ALOGE("couldn't get uxe_event function handle.");
            return;
        }
    }
}
int PerfService::perfLockAcquire(int duration, int len, int* boostsList) {
    if (!perf_lock_acq) {
        ALOGE("couldn't get perf_lock_acq function handle.");
        return -1;
    }

    mHandle = perf_lock_acq(mHandle, duration, boostsList, len);
    if (((duration > POLL_TIMER_MS) || (duration == INDEFINITE_DURATION))
            && (mHandle > 0)) {
        Mutex::Autolock _l(mLock);
        int pid = IPCThreadState::self()->getCallingPid();
        HandleInfo handleInfo = {mHandle, pid, -1, -1, -1};
        mHandleSet.insert(handleInfo);
    }
    return mHandle;
}

int PerfService::perfHint(int hint, String16& userDataStr, int userData1, int userData2, int tid) {
    String8 s = (userDataStr) ? String8(userDataStr) : String8("");
    if (!perf_hint) {
        ALOGE("couldn't get perf_hint function handle.");
        return -1;
    }

    if (hint == VENDOR_HINT_PERFORMANCE_MODE) {
        // Don't want to monitor App death for UI perf mode
        return perf_hint(hint, s.c_str(), userData1, userData2);
    }
    mHandle = perf_hint(hint, s.c_str(), userData1, userData2);

    if (hint >= VENDOR_PERF_HINT_BEGIN && hint <= VENDOR_POWER_HINT_END) {
        if (((userData1 > POLL_TIMER_MS) || (userData1 <= 0)) && (mHandle > 0)) {
            Mutex::Autolock _l(mLock);
            set<HandleInfo>::iterator it;
            for (it=mHandleSet.begin(); it!=mHandleSet.end(); it++) {
                if ((tid == it->tid) && (hint == it->hintId) && (userData2 == it->hintType)) {
                    if (mHandle == it->handle) {
                        return mHandle;
                    } else {
                        perf_lock_rel(it->handle);
                        mHandleSet.erase(it);
                        break;
                    }
                }
            }
            int pid = IPCThreadState::self()->getCallingPid();
            HandleInfo handleInfo = {mHandle, pid, tid, hint, userData2};
            mHandleSet.insert(handleInfo);
        }
    }
    return mHandle;
}

int PerfService::perfLockRelease() {
    if (!perf_lock_rel) {
        ALOGE("couldn't get perf_lock_rel function handle.");
        return -1;
    }
    int ret = perf_lock_rel(mHandle);

    Mutex::Autolock _l(mLock);
    HandleInfo handleInfo = {mHandle, -1, -1, -1, -1};
    set<HandleInfo>::iterator it = mHandleSet.find(handleInfo);
    if (it != mHandleSet.end()) {
        mHandleSet.erase(it);
    }
    return ret;
}

int PerfService::perfLockReleaseHandler(int _handle) {
    if (!perf_lock_rel) {
        ALOGE("couldn't get perf_lock_rel function handle.");
        return -1;
    }
    int ret = perf_lock_rel(_handle);
    Mutex::Autolock _l(mLock);
    HandleInfo handleInfo = {_handle, -1, -1, -1, -1};
    set<HandleInfo>::iterator it = mHandleSet.find(handleInfo);
    if (it != mHandleSet.end()) {
        mHandleSet.erase(it);
    }
    return ret;
}

int PerfService::perfUXEngine_events(int opcode, int pid, String16& pkg_name, int lat) {
    String8 s = (pkg_name) ? String8(pkg_name) : String8("");
    if (!perf_ux_engine_events) {
        ALOGE("couldn't get perf_ux_engine_events function handle.");
        return -1;
    }
    return perf_ux_engine_events(opcode, pid, s.c_str(), lat);
}

int PerfService::setClientBinder(const sp<IBinder>& client) {
    sp<DeathNotifier> deathNotifier = new(std::nothrow) DeathNotifier(this);
    if (client == NULL || deathNotifier == NULL)
        return -1;
    int ret = client->linkToDeath(deathNotifier);

    Mutex::Autolock _l(mLock);
    int pid = IPCThreadState::self()->getCallingPid();
    BinderInfo binderInfo = {pid, client, deathNotifier};
    mBinderSet.insert(binderInfo);
    return ret;
}

int PerfService::perfLockAcqAndRelease(int handle, int duration, int len,int reservelen, int* boostsList) {
    if (!perf_lock_acq_rel) {
        ALOGE("couldn't get perf_lock_acq_rel function handle.");
        return -1;
    }

    mHandle = perf_lock_acq_rel(handle, duration, boostsList, len, reservelen);
    if (((duration > POLL_TIMER_MS) || (duration == INDEFINITE_DURATION))
            && (mHandle > 0)) {
        Mutex::Autolock _l(mLock);
        int pid = IPCThreadState::self()->getCallingPid();
        HandleInfo handleInfo = {mHandle, pid, -1, -1, -1};
        mHandleSet.insert(handleInfo);
    }
    return mHandle;
}

int PerfService::perfGetFeedbackExtn(int req, String16& pkg_name, int tid, int len, int* list) {
    String8 s = (pkg_name) ? String8(pkg_name) : String8("");
    if (!perf_get_feedback_extn) {
        ALOGE("couldn't get perf_get_feedback_extn function handle.");
        return -1;
    }
    return perf_get_feedback_extn(req, s.c_str(), len, list);
}

void PerfService::perfEvent(int eventId, String16& pkg_name,int tid, int len, int* list) {
    String8 s = (pkg_name) ? String8(pkg_name) : String8("");
    if (!perf_event) {
        ALOGE("couldn't get perf_event function handle.");
        return;
    }
    perf_event(eventId, s.c_str(), len, list);
}

int PerfService::perfHintAcqRel(int handle, int hint, String16& pkg_name, int duration, int hint_type, int tid, int len, int* list) {
    String8 s = (pkg_name) ? String8(pkg_name) : String8("");
    if (!perf_hint_acq_rel) {
        ALOGE("couldn't get perf_hint_acq_rel function handle.");
        return -1;
    }
    mHandle = perf_hint_acq_rel(handle, hint, s.c_str(), duration, hint_type, len, list);

    if (hint >= VENDOR_PERF_HINT_BEGIN && hint <= VENDOR_POWER_HINT_END) {
        if (((duration > POLL_TIMER_MS) || (duration <= 0)) && (mHandle > 0)) {
            Mutex::Autolock _l(mLock);
            set<HandleInfo>::iterator it;
            for (it=mHandleSet.begin(); it!=mHandleSet.end(); it++) {
                if ((tid == it->tid) && (hint == it->hintId) && (hint_type == it->hintType)) {
                    if (mHandle == it->handle) {
                        return mHandle;
                    } else {
                        perf_lock_rel(it->handle);
                        mHandleSet.erase(it);
                        break;
                    }
                }
            }
            int pid = IPCThreadState::self()->getCallingPid();
            HandleInfo handleInfo = {mHandle, pid, tid, hint, hint_type};
            mHandleSet.insert(handleInfo);
        }
    }
    return mHandle;
}

int PerfService::perfHintRenew(int handle, int hint, String16& pkg_name, int duration, int hint_type, int tid, int len, int* list) {
    String8 s = (pkg_name) ? String8(pkg_name) : String8("");
    if (!perf_hint_renew) {
        ALOGE("couldn't get perf_hint_renew function handle.");
        return -1;
    }
    mHandle = perf_hint_renew(handle, hint, s.c_str(), duration, hint_type, len, list);

    if (hint >= VENDOR_PERF_HINT_BEGIN && hint <= VENDOR_POWER_HINT_END) {
        if (((duration > POLL_TIMER_MS) || (duration <= 0)) && (mHandle > 0)) {
            Mutex::Autolock _l(mLock);
            set<HandleInfo>::iterator it;
            for (it=mHandleSet.begin(); it!=mHandleSet.end(); it++) {
                if ((tid == it->tid) && (hint == it->hintId) && (hint_type == it->hintType)) {
                    if (mHandle == it->handle) {
                        return mHandle;
                    } else {
                        perf_lock_rel(it->handle);
                        mHandleSet.erase(it);
                        break;
                    }
                }
            }
            int pid = IPCThreadState::self()->getCallingPid();
            HandleInfo handleInfo = {mHandle, pid, tid, hint, hint_type};
            mHandleSet.insert(handleInfo);
        }
    }
    return mHandle;
}

double PerfService::getPerfHalVer() {
    if (!get_perf_hal_ver) {
        ALOGE("couldn't get get_perf_hal_ver function handle.");
        return 2.2;
    }
    return get_perf_hal_ver();
}

void PerfService::DeathNotifier::binderDied(const wp<IBinder>& who) {
    Mutex::Autolock _l(mPerf->mLock);
    int pid = -1;
    set<BinderInfo>::iterator it1;
    for (it1=mPerf->mBinderSet.begin(); it1!=mPerf->mBinderSet.end(); ++it1) {
        if (it1->binder.get() == who.unsafe_get()) {
            pid = it1->pid;
            mPerf->mBinderSet.erase(it1);
            break;
        }
    }
    set<HandleInfo>::iterator it2;
    for (it2=mPerf->mHandleSet.begin(); it2!=mPerf->mHandleSet.end();) {
        if (it2->pid == pid) {
            mPerf->perf_lock_rel(it2->handle);
            mPerf->mHandleSet.erase(it2++);
        } else {
            ++it2;
        }
    }
}

status_t PerfService::dump(int fd, const Vector<String16>& args) {
    if (perf_sync_request) {
        const char *result = NULL;
        if (args.size() && args[0] == String16("--all")) {
            result = perf_sync_request(SYNC_CMD_DUMP_DEG_INFO_HISTORICAL);
        } else {
            result = perf_sync_request(SYNC_CMD_DUMP_DEG_INFO);
        }

        if (result) {
            write(fd, result, strlen(result));
            free((void *)result);
        }
    }
    return OK;
}

PerfService::DeathNotifier::~DeathNotifier() {
}
};
