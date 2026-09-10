package com.screenlock.floatbutton;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class ColorWheelView extends View {
    private final Paint paint;
    private final Paint indicatorPaint;
    private final Paint innerPaint;
    private final Paint borderPaint;
    private int centerX, centerY;
    private int radius;
    private OnColorSelectedListener listener;
    private int selectedColor = Color.CYAN;
    private float indicatorX, indicatorY;

    public interface OnColorSelectedListener {
        void onColorSelected(int color);
    }

    public ColorWheelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        
        indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        indicatorPaint.setStyle(Paint.Style.FILL);
        indicatorPaint.setColor(Color.WHITE);
        indicatorPaint.setShadowLayer(8, 0, 0, 0x80000000);

        innerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(4);
        borderPaint.setColor(0x33FFFFFF);
        
        setLayerType(LAYER_TYPE_SOFTWARE, null); // For shadow layer
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        centerX = w / 2;
        centerY = h / 2;
        radius = Math.min(centerX, centerY) - 30;

        int[] colors = {
            0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 
            0xFF0000FF, 0xFFFF00FF, 0xFFFF0000
        };
        Shader sweep = new SweepGradient(centerX, centerY, colors, null);
        Shader radial = new RadialGradient(centerX, centerY, radius, 0xFFFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
        paint.setShader(new ComposeShader(sweep, radial, PorterDuff.Mode.SRC_OVER));
        
        updateIndicatorPosition();
    }

    private void updateIndicatorPosition() {
        float[] hsv = new float[3];
        Color.colorToHSV(selectedColor, hsv);
        double angle = Math.toRadians(hsv[0]);
        float dist = hsv[1] * radius;
        
        indicatorX = (float) (centerX + dist * Math.cos(angle));
        indicatorY = (float) (centerY + dist * Math.sin(angle));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawCircle(centerX, centerY, radius, paint);
        canvas.drawCircle(centerX, centerY, radius, borderPaint);
        
        // Draw selection indicator
        canvas.drawCircle(indicatorX, indicatorY, 15, indicatorPaint);
        innerPaint.setColor(selectedColor);
        canvas.drawCircle(indicatorX, indicatorY, 10, innerPaint);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            performClick();
        }

        float x = event.getX() - centerX;
        float y = event.getY() - centerY;
        float d = (float) Math.sqrt(x * x + y * y);

        if (d <= radius || event.getAction() == MotionEvent.ACTION_MOVE) {
            float finalX = x;
            float finalY = y;
            if (d > radius) {
                finalX = x * radius / d;
                finalY = y * radius / d;
            }

            float angle = (float) Math.toDegrees(Math.atan2(finalY, finalX));
            if (angle < 0) angle += 360f;
            
            float saturation = Math.min(d, radius) / radius;
            float[] hsv = {angle, saturation, 1f};
            selectedColor = Color.HSVToColor(hsv);
            
            indicatorX = centerX + finalX;
            indicatorY = centerY + finalY;

            if (listener != null) {
                listener.onColorSelected(selectedColor);
            }
            invalidate();
            return true;
        }
        return super.onTouchEvent(event);
    }

    public void setOnColorSelectedListener(OnColorSelectedListener l) {
        this.listener = l;
    }

    public void setSelectedColor(int color) {
        if (this.selectedColor != color) {
            this.selectedColor = color;
            updateIndicatorPosition();
            invalidate();
        }
    }
}
