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
    private final Paint cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPixelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint reticlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Detection> detections = new ArrayList<>();
    private int imageWidth = 1;
    private int imageHeight = 1;
    private boolean showLabels = true;
    private boolean showCenterReticle = true;

    public OverlayView(Context context) {
        super(context);
        init();
    }

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        float d = getResources().getDisplayMetrics().density;

        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(1.2f * d);
        boxPaint.setColor(0x8800e5ff);

        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setStrokeWidth(3.4f * d);
        cornerPaint.setStrokeCap(Paint.Cap.SQUARE);
        cornerPaint.setColor(0xffffd54f);

        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(7.5f * d);
        glowPaint.setColor(0x3300e5ff);

        fillPaint.setColor(0x1200e5ff);
        fillPaint.setStyle(Paint.Style.FILL);

        textPixelPaint.setColor(0xffffffff);
        textPixelPaint.setStyle(Paint.Style.FILL);

        textBgPaint.setColor(0xcc02070a);
        textBgPaint.setStyle(Paint.Style.FILL);

        barPaint.setColor(0xff00e5ff);
        barPaint.setStyle(Paint.Style.FILL);

        reticlePaint.setColor(0xaa00e5ff);
        reticlePaint.setStrokeWidth(1.2f * d);
        reticlePaint.setStyle(Paint.Style.STROKE);
    }

    public synchronized void setRenderOptions(boolean labels, boolean centerReticle) {
        showLabels = labels;
        showCenterReticle = centerReticle;
        postInvalidate();
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
        float d = getResources().getDisplayMetrics().density;

        for (Detection det : detections) {
            float left = det.x * scale + dx;
            float top = det.y * scale + dy;
            float right = (det.x + det.width) * scale + dx;
            float bottom = (det.y + det.height) * scale + dy;
            RectF rect = new RectF(left, top, right, bottom);

            float w = Math.max(1f, rect.width());
            float h = Math.max(1f, rect.height());
            float corner = Math.max(18f * d, Math.min(Math.min(w, h) * 0.28f, 52f * d));

            canvas.drawRect(rect, fillPaint);
            canvas.drawRect(rect, glowPaint);
            canvas.drawRect(rect, boxPaint);
            drawCorners(canvas, rect, corner);
            drawTargetTicks(canvas, rect, d);
            if (showCenterReticle) drawReticle(canvas, rect, d);
            if (showLabels) drawLabel(canvas, det, rect, d);
        }
    }

    private void drawCorners(Canvas canvas, RectF r, float len) {
        canvas.drawLine(r.left, r.top, r.left + len, r.top, cornerPaint);
        canvas.drawLine(r.left, r.top, r.left, r.top + len, cornerPaint);
        canvas.drawLine(r.right, r.top, r.right - len, r.top, cornerPaint);
        canvas.drawLine(r.right, r.top, r.right, r.top + len, cornerPaint);
        canvas.drawLine(r.left, r.bottom, r.left + len, r.bottom, cornerPaint);
        canvas.drawLine(r.left, r.bottom, r.left, r.bottom - len, cornerPaint);
        canvas.drawLine(r.right, r.bottom, r.right - len, r.bottom, cornerPaint);
        canvas.drawLine(r.right, r.bottom, r.right, r.bottom - len, cornerPaint);
    }

    private void drawTargetTicks(Canvas canvas, RectF r, float d) {
        float midX = r.centerX();
        float midY = r.centerY();
        float tick = 10f * d;
        canvas.drawLine(midX - tick, r.top - tick * 0.4f, midX + tick, r.top - tick * 0.4f, reticlePaint);
        canvas.drawLine(midX - tick, r.bottom + tick * 0.4f, midX + tick, r.bottom + tick * 0.4f, reticlePaint);
        canvas.drawLine(r.left - tick * 0.4f, midY - tick, r.left - tick * 0.4f, midY + tick, reticlePaint);
        canvas.drawLine(r.right + tick * 0.4f, midY - tick, r.right + tick * 0.4f, midY + tick, reticlePaint);
    }

    private void drawReticle(Canvas canvas, RectF r, float d) {
        float cx = r.centerX();
        float cy = r.centerY();
        float s = Math.max(8f * d, Math.min(r.width(), r.height()) * 0.08f);
        canvas.drawCircle(cx, cy, s, reticlePaint);
        canvas.drawLine(cx - s * 1.8f, cy, cx - s * 0.55f, cy, reticlePaint);
        canvas.drawLine(cx + s * 0.55f, cy, cx + s * 1.8f, cy, reticlePaint);
        canvas.drawLine(cx, cy - s * 1.8f, cx, cy - s * 0.55f, reticlePaint);
        canvas.drawLine(cx, cy + s * 0.55f, cx, cy + s * 1.8f, reticlePaint);
    }

    private void drawLabel(Canvas canvas, Detection det, RectF r, float d) {
        String text = String.format(Locale.US, "LOCK // %s %.0f%%", det.label, det.confidence * 100f).toUpperCase(Locale.US);
        float cell = Math.max(3.0f, 3.8f * d);
        float paddingX = 8f * d;
        float paddingY = 5f * d;
        float textW = measureTacticalText(text, cell);
        float textH = 7f * cell;
        float labelH = textH + paddingY * 2f;
        float labelW = Math.min(getWidth() - 8f * d, textW + paddingX * 2f + 42f * d);
        float x = Math.max(4f * d, Math.min(r.left, getWidth() - labelW - 4f * d));
        float y = Math.max(4f * d, r.top - labelH - 5f * d);
        if (y < 6f * d) y = Math.min(getHeight() - labelH - 4f * d, r.top + 5f * d);

        RectF bg = new RectF(x, y, x + labelW, y + labelH);
        canvas.drawRect(bg, textBgPaint);
        canvas.drawLine(bg.left, bg.top, bg.right, bg.top, cornerPaint);
        canvas.drawLine(bg.left, bg.bottom, bg.left + 32f * d, bg.bottom, barPaint);

        float barW = Math.max(8f * d, (labelW - 10f * d) * Math.max(0f, Math.min(1f, det.confidence)));
        canvas.drawRect(bg.left + 5f * d, bg.bottom - 4f * d, bg.left + barW, bg.bottom - 1f * d, barPaint);
        drawTacticalText(canvas, text, bg.left + paddingX, bg.top + paddingY, cell);
    }

    private float measureTacticalText(String text, float cell) {
        float x = 0f;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') x += 3.5f * cell;
            else x += 6.2f * cell;
        }
        return x;
    }

    private void drawTacticalText(Canvas canvas, String text, float x, float y, float cell) {
        float cursor = x;
        float gap = Math.max(0.8f, cell * 0.16f);
        for (int i = 0; i < text.length(); i++) {
            char c = Character.toUpperCase(text.charAt(i));
            String[] pattern = patternFor(c);
            if (c == ' ') {
                cursor += 3.5f * cell;
                continue;
            }
            if (pattern != null) {
                for (int row = 0; row < pattern.length; row++) {
                    String line = pattern[row];
                    for (int col = 0; col < line.length(); col++) {
                        if (line.charAt(col) == '1') {
                            float left = cursor + col * cell + gap;
                            float top = y + row * cell + gap;
                            canvas.drawRect(left, top, left + cell - gap, top + cell - gap, textPixelPaint);
                        }
                    }
                }
            }
            cursor += 6.2f * cell;
            if (cursor > getWidth() - 6f * cell) break;
        }
    }

    private String[] patternFor(char c) {
        switch (c) {
            case 'A': return new String[]{"01110","10001","10001","11111","10001","10001","10001"};
            case 'B': return new String[]{"11110","10001","10001","11110","10001","10001","11110"};
            case 'C': return new String[]{"01111","10000","10000","10000","10000","10000","01111"};
            case 'D': return new String[]{"11110","10001","10001","10001","10001","10001","11110"};
            case 'E': return new String[]{"11111","10000","10000","11110","10000","10000","11111"};
            case 'F': return new String[]{"11111","10000","10000","11110","10000","10000","10000"};
            case 'G': return new String[]{"01111","10000","10000","10111","10001","10001","01111"};
            case 'H': return new String[]{"10001","10001","10001","11111","10001","10001","10001"};
            case 'I': return new String[]{"11111","00100","00100","00100","00100","00100","11111"};
            case 'J': return new String[]{"00111","00010","00010","00010","10010","10010","01100"};
            case 'K': return new String[]{"10001","10010","10100","11000","10100","10010","10001"};
            case 'L': return new String[]{"10000","10000","10000","10000","10000","10000","11111"};
            case 'M': return new String[]{"10001","11011","10101","10101","10001","10001","10001"};
            case 'N': return new String[]{"10001","11001","10101","10011","10001","10001","10001"};
            case 'O': return new String[]{"01110","10001","10001","10001","10001","10001","01110"};
            case 'P': return new String[]{"11110","10001","10001","11110","10000","10000","10000"};
            case 'Q': return new String[]{"01110","10001","10001","10001","10101","10010","01101"};
            case 'R': return new String[]{"11110","10001","10001","11110","10100","10010","10001"};
            case 'S': return new String[]{"01111","10000","10000","01110","00001","00001","11110"};
            case 'T': return new String[]{"11111","00100","00100","00100","00100","00100","00100"};
            case 'U': return new String[]{"10001","10001","10001","10001","10001","10001","01110"};
            case 'V': return new String[]{"10001","10001","10001","10001","10001","01010","00100"};
            case 'W': return new String[]{"10001","10001","10001","10101","10101","10101","01010"};
            case 'X': return new String[]{"10001","10001","01010","00100","01010","10001","10001"};
            case 'Y': return new String[]{"10001","10001","01010","00100","00100","00100","00100"};
            case 'Z': return new String[]{"11111","00001","00010","00100","01000","10000","11111"};
            case '0': return new String[]{"01110","10001","10011","10101","11001","10001","01110"};
            case '1': return new String[]{"00100","01100","00100","00100","00100","00100","01110"};
            case '2': return new String[]{"01110","10001","00001","00010","00100","01000","11111"};
            case '3': return new String[]{"11110","00001","00001","01110","00001","00001","11110"};
            case '4': return new String[]{"10010","10010","10010","11111","00010","00010","00010"};
            case '5': return new String[]{"11111","10000","10000","11110","00001","00001","11110"};
            case '6': return new String[]{"01110","10000","10000","11110","10001","10001","01110"};
            case '7': return new String[]{"11111","00001","00010","00100","01000","01000","01000"};
            case '8': return new String[]{"01110","10001","10001","01110","10001","10001","01110"};
            case '9': return new String[]{"01110","10001","10001","01111","00001","00001","01110"};
            case '-': return new String[]{"00000","00000","00000","11111","00000","00000","00000"};
            case '_': return new String[]{"00000","00000","00000","00000","00000","00000","11111"};
            case '.': return new String[]{"00000","00000","00000","00000","00000","01100","01100"};
            case ':': return new String[]{"00000","01100","01100","00000","01100","01100","00000"};
            case '%': return new String[]{"11001","11010","00100","01000","10110","00110","00000"};
            case '/': return new String[]{"00001","00010","00010","00100","01000","01000","10000"};
            case '+': return new String[]{"00000","00100","00100","11111","00100","00100","00000"};
            default: return new String[]{"11111","00001","00010","00100","00000","00100","00000"};
        }
    }
}
