#ifndef _COM_MEDIATK_RPCBINDER_RPCSESSION_H_
#define _COM_MEDIATK_RPCBINDER_RPCSESSION_H_

#include <jni.h>
#include <android/log.h>
#include <android-base/logging.h>
#include <android-base/macros.h>
#include <android_util_Binder.h>
#include <binder/RpcSession.h>
#include <nativehelper/JNIHelp.h>
#include <utils/Errors.h>
#include <utils/Log.h>
#include <utils/KeyedVector.h>
#include <utils/RefBase.h>
#include <utils/String8.h>

using namespace android;

struct JRpcSession : public RefBase {
    JRpcSession(JNIEnv *env, jobject thiz);
    sp<RpcSession> getRpcSession();

protected:
    virtual ~JRpcSession();

private:
    jclass mClass;
    jweak mObject;
    sp<RpcSession> mImpl;

    // DISALLOW_COPY_AND_ASSIGN(JRpcSession);
};


#endif
