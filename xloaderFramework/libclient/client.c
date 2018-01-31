#include <sys/stat.h>
#include <jni.h>  
#include <cutils/properties.h>

#include "../xloaderd/xloader.h"

static int load_native(const char *modulePath)    
{
    void *handle;
    JNIEnv* (*callfun)();
    char *err;

    handle = dlopen(modulePath, 0);
    if (!handle) {
      PRINT_LOGD("%s \n",dlerror());
      return ERROR_CLIENT_MODULE_NOT_FOUND;
    }
    dlerror();

    callfun = dlsym(handle,"module_entry");
    if ((err = dlerror()) != NULL) {
      PRINT_LOGD("%s \n",err);
      dlclose(handle);
      return ERROR_CLIENT_LOAD_ERROR;
    }

    callfun();
    dlclose(handle);
    return SUCCESS;
}    

static jstring stoJstring(JNIEnv* env, const char* pat) 
{ 
    jclass strClass = (*env)->FindClass(env, "java/lang/String"); 
    jmethodID ctorID = (*env)->GetMethodID(env, strClass, "<init>", "([BLjava/lang/String;)V"); 
    jbyteArray bytes = (*env)->NewByteArray(env, strlen(pat)); 
    (*env)->SetByteArrayRegion(env, bytes, 0, strlen(pat), (jbyte*)pat); 
    jstring encoding = (*env)->NewStringUTF(env, "utf-8"); 
    return (jstring)(*env)->NewObject(env, strClass, ctorID, bytes, encoding); 
}

#if 0
static void load_jar_and_run(JNIEnv *env, char* jarPath, char* dexPath, char* className, char* methodName) {
  jclass classloaderClass = (*env)->FindClass(env, "java/lang/ClassLoader");
  jmethodID getsysloaderMethod = (*env)->GetStaticMethodID(env, classloaderClass, "getSystemClassLoader","()Ljava/lang/ClassLoader;");
  jobject loader = (*env)->CallStaticObjectMethod(env, classloaderClass,getsysloaderMethod);
  
  jstring dexpath = stoJstring(env, jarPath);
  jstring dex_odex_path = stoJstring(env, dexPath);
  jclass dexLoaderClass = (*env)->FindClass(env, "dalvik/system/DexClassLoader");
  jmethodID initDexLoaderMethod = (*env)->GetMethodID(env, dexLoaderClass, "<init>","(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/ClassLoader;)V");
  jobject dexLoader = (*env)->NewObject(env, dexLoaderClass,initDexLoaderMethod, dexpath, dex_odex_path, NULL, loader);

  jmethodID findclassMethod = (*env)->GetMethodID(env, dexLoaderClass,"findClass", "(Ljava/lang/String;)Ljava/lang/Class;");
    if (NULL==findclassMethod)
    {
           findclassMethod = (*env)->GetMethodID(env, dexLoaderClass,"loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
    }
  jstring javaClassName = stoJstring(env, className);
  jclass javaClientClass=(jclass)(*env)->CallObjectMethod(env, dexLoader,findclassMethod,javaClassName);

  jmethodID initClientMethod = (*env)->GetMethodID(env, javaClientClass, "<init>","()V");
  jobject javaClientObj = (*env)->NewObject(env, javaClientClass,initClientMethod);

  jstring javaMethodName = stoJstring(env, methodName);
  const char* func = (*env)->GetStringUTFChars(env, javaMethodName, NULL);
  jmethodID inject_method = (*env)->GetMethodID(env, javaClientClass, func, "()V");
  (*env)->CallVoidMethod(env, javaClientObj,inject_method);
}
#endif

static int ClearException(JNIEnv *env) {
    jthrowable exception = (*env)->ExceptionOccurred(env);
    if (exception != NULL) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
        return 1;
    }
    return 0;
}

