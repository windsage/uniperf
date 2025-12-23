#include "android_runtime/AndroidRuntime.h"

#include "com_mediatek_rpcbinder_RpcServer.h"

using namespace android;

#define LOG_TAG "RpcServer-JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static struct fields_t {
    jfieldID context;
} gFields;

JRpcServer::JRpcServer(JNIEnv *env, jobject thiz)
    : mClass(NULL),
      mObject(NULL) {
    jclass clazz = env->GetObjectClass(thiz);
    CHECK(clazz != NULL);
    mClass = (jclass)env->NewGlobalRef(clazz);
    mObject = env->NewWeakGlobalRef(thiz);
    mImpl = RpcServer::make();
    LOGI("create JRpcServer");
}

sp<RpcServer> JRpcServer::getRpcServer() {
    return mImpl;
}

JRpcServer::~JRpcServer() {
    JNIEnv *env = AndroidRuntime::getJNIEnv();

    env->DeleteWeakGlobalRef(mObject);
    mObject = NULL;
    env->DeleteGlobalRef(mClass);
    mClass = NULL;
    mImpl = nullptr;
    LOGI("destroy JRpcServer");
}


static sp<JRpcServer> setJRpcServer(
        JNIEnv *env, jobject thiz, const sp<JRpcServer> &rpcserver) {
    LOGI("setJRpcServer");
    sp<JRpcServer> old =
        (JRpcServer *)env->GetLongField(thiz, gFields.context);

    if (rpcserver != NULL) {
        LOGI("setJRpcServer: incStrong %p", rpcserver.get());
        rpcserver->incStrong(thiz);
    }
    if (old != NULL) {
        LOGI("setJRpcServer: decStrong %p", old.get());
        old->decStrong(thiz);
    }
    env->SetLongField(thiz, gFields.context, (jlong)rpcserver.get());

    return old;
}

static sp<JRpcServer> getJRpcServer(JNIEnv *env, jobject thiz) {
    return (JRpcServer *)env->GetLongField(thiz, gFields.context);
}

static void com_mediatek_rpcbinder_rpcserver_native_setup(
        JNIEnv *env, jobject thiz) {
    LOGI("native setup");
    sp<JRpcServer> server = new JRpcServer(env, thiz);

    LOGI("native setup: server=%p, this=%p", server.get(), thiz);
    setJRpcServer(env, thiz, server);
    return;
}

static void com_mediatek_rpcbinder_rpcserver_join(
        JNIEnv *env, jobject thiz) {
    sp <JRpcServer> jserver = getJRpcServer(env, thiz);
    if (jserver == nullptr) {
        LOGE("JRpcServer is null");
        return;
    }
    sp <RpcServer> rpcserver = jserver->getRpcServer();
    if (rpcserver == nullptr) {
        LOGE("RpcServer is null");
        return;
    }
    rpcserver->join();
    LOGI("joined RpcServer");
    return;
}

static void com_mediatek_rpcbinder_rpcserver_release(
        JNIEnv *env, jobject thiz) {
    setJRpcServer(env, thiz, nullptr);
    return;
}

static void com_mediatek_rpcbinder_rpcserver_native_finalize(
        JNIEnv *env, jobject thiz) {
    com_mediatek_rpcbinder_rpcserver_release(env, thiz);
    return;
}

static void rpcservercom_mediatek_rpcbinder_rpcserver_set_max_threads(
        JNIEnv *env, jobject thiz, jlong threads) {
    sp <JRpcServer> jserver = getJRpcServer(env, thiz);
    if (jserver == nullptr) {
        LOGE("JRpcServer is null");
        return;
    }
    sp <RpcServer> rpcserver = jserver->getRpcServer();
    if (rpcserver == nullptr) {
        LOGE("RpcServer is null");
        return;
    }
    LOGI("setMaxThreads %ld", threads);
    rpcserver->setMaxThreads(static_cast<size_t>(threads));
    return;
}

