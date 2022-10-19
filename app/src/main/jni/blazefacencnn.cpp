//
// Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
//
// Licensed under the BSD 3-Clause License (the "License"); you may not use this file except
// in compliance with the License. You may obtain a copy of the License at
//
// https://opensource.org/licenses/BSD-3-Clause
//
// Unless required by applicable law or agreed to in writing, software distributed
// under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied. See the License for the
// specific language governing permissions and limitations under the License.

#include <android/asset_manager_jni.h>
#include <android/native_window_jni.h>
#include <android/native_window.h>

#include <android/log.h>

#include <jni.h>

#include <string>
#include <vector>

#include <platform.h>
#include <benchmark.h>

#include "blazeface.h"

#include "ndkcamera.h"

#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/highgui/highgui.hpp>
#include "ObserverChain.h"

#if __ARM_NEON
#include <arm_neon.h>
#endif // __ARM_NEON

static int draw_unsupported(cv::Mat& rgb)
{
    const char text[] = "unsupported";

    int baseLine = 0;
    cv::Size label_size = cv::getTextSize(text, cv::FONT_HERSHEY_SIMPLEX, 1.0, 1, &baseLine);

    int y = (rgb.rows - label_size.height) / 2;
    int x = (rgb.cols - label_size.width) / 2;

    cv::rectangle(rgb, cv::Rect(cv::Point(x, y), cv::Size(label_size.width, label_size.height + baseLine)),
                    cv::Scalar(255, 255, 255), -1);

    cv::putText(rgb, text, cv::Point(x, y + label_size.height),
                cv::FONT_HERSHEY_SIMPLEX, 1.0, cv::Scalar(0, 0, 0));

    return 0;
}

static int draw_fps(cv::Mat& rgb)
{
    // resolve moving average
    float avg_fps = 0.f;
    {
        static double t0 = 0.f;
        static float fps_history[10] = {0.f};

        double t1 = ncnn::get_current_time();
        if (t0 == 0.f)
        {
            t0 = t1;
            return 0;
        }

        float fps = 1000.f / (t1 - t0);
        t0 = t1;

        for (int i = 9; i >= 1; i--)
        {
            fps_history[i] = fps_history[i - 1];
        }
        fps_history[0] = fps;

        if (fps_history[9] == 0.f)
        {
            return 0;
        }

        for (int i = 0; i < 10; i++)
        {
            avg_fps += fps_history[i];
        }
        avg_fps /= 10.f;
    }

    char text[32];
    sprintf(text, "FPS=%.2f", avg_fps);

    int baseLine = 0;
    cv::Size label_size = cv::getTextSize(text, cv::FONT_HERSHEY_SIMPLEX, 0.5, 1, &baseLine);

    int y = 0;
    int x = rgb.cols - label_size.width;

    cv::rectangle(rgb, cv::Rect(cv::Point(x, y), cv::Size(label_size.width, label_size.height + baseLine)),
                    cv::Scalar(255, 255, 255), -1);

    cv::putText(rgb, text, cv::Point(x, y + label_size.height),
                cv::FONT_HERSHEY_SIMPLEX, 0.5, cv::Scalar(0, 0, 0));

    return 0;
}

static BlazeFace* g_blazeface = 0;
static ncnn::Mutex lock;

class MyNdkCamera : public NdkCameraWindow
{
public:
    virtual void on_image_render(cv::Mat& rgb) const;
    std::function<void(cv::Mat const &)> on_image_render_callback;
};

void MyNdkCamera::on_image_render(cv::Mat& rgb) const
{
    // scrfd
    {
        ncnn::MutexLockGuard g(lock);

        if (g_blazeface)
        {
            std::vector<FaceObject> faceobjects;
            g_blazeface->detect(rgb, faceobjects);
            g_blazeface->draw(rgb, faceobjects);
            on_image_render_callback(rgb);
        }
        else
        {
            draw_unsupported(rgb);
        }
    }

    draw_fps(rgb);
}

