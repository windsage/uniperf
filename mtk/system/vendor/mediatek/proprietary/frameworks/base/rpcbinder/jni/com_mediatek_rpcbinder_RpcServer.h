#ifndef _COM_MEDIATK_RPCBINDER_RPCSERVER_H_
#define _COM_MEDIATK_RPCBINDER_RPCSERVER_H_

#include <jni.h>
#include <android/log.h>
#include <android-base/logging.h>
#include <android-base/macros.h>
#include <android_util_Binder.h>
#include <binder/RpcServer.h>
#include <nativehelper/JNIHelp.h>
#include <utils/Errors.h>
#include <utils/Log.h>
#include <utils/KeyedVector.h>
#include <utils/RefBase.h>
#include <utils/String8.h>

using namespace android;

struct JRpcServer : public RefBase {
    JRpcServer(JNIEnv *env, jobject thiz);
    sp<RpcServer> getRpcServer();

protected:
    virtual ~JRpcServer();

private:
    jclass mClass;
    jweak mObject;
    sp<RpcServer> mImpl;

    // DISALLOW_COPY_AND_ASSIGN(JRpcServer);
};


#endif
