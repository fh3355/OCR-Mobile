// Copyright (c) 2019 PaddlePaddle Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "Native.h"
#include "pipeline.h"
#include <android/log.h>
#include <android/bitmap.h>

#ifdef __cplusplus
extern "C" {
#endif

// A helper function to convert a C++ string to a Java string.
static jstring CStr2Jstring(JNIEnv* env, const char* pat) {
    jclass strClass = (env)->FindClass("java/lang/String");
    jmethodID ctorID = (env)->GetMethodID(strClass, "<init>", "([BLjava/lang/String;)V");
    jbyteArray bytes = (env)->NewByteArray(strlen(pat));
    (env)->SetByteArrayRegion(bytes, 0, strlen(pat), (jbyte*)pat);
    jstring encoding = (env)->NewStringUTF("UTF-8");
    return (jstring)(env)->NewObject(strClass, ctorID, bytes, encoding);
}


JNIEXPORT jlong JNICALL
Java_fh3355_ocr_1mobile_OCRPredictor_nativeInit(
    JNIEnv *env, jobject thiz, jstring jDetModelPath, jstring jClsModelPath,
    jstring jRecModelPath, jstring jConfigPath, jstring jLabelPath,
    jint cpuThreadNum, jstring jCPUPowerMode) {
  std::string detModelPath = jstring_to_cpp_string(env, jDetModelPath);
  std::string clsModelPath = jstring_to_cpp_string(env, jClsModelPath);
  std::string recModelPath = jstring_to_cpp_string(env, jRecModelPath);
  std::string configPath = jstring_to_cpp_string(env, jConfigPath);
  std::string labelPath = jstring_to_cpp_string(env, jLabelPath);
  std::string cpuPowerMode = jstring_to_cpp_string(env, jCPUPowerMode);

  return reinterpret_cast<jlong>(
      new Pipeline(detModelPath, clsModelPath, recModelPath, cpuPowerMode,
                   cpuThreadNum, configPath, labelPath));
}

JNIEXPORT jboolean JNICALL
Java_fh3355_ocr_1mobile_OCRPredictor_nativeRelease(JNIEnv *env,
                                                                 jobject thiz,
                                                                 jlong ctx) {
  if (ctx == 0) {
    return JNI_FALSE;
  }
  Pipeline *pipeline = reinterpret_cast<Pipeline *>(ctx);
  delete pipeline;
  return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_fh3355_ocr_1mobile_OCRPredictor_nativeProcess(
    JNIEnv *env, jobject thiz, jlong ctx, jobject bitmap) {
    if (ctx == 0) {
        return CStr2Jstring(env, "Error: Predictor not initialized.");
    }

    // 1. Get bitmap info and pixels
    AndroidBitmapInfo info;
    void* pixels;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        return CStr2Jstring(env, "Error: Failed to get bitmap info.");
    }
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        return CStr2Jstring(env, "Error: Bitmap format is not RGBA_8888.");
    }
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        return CStr2Jstring(env, "Error: Failed to lock bitmap pixels.");
    }

    // 2. Create cv::Mat from bitmap pixels
    cv::Mat rgbaImage(info.height, info.width, CV_8UC4, pixels);

    // The pipeline expects a BGR image, so we need to convert it.
    cv::Mat bgrImage;
    cv::cvtColor(rgbaImage, bgrImage, cv::COLOR_RGBA2BGR);

    // Unlock the bitmap pixels as they are no longer needed
    AndroidBitmap_unlockPixels(env, bitmap);

    // 3. Run the pipeline
    Pipeline *pipeline = reinterpret_cast<Pipeline *>(ctx);
    std::string result_text = pipeline->Process(bgrImage);

    // 4. Return the result as a Java string
    return CStr2Jstring(env, result_text.c_str());
}

#ifdef __cplusplus
}
#endif
