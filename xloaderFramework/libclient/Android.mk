LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_PRELINK_MODULE:=false
LOCAL_SRC_FILES := client.c
LOCAL_MODULE := libclient
LOCAL_CFLAGS += -DDEBUG

LOCAL_SHARED_LIBRARIES := \
	libcutils \
	libutils \
	liblog \
	libdl \
	libandroid_runtime

include $(BUILD_SHARED_LIBRARY)

##include $(BUILD_EXECUTABLE)
