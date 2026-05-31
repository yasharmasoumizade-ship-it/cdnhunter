LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

HEV := $(LOCAL_PATH)/hev-socks5-tunnel

LOCAL_MODULE := hev-socks5-tunnel
LOCAL_CFLAGS := -O2 -fPIC -DANDROID -Wno-typedef-redefinition

LOCAL_C_INCLUDES := $(HEV)/src $(HEV)/src/core/include $(HEV)/third-part/hev-task-system/include $(HEV)/third-part/lwip/src/include $(HEV)/third-part/lwip/src/ports/include $(HEV)/third-part/yaml/include

LOCAL_SRC_FILES := $(wildcard $(HEV)/src/*.c) $(wildcard $(HEV)/src/core/src/*.c)

LOCAL_LDLIBS := -llog

include $(BUILD_SHARED_LIBRARY)