static int load_java(JNIEnv *env, char* bridgePath, char* bridgeDexPath, char* modulePath) {
	PRINT_LOGD("load_module %s, %s, %s\n", bridgePath, (bridgeDexPath != NULL ? bridgeDexPath : "NULL"), modulePath);
	jclass jcActivityThread = (*env)->FindClass(env, "android/app/ActivityThread");
	if (ClearException(env)) {
		PRINT_LOGD("No class : %s", "android/app/ActivityThread");
		return -ERROR_CLIENT_LOAD_ERROR;
	}
	jmethodID jmAT_currentApp = (*env)->GetStaticMethodID(env, jcActivityThread, 
                                      "currentApplication","()Landroid/app/Application;");
	jobject joApplication = (*env)->CallStaticObjectMethod(env, jcActivityThread, jmAT_currentApp);
  
	jclass jcApplication = (*env)->FindClass(env, "android/app/Application");
	if (ClearException(env)) {
		PRINT_LOGD("No class : %s", "android/app/Application");
		return -ERROR_CLIENT_LOAD_ERROR;
	}
	jmethodID jmApp_getClassLoader = (*env)->GetMethodID(env, jcApplication, 
                                           "getClassLoader","()Ljava/lang/ClassLoader;");
	jobject joAppLoader = (*env)->CallObjectMethod(env, joApplication, jmApp_getClassLoader);
  
	jstring jstrJarPath = NULL;
	jstring jstrJarDexPath = NULL;
	jclass jcClientLoader = NULL;
	jmethodID jmClientLoader_init = NULL;
	jobject joClientLoader = NULL;
	if (bridgeDexPath == NULL) {
		PRINT_LOGD("Using PathClassLoader");
		jstrJarPath = stoJstring(env, bridgePath);
		jcClientLoader = (*env)->FindClass(env, "dalvik/system/PathClassLoader");
		if (ClearException(env)) {
			PRINT_LOGD("No class : %s", "dalvik/system/PathClassLoader");
			return -ERROR_CLIENT_LOAD_ERROR;
		}
		jmClientLoader_init = (*env)->GetMethodID(env, jcClientLoader, 
                                    "<init>","(Ljava/lang/String;Ljava/lang/ClassLoader;)V");
		joClientLoader = (*env)->NewObject(env, jcClientLoader, jmClientLoader_init, jstrJarPath, joAppLoader);
		if (ClearException(env) || joClientLoader == NULL) {
			PRINT_LOGD("Load bridge error using PathClassLoader");
			return -ERROR_CLIENT_LOAD_ERROR;
		}
    } else {
		PRINT_LOGD("Using DexClassLoader");
		jstrJarPath = stoJstring(env, bridgePath);
		jstrJarDexPath = stoJstring(env, bridgeDexPath);
		jcClientLoader = (*env)->FindClass(env, "dalvik/system/DexClassLoader");
		if (ClearException(env)) {
			PRINT_LOGD("No class : %s", "dalvik/system/DexClassLoader");
			return -ERROR_CLIENT_LOAD_ERROR;
		}
		jmClientLoader_init = (*env)->GetMethodID(env, jcClientLoader, 
                                    "<init>","(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/ClassLoader;)V");
		joClientLoader = (*env)->NewObject(env, jcClientLoader, jmClientLoader_init, jstrJarPath, jstrJarDexPath, NULL, joAppLoader);
		if (ClearException(env) || joClientLoader == NULL) {
			PRINT_LOGD("Load bridge error using DexClassLoader");
			return -ERROR_CLIENT_LOAD_ERROR;
		}
    }

	jmethodID jmDexLoader_findClass = (*env)->GetMethodID(env, jcClientLoader, 
											"findClass", "(Ljava/lang/String;)Ljava/lang/Class;");
	if (NULL == jmDexLoader_findClass) {
		jmDexLoader_findClass = (*env)->GetMethodID(env, jcClientLoader, 
										"loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
	}
	jstring jstrBridge = stoJstring(env, "de.robv.android.xposed.XposedBridge");
	jclass jcBridge =(jclass)(*env)->CallObjectMethod(env, joClientLoader, jmDexLoader_findClass, jstrBridge);
	if (ClearException(env) || jcBridge == NULL) {
		PRINT_LOGD("Brigde class not found %s");
		return -ERROR_CLIENT_BRIDGE_ERROR;
	}

	jstring jstrModulePath = stoJstring(env, modulePath);
	jmethodID jmBridge_loadModule = (*env)->GetStaticMethodID(env, jcBridge, 
                                             "loadModuleFromJar", "(Ljava/lang/String;Ljava/lang/ClassLoader;)V");
	(*env)->CallStaticVoidMethod(env, jcBridge, jmBridge_loadModule, jstrModulePath, joClientLoader);
  	if (ClearException(env)) {
		PRINT_LOGD("Bridge Method error");
		return -ERROR_CLIENT_BRIDGE_FAILED;
	}
	return SUCCESS;
}

JNIEnv* getEnv() {
    void *handle;
    JNIEnv* (*callfun)();
    char *err;
    JNIEnv *env = NULL;

    handle = dlopen("/system/lib/libandroid_runtime.so", 0);
    if (!handle) {
      PRINT_LOGD("%s \n",dlerror());
      return NULL;
    }
    dlerror();

    callfun = dlsym(handle,"_ZN7android14AndroidRuntime9getJNIEnvEv");
    if ((err = dlerror()) != NULL) {
      PRINT_LOGD("%s \n",err);
      dlclose(handle);
      return NULL;
    }

    env = callfun();
    dlclose(handle);
    return env; 
}

#define DALVIK_CACHE_PREFIX_OLD    "/data/dalvik-cache/"
#define DALVIK_CACHE_PREFIX_ARM    "/data/dalvik-cache/arm"
#define DALVIK_CACHE_PREFIX_ARM64    "/data/dalvik-cache/arm64"
#define DALVIK_CACHE_POSTFIX   "/classes.dex"

int isDexOutputCache(char* apkPath)
{
    char sdk[PROPERTY_VALUE_MAX];
    char dexPath[STR_LEN_LIMIT];
	char *dalvik_cache_prefix;
    char *tmp;

    if (strncmp(apkPath, "/system/framework/", 18) == 0) {
        return 1;
    }

    property_get("ro.build.version.sdk", sdk, "n/a");
    if (strncmp(sdk, "2", 1) == 0) {
		dalvik_cache_prefix = DALVIK_CACHE_PREFIX_OLD;
    } else {
		dalvik_cache_prefix = DALVIK_CACHE_PREFIX_ARM;
	}

    if (*apkPath == '/')
        apkPath++;

	memset(dexPath, 0, STR_LEN_LIMIT);
	strcpy(dexPath, dalvik_cache_prefix);
	strcat(dexPath, apkPath);
    strcat(dexPath, DALVIK_CACHE_POSTFIX);
    for(tmp = dexPath + strlen(dalvik_cache_prefix); *tmp; tmp++) {
        if (*tmp == '/') {
            *tmp = '@';
        }
    }
    struct stat st;
    if ( stat(dexPath, &st) < 0 ) {
        PRINT_LOGD("DexOutput [%s] not found, using DexClassLoader!!", dexPath);
        return 0;
    }
    PRINT_LOGD("DexOutput [%s] found, using PathClassLoader!!", dexPath);
    return 1;
}


int g_already_Xloader = 0;

int do_xload(XloaderInfo *pXloaderInfo)
{
    if (pXloaderInfo->packageName == NULL || pXloaderInfo->bridgePath == NULL 
            || pXloaderInfo->modulePath == NULL) {
        PRINT_LOGD("parameter error!!");
        return -1;
    }
    
    PRINT_LOGD("xclient parameter: %s, %s, %s\n", 
            pXloaderInfo->packageName, pXloaderInfo->bridgePath, pXloaderInfo->modulePath);
    
    struct stat st;
    if ( stat(pXloaderInfo->modulePath, &st) < 0 ) {
        PRINT_LOGD("module not found!!");
        return -ERROR_CLIENT_MODULE_NOT_FOUND;
    }
    if ( strstr(pXloaderInfo->modulePath, ".so")) {
        return load_native(pXloaderInfo->modulePath); 
    }

    if ( stat(pXloaderInfo->bridgePath, &st) < 0 ) {
        PRINT_LOGD("bridge not found!!");
        return -ERROR_CLIENT_BRIDEG_NOT_FOUND;
    }

    JNIEnv* env = getEnv();
    if (env == NULL) {
        PRINT_LOGD("env not found!!");
        return -ERROR_CLIENT_ENV_NOT_FOUND;
    }

    int ret;
    if (isDexOutputCache(pXloaderInfo->bridgePath)) {
        ret = load_java(env, pXloaderInfo->bridgePath, NULL, pXloaderInfo->modulePath);
    } else {
        char* bridgeDexPath = (char *)malloc(STR_LEN_LIMIT);
        if (!strcmp(pXloaderInfo->packageName, "android")) {
            strcpy(bridgeDexPath, "/data/dalvik-cache/");
        } else {
            strcpy(bridgeDexPath, "/data/data/");
            strcat(bridgeDexPath, pXloaderInfo->packageName);
        }
        ret = load_java(env, pXloaderInfo->bridgePath, bridgeDexPath, pXloaderInfo->modulePath);
        free(bridgeDexPath);
    }
    g_already_Xloader = 1;
    return ret;
}

int client_entry(void* para) {  
    PRINT_LOGD("client_entry\n");
    if ( g_already_Xloader != 0) {
        PRINT_LOGD("already in Xloader\n");
        return ERROR_CLIENT_ENTRY_LOADER;
    }
    return do_xload((XloaderInfo *)para);
}  