static MyNdkCamera* g_camera = 0;
static JavaVM *jvm = NULL;
std::vector<ObserverChain *> store_Wlistener_vector;
JNIEnv *store_env;
extern "C" {

void on_image_render_callback(cv::Mat image) {
    JNIEnv *g_env;
    if (NULL == jvm) {
        __android_log_print(ANDROID_LOG_ERROR, "GetEnv:", "  No VM  \n");
        return;
    }

    JavaVMAttachArgs args;
    args.version = JNI_VERSION_1_6; // set your JNI version
    args.name = NULL; // you might want to give the java thread a name
    args.group = NULL; // you might want to assign the java thread to a ThreadGroup

    int getEnvStat = jvm->GetEnv((void **) &g_env, JNI_VERSION_1_6);
    if (getEnvStat == JNI_EDETACHED) {
        __android_log_print(ANDROID_LOG_ERROR, "GetEnv:", " not attached\n");
        if (jvm->AttachCurrentThread(&g_env, &args) != 0) {
            __android_log_print(ANDROID_LOG_ERROR, "GetEnv:", " Failed to attach\n");
        }
    } else if (getEnvStat == JNI_OK) {
        __android_log_print(ANDROID_LOG_VERBOSE, "GetEnv:", " JNI_OK\n");
    } else if (getEnvStat == JNI_EVERSION) {
        __android_log_print(ANDROID_LOG_ERROR, "GetEnv:", " version not supported\n");
    }

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "on_image_render_callback");
    int size = image.total() * image.elemSize();
    jbyte *bytes;
    bytes = new jbyte[size];  // you will have to delete[] that later
    std::memcpy(bytes, image.data,size * sizeof(jbyte));


    if (!store_Wlistener_vector.empty()) {
        for (int i = 0; i < store_Wlistener_vector.size(); i++) {
            g_env->CallVoidMethod(store_Wlistener_vector[i]->store_Wlistener, store_Wlistener_vector[i]->store_method, bytes);
        }
    }
}

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "JNI_OnLoad");

    g_camera = new MyNdkCamera;
    g_camera->on_image_render_callback = on_image_render_callback;

    return JNI_VERSION_1_4;
}

JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved)
{
    jvm = nullptr;
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "JNI_OnUnload");

    {
        ncnn::MutexLockGuard g(lock);

        delete g_blazeface;
        g_blazeface = 0;
    }

    delete g_camera;
    g_camera = 0;
}

// public native boolean loadModel(AssetManager mgr, int modelid, int cpugpu);
JNIEXPORT jboolean JNICALL Java_com_tencent_blazefacencnn_BlazeFaceNcnn_loadModel(JNIEnv* env, jobject thiz, jobject assetManager, jint modelid, jint cpugpu)
{
    if (modelid < 0 || modelid > 6 || cpugpu < 0 || cpugpu > 1)
    {
        return JNI_FALSE;
    }

    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "loadModel %p", mgr);

    const int target_sizes[] =
    {
        128,
        320,
        416,
        640
    };

    int target_size = target_sizes[(int)modelid];
    bool use_gpu = (int)cpugpu == 1;

    // reload
    {
        ncnn::MutexLockGuard g(lock);

        if (use_gpu && ncnn::get_gpu_count() == 0)
        {
            // no gpu
            delete g_blazeface;
            g_blazeface = 0;
        }
        else
        {
            if (!g_blazeface)
                g_blazeface = new BlazeFace;
            g_blazeface->load(mgr, target_size, use_gpu);
        }
    }

    return JNI_TRUE;
}

