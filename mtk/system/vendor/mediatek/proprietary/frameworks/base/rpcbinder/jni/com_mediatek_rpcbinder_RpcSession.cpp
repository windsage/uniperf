#include "android_runtime/AndroidRuntime.h"

#include "com_mediatek_rpcbinder_RpcSession.h"

using namespace android;

#define LOG_TAG "RpcSession-JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static struct fields_t {
    jfieldID context;
} gFields;

JRpcSession::JRpcSession(JNIEnv *env, jobject thiz)
    : mClass(NULL),
      mObject(NULL) {
    jclass clazz = env->GetObjectClass(thiz);
    CHECK(clazz != NULL);
    mClass = (jclass)env->NewGlobalRef(clazz);
    mObject = env->NewWeakGlobalRef(thiz);
    mImpl = RpcSession::make();
    LOGI("create JRpcSession");
}

sp<RpcSession> JRpcSession::getRpcSession() {
    return mImpl;
}

JRpcSession::~JRpcSession() {
    JNIEnv *env = AndroidRuntime::getJNIEnv();

    env->DeleteWeakGlobalRef(mObject);
    mObject = NULL;
    env->DeleteGlobalRef(mClass);
    mClass = NULL;
    mImpl = nullptr;
    LOGI("destroy JRpcSession");
}


static sp<JRpcSession> setJRpcSession(
        JNIEnv *env, jobject thiz, const sp<JRpcSession> &rpcSession) {
    LOGI("setJRpcSession");
    sp<JRpcSession> old =
        (JRpcSession *)env->GetLongField(thiz, gFields.context);

    if (rpcSession != NULL) {
        LOGI("setJRpcSession: incStrong %p", rpcSession.get());
        rpcSession->incStrong(thiz);
    }
    if (old != NULL) {
        LOGI("setJRpcSession: decStrong %p", old.get());
        old->decStrong(thiz);
    }
    env->SetLongField(thiz, gFields.context, (jlong)rpcSession.get());

    return old;
}

static sp<JRpcSession> getJRpcSession(JNIEnv *env, jobject thiz) {
    return (JRpcSession *)env->GetLongField(thiz, gFields.context);
}

static void com_mediatek_rpcbinder_rpcsession_native_setup(
        JNIEnv *env, jobject thiz) {
    LOGI("native setup");
    sp<JRpcSession> session = new JRpcSession(env, thiz);

    LOGI("native setup: session=%p, this=%p", session.get(), thiz);
    setJRpcSession(env, thiz, session);
    return;
}

static void com_mediatek_rpcbinder_rpcsession_release(
        JNIEnv *env, jobject thiz) {
    setJRpcSession(env, thiz, nullptr);
    return;
}

static void com_mediatek_rpcbinder_rpcsession_native_finalize(
        JNIEnv *env, jobject thiz) {
    com_mediatek_rpcbinder_rpcsession_release(env, thiz);
    return;
}

static void com_mediatek_rpcbinder_rpcsession_set_max_incoming_threads(
        JNIEnv *env, jobject thiz, jlong threads) {
    sp <JRpcSession> jsession = getJRpcSession(env, thiz);
    if (jsession == nullptr) {
        LOGE("JRpcSession is null");
        return;
    }
    sp <RpcSession> rpcSession = jsession->getRpcSession();
    if (rpcSession == nullptr) {
        LOGE("RpcSession is null");
        return;
    }
    LOGI("setMaxIncomingThreads %ld", threads);
    rpcSession->setMaxIncomingThreads(static_cast<size_t>(threads));
    return;
}

static jint com_mediatek_rpcbinder_rpcsession_setup_vsock_client(
        JNIEnv *env, jobject thiz, jint cvd, jint port) {
    jint result = -1;
    sp <JRpcSession> jsession = getJRpcSession(env, thiz);
    if (jsession == nullptr) {
        LOGE("JRpcSession is null");
        return result;
    }
    sp <RpcSession> rpcSession = jsession->getRpcSession();
    if (rpcSession == nullptr) {
        LOGE("RpcSession is null");
        return result;
    }
    LOGI("setupVsockClient %d/%d", cvd, port);
    return static_cast<jint>(rpcSession->setupVsockClient(
            static_cast<unsigned int>(cvd), static_cast<unsigned int>(port)));
}

