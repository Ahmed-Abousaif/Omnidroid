#pragma once
#include "omnidroid_ps3.h"
#include <jni.h>
#include <string>
namespace ps3 {
std::string utf8(JNIEnv*, jstring);
jstring java_string(JNIEnv*, const char*);
void fail(JNIEnv*, const char*);
bool attach(JNIEnv*, jclass host, jclass storage);
omni_ps3_callbacks callbacks();
}