cv::Mat convertYUV420ImageToRGBImage(unsigned char* nv21, jint nv21_width, jint nv21_height, int win_w, int win_h, jint camera_orientation, jint camera_facing, jint accelerometer_orientation) {
    // roi crop and rotate nv21
    int nv21_roi_x = 0;
    int nv21_roi_y = 0;
    int nv21_roi_w = 0;
    int nv21_roi_h = 0;
    int roi_x = 0;
    int roi_y = 0;
    int roi_w = 0;
    int roi_h = 0;
    int rotate_type = 0;
    int render_w = 0;
    int render_h = 0;
    int render_rotate_type = 0;
    {
        __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "winW %p", win_w);
        __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "winH %p", win_h);

        if (accelerometer_orientation == 90 || accelerometer_orientation == 270)
        {
            std::swap(win_w, win_h);
        }

        const int final_orientation = (camera_orientation + accelerometer_orientation) % 360;

        if (final_orientation == 0 || final_orientation == 180)
        {
            if (win_w * nv21_height > win_h * nv21_width)
            {
                roi_w = nv21_width;
                roi_h = (nv21_width * win_h / win_w) / 2 * 2;
                roi_x = 0;
                roi_y = ((nv21_height - roi_h) / 2) / 2 * 2;
            }
            else
            {
                roi_h = nv21_height;
                roi_w = (nv21_height * win_w / win_h) / 2 * 2;
                roi_x = ((nv21_width - roi_w) / 2) / 2 * 2;
                roi_y = 0;
            }

            nv21_roi_x = roi_x;
            nv21_roi_y = roi_y;
            nv21_roi_w = roi_w;
            nv21_roi_h = roi_h;
        }
        if (final_orientation == 90 || final_orientation == 270)
        {
            if (win_w * nv21_width > win_h * nv21_height)
            {
                roi_w = nv21_height;
                roi_h = (nv21_height * win_h / win_w) / 2 * 2;
                roi_x = 0;
                roi_y = ((nv21_width - roi_h) / 2) / 2 * 2;
            }
            else
            {
                roi_h = nv21_width;
                roi_w = (nv21_width * win_w / win_h) / 2 * 2;
                roi_x = ((nv21_height - roi_w) / 2) / 2 * 2;
                roi_y = 0;
            }

            nv21_roi_x = roi_y;
            nv21_roi_y = roi_x;
            nv21_roi_w = roi_h;
            nv21_roi_h = roi_w;
        }

        if (camera_facing == 0)
        {
            if (camera_orientation == 0 && accelerometer_orientation == 0)
            {
                rotate_type = 2;
            }
            if (camera_orientation == 0 && accelerometer_orientation == 90)
            {
                rotate_type = 7;
            }
            if (camera_orientation == 0 && accelerometer_orientation == 180)
            {
                rotate_type = 4;
            }
            if (camera_orientation == 0 && accelerometer_orientation == 270)
            {
                rotate_type = 5;
            }
            if (camera_orientation == 90 && accelerometer_orientation == 0)
            {
                rotate_type = 5;
            }
            if (camera_orientation == 90 && accelerometer_orientation == 90)
            {
                rotate_type = 2;
            }
            if (camera_orientation == 90 && accelerometer_orientation == 180)
            {
                rotate_type = 7;
            }
            if (camera_orientation == 90 && accelerometer_orientation == 270)
            {
                rotate_type = 4;
            }
            if (camera_orientation == 180 && accelerometer_orientation == 0)
            {
                rotate_type = 4;
            }
            if (camera_orientation == 180 && accelerometer_orientation == 90)
            {
                rotate_type = 5;
            }
            if (camera_orientation == 180 && accelerometer_orientation == 180)
            {
                rotate_type = 2;
            }
            if (camera_orientation == 180 && accelerometer_orientation == 270)
            {
                rotate_type = 7;
            }
            if (camera_orientation == 270 && accelerometer_orientation == 0)
            {
                rotate_type = 7;
            }
            if (camera_orientation == 270 && accelerometer_orientation == 90)
            {
                rotate_type = 4;
            }
            if (camera_orientation == 270 && accelerometer_orientation == 180)
            {
                rotate_type = 5;
            }
            if (camera_orientation == 270 && accelerometer_orientation == 270)
            {
                rotate_type = 2;
            }
        }
        else
        {
            if (final_orientation == 0)
            {
                rotate_type = 1;
            }
            if (final_orientation == 90)
            {
                rotate_type = 6;
            }
            if (final_orientation == 180)
            {
                rotate_type = 3;
            }
            if (final_orientation == 270)
            {
                rotate_type = 8;
            }
        }

        if (accelerometer_orientation == 0)
        {
            render_w = roi_w;
            render_h = roi_h;
            render_rotate_type = 1;
        }
        if (accelerometer_orientation == 90)
        {
            render_w = roi_h;
            render_h = roi_w;
            render_rotate_type = 8;
        }
        if (accelerometer_orientation == 180)
        {
            render_w = roi_w;
            render_h = roi_h;
            render_rotate_type = 3;
        }
        if (accelerometer_orientation == 270)
        {
            render_w = roi_h;
            render_h = roi_w;
            render_rotate_type = 6;
        }
    }

    // crop and rotate nv21
    cv::Mat nv21_croprotated(roi_h + roi_h / 2, roi_w, CV_8UC1);
    {
        const unsigned char* srcY = nv21 + nv21_roi_y * nv21_width + nv21_roi_x;
        unsigned char* dstY = nv21_croprotated.data;
        ncnn::kanna_rotate_c1(srcY, nv21_roi_w, nv21_roi_h, nv21_width, dstY, roi_w, roi_h, roi_w, rotate_type);

        const unsigned char* srcUV = nv21 + nv21_width * nv21_height + nv21_roi_y * nv21_width / 2 + nv21_roi_x;
        unsigned char* dstUV = nv21_croprotated.data + roi_w * roi_h;
        ncnn::kanna_rotate_c2(srcUV, nv21_roi_w / 2, nv21_roi_h / 2, nv21_width, dstUV, roi_w / 2, roi_h / 2, roi_w, rotate_type);
    }

    // nv21_croprotated to rgb
    cv::Mat rgb(roi_h, roi_w, CV_8UC3);
    ncnn::yuv420sp2rgb(nv21_croprotated.data, roi_w, roi_h, rgb.data);

    return rgb;
}

