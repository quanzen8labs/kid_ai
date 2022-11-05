package com.tencent.blazefacencnn;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;

public class ImageHelper {
    static Bitmap compressImage(Bitmap originImage, int width, int heigth) {
        int originWidth = originImage.getWidth();
        int originHeight = originImage.getHeight();

        float sx = (float) width / originWidth;
        float sy = (float) heigth / originHeight;

        final Bitmap resultImage = Bitmap.createBitmap(width, heigth, Bitmap.Config.ARGB_8888);
        final Canvas cv = new Canvas(resultImage);
        final Matrix matrix = new Matrix();
        matrix.postScale(sx, sy);
        cv.drawBitmap(originImage, matrix, null);
        return resultImage;
    }
}
