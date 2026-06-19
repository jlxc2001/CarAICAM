package com.jlxc.vehicleinfoncnn;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OverlayView extends View {
    private static final int HUD_GREEN = 0xff39ff14;
    private static final int HUD_GREEN_SOFT = 0xffa8ff9c;
    private static final int HUD_GREEN_DIM = 0x6639ff14;

    private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint panelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scanPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint smallTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint monoPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Detection> detections = new ArrayList<>();

    private Typeface hudTypeface;
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
        try {
            hudTypeface = Typeface.createFromAsset(getContext().getAssets(), "fonts/jlxc_hud_vector.ttf");
        } catch (Throwable t) {
            hudTypeface = Typeface.create("monospace", Typeface.BOLD);
        }

        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setStrokeWidth(1.05f * d);
        framePaint.setColor(0x9939ff14);
        framePaint.setStrokeCap(Paint.Cap.SQUARE);

        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setStrokeWidth(2.25f * d);
        cornerPaint.setStrokeCap(Paint.Cap.SQUARE);
        cornerPaint.setColor(HUD_GREEN);

        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(6.0f * d);
        glowPaint.setColor(0x3039ff14);
        glowPaint.setStrokeCap(Paint.Cap.SQUARE);

        fillPaint.setColor(0x0700ff33);
        fillPaint.setStyle(Paint.Style.FILL);

        panelPaint.setColor(0xb8000800);
        panelPaint.setStyle(Paint.Style.FILL);

        gridPaint.setColor(0x1d39ff14);
        gridPaint.setStrokeWidth(0.65f * d);
        gridPaint.setStyle(Paint.Style.STROKE);

        scanPaint.setColor(0x1639ff14);
        scanPaint.setStrokeWidth(0.7f * d);
        scanPaint.setStyle(Paint.Style.STROKE);

        textPaint.setColor(HUD_GREEN_SOFT);
        textPaint.setTypeface(hudTypeface);
        textPaint.setLetterSpacing(0.08f);
        textPaint.setTextSize(12f * d);
        textPaint.setSubpixelText(false);

        smallTextPaint.setColor(HUD_GREEN);
        smallTextPaint.setTypeface(hudTypeface);
        smallTextPaint.setLetterSpacing(0.06f);
        smallTextPaint.setTextSize(9.5f * d);
        smallTextPaint.setSubpixelText(false);

        monoPaint.setColor(0xdd39ff14);
        monoPaint.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        monoPaint.setTextSize(10f * d);
        monoPaint.setLetterSpacing(0.04f);
        monoPaint.setSubpixelText(true);
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
        float viewW = getWidth();
        float viewH = getHeight();
        float d = getResources().getDisplayMetrics().density;
        long now = SystemClock.uptimeMillis();
        float pulse = 0.55f + 0.45f * (float) Math.sin(now / 185.0);

        drawGlobalHud(canvas, viewW, viewH, d, pulse);

        if (!detections.isEmpty()) {
            float scale = Math.max(viewW / imageWidth, viewH / imageHeight);
            float dx = (viewW - imageWidth * scale) * 0.5f;
            float dy = (viewH - imageHeight * scale) * 0.5f;

            for (int i = 0; i < detections.size(); i++) {
                Detection det = detections.get(i);
                float left = det.x * scale + dx;
                float top = det.y * scale + dy;
                float right = (det.x + det.width) * scale + dx;
                float bottom = (det.y + det.height) * scale + dy;
                RectF rect = new RectF(left, top, right, bottom);
                drawTarget(canvas, rect, det, i + 1, d, pulse);
            }
        }

        // Keep the avionics HUD alive, but avoid wasting too much CPU on old phones.
        postInvalidateDelayed(85);
    }

    private void drawGlobalHud(Canvas canvas, float w, float h, float d, float pulse) {
        // Fine scan lines: deliberately weak, just enough to remove the phone-app feeling.
        float scanStep = 12f * d;
        for (float y = 0; y < h; y += scanStep) {
            canvas.drawLine(0, y, w, y, scanPaint);
        }

        float gridStep = 88f * d;
        for (float x = gridStep; x < w; x += gridStep) {
            canvas.drawLine(x, 0, x, h, gridPaint);
        }
        for (float y = gridStep; y < h; y += gridStep) {
            canvas.drawLine(0, y, w, y, gridPaint);
        }

        float margin = 13f * d;
        float arm = Math.min(96f * d, Math.min(w, h) * 0.13f);
        framePaint.setAlpha(130);
        canvas.drawLine(margin, margin, margin + arm, margin, framePaint);
        canvas.drawLine(margin, margin, margin, margin + arm, framePaint);
        canvas.drawLine(w - margin, margin, w - margin - arm, margin, framePaint);
        canvas.drawLine(w - margin, margin, w - margin, margin + arm, framePaint);
        canvas.drawLine(margin, h - margin, margin + arm, h - margin, framePaint);
        canvas.drawLine(margin, h - margin, margin, h - margin - arm, framePaint);
        canvas.drawLine(w - margin, h - margin, w - margin - arm, h - margin, framePaint);
        canvas.drawLine(w - margin, h - margin, w - margin, h - margin - arm, framePaint);

        smallTextPaint.setAlpha(185);
        canvas.drawText("FCS ONLINE // NCNN VISION // HUD-G", margin + 8f * d, margin + 18f * d, smallTextPaint);
        String right = String.format(Locale.US, "TGT:%02d // FRAME:%dx%d", detections.size(), imageWidth, imageHeight);
        float rw = smallTextPaint.measureText(right);
        canvas.drawText(right, Math.max(margin, w - margin - rw - 8f * d), margin + 18f * d, smallTextPaint);

        drawSideScale(canvas, margin + 5f * d, h * 0.22f, h * 0.78f, d, true);
        drawSideScale(canvas, w - margin - 5f * d, h * 0.22f, h * 0.78f, d, false);

        if (showCenterReticle) {
            drawScreenReticle(canvas, w * 0.5f, h * 0.5f, d, pulse);
        }
    }

    private void drawSideScale(Canvas canvas, float x, float top, float bottom, float d, boolean left) {
        int ticks = 12;
        float span = bottom - top;
        for (int i = 0; i <= ticks; i++) {
            float y = top + span * i / ticks;
            float len = (i % 3 == 0) ? 18f * d : 8f * d;
            if (left) canvas.drawLine(x, y, x + len, y, gridPaint);
            else canvas.drawLine(x, y, x - len, y, gridPaint);
        }
    }

    private void drawScreenReticle(Canvas canvas, float cx, float cy, float d, float pulse) {
        int oldAlpha = cornerPaint.getAlpha();
        cornerPaint.setAlpha((int) (120 + 70 * pulse));
        float s = 22f * d;
        float gap = 7f * d;
        canvas.drawLine(cx - s, cy, cx - gap, cy, cornerPaint);
        canvas.drawLine(cx + gap, cy, cx + s, cy, cornerPaint);
        canvas.drawLine(cx, cy - s, cx, cy - gap, cornerPaint);
        canvas.drawLine(cx, cy + gap, cx, cy + s, cornerPaint);
        canvas.drawCircle(cx, cy, 3.0f * d, framePaint);
        cornerPaint.setAlpha(oldAlpha);
    }

    private void drawTarget(Canvas canvas, RectF r, Detection det, int index, float d, float pulse) {
        float w = Math.max(1f, r.width());
        float h = Math.max(1f, r.height());
        float len = Math.max(22f * d, Math.min(Math.min(w, h) * 0.32f, 62f * d));
        float cut = 8f * d;

        canvas.drawRect(r, fillPaint);
        glowPaint.setAlpha((int) (28 + 24 * pulse));
        canvas.drawRect(r, glowPaint);

        framePaint.setAlpha(115);
        canvas.drawLine(r.left + cut, r.top, r.right - cut, r.top, framePaint);
        canvas.drawLine(r.right, r.top + cut, r.right, r.bottom - cut, framePaint);
        canvas.drawLine(r.right - cut, r.bottom, r.left + cut, r.bottom, framePaint);
        canvas.drawLine(r.left, r.bottom - cut, r.left, r.top + cut, framePaint);

        cornerPaint.setAlpha((int) (175 + 80 * pulse));
        drawHardCorners(canvas, r, len, cut, d);
        drawTargetTicks(canvas, r, d);
        drawConfidenceRail(canvas, r, det.confidence, d);
        if (showCenterReticle) drawTargetReticle(canvas, r, d, pulse);
        if (showLabels) drawTargetPanel(canvas, det, r, index, d);
    }

    private void drawHardCorners(Canvas canvas, RectF r, float len, float cut, float d) {
        // Top-left
        canvas.drawLine(r.left + cut, r.top, r.left + len, r.top, cornerPaint);
        canvas.drawLine(r.left, r.top + cut, r.left, r.top + len, cornerPaint);
        canvas.drawLine(r.left, r.top + cut, r.left + cut, r.top, cornerPaint);
        // Top-right
        canvas.drawLine(r.right - cut, r.top, r.right - len, r.top, cornerPaint);
        canvas.drawLine(r.right, r.top + cut, r.right, r.top + len, cornerPaint);
        canvas.drawLine(r.right, r.top + cut, r.right - cut, r.top, cornerPaint);
        // Bottom-left
        canvas.drawLine(r.left + cut, r.bottom, r.left + len, r.bottom, cornerPaint);
        canvas.drawLine(r.left, r.bottom - cut, r.left, r.bottom - len, cornerPaint);
        canvas.drawLine(r.left, r.bottom - cut, r.left + cut, r.bottom, cornerPaint);
        // Bottom-right
        canvas.drawLine(r.right - cut, r.bottom, r.right - len, r.bottom, cornerPaint);
        canvas.drawLine(r.right, r.bottom - cut, r.right, r.bottom - len, cornerPaint);
        canvas.drawLine(r.right, r.bottom - cut, r.right - cut, r.bottom, cornerPaint);

        float mark = 6f * d;
        canvas.drawLine(r.left + len + mark, r.top, r.left + len + mark * 2.2f, r.top, framePaint);
        canvas.drawLine(r.right - len - mark, r.bottom, r.right - len - mark * 2.2f, r.bottom, framePaint);
    }

    private void drawTargetTicks(Canvas canvas, RectF r, float d) {
        float cx = r.centerX();
        float cy = r.centerY();
        float tick = 12f * d;
        canvas.drawLine(cx - tick, r.top - tick * 0.55f, cx + tick, r.top - tick * 0.55f, framePaint);
        canvas.drawLine(cx - tick, r.bottom + tick * 0.55f, cx + tick, r.bottom + tick * 0.55f, framePaint);
        canvas.drawLine(r.left - tick * 0.55f, cy - tick, r.left - tick * 0.55f, cy + tick, framePaint);
        canvas.drawLine(r.right + tick * 0.55f, cy - tick, r.right + tick * 0.55f, cy + tick, framePaint);
    }

    private void drawTargetReticle(Canvas canvas, RectF r, float d, float pulse) {
        float cx = r.centerX();
        float cy = r.centerY();
        float s = Math.max(9f * d, Math.min(r.width(), r.height()) * 0.075f);
        framePaint.setAlpha((int) (125 + 65 * pulse));
        canvas.drawLine(cx - s * 1.9f, cy, cx - s * 0.55f, cy, framePaint);
        canvas.drawLine(cx + s * 0.55f, cy, cx + s * 1.9f, cy, framePaint);
        canvas.drawLine(cx, cy - s * 1.9f, cx, cy - s * 0.55f, framePaint);
        canvas.drawLine(cx, cy + s * 0.55f, cx, cy + s * 1.9f, framePaint);
        canvas.drawRect(cx - s * 0.45f, cy - s * 0.45f, cx + s * 0.45f, cy + s * 0.45f, framePaint);
    }

    private void drawConfidenceRail(Canvas canvas, RectF r, float confidence, float d) {
        int segments = 10;
        int lit = Math.max(1, Math.min(segments, Math.round(confidence * segments)));
        float segH = Math.max(3.5f * d, Math.min(8f * d, r.height() / 17f));
        float segW = 4.0f * d;
        float gap = 2.2f * d;
        float x = r.right + 5f * d;
        if (x + segW > getWidth() - 2f * d) x = r.right - 9f * d;
        float totalH = segments * segH + (segments - 1) * gap;
        float startY = Math.max(2f * d, r.centerY() - totalH * 0.5f);
        for (int i = 0; i < segments; i++) {
            int alpha = i >= segments - lit ? 230 : 48;
            framePaint.setAlpha(alpha);
            float y = startY + i * (segH + gap);
            canvas.drawRect(x, y, x + segW, y + segH, framePaint);
        }
    }

    private void drawTargetPanel(Canvas canvas, Detection det, RectF r, int index, float d) {
        String safeLabel = det.label == null ? "UNKNOWN" : det.label.replace(' ', '_').toUpperCase(Locale.US);
        String line1 = String.format(Locale.US, "TGT-%02d // %s", index, safeLabel);
        String line2 = String.format(Locale.US, "LOCK %03.0f%%  AREA %.1f", det.confidence * 100f, det.areaRatio * 100f);

        textPaint.setTextSize(11.2f * d);
        smallTextPaint.setTextSize(8.8f * d);
        float padX = 7f * d;
        float padY = 5f * d;
        float lineH1 = 13.5f * d;
        float lineH2 = 11.0f * d;
        float labelW = Math.max(textPaint.measureText(line1), smallTextPaint.measureText(line2)) + padX * 2f;
        float labelH = lineH1 + lineH2 + padY * 2f;
        labelW = Math.min(labelW, getWidth() - 8f * d);

        float x = r.left;
        float y = r.top - labelH - 6f * d;
        if (y < 6f * d) y = r.bottom + 6f * d;
        if (y + labelH > getHeight() - 4f * d) y = Math.max(4f * d, r.top + 4f * d);
        if (x + labelW > getWidth() - 4f * d) x = getWidth() - labelW - 4f * d;
        if (x < 4f * d) x = 4f * d;

        RectF p = new RectF(x, y, x + labelW, y + labelH);
        canvas.drawRect(p, panelPaint);
        framePaint.setAlpha(170);
        canvas.drawLine(p.left, p.top, p.right - 10f * d, p.top, framePaint);
        canvas.drawLine(p.right - 10f * d, p.top, p.right, p.top + 10f * d, framePaint);
        canvas.drawLine(p.right, p.top + 10f * d, p.right, p.bottom, framePaint);
        canvas.drawLine(p.right, p.bottom, p.left, p.bottom, framePaint);
        canvas.drawLine(p.left, p.bottom, p.left, p.top, framePaint);

        textPaint.setAlpha(245);
        smallTextPaint.setAlpha(215);
        canvas.drawText(line1, p.left + padX, p.top + padY + lineH1 - 2f * d, textPaint);
        canvas.drawText(line2, p.left + padX, p.top + padY + lineH1 + lineH2 - 1f * d, smallTextPaint);

        float barLeft = p.left + padX;
        float barTop = p.bottom - 3.0f * d;
        float barRight = p.left + padX + (p.width() - padX * 2f) * Math.max(0f, Math.min(1f, det.confidence));
        framePaint.setAlpha(230);
        canvas.drawRect(barLeft, barTop, barRight, p.bottom - 1.2f * d, framePaint);

        // Small lead line from panel to box, like an avionics callout.
        framePaint.setAlpha(100);
        float sx = Math.max(r.left, Math.min(r.right, p.centerX()));
        float sy = y < r.top ? p.bottom : p.top;
        canvas.drawLine(p.centerX(), sy, sx, y < r.top ? r.top : r.bottom, framePaint);
    }
}