static jint com_mediatek_rpcbinder_rpcserver_setup_vsock_server(
        JNIEnv *env, jobject thiz, jint cvd, jint port) {
    jint result = -1;
    sp <JRpcServer> jserver = getJRpcServer(env, thiz);
    if (jserver == nullptr) {
        LOGE("JRpcServer is null");
        return result;
    }
    sp <RpcServer> rpcserver = jserver->getRpcServer();
    if (rpcserver == nullptr) {
        LOGE("RpcServer is null");
        return result;
    }
    LOGI("setupVsockServer %d:%d", cvd, port);
    return static_cast<jint>(rpcserver->setupVsockServer(
            static_cast<unsigned int>(cvd), static_cast<unsigned int>(port)));
}

static jint com_mediatek_rpcbinder_rpcserver_setup_inet_server(
        JNIEnv *env, jobject thiz, jstring addr, jint port) {
    jint result = -1;
    sp <JRpcServer> jserver = getJRpcServer(env, thiz);
    if (jserver == nullptr) {
        LOGE("JRpcServer is null");
        return result;
    }
    sp <RpcServer> rpcserver = jserver->getRpcServer();
    if (rpcserver == nullptr) {
        LOGE("RpcServer is null");
        return result;
    }

    const char* nativeAddr = env->GetStringUTFChars(addr, nullptr);
    result = static_cast<jint>(rpcserver->setupInetServer(
                nativeAddr, static_cast<unsigned int>(port)));
    LOGI("setupInetServer %s:%d", nativeAddr, port);
    env->ReleaseStringUTFChars(addr, nativeAddr);
    return result;
}

static void com_mediatek_rpcbinder_rpcserver_set_root_object(
        JNIEnv *env, jobject thiz, jobject rootObject) {
    jobject result = nullptr;
    sp <JRpcServer> jserver = getJRpcServer(env, thiz);
    if (jserver == nullptr) {
        LOGE("JRpcServer is null");
        return;
    }
    sp <RpcServer> rpcserver = jserver->getRpcServer();
    if (rpcserver == nullptr) {
        LOGE("RpcServer is null");
        return;
    }

    sp<IBinder> root = ibinderForJavaObject(env, rootObject);
    rpcserver->setRootObject(root);
    return;
}

static void com_mediatek_rpcbinder_rpcserver_native_init(JNIEnv *env) {
    LOGI("native init");
    jclass clazz = env->FindClass("com/mediatek/rpcbinder/RpcServer");
    CHECK(clazz != NULL);

    gFields.context = env->GetFieldID(clazz, "mNativeContext", "J");
    CHECK(gFields.context != NULL);
    return;
}

static JNINativeMethod gMethods[] = {
    {"native_init", "()V",
        (void*)com_mediatek_rpcbinder_rpcserver_native_init},
    {"native_setup", "()V",
        (void*)com_mediatek_rpcbinder_rpcserver_native_setup},
    {"setMaxThreads", "(J)V",
        (void*)rpcservercom_mediatek_rpcbinder_rpcserver_set_max_threads},
    {"setupVsockServer", "(II)I",
        (void*)com_mediatek_rpcbinder_rpcserver_setup_vsock_server},
    {"setupInetServer", "(Ljava/lang/String;I)I",
        (void*)com_mediatek_rpcbinder_rpcserver_setup_inet_server},
    {"setRootObject", "(Landroid/os/IBinder;)V",
        (void*)com_mediatek_rpcbinder_rpcserver_set_root_object},
    {"join", "()V",
        (void*)com_mediatek_rpcbinder_rpcserver_join},
    {"release", "()V",
        (void*)com_mediatek_rpcbinder_rpcserver_release},
    {"native_finalize", "()V",
        (void*)com_mediatek_rpcbinder_rpcserver_native_finalize},
};

int register_com_mediatek_rpcbinder_RpcServer(JNIEnv* env) {
    LOGI("jni register native methods");
    AndroidRuntime::registerNativeMethods(env,
                "com/mediatek/rpcbinder/RpcServer",
                gMethods, sizeof(gMethods) / sizeof(gMethods[0]));

    return JNI_OK;
}