LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := termux-bootstrap
LOCAL_SRC_FILES := termux-bootstrap-zip.S termux-bootstrap.c
LOCAL_LDLIBS := -llog -lc -landroid
include $(BUILD_SHARED_LIBRARY)


include $(CLEAR_VARS)
LOCAL_MODULE := termux
LOCAL_SRC_FILES := termux.c
LOCAL_LDLIBS := -llog -lc -landroid
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := local-socket
LOCAL_SRC_FILES := local-socket.cpp
LOCAL_LDLIBS := -llog -lc -landroid -lstdc++
include $(BUILD_SHARED_LIBRARY)
