package com.tencent.blazefacencnn;

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

    DetectFace(float x, float y, float width, float height, float probs, byte[] byteBuffer) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.probs = probs;
        this.byteBuffer = byteBuffer;
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
     private final byte[] mByteBuffer;
     private final int mWidth;
     private final int mHeight;
     private final int mOrientation;

     MyFaceDetectorCallback c;

     /**
      * The file we save the image into.
      */
     private final BlazeFaceNcnn mBlazeFaceNcnn;

     MyFaceDetector(byte[] byteBuffer, int width, int height, int orientation, BlazeFaceNcnn blazeFaceNcnn, MyFaceDetectorCallback c) {
         mByteBuffer = byteBuffer;
         mWidth = width;
         mHeight = height;
         mOrientation = orientation;
         mBlazeFaceNcnn = blazeFaceNcnn;
         this.c = c;
     }

     @Override
     public void run() {
         float[] result = mBlazeFaceNcnn.detectFaceJNI(mByteBuffer, mWidth, mHeight, mOrientation);
         int i = 0;
         int resultLength = result.length;

         ArrayList<DetectFace> detectFaces = new ArrayList<>();
         while (i < resultLength) {
             float x = result[i];
             float y = result[i+1];
             float width = result[i+2];
             float height = result[i+3];
             float probs = result[i+4];

             detectFaces.add(new DetectFace(x, y, width, height, probs, mByteBuffer));

             i += 5;
         }

         c.onFaceDetect(detectFaces);
     }
 }