package com.jlxc.vehicleinfoncnn;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OverlayView extends View {
    private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Detection> detections = new ArrayList<>();
    private int imageWidth = 1;
    private int imageHeight = 1;

    public OverlayView(Context context) {
        super(context);
        init();
    }

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(4f);
        boxPaint.setColor(0xff00e5ff);

        textPaint.setColor(0xffffffff);
        textPaint.setTextSize(34f);
        textPaint.setFakeBoldText(true);

        textBgPaint.setColor(0xaa000000);
        textBgPaint.setStyle(Paint.Style.FILL);
    }

    public synchronized void setDetections(List<Detection> result, int width, int height) {
        detections.clear();
        if (result != null) detections.addAll(result);
        imageWidth = Math.max(1, width);
        imageHeight = Math.max(1, height);
        postInvalidate();
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (detections.isEmpty()) return;

        float viewW = getWidth();
        float viewH = getHeight();
        float scale = Math.max(viewW / imageWidth, viewH / imageHeight);
        float dx = (viewW - imageWidth * scale) * 0.5f;
        float dy = (viewH - imageHeight * scale) * 0.5f;

        for (Detection d : detections) {
            float left = d.x * scale + dx;
            float top = d.y * scale + dy;
            float right = (d.x + d.width) * scale + dx;
            float bottom = (d.y + d.height) * scale + dy;
            RectF rect = new RectF(left, top, right, bottom);
            canvas.drawRect(rect, boxPaint);

            String text = String.format(Locale.US, "%s %.0f%%", d.label, d.confidence * 100f);
            float padding = 8f;
            float textW = textPaint.measureText(text);
            float textH = Math.abs(textPaint.ascent()) + textPaint.descent();
            float bgTop = Math.max(0, top - textH - padding * 2f);
            canvas.drawRect(left, bgTop, left + textW + padding * 2f, bgTop + textH + padding * 2f, textBgPaint);
            canvas.drawText(text, left + padding, bgTop + padding + Math.abs(textPaint.ascent()), textPaint);
        }
    }
}
