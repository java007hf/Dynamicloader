#ifndef __XLOADER_H__
#define __XLOADER_H__

#ifdef __cplusplus
extern "C" {
#endif

#ifndef LOG_TAG
#define LOG_TAG "xloader"
#endif

#include <stdio.h>    
#include <stdlib.h>    
#include <dlfcn.h>    
#include <dirent.h>    
#include <string.h>    
#include <cutils/log.h>    

#ifdef DEBUG
    #define PRINT_LOGD(fmt, args...) ALOGD(fmt, ##args)  
    #ifdef XLOADER_BIN
        #define PRINT_ERROR(fmt, args...) printf(fmt, ##args) 
    #else
        #define PRINT_ERROR(fmt, args...) ALOGE(fmt, ##args) 
    #endif
#else
    #define PRINT_LOGD(fmt, args...)
    #ifdef XLOADER_BIN
        #define PRINT_ERROR(fmt, args...) printf(fmt, ##args) 
    #else
        #define PRINT_ERROR(fmt, args...) ALOGE(fmt, ##args) 
    #endif
#endif

#define SUCCESS					0
#define ERROR_XLOADER_CLIENT_NOT_FOUND		1
#define ERROR_XLOADER_BRIDEG_NOT_FOUND		2
#define ERROR_XLOADER_MODULE_NOT_FOUND		3
#define ERROR_XLOADER_PROCESS_NOT_FOUND		4
#define ERROR_XLOADER_PROCESS_LOADED		5
#define ERROR_XLOADER_PTRACE_FAILED			6
#define ERROR_XLOADER_MMAP_FAILED			7
#define ERROR_XLOADER_REMOTE_CALL_FAILED	8
#define ERROR_XLOADER_DL_NOT_LOADED			8
#define ERROR_XLOADER_DLOPEN_FAILED			9
#define ERROR_XLOADER_DLERROR_FAILED		10
#define ERROR_XLOADER_DLSYM_FAILED			11
#define ERROR_XLOADER_ENTRY_FAILED			12

#define ERROR_CLIENT_ENTRY_LOADER			13
#define ERROR_CLIENT_BRIDEG_NOT_FOUND		14
#define ERROR_CLIENT_MODULE_NOT_FOUND		15
#define ERROR_CLIENT_ENV_NOT_FOUND			16
#define ERROR_CLIENT_LOAD_ERROR				17
#define ERROR_CLIENT_BRIDGE_ERROR			18
#define ERROR_CLIENT_BRIDGE_FAILED			19

#ifdef MODE_XPOSED
    #define CLIENT_PATH_CURRENT "/system/lib/libclient.so"
    #define CLIENT_ENTRY_FUNC "client_entry"
    #define BRIDGE_PATH_CURRENT "/system/framework/XposedBridge.jar"
    #define BRIDGE_LIB_PATH_CURRENT "/system/lib/libxposed.so"
#else
    #define JAVA_PACKAGE_NAME "com.tencent.qrom.dynxloader"

    #define CLIENT_PATH "/data/data/"JAVA_PACKAGE_NAME"/cache/client"
    #define CLIENT_PATH_BACKUP "/system/lib/libclient.so"
    #define CLIENT_PATH_CURRENT CLIENT_PATH_BACKUP

    #define CLIENT_ENTRY_FUNC "client_entry"

    #define BRIDGE_PATH "/data/data/"JAVA_PACKAGE_NAME"/cache/bridge.jar"
    #define BRIDGE_PATH_BACKUP "/system/framework/bridge.jar"
    #define BRIDGE_PATH_CURRENT BRIDGE_PATH

    #define BRIDGE_LIB_PATH "/data/data/"JAVA_PACKAGE_NAME"/cache/bridge"
    #define BRIDGE_LIB_PATH_BACKUP "/system/lib/libbridge.so"
    #define BRIDGE_LIB_PATH_CURRENT BRIDGE_LIB_PATH_BACKUP 
#endif

#define STR_LEN_LIMIT 256
typedef struct XloaderInfo{
    char packageName[STR_LEN_LIMIT];
    char bridgePath[STR_LEN_LIMIT];
    char modulePath[STR_LEN_LIMIT];
}XloaderInfo;

int xload(const char *packageName, const char *processName, const char *modulePath);

#ifdef __cplusplus
}
#endif
#endif
