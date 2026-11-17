#include <jni.h>

#define PROJECT_NAME "W3AndroidEngine"
#define PROJECT_URL "https://github.com/nikoltu/W3AndroidEngine"

JNIEXPORT jstring JNICALL Java_com_etheller_warsmash_android_ProjectLink_nativeName(JNIEnv *env, jclass type) {
	return (*env)->NewStringUTF(env, PROJECT_NAME);
}

JNIEXPORT jstring JNICALL Java_com_etheller_warsmash_android_ProjectLink_nativeUrl(JNIEnv *env, jclass type) {
	return (*env)->NewStringUTF(env, PROJECT_URL);
}
