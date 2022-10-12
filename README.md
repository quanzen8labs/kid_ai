# ncnn_Android_blazeface

This repo is fork from [origin](https://github.com/FeiGeChuanShu/ncnn_Android_blazeface) with some configurations.
The BlazeFace face detection demo infer by ncnn.  
This is a sample ncnn android project, it depends on ncnn library and opencv

https://github.com/Tencent/ncnn
https://github.com/nihui/opencv-mobile
## How to build and run
### step 1
https://github.com/Tencent/ncnn/releases

* Download [ncnn-20210720-android-vulkan.zip](https://github.com/Tencent/ncnn/releases/download/20210720/ncnn-20210720-android-vulkan.zip) then extract to folder **ncnn-20210720-android-vulkan**
* Copy folder **ncnn-20210720-android-vulkan** into **app/src/main/jni**

### step 2
https://github.com/nihui/opencv-mobile

* Download [opencv-mobile-4.5.4-android.zip](https://github.com/nihui/opencv-mobile/releases/download/v14/opencv-mobile-4.5.4-android.zip]) then extract to folder **opencv-mobile-4.5.4-android**
* Copy folder **opencv-mobile-4.5.4-android** into **app/src/main/jni**

### step 3
* Open this project with Android Studio, build and run.

## REFERENCE
1.https://github.com/PaddlePaddle/PaddleDetection/blob/release/2.3/configs/face_detection/README.md  
2.https://github.com/nihui/ncnn-android-scrfd  
3.https://github.com/deepcam-cn/yolov5-face  
4.https://github.com/google/mediapipe
