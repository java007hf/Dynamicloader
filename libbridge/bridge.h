#ifndef BRIDGE_H_
#define BRIDGE_H_

#define ANDROID_SMP 0
#include "Dalvik.h"


namespace android {

#define BRIDGE_DIR "/data/data/com.tencent.qrom.dynxloaderbridge.installer/"
#define BRIDGE_JAR BRIDGE_DIR "bin/Bridge.jar"
#define BRIDGE_JAR_NEWVERSION BRIDGE_DIR "bin/Bridge.jar.newversion"
#define BRIDGE_LOAD_BLOCKER BRIDGE_DIR "conf/disabled"
#define BRIDGE_ENABLE_FOR_TOOLS BRIDGE_DIR "conf/enable_for_tools"
#define BRIDGE_SAFEMODE_NODELAY BRIDGE_DIR "conf/safemode_nodelay"
#define BRIDGE_SAFEMODE_DISABLE BRIDGE_DIR "conf/safemode_disable"
#define BRIDGE_OVERRIDE_JIT_RESET_OFFSET BRIDGE_DIR "conf/jit_reset_offset"

#define BRIDGE_CLASS "com/tencent/qrom/dynxloaderbridge/Bridge"
#define BRIDGE_CLASS_DOTS "com.tencent.qrom.dynxloaderbridge.Bridge"
#define BRIDGERESOURCES_CLASS "android/content/res/BridgeResources"
#define MIUI_RESOURCES_CLASS "android/content/res/MiuiResources"
#define BRIDGETYPEDARRAY_CLASS "android/content/res/BridgeResources$BridgeTypedArray"

#define BRIDGE_VERSION "58"

#ifndef ALOGD
#define ALOGD LOGD
#define ALOGE LOGE
#define ALOGI LOGI
#define ALOGV LOGV
#endif

extern bool keepLoadingBridge;

struct BridgeHookInfo {
    struct {
        Method originalMethod;
        // copy a few bytes more than defined for Method in AOSP
        // to accomodate for (rare) extensions by the target ROM
        int dummyForRomExtensions[4];
    } originalMethodStruct;

    Object* reflectedMethod;
    Object* additionalInfo;
};

// called directoy by app_process
void bridgeInfo();
void bridgeEnforceDalvik();
void disableBridge();
bool isBridgeDisabled();
bool bridgeSkipSafemodeDelay();
bool bridgeDisableSafemode();
static int bridgeReadIntConfig(const char* fileName, int defaultValue);
bool bridgeShouldIgnoreCommand(const char* className, int argc, const char* const argv[]);
bool addBridgeToClasspath(bool zygote);
static void bridgePrepareSubclassReplacement(jclass clazz);
bool bridgeOnVmCreated(JNIEnv* env, const char* className);
static bool bridgeInitMemberOffsets(JNIEnv* env);
static inline void bridgeSetObjectArrayElement(const ArrayObject* obj, int index, Object* val);

// handling hooked methods / helpers
static void bridgeCallHandler(const u4* args, JValue* pResult, const Method* method, ::Thread* self);
static inline bool bridgeIsHooked(const Method* method);

// JNI methods
static jboolean com_tencent_qrom_dynxloaderbridge_Bridge_initNative(JNIEnv* env, jclass clazz);
static void com_tencent_qrom_dynxloaderbridge_Bridge_hookMethodNative(JNIEnv* env, jclass clazz, jobject reflectedMethodIndirect,
            jobject declaredClassIndirect, jint slot, jobject additionalInfoIndirect);
static void com_tencent_qrom_dynxloaderbridge_Bridge_invokeOriginalMethodNative(const u4* args, JValue* pResult, const Method* method, ::Thread* self);
static void android_content_res_BridgeResources_rewriteXmlReferencesNative(JNIEnv* env, jclass clazz, jint parserPtr, jobject origRes, jobject repRes);
static jobject com_tencent_qrom_dynxloaderbridge_Bridge_getStartClassName(JNIEnv* env, jclass clazz);
static void com_tencent_qrom_dynxloaderbridge_Bridge_setObjectClassNative(JNIEnv* env, jclass clazz, jobject objIndirect, jclass clzIndirect);
static void com_tencent_qrom_dynxloaderbridge_Bridge_dumpObjectNative(JNIEnv* env, jclass clazz, jobject objIndirect);
static jobject com_tencent_qrom_dynxloaderbridge_Bridge_cloneToSubclassNative(JNIEnv* env, jclass clazz, jobject objIndirect, jclass clzIndirect);

static int register_com_tencent_qrom_dynxloaderbridge_Bridge(JNIEnv* env);
static int register_android_content_res_BridgeResources(JNIEnv* env);

} // namespace android

#endif  // BRIDGE_H_
