/*
 * Bridge enables "god mode" for developers
 */

#define LOG_TAG "Bridge"

#include "bridge.h"

#include <utils/Log.h>
#include <android_runtime/AndroidRuntime.h>

#define private public
#if PLATFORM_SDK_VERSION == 15
#include <utils/ResourceTypes.h>
#else
#include <androidfw/ResourceTypes.h>
#endif
#undef private

#include <stdio.h>
#include <sys/mman.h>
#include <cutils/properties.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <dlfcn.h>

//fredyfang add
#include <jni.h>

#include "bridge_offsets.h"
//fredyfang modify
static int RUNNING_PLATFORM_SDK_VERSION;


namespace android {

////////////////////////////////////////////////////////////
// variables
////////////////////////////////////////////////////////////
bool keepLoadingBridge = false;
ClassObject* objectArrayClass = NULL;
jclass bridgeClass = NULL;
Method* bridgeHandleHookedMethod = NULL;
jclass bridgeresourcesClass = NULL;
jmethodID bridgeresourcesTranslateResId = NULL;
jmethodID bridgeresourcesTranslateAttrId = NULL;
const char* startClassName = NULL;
void* PTR_gDvmJit = NULL;
size_t arrayContentsOffset = 0;




////////////////////////////////////////////////////////////
// called directoy by app_process
////////////////////////////////////////////////////////////

void bridgeInfo() {
    char release[PROPERTY_VALUE_MAX];
    char sdk[PROPERTY_VALUE_MAX];
    char manufacturer[PROPERTY_VALUE_MAX];
    char model[PROPERTY_VALUE_MAX];
    char rom[PROPERTY_VALUE_MAX];
    char fingerprint[PROPERTY_VALUE_MAX];
    
    property_get("ro.build.version.release", release, "n/a");
    property_get("ro.build.version.sdk", sdk, "n/a");
    property_get("ro.product.manufacturer", manufacturer, "n/a");
    property_get("ro.product.model", model, "n/a");
    property_get("ro.build.display.id", rom, "n/a");
    property_get("ro.build.fingerprint", fingerprint, "n/a");
    
    ALOGD("Starting Bridge binary version %s, compiled for SDK %d\n", BRIDGE_VERSION, PLATFORM_SDK_VERSION);
    ALOGD("Phone: %s (%s), Android version %s (SDK %s)\n", model, manufacturer, release, sdk);
    ALOGD("ROM: %s\n", rom);
    ALOGD("Build fingerprint: %s\n", fingerprint);
}

void bridgeEnforceDalvik() {
	char sdk[PROPERTY_VALUE_MAX];
	property_get("ro.build.version.sdk", sdk, "0");
	RUNNING_PLATFORM_SDK_VERSION = atoi(sdk);
    if (RUNNING_PLATFORM_SDK_VERSION < 19)
        return;

    char runtime[PROPERTY_VALUE_MAX];
    property_get("persist.sys.dalvik.vm.lib", runtime, "");
    if (strcmp(runtime, "libdvm.so") != 0) {
        ALOGE("Unsupported runtime library %s, setting to libdvm.so", runtime);
        property_set("persist.sys.dalvik.vm.lib", "libdvm.so");
    }
}

void disableBridge() {
    int fd;
    fd = open(BRIDGE_LOAD_BLOCKER, O_WRONLY | O_CREAT, S_IRUSR | S_IWUSR);
    if (fd >= 0)
        close(fd);
}

bool isBridgeDisabled() {
    // is the blocker file present?
    if (access(BRIDGE_LOAD_BLOCKER, F_OK) == 0) {
        ALOGE("found %s, not loading Bridge\n", BRIDGE_LOAD_BLOCKER);
        return true;
    }
    return false;
}

bool bridgeSkipSafemodeDelay() {
    // is the flag file present?
    if (access(BRIDGE_SAFEMODE_NODELAY, F_OK) == 0)
        return true;
    else
        return false;
}

bool bridgeDisableSafemode() {
    // is the flag file present?
    if (access(BRIDGE_SAFEMODE_DISABLE, F_OK) == 0)
        return true;
    else
        return false;
}

static int bridgeReadIntConfig(const char* fileName, int defaultValue) {
    FILE *fp = fopen(fileName, "r");
    if (fp == NULL)
        return defaultValue;

    int result;
    int success = fscanf(fp, "%i", &result);
    fclose(fp);

    return (success >= 1) ? result : defaultValue;
}

// ignore the broadcasts by various Superuser implementations to avoid spamming the Bridge log
bool bridgeShouldIgnoreCommand(const char* className, int argc, const char* const argv[]) {
    if (className == NULL || argc < 4 || strcmp(className, "com.android.commands.am.Am") != 0)
        return false;
        
    if (strcmp(argv[2], "broadcast") != 0 && strcmp(argv[2], "start") != 0)
        return false;
        
    bool mightBeSuperuser = false;
    for (int i = 3; i < argc; i++) {
        if (strcmp(argv[i], "com.noshufou.android.su.RESULT") == 0
         || strcmp(argv[i], "eu.chainfire.supersu.NativeAccess") == 0)
            return true;
            
        if (mightBeSuperuser && strcmp(argv[i], "--user") == 0)
            return true;
            
        char* lastComponent = strrchr(argv[i], '.');
        if (!lastComponent)
            continue;
        
        if (strcmp(lastComponent, ".RequestActivity") == 0
         || strcmp(lastComponent, ".NotifyActivity") == 0
         || strcmp(lastComponent, ".SuReceiver") == 0)
            mightBeSuperuser = true;
    }
    
    return false;
}

bool addBridgeToClasspath(bool zygote) {
    ALOGI("-----------------\n");
    // do we have a new version and are (re)starting zygote? Then load it!
    if (zygote && access(BRIDGE_JAR_NEWVERSION, R_OK) == 0) {
        ALOGI("Found new Bridge jar version, activating it\n");
        if (rename(BRIDGE_JAR_NEWVERSION, BRIDGE_JAR) != 0) {
            ALOGE("could not move %s to %s\n", BRIDGE_JAR_NEWVERSION, BRIDGE_JAR);
            return false;
        }
    }
    if (access(BRIDGE_JAR, R_OK) == 0) {
        char* oldClassPath = getenv("CLASSPATH");
        if (oldClassPath == NULL) {
            setenv("CLASSPATH", BRIDGE_JAR, 1);
        } else {
            char classPath[4096];
            int neededLength = snprintf(classPath, sizeof(classPath), "%s:%s", BRIDGE_JAR, oldClassPath);
            if (neededLength >= (int)sizeof(classPath)) {
                ALOGE("ERROR: CLASSPATH would exceed %d characters", sizeof(classPath));
                return false;
            }
            setenv("CLASSPATH", classPath, 1);
        }
        ALOGI("Added Bridge (%s) to CLASSPATH.\n", BRIDGE_JAR);
        return true;
    } else {
        ALOGE("ERROR: could not access Bridge jar '%s'\n", BRIDGE_JAR);
        return false;
    }
}

static void bridgePrepareSubclassReplacement(jclass clazz) {
    // clazz is supposed to replace its superclass, so make sure enough memory is allocated
    ClassObject* sub = (ClassObject*) dvmDecodeIndirectRef(dvmThreadSelf(), clazz);
    ClassObject* super = sub->super;
    super->objectSize = sub->objectSize;
}


bool bridgeOnVmCreated(JNIEnv* env, const char* className) {
    startClassName = className;

    //fredyfang modify
#if 0
    keepLoadingBridge = keepLoadingBridge && bridgeInitMemberOffsets(env);
    if (!keepLoadingBridge)
        return false;
#endif

	keepLoadingBridge = true;
	bridgeInitMemberOffsets(env);
	
#if 0
    jclass miuiResourcesClass = env->FindClass(MIUI_RESOURCES_CLASS);
    if (miuiResourcesClass != NULL) {
        ClassObject* clazz = (ClassObject*)dvmDecodeIndirectRef(dvmThreadSelf(), miuiResourcesClass);
        if (dvmIsFinalClass(clazz)) {
            ALOGD("Removing final flag for class '%s'", MIUI_RESOURCES_CLASS);
            clazz->accessFlags &= ~ACC_FINAL;
        }
    }
    env->ExceptionClear();

    jclass bridgeTypedArrayClass = env->FindClass(BRIDGETYPEDARRAY_CLASS);
    if (bridgeTypedArrayClass == NULL) {
        ALOGE("Error while loading BridgeTypedArray class '%s':\n", BRIDGETYPEDARRAY_CLASS);
        dvmLogExceptionStackTrace();
        env->ExceptionClear();
        return false;
    }
    bridgePrepareSubclassReplacement(bridgeTypedArrayClass);
#endif

    bridgeClass = env->FindClass(BRIDGE_CLASS);
    bridgeClass = reinterpret_cast<jclass>(env->NewGlobalRef(bridgeClass));
    
    if (bridgeClass == NULL) {
        ALOGE("Error while loading Bridge class '%s':\n", BRIDGE_CLASS);
        dvmLogExceptionStackTrace();
        env->ExceptionClear();
        return false;
    }
    
    ALOGI("Found Bridge class '%s', now initializing\n", BRIDGE_CLASS);
    if (register_com_tencent_qrom_dynxloaderbridge_Bridge(env) != JNI_OK) {
        ALOGE("Could not register natives for '%s'\n", BRIDGE_CLASS);
        env->ExceptionClear();
        return false;
    }
    return true;
}


static bool bridgeInitMemberOffsets(JNIEnv* env) {
    PTR_gDvmJit = dlsym(RTLD_DEFAULT, "gDvmJit");

    if (PTR_gDvmJit == NULL) {
        offsetMode = MEMBER_OFFSET_MODE_NO_JIT;
    } else {
        offsetMode = MEMBER_OFFSET_MODE_WITH_JIT;
    }
    ALOGD("Using structure member offsets for mode %s", bridgeOffsetModesDesc[offsetMode]);

    MEMBER_OFFSET_COPY(DvmJitGlobals, codeCacheFull);

    int overrideCodeCacheFull = bridgeReadIntConfig(BRIDGE_OVERRIDE_JIT_RESET_OFFSET, -1);
    if (overrideCodeCacheFull > 0 && overrideCodeCacheFull < 0x400) {
        ALOGI("Offset for DvmJitGlobals.codeCacheFull is overridden, new value is 0x%x", overrideCodeCacheFull);
        MEMBER_OFFSET_VAR(DvmJitGlobals, codeCacheFull) = overrideCodeCacheFull;
    }

    // detect offset of ArrayObject->contents
    jintArray dummyArray = env->NewIntArray(1);
    if (dummyArray == NULL) {
        ALOGE("Could allocate int array for testing");
        dvmLogExceptionStackTrace();
        env->ExceptionClear();
        return false;
    }

    jint* dummyArrayElements = env->GetIntArrayElements(dummyArray, NULL);
    arrayContentsOffset = (size_t)dummyArrayElements - (size_t)dvmDecodeIndirectRef(dvmThreadSelf(), dummyArray);
    env->ReleaseIntArrayElements(dummyArray,dummyArrayElements, 0);
    env->DeleteLocalRef(dummyArray);

    if (arrayContentsOffset < 12 || arrayContentsOffset > 128) {
        ALOGE("Detected strange offset %d of ArrayObject->contents", arrayContentsOffset);
        return false;
    }

    return true;
}

static inline void bridgeSetObjectArrayElement(const ArrayObject* obj, int index, Object* val) {
    uintptr_t arrayContents = (uintptr_t)obj + arrayContentsOffset;
    ((Object **)arrayContents)[index] = val;
    dvmWriteBarrierArray(obj, index, index + 1);
}


////////////////////////////////////////////////////////////
// handling hooked methods / helpers
////////////////////////////////////////////////////////////

static void bridgeCallHandler(const u4* args, JValue* pResult, const Method* method, ::Thread* self) {
    if (!bridgeIsHooked(method)) {
        dvmThrowNoSuchMethodError("could not find Bridge original method - how did you even get here?");
        return;
    }

    BridgeHookInfo* hookInfo = (BridgeHookInfo*) method->insns;
    Method* original = (Method*) hookInfo;
    Object* originalReflected = hookInfo->reflectedMethod;
    Object* additionalInfo = hookInfo->additionalInfo;
  
    // convert/box arguments
    const char* desc = &method->shorty[1]; // [0] is the return type.
    Object* thisObject = NULL;
    size_t srcIndex = 0;
    size_t dstIndex = 0;
    
    // for non-static methods determine the "this" pointer
    if (!dvmIsStaticMethod(original)) {
        thisObject = (Object*) args[0];
        srcIndex++;
    }
    
    ArrayObject* argsArray = dvmAllocArrayByClass(objectArrayClass, strlen(method->shorty) - 1, ALLOC_DEFAULT);
    if (argsArray == NULL) {
        return;
    }
    
    while (*desc != '\0') {
        char descChar = *(desc++);
        JValue value;
        Object* obj;

        switch (descChar) {
        case 'Z':
        case 'C':
        case 'F':
        case 'B':
        case 'S':
        case 'I':
            value.i = args[srcIndex++];
            obj = (Object*) dvmBoxPrimitive(value, dvmFindPrimitiveClass(descChar));
            dvmReleaseTrackedAlloc(obj, self);
            break;
        case 'D':
        case 'J':
            value.j = dvmGetArgLong(args, srcIndex);
            srcIndex += 2;
            obj = (Object*) dvmBoxPrimitive(value, dvmFindPrimitiveClass(descChar));
            dvmReleaseTrackedAlloc(obj, self);
            break;
        case '[':
        case 'L':
            obj  = (Object*) args[srcIndex++];
            break;
        default:
            ALOGE("Unknown method signature description character: %c\n", descChar);
            obj = NULL;
            srcIndex++;
        }
        bridgeSetObjectArrayElement(argsArray, dstIndex++, obj);
    }
    
    // call the Java handler function
    JValue result;
    dvmCallMethod(self, bridgeHandleHookedMethod, NULL, &result,
        originalReflected, (int) original, additionalInfo, thisObject, argsArray);
        
    dvmReleaseTrackedAlloc(argsArray, self);

    // exceptions are thrown to the caller
    if (dvmCheckException(self)) {
        return;
    }

    // return result with proper type
    ClassObject* returnType = dvmGetBoxedReturnType(method);
    if (returnType->primitiveType == PRIM_VOID) {
        // ignored
    } else if (result.l == NULL) {
        if (dvmIsPrimitiveClass(returnType)) {
            dvmThrowNullPointerException("null result when primitive expected");
        }
        pResult->l = NULL;
    } else {
        if (!dvmUnboxPrimitive(result.l, returnType, pResult)) {
            dvmThrowClassCastException(result.l->clazz, returnType);
        }
    }
}



////////////////////////////////////////////////////////////
// JNI methods
////////////////////////////////////////////////////////////

static jboolean com_tencent_qrom_dynxloaderbridge_Bridge_initNative(JNIEnv* env, jclass clazz) {
    if (!keepLoadingBridge) {
        ALOGE("Not initializing Bridge because of previous errors\n");
        return false;
    }

    ::Thread* self = dvmThreadSelf();

    bridgeHandleHookedMethod = (Method*) env->GetStaticMethodID(bridgeClass, "handleHookedMethod",
        "(Ljava/lang/reflect/Member;ILjava/lang/Object;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");
    if (bridgeHandleHookedMethod == NULL) {
        ALOGE("ERROR: could not find method %s.handleHookedMethod(Member, int, Object, Object, Object[])\n", BRIDGE_CLASS);
        dvmLogExceptionStackTrace();
        env->ExceptionClear();
        keepLoadingBridge = false;
        return false;
    }

    Method* bridgeInvokeOriginalMethodNative = (Method*) env->GetStaticMethodID(bridgeClass, "invokeOriginalMethodNative",
        "(Ljava/lang/reflect/Member;I[Ljava/lang/Class;Ljava/lang/Class;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");
    if (bridgeInvokeOriginalMethodNative == NULL) {
        ALOGE("ERROR: could not find method %s.invokeOriginalMethodNative(Member, int, Class[], Class, Object, Object[])\n", BRIDGE_CLASS);
        dvmLogExceptionStackTrace();
        env->ExceptionClear();
        keepLoadingBridge = false;
        return false;
    }
    dvmSetNativeFunc(bridgeInvokeOriginalMethodNative, com_tencent_qrom_dynxloaderbridge_Bridge_invokeOriginalMethodNative, NULL);

    objectArrayClass = dvmFindArrayClass("[Ljava/lang/Object;", NULL);
    if (objectArrayClass == NULL) {
        ALOGE("Error while loading Object[] class");
        dvmLogExceptionStackTrace();
        env->ExceptionClear();
        keepLoadingBridge = false;
        return false;
    }

    bridgeresourcesClass = env->FindClass(BRIDGERESOURCES_CLASS);
    bridgeresourcesClass = reinterpret_cast<jclass>(env->NewGlobalRef(bridgeresourcesClass));
    if (bridgeresourcesClass == NULL) {
        ALOGE("Error while loading BridgeResources class '%s':\n", BRIDGERESOURCES_CLASS);
        dvmLogExceptionStackTrace();
        env->ExceptionClear();
        keepLoadingBridge = false;
        return false;
    }
    if (register_android_content_res_BridgeResources(env) != JNI_OK) {
        ALOGE("Could not register natives for '%s'\n", BRIDGERESOURCES_CLASS);
        env->ExceptionClear();
        keepLoadingBridge = false;
        return false;
    }

    bridgeresourcesTranslateResId = env->GetStaticMethodID(bridgeresourcesClass, "translateResId",
        "(ILandroid/content/res/BridgeResources;Landroid/content/res/Resources;)I");
    if (bridgeresourcesTranslateResId == NULL) {
        ALOGE("ERROR: could not find method %s.translateResId(int, Resources, Resources)\n", BRIDGERESOURCES_CLASS);
        dvmLogExceptionStackTrace();
        env->ExceptionClear();
        keepLoadingBridge = false;
        return false;
    }

    bridgeresourcesTranslateAttrId = env->GetStaticMethodID(bridgeresourcesClass, "translateAttrId",
        "(Ljava/lang/String;Landroid/content/res/BridgeResources;)I");
    if (bridgeresourcesTranslateAttrId == NULL) {
        ALOGE("ERROR: could not find method %s.findAttrId(String, Resources, Resources)\n", BRIDGERESOURCES_CLASS);
        dvmLogExceptionStackTrace();
        env->ExceptionClear();
        keepLoadingBridge = false;
        return false;
    }

    return true;
}

static void com_tencent_qrom_dynxloaderbridge_Bridge_hookMethodNative(JNIEnv* env, jclass clazz, jobject reflectedMethodIndirect,
            jobject declaredClassIndirect, jint slot, jobject additionalInfoIndirect) {
    // Usage errors?
    if (declaredClassIndirect == NULL || reflectedMethodIndirect == NULL) {
        dvmThrowIllegalArgumentException("method and declaredClass must not be null");
        return;
    }
    
    // Find the internal representation of the method
    ClassObject* declaredClass = (ClassObject*) dvmDecodeIndirectRef(dvmThreadSelf(), declaredClassIndirect);
    Method* method = dvmSlotToMethod(declaredClass, slot);
    if (method == NULL) {
        dvmThrowNoSuchMethodError("could not get internal representation for method");
        return;
    }
    
    if (bridgeIsHooked(method)) {
        // already hooked
        return;
    }
    
    // Save a copy of the original method and other hook info
    BridgeHookInfo* hookInfo = (BridgeHookInfo*) calloc(1, sizeof(BridgeHookInfo));
    memcpy(hookInfo, method, sizeof(hookInfo->originalMethodStruct));
    hookInfo->reflectedMethod = dvmDecodeIndirectRef(dvmThreadSelf(), env->NewGlobalRef(reflectedMethodIndirect));
    hookInfo->additionalInfo = dvmDecodeIndirectRef(dvmThreadSelf(), env->NewGlobalRef(additionalInfoIndirect));

    // Replace method with our own code
    SET_METHOD_FLAG(method, ACC_NATIVE);
    method->nativeFunc = &bridgeCallHandler;
    method->insns = (const u2*) hookInfo;
    method->registersSize = method->insSize;
    method->outsSize = 0;

    if (PTR_gDvmJit != NULL) {
        // reset JIT cache
        char currentValue = *((char*)PTR_gDvmJit + MEMBER_OFFSET_VAR(DvmJitGlobals,codeCacheFull));
        if (currentValue == 0 || currentValue == 1) {
            MEMBER_VAL(PTR_gDvmJit, DvmJitGlobals, codeCacheFull) = true;
        } else {
            ALOGE("Unexpected current value for codeCacheFull: %d", currentValue);
        }
    }
}

static inline bool bridgeIsHooked(const Method* method) {
    return (method->nativeFunc == &bridgeCallHandler);
}

// simplified copy of Method.invokeNative, but calls the original (non-hooked) method and has no access checks
// used when a method has been hooked
static void com_tencent_qrom_dynxloaderbridge_Bridge_invokeOriginalMethodNative(const u4* args, JValue* pResult,
            const Method* method, ::Thread* self) {
    Method* meth = (Method*) args[1];
    if (meth == NULL) {
        meth = dvmGetMethodFromReflectObj((Object*) args[0]);
        if (bridgeIsHooked(meth)) {
            meth = (Method*) meth->insns;
        }
    }
    ArrayObject* params = (ArrayObject*) args[2];
    ClassObject* returnType = (ClassObject*) args[3];
    Object* thisObject = (Object*) args[4]; // null for static methods
    ArrayObject* argList = (ArrayObject*) args[5];

    // invoke the method
    pResult->l = dvmInvokeMethod(thisObject, meth, argList, params, returnType, true);
    return;
}

static void android_content_res_BridgeResources_rewriteXmlReferencesNative(JNIEnv* env, jclass clazz,
            jint parserPtr, jobject origRes, jobject repRes) {

    ResXMLParser* parser = (ResXMLParser*)parserPtr;
    const ResXMLTree& mTree = parser->mTree;
    uint32_t* mResIds = (uint32_t*)mTree.mResIds;
    ResXMLTree_attrExt* tag;
    int attrCount;
    
    if (parser == NULL)
        return;
    
    //fredyfang del
#if 0
    do {
        switch (parser->next()) {
            case ResXMLParser::START_TAG:
                tag = (ResXMLTree_attrExt*)parser->mCurExt;
                attrCount = dtohs(tag->attributeCount);
                for (int idx = 0; idx < attrCount; idx++) {
                    ResXMLTree_attribute* attr = (ResXMLTree_attribute*)
                        (((const uint8_t*)tag)
                         + dtohs(tag->attributeStart)
                         + (dtohs(tag->attributeSize)*idx));
                         
                    // find resource IDs for attribute names
                    int32_t attrNameID = parser->getAttributeNameID(idx);
                    // only replace attribute name IDs for app packages
                    if (attrNameID >= 0 && (size_t)attrNameID < mTree.mNumResIds && dtohl(mResIds[attrNameID]) >= 0x7f000000) {
                        size_t attNameLen;
                        const char16_t* attrName = mTree.mStrings.stringAt(attrNameID, &attNameLen);
                        jint attrResID = env->CallStaticIntMethod(bridgeresourcesClass, bridgeresourcesTranslateAttrId,
                            env->NewString((const jchar*)attrName, attNameLen), origRes);
                        if (env->ExceptionCheck())
                            goto leave;
                            
                        mResIds[attrNameID] = htodl(attrResID);
                    }
                    
                    // find original resource IDs for reference values (app packages only)
                    if (attr->typedValue.dataType != Res_value::TYPE_REFERENCE)
                        continue;

                    jint oldValue = dtohl(attr->typedValue.data);
                    if (oldValue < 0x7f000000)
                        continue;
                    
                    jint newValue = env->CallStaticIntMethod(bridgeresourcesClass, bridgeresourcesTranslateResId,
                        oldValue, origRes, repRes);
                    if (env->ExceptionCheck())
                        goto leave;

                    if (newValue != oldValue)
                        attr->typedValue.data = htodl(newValue);
                }
                continue;
            case ResXMLParser::END_DOCUMENT:
            case ResXMLParser::BAD_DOCUMENT:
                goto leave;
            default:
                continue;
        }
    } while (true);
    
    leave:
    parser->restart();
#endif
}

static jobject com_tencent_qrom_dynxloaderbridge_Bridge_getStartClassName(JNIEnv* env, jclass clazz) {
    return env->NewStringUTF(startClassName);
}

static void com_tencent_qrom_dynxloaderbridge_Bridge_setObjectClassNative(JNIEnv* env, jclass clazz, jobject objIndirect, jclass clzIndirect) {
    Object* obj = (Object*) dvmDecodeIndirectRef(dvmThreadSelf(), objIndirect);
    ClassObject* clz = (ClassObject*) dvmDecodeIndirectRef(dvmThreadSelf(), clzIndirect);
    if (clz->status < CLASS_INITIALIZED && !dvmInitClass(clz)) {
        ALOGE("Could not initialize class %s", clz->descriptor);
        return;
    }
    obj->clazz = clz;
}

static void com_tencent_qrom_dynxloaderbridge_Bridge_dumpObjectNative(JNIEnv* env, jclass clazz, jobject objIndirect) {
    Object* obj = (Object*) dvmDecodeIndirectRef(dvmThreadSelf(), objIndirect);
    dvmDumpObject(obj);
}

static jobject com_tencent_qrom_dynxloaderbridge_Bridge_cloneToSubclassNative(JNIEnv* env, jclass clazz, jobject objIndirect, jclass clzIndirect) {
    Object* obj = (Object*) dvmDecodeIndirectRef(dvmThreadSelf(), objIndirect);
    ClassObject* clz = (ClassObject*) dvmDecodeIndirectRef(dvmThreadSelf(), clzIndirect);

    jobject copyIndirect = env->AllocObject(clzIndirect);
    if (copyIndirect == NULL)
        return NULL;

    Object* copy = (Object*) dvmDecodeIndirectRef(dvmThreadSelf(), copyIndirect);
    size_t size = obj->clazz->objectSize;
    size_t offset = sizeof(Object);
    memcpy((char*)copy + offset, (char*)obj + offset, size - offset);

    if (IS_CLASS_FLAG_SET(clz, CLASS_ISFINALIZABLE))
        dvmSetFinalizable(copy);

    return copyIndirect;
}

static const JNINativeMethod bridgeMethods[] = {
    {"getStartClassName", "()Ljava/lang/String;", (void*)com_tencent_qrom_dynxloaderbridge_Bridge_getStartClassName},
    {"initNative", "()Z", (void*)com_tencent_qrom_dynxloaderbridge_Bridge_initNative},
    {"hookMethodNative", "(Ljava/lang/reflect/Member;Ljava/lang/Class;ILjava/lang/Object;)V", (void*)com_tencent_qrom_dynxloaderbridge_Bridge_hookMethodNative},
    {"setObjectClassNative", "(Ljava/lang/Object;Ljava/lang/Class;)V", (void*)com_tencent_qrom_dynxloaderbridge_Bridge_setObjectClassNative},
    {"dumpObjectNative", "(Ljava/lang/Object;)V", (void*)com_tencent_qrom_dynxloaderbridge_Bridge_dumpObjectNative},
    {"cloneToSubclassNative", "(Ljava/lang/Object;Ljava/lang/Class;)Ljava/lang/Object;", (void*)com_tencent_qrom_dynxloaderbridge_Bridge_cloneToSubclassNative},
};

static const JNINativeMethod bridgeresourcesMethods[] = {
    {"rewriteXmlReferencesNative", "(ILandroid/content/res/BridgeResources;Landroid/content/res/Resources;)V", (void*)android_content_res_BridgeResources_rewriteXmlReferencesNative},
};

static int register_com_tencent_qrom_dynxloaderbridge_Bridge(JNIEnv* env) {
    return env->RegisterNatives(bridgeClass, bridgeMethods, NELEM(bridgeMethods));
}

static int register_android_content_res_BridgeResources(JNIEnv* env) {
    return env->RegisterNatives(bridgeresourcesClass, bridgeresourcesMethods, NELEM(bridgeresourcesMethods));
}


extern "C" jint JNI_OnLoad(JavaVM* vm, void* reserved) 
{
	JNIEnv* env = NULL;
    jint result = -1;

	ALOGE("JNI_OnLoad enter...");

    if (vm->GetEnv((void**) &env, JNI_VERSION_1_4) != JNI_OK) {
		return -1;
	}

    bridgeOnVmCreated(env, NULL);

	if(!com_tencent_qrom_dynxloaderbridge_Bridge_initNative(env, NULL)) {
		ALOGE("libbridge load failed...");
	}

    return JNI_VERSION_1_4;
}

}