extern "C" JNIEXPORT jbyteArray JNICALL Java_com_tencent_blazefacencnn_BlazeFaceNcnn_constructNV21IfNeed(JNIEnv *env,
                                                                    jobject instance,
                                                                  jint width,
                                                                  jint height,
                                                                  jbyteArray yData,
                                                                  jbyteArray uData,
                                                                  jbyteArray vData,
                                                                  jint y_len,
                                                                  jint u_len,
                                                                  jint v_len,
                                                                  jint y_pixelStride,
                                                                  jint u_pixelStride,
                                                                  jint v_pixelStride,
                                                                  jint y_rowStride,
                                                                  jint u_rowStride,
                                                                  jint v_rowStride)
{
    jbyte *y_data = env->GetByteArrayElements(yData, 0);
    jbyte *u_data = env->GetByteArrayElements(uData, 0);
    jbyte *v_data = env->GetByteArrayElements(vData, 0);

    if (u_data == v_data + 1 && v_data == y_data + width * height && y_pixelStride == 1 && u_pixelStride == 2 && v_pixelStride == 2 && y_rowStride == width && u_rowStride == width && v_rowStride == width)
    {
        // already nv21  :)
        jbyteArray result = env -> NewByteArray(sizeof(y_data));
        env->SetByteArrayRegion(result, 0, sizeof(y_data), y_data);
        return result;
    }
    else
    {
        // construct nv21
        unsigned char* nv21 = new unsigned char[width * height + width * height / 2];
        {
            // Y
            unsigned char* yptr = nv21;
            for (int y=0; y<height; y++)
            {
                const signed char* y_data_ptr = y_data + y_rowStride * y;
                for (int x=0; x<width; x++)
                {
                    yptr[0] = y_data_ptr[0];
                    yptr++;
                    y_data_ptr += y_pixelStride;
                }
            }

            // UV
            unsigned char* uvptr = nv21 + width * height;
            for (int y=0; y<height/2; y++)
            {
                const signed char* v_data_ptr = v_data + v_rowStride * y;
                const signed char* u_data_ptr = u_data + u_rowStride * y;
                for (int x=0; x<width/2; x++)
                {
                    uvptr[0] = v_data_ptr[0];
                    uvptr[1] = u_data_ptr[0];
                    uvptr += 2;
                    v_data_ptr += v_pixelStride;
                    u_data_ptr += u_pixelStride;
                }
            }
        }

        jbyteArray result = env -> NewByteArray(width * height + width * height / 2);
        env->SetByteArrayRegion(result, 0, width * height + width * height / 2, (jbyte *)(nv21));
        return result;

        delete[] nv21;
        env->ReleaseByteArrayElements(yData, y_data, 0);
        env->ReleaseByteArrayElements(uData, u_data, 0);
        env->ReleaseByteArrayElements(vData, v_data, 0);
    }
}

