LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
	bridge_safemode.cpp \
	bridge.cpp

LOCAL_MODULE_TAGS := optional

LOCAL_SHARED_LIBRARIES := \
	libcutils \
	libutils \
	libbinder \
	libandroid_runtime \
	libdvm \
	libstlport \
	libdl

ifneq ($(PLATFORM_SDK_VERSION),15)
LOCAL_SHARED_LIBRARIES += libandroidfw
endif

LOCAL_C_INCLUDES += dalvik \
                    dalvik/vm \
                    external/stlport/stlport \
                    bionic \
                    bionic/libstdc++/include

LOCAL_MODULE_TAGS := optional

LOCAL_CFLAGS += -DPLATFORM_SDK_VERSION=$(PLATFORM_SDK_VERSION)

ifeq ($(strip $(WITH_JIT)),true)
LOCAL_CFLAGS += -DWITH_JIT
endif

ifeq ($(strip $(BRIDGE_SHOW_OFFSETS)),true)
LOCAL_CFLAGS += -DBRIDGE_SHOW_OFFSETS
endif

LOCAL_CFLAGS += -DPLATFORM_SDK_VERSION=$(PLATFORM_SDK_VERSION)
LOCAL_MODULE := libbridge

include $(BUILD_SHARED_LIBRARY)