static jint com_mediatek_rpcbinder_rpcsession_setup_inet_client(
        JNIEnv *env, jobject thiz, jstring addr, jint port) {
    jint result = -1;
    sp <JRpcSession> jsession = getJRpcSession(env, thiz);
    if (jsession == nullptr) {
        LOGE("JRpcSession is null");
        return result;
    }
    sp <RpcSession> rpcSession = jsession->getRpcSession();
    if (rpcSession == nullptr) {
        LOGE("RpcSession is null");
        return result;
    }

    const char* nativeAddr = env->GetStringUTFChars(addr, nullptr);
    result = static_cast<jint>(rpcSession->setupInetClient(
                nativeAddr, static_cast<unsigned int>(port)));
    env->ReleaseStringUTFChars(addr, nativeAddr);
    return result;
}

static jobject com_mediatek_rpcbinder_rpcsession_get_root_object(
        JNIEnv *env, jobject thiz) {
    jobject result = nullptr;
    sp <JRpcSession> jsession = getJRpcSession(env, thiz);
    if (jsession == nullptr) {
        LOGE("JRpcSession is null");
        return result;
    }
    sp <RpcSession> rpcSession = jsession->getRpcSession();
    if (rpcSession == nullptr) {
        LOGE("RpcSession is null");
        return result;
    }

    sp<IBinder> rootObject = rpcSession->getRootObject();
    if (rootObject != nullptr) {
        return javaObjectForIBinder(env, rootObject);
    } else {
        LOGE("nativeGetRootObject: Failed to get root object");
        return nullptr;
    }
}

static void com_mediatek_rpcbinder_rpcsession_native_init(JNIEnv *env) {
    LOGI("native init");
    jclass clazz = env->FindClass("com/mediatek/rpcbinder/RpcSession");
    CHECK(clazz != NULL);

    gFields.context = env->GetFieldID(clazz, "mNativeContext", "J");
    CHECK(gFields.context != NULL);
    return;
}

static JNINativeMethod gMethods[] = {
    {"native_init", "()V",
        (void*)com_mediatek_rpcbinder_rpcsession_native_init},
    {"native_setup", "()V",
        (void*)com_mediatek_rpcbinder_rpcsession_native_setup},
    {"setMaxIncomingThreads", "(J)V",
        (void*)com_mediatek_rpcbinder_rpcsession_set_max_incoming_threads},
    {"setupVsockClient", "(II)I",
        (void*)com_mediatek_rpcbinder_rpcsession_setup_vsock_client},
    {"setupInetClient", "(Ljava/lang/String;I)I",
        (void*)com_mediatek_rpcbinder_rpcsession_setup_inet_client},
    {"getRootObject", "()Landroid/os/IBinder;",
        (void*)com_mediatek_rpcbinder_rpcsession_get_root_object},
    {"release", "()V",
        (void*)com_mediatek_rpcbinder_rpcsession_release},
    {"native_finalize", "()V",
        (void*)com_mediatek_rpcbinder_rpcsession_native_finalize},
};

int register_com_mediatek_rpcbinder_RpcSession(JNIEnv* env) {
    LOGI("jni register native methods");
    AndroidRuntime::registerNativeMethods(env,
                "com/mediatek/rpcbinder/RpcSession",
                gMethods, sizeof(gMethods) / sizeof(gMethods[0]));
    return JNI_OK;
}

extern int register_com_mediatek_rpcbinder_RpcServer(JNIEnv* env);

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    register_com_mediatek_rpcbinder_RpcSession(env);
    register_com_mediatek_rpcbinder_RpcServer(env);

    return JNI_VERSION_1_6;
}