extern "C" JNIEXPORT jfloatArray JNICALL Java_com_tencent_blazefacencnn_BlazeFaceNcnn_detectFaceJNI(JNIEnv *env,
                                                                                                    jobject instance,
                                                                                                    jbyteArray byteBuffer,
                                                                                                    jint width,
                                                                                                    jint height,
                                                                                                    jint winWidth,
                                                                                                    jint winHeight,
                                                                                                    jint rotation,
                                                                                                    jint accelerometer_orientation)
{

    if (g_blazeface)
    {
        jbyte *yuv = env->GetByteArrayElements(byteBuffer, 0);
        cv::Mat rgb = convertYUV420ImageToRGBImage((unsigned char*)yuv, width, height, winWidth, winHeight, rotation, 0, accelerometer_orientation);
        env->ReleaseByteArrayElements(byteBuffer, yuv, 0);

        std::vector<FaceObject> faceobjects;
        g_blazeface->detect(rgb, faceobjects);
        int faceCount = faceobjects.size();

        int properties = 5;
        float floatResult[faceCount * properties];
        for (int i = 0; i < faceCount; i++) {
            FaceObject faceObject = faceobjects[i];
            floatResult[properties * i] = faceObject.rect.x;
            floatResult[properties * i + 1] = faceObject.rect.y;
            floatResult[properties * i + 2] = faceObject.rect.width;
            floatResult[properties * i + 3] = faceObject.rect.height;
            floatResult[properties * i + 4] = faceObject.prob;
        }
        jfloatArray result = env -> NewFloatArray(faceCount * properties);
        env->SetFloatArrayRegion(result, 0, faceCount * properties, floatResult);
        return result;
    }
    else
    {
        return NULL;
    }
}

