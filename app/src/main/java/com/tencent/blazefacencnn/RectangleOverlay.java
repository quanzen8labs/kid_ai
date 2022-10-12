package com.tencent.blazefacencnn;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;

public class RectangleOverlay extends View {
    private Rect rect = new Rect(400, 200, 800, 600);

    public RectangleOverlay(Context context) {
        super(context);
    }

    public RectangleOverlay(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public RectangleOverlay(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setRect(Rect rect) {
        this.rect = rect;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(rect.left,rect.top,rect.right,rect.bottom,paint);
    }
}
