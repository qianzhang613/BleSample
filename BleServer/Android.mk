LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_STATIC_JAVA_LIBRARIES := android-support-v4 \
							   android-support-v13 \
							   android-support-v7-appcompat \
							   android-support-v7-cardview

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-subdir-java-files)

LOCAL_CERTIFICATE := platform

LOCAL_PACKAGE_NAME := BleServer

include $(BUILD_PACKAGE)


include $(CLEAR_VARS)

LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := android-support-v4:libs/android-support-v4.jar \
										android-support-v13:libs/android-support-v13.jar \
										android-support-v7-appcompat:libs/android-support-v7-appcompat.jar \
										android-support-v7-cardview:libs/android-support-v7-cardview.jar

include $(BUILD_MULTI_PREBUILT)