void MatToBitmap2(JNIEnv *env, cv::Mat& mat, jobject& bitmap, jboolean needPremultiplyAlpha) {
    AndroidBitmapInfo info;
    void *pixels = 0;
    cv::Mat &src = mat;

    // Creaing Bitmap Config Class
    jclass bmpCfgCls = env->FindClass("android/graphics/Bitmap$Config");
    jmethodID bmpClsValueOfMid = env->GetStaticMethodID(bmpCfgCls, "valueOf",
                                                        "(Ljava/lang/String;)Landroid/graphics/Bitmap$Config;");
    jobject jBmpCfg = env->CallStaticObjectMethod(bmpCfgCls, bmpClsValueOfMid,
                                                  env->NewStringUTF("ARGB_8888"));

    // Creating a Bitmap Class
    jclass bmpCls = env->FindClass("android/graphics/Bitmap");
    jmethodID createBitmapMid = env->GetStaticMethodID(bmpCls, "createBitmap",
                                                       "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    bitmap = env->CallStaticObjectMethod(bmpCls, createBitmapMid, src.cols, src.rows, jBmpCfg);

    CV_Assert(AndroidBitmap_getInfo(env, bitmap, &info) >= 0);
    CV_Assert(info.format == ANDROID_BITMAP_FORMAT_RGBA_8888 ||
              info.format == ANDROID_BITMAP_FORMAT_RGB_565);
    CV_Assert(src.dims == 2 && info.height == (uint32_t) src.rows && info.width == (uint32_t) src.cols);
    CV_Assert(src.type() == CV_8UC1 || src.type() == CV_8UC3 || src.type() == CV_8UC4);
    CV_Assert(AndroidBitmap_lockPixels(env, bitmap, &pixels) >= 0);
    CV_Assert(pixels);

    if (info.format == ANDROID_BITMAP_FORMAT_RGBA_8888) {
        cv::Mat tmp(info.height, info.width, CV_8UC4, pixels);
        if (src.type() == CV_8UC1) {
            //LOGD("nMatToBitmap: CV_8UC1 -> RGBA_8888");
            cv::cvtColor(src, tmp, cv::COLOR_GRAY2RGBA);
        }
        else if (src.type() == CV_8UC3) {
            //LOGD("nMatToBitmap: CV_8UC3 -> RGBA_8888");
            cv::cvtColor(src, tmp, cv::COLOR_RGB2RGBA);
        }
        else if (src.type() == CV_8UC4) {
            //LOGD("nMatToBitmap: CV_8UC4 -> RGBA_8888");
            if (needPremultiplyAlpha) {
                cv::cvtColor(src, tmp, cv::COLOR_RGBA2mRGBA);
            }
            else {
                src.copyTo(tmp);
            }
        }
    }
    else {
        // info.format == ANDROID_BITMAP_FORMAT_RGB_565
        cv::Mat tmp(info.height, info.width, CV_8UC2, pixels);
        if (src.type() == CV_8UC1) {
            //LOGD("nMatToBitmap: CV_8UC1 -> RGB_565");
            cv::cvtColor(src, tmp, cv::COLOR_GRAY2BGR565);
        }
        else if (src.type() == CV_8UC3) {
            //LOGD("nMatToBitmap: CV_8UC3 -> RGB_565");
            cv::cvtColor(src, tmp, cv::COLOR_RGB2BGR565);
        }
        else if (src.type() == CV_8UC4) {
            //LOGD("nMatToBitmap: CV_8UC4 -> RGB_565");
            cv::cvtColor(src, tmp, cv::COLOR_RGBA2BGR565);
        }
    }
    AndroidBitmap_unlockPixels(env, bitmap);
}

void MatToBitmap(JNIEnv *env, cv::Mat& mat, jobject& bitmap) {
    MatToBitmap2(env, mat, bitmap, false);
}

extern "C" JNIEXPORT jobject JNICALL Java_com_tencent_blazefacencnn_BlazeFaceNcnn_cropFace(JNIEnv *env,
                                                                                                    jobject instance,
                                                                                                    jobject bitmap,
                                                                                                    jbyteArray byteBuffer,
                                                                                                    jint width,
                                                                                                    jint height,
                                                                                                    jint winWidth,
                                                                                                    jint winHeight,
                                                                                                    jint rotation,
                                                                                                    jint accelerometer_orientation,
                                                                                                    jint x,
                                                                                                    jint y,
                                                                                                    jint cropWidth,
                                                                                                    jint cropHeight)
{
    if (g_blazeface)
    {
        jbyte *yuv = env->GetByteArrayElements(byteBuffer, 0);
        cv::Mat rgb = convertYUV420ImageToRGBImage((unsigned char*)yuv, width, height, winWidth, winHeight, rotation, 0, accelerometer_orientation);
        env->ReleaseByteArrayElements(byteBuffer, yuv, 0);
        jobject mybitmap;
        jint _x = MAX(x, 0);
        jint _y = MAX(y, 0);
        jint _cropWidth = MIN(cropWidth, width);
        jint _cropHeight = MIN(cropHeight, height);
        cv::Rect myROI(_x, _y, _cropWidth, _cropHeight);
        cv::Mat cropImage = rgb(myROI);
        MatToBitmap(env, cropImage, mybitmap);
        return mybitmap;
    }
    else
    {
        return NULL;
    }
}

/**
*   Fills JNI details.
*/

// public native boolean setOutputWindow(Surface surface);
JNIEXPORT jboolean JNICALL Java_com_tencent_blazefacencnn_BlazeFaceNcnn_setOutputWindow(JNIEnv* env, jobject thiz, jobject surface)
{
    ANativeWindow* win = ANativeWindow_fromSurface(env, surface);

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "setOutputWindow %p", win);

    g_camera->set_window(win);

    return JNI_TRUE;
}
}
