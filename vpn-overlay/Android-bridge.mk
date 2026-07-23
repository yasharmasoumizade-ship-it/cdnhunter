# Builds hev-socks5-tunnel's real sources together with our own hev-jni.c
# bridge (see hev-jni.c in this same folder) into a single libhev-socks5-tunnel.so.
#
# Current upstream hev-socks5-tunnel ships NO Java/JNI wrapper of its own —
# only a pure C API (src/hev-main.h). Its own Android.mk therefore produces a
# .so with no Java-callable symbols at all. This file reuses the upstream
# library's own third-party static-lib includes (yaml, lwip, hev-task-system)
# and source list, then adds hev-jni.c on top so the resulting .so actually
# exports Java_hev_htproxy_TProxyService_* symbols that our TProxyService.java
# can call.

TOP_PATH := $(call my-dir)

ifeq ($(filter $(modules-get-list),yaml),)
    include $(TOP_PATH)/third-part/yaml/Android.mk
endif
ifeq ($(filter $(modules-get-list),lwip),)
    include $(TOP_PATH)/third-part/lwip/Android.mk
endif
ifeq ($(filter $(modules-get-list),hev-task-system),)
    include $(TOP_PATH)/third-part/hev-task-system/Android.mk
endif

LOCAL_PATH := $(TOP_PATH)
SRCDIR := $(LOCAL_PATH)/src
include $(LOCAL_PATH)/build.mk

include $(CLEAR_VARS)
LOCAL_MODULE := hev-socks5-tunnel
LOCAL_SRC_FILES := $(patsubst $(SRCDIR)/%,src/%,$(SRCFILES)) hev-jni.c
LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)/src \
    $(LOCAL_PATH)/src/misc \
    $(LOCAL_PATH)/src/core/include \
    $(LOCAL_PATH)/third-part/yaml/include \
    $(LOCAL_PATH)/third-part/lwip/src/include \
    $(LOCAL_PATH)/third-part/lwip/src/ports/include \
    $(LOCAL_PATH)/third-part/hev-task-system/include
LOCAL_CFLAGS += -DFD_SET_DEFINED -DSOCKLEN_T_DEFINED -DENABLE_LIBRARY -DANDROID
ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
LOCAL_CFLAGS += -mfpu=neon
endif
LOCAL_LDLIBS := -llog
LOCAL_STATIC_LIBRARIES := yaml lwip hev-task-system
LOCAL_LDFLAGS += -Wl,-z,max-page-size=16384
LOCAL_LDFLAGS += -Wl,-z,common-page-size=16384
include $(BUILD_SHARED_LIBRARY)
