// Tencent is pleased to support the open source community by making ncnn available.
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

package com.tencent.blazefacencnn;

import android.content.res.AssetManager;
import android.graphics.Bitmap;

import java.nio.ByteBuffer;

public class BlazeFaceNcnn
{
    public native boolean loadModel(AssetManager mgr, int modelid, int cpugpu);
    public native byte[] constructNV21IfNeed(int width, int height, byte[] yData, byte[] uData, byte[] vData, int y_len, int u_len, int v_len, int y_pixelStride, int u_pixelStride, int v_pixelStride, int y_rowStride, int u_rowStride, int v_rowStride);
    public native float[] detectFaceJNI(byte[] byteBuffer, int width, int height, int winWidth, int winHeight, int rotation, int accelerometer_orientation);
    public native Bitmap cropFace(Bitmap bitmap, byte[] originImage, int width, int height, int winWidth,
                                  int winHeight, int rotation, int accelerometer_orientation, int x, int y, int cropWidth, int cropHeight);

    static {
        System.loadLibrary("blazefacencnn");
    }
}
