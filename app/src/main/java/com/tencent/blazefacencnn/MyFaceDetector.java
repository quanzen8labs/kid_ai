package com.tencent.blazefacencnn;

import android.content.res.Resources;
import android.media.FaceDetector;
import android.media.Image;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.ArrayList;

class DetectFace {
    float x;
    float y;
    float width;
    float height;
    float probs;
    byte[] byteBuffer;
    final int orientation;
    final int accelerometerOrientation;
    final int winWidth;
    final int winHeight;

    DetectFace(float x, float y, float width, float height, float probs, byte[] byteBuffer, int orientation, int accelerometerOrientation, int winWidth, int winHeight) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.probs = probs;
        this.byteBuffer = byteBuffer;
        this.orientation = orientation;
        this.accelerometerOrientation = accelerometerOrientation;
        this.winWidth = winWidth;
        this.winHeight = winHeight;
    }
}

interface MyFaceDetectorCallback {
    void onFaceDetect(ArrayList<DetectFace> detectFaces); // would be in any signature
}

 class MyFaceDetector implements Runnable {
     private static String TAG = "MyFaceDetector";
     /**
      * The JPEG image
      */
     private final byte[] yData;
     private final byte[] uData;
     private final byte[] vData;
     private final int yLen;
     private final int uLen;
     private final int vLen;
     private final int yPixelStride;
     private final int uPixelStride;
     private final int vPixelStride;
     private final int yRowStride;
     private final int uRowStride;
     private final int vRowStride;
     private final int mWidth;
     private final int mHeight;
     private final int mOrientation;
     private final int mAccelerometerOrientation;
     private final int mWinWidth;
     private final int mWinHeight;

     MyFaceDetectorCallback c;

     /**
      * The file we save the image into.
      */
     private final BlazeFaceNcnn mBlazeFaceNcnn;

     MyFaceDetector(byte[] yData, byte[] uData, byte[] vData,
                    int yLen, int uLen, int vLen,
                    int yPixelStride, int uPixelStride, int vPixelStride,
                    int yRowStride, int uRowStride, int vRowStride,
                    int width, int height, int orientation,
                    BlazeFaceNcnn blazeFaceNcnn, int accelerometerOrientation, MyFaceDetectorCallback c,
                    int winWidth,
                    int winHeight) {
         this.yData = yData;
         this.uData = uData;
         this.vData = vData;
         this.yLen = yLen;
         this.uLen = uLen;
         this.vLen = vLen;
         this.yPixelStride = yPixelStride;
         this.uPixelStride = uPixelStride;
         this.vPixelStride = vPixelStride;
         this.yRowStride = yRowStride;
         this.uRowStride = uRowStride;
         this.vRowStride = vRowStride;
         mWidth = width;
         mHeight = height;
         mOrientation = orientation;
         mBlazeFaceNcnn = blazeFaceNcnn;
         mAccelerometerOrientation = accelerometerOrientation;
         this.c = c;
         mWinWidth = winWidth;
         mWinHeight = winHeight;
     }

     @Override
     public void run() {
         byte[] nv21 = mBlazeFaceNcnn.constructNV21IfNeed(mWidth, mHeight, yData, uData, vData, yLen, uLen, vLen, yPixelStride, uPixelStride, vPixelStride, yRowStride, uRowStride, vRowStride);
         float[] result = mBlazeFaceNcnn.detectFaceJNI(nv21, mWidth, mHeight, mWinWidth, mWinHeight, mOrientation, mAccelerometerOrientation);
         int i = 0;
         int resultLength = result.length;

         ArrayList<DetectFace> detectFaces = new ArrayList<>();
         while (i < resultLength) {
             float x = result[i];
             float y = result[i+1];
             float width = result[i+2];
             float height = result[i+3];
             float probs = result[i+4];

             detectFaces.add(new DetectFace(x, y, width, height, probs, nv21, mOrientation, mAccelerometerOrientation, mWinWidth, mWinHeight));

             i += 5;
         }

         c.onFaceDetect(detectFaces);
     }
 }