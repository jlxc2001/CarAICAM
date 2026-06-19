package com.jlxc.vehicleinfoncnn;

import android.Manifest;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Bundle;
import android.util.Size;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends ComponentActivity {
    private static final int REQ_CAMERA = 1001;
    private static final String PREFS = "tactical_detector_settings";
    private static final String KEY_CONF = "confidence";
    private static final String KEY_NMS = "nms";
    private static final String KEY_MAX = "max_results";
    private static final String KEY_INPUT = "input_size";
    private static final String KEY_INTERVAL = "infer_interval";
    private static final String KEY_LABELS = "show_labels";
    private static final String KEY_RETICLE = "show_reticle";
    private static final String KEY_VEHICLE_ONLY = "vehicle_only";
    private static final String KEY_GPU = "use_gpu";

    private PreviewView previewView;
    private OverlayView overlayView;
    private ExecutorService cameraExecutor;
    private VehicleDetector detector;
    private SharedPreferences prefs;
    private volatile boolean detectorReady = false;
    private volatile float confThreshold = 0.25f;
    private volatile float nmsThreshold = 0.45f;
    private volatile int maxResults = 40;
    private volatile int inputSize = 320;
    private volatile int inferIntervalMs = 180;
    private volatile boolean showLabels = true;
    private volatile boolean showReticle = true;
    private volatile boolean vehicleOnly = false;
    private volatile boolean useGpu = false;
    private long lastInferMs = 0L;
    private long lastErrorToastMs = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        loadSettings();
        buildUi();

        cameraExecutor = Executors.newSingleThreadExecutor();
        detector = new VehicleDetector();
        detectorReady = detector.init(getAssets(), useGpu, inputSize);
        if (detectorReady) {
            detector.setOptions(confThreshold, nmsThreshold, maxResults);
        } else {
            Toast.makeText(this, "模型加载失败：请确认 assets 中有 yolov8n.ncnn.param / yolov8n.ncnn.bin", Toast.LENGTH_LONG).show();
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        }
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xff000000);
        root.setLongClickable(true);
        root.setOnLongClickListener(v -> {
            showSettingsDialog();
            return true;
        });

        previewView = new PreviewView(this);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        previewView.setLongClickable(true);
        previewView.setOnLongClickListener(v -> {
            showSettingsDialog();
            return true;
        });

        overlayView = new OverlayView(this);
        overlayView.setRenderOptions(showLabels, showReticle);
        overlayView.setLongClickable(true);
        overlayView.setOnLongClickListener(v -> {
            showSettingsDialog();
            return true;
        });

        root.addView(previewView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(overlayView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        Toast.makeText(this, "长按屏幕打开识别设置", Toast.LENGTH_SHORT).show();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                analysis.setAnalyzer(cameraExecutor, this::analyzeImage);

                CameraSelector selector = CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, selector, preview, analysis);
            } catch (Exception e) {
                Toast.makeText(this, "相机启动失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        long now = System.currentTimeMillis();
        if (!detectorReady || now - lastInferMs < inferIntervalMs) {
            imageProxy.close();
            return;
        }
        lastInferMs = now;

        try {
            Bitmap bitmap = imageProxyToBitmap(imageProxy);
            int rotation = imageProxy.getImageInfo().getRotationDegrees();
            if (rotation != 0) bitmap = rotateBitmap(bitmap, rotation);
            if (bitmap.getConfig() != Bitmap.Config.ARGB_8888) {
                bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
            }

            Detection[] arr = detector.detect(bitmap);
            List<Detection> list = new ArrayList<>();
            if (arr != null) {
                for (Detection d : arr) {
                    if (!vehicleOnly || isVehicleLabel(d.labelId)) list.add(d);
                }
            }
            int bw = bitmap.getWidth();
            int bh = bitmap.getHeight();
            runOnUiThread(() -> overlayView.setDetections(list, bw, bh));
        } catch (Throwable t) {
            long tNow = System.currentTimeMillis();
            if (tNow - lastErrorToastMs > 2500) {
                lastErrorToastMs = tNow;
                runOnUiThread(() -> Toast.makeText(this, "识别异常：" + t.getMessage(), Toast.LENGTH_SHORT).show());
            }
        } finally {
            imageProxy.close();
        }
    }

    private boolean isVehicleLabel(int labelId) {
        // COCO: 1 bicycle, 2 car, 3 motorcycle, 5 bus, 7 truck
        return labelId == 1 || labelId == 2 || labelId == 3 || labelId == 5 || labelId == 7;
    }

    private void showSettingsDialog() {
        final int oldInputSize = inputSize;
        final boolean oldUseGpu = useGpu;
        Typeface hudTypeface;
        try {
            hudTypeface = Typeface.createFromAsset(getAssets(), "fonts/jlxc_hud_vector.ttf");
        } catch (Throwable t) {
            hudTypeface = Typeface.create("monospace", Typeface.BOLD);
        }

        final Dialog dialog = new Dialog(this);
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        scrollView.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        panel.setPadding(pad, pad, pad, pad);
        panel.setBackground(panelBg(0xee020900, 0xff39ff14, 1));
        scrollView.addView(panel, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("FCS / HUD CONFIG");
        title.setTextColor(0xffa8ff9c);
        title.setTextSize(20f);
        title.setTypeface(hudTypeface);
        title.setLetterSpacing(0.08f);
        title.setPadding(0, 0, 0, dp(4));
        panel.addView(title);

        TextView sub = new TextView(this);
        sub.setText("VISION SYSTEM // LONG PRESS TO CLOSE PANEL // ALL TARGETS BY DEFAULT");
        sub.setTextColor(0xaa39ff14);
        sub.setTextSize(10.5f);
        sub.setTypeface(hudTypeface);
        sub.setLetterSpacing(0.05f);
        sub.setPadding(0, 0, 0, dp(12));
        panel.addView(sub);

        TextView hint = new TextView(this);
        hint.setText("默认显示 YOLOv8 COCO 全类别；画面底部信息栏已移除。建议骁龙810先用 320 输入尺寸、关闭 GPU。保存后立即应用。 ");
        hint.setTextSize(13.5f);
        hint.setTextColor(0xff8dff82);
        hint.setLineSpacing(0, 1.15f);
        hint.setPadding(0, 0, 0, dp(10));
        panel.addView(hint);

        SeekControl conf = addSeek(panel, "01 // 置信度阈值", Math.round(confThreshold * 100f), 5, 90, 1, "%");
        SeekControl nms = addSeek(panel, "02 // NMS重叠过滤", Math.round(nmsThreshold * 100f), 10, 90, 1, "%");
        SeekControl max = addSeek(panel, "03 // 最多显示目标", maxResults, 1, 80, 1, "个");
        SeekControl interval = addSeek(panel, "04 // 推理间隔", inferIntervalMs, 60, 500, 20, "ms");

        TextView inputTitle = sectionTitle("05 // 模型输入尺寸");
        panel.addView(inputTitle);
        RadioGroup inputGroup = new RadioGroup(this);
        inputGroup.setOrientation(RadioGroup.HORIZONTAL);
        inputGroup.setPadding(0, 0, 0, dp(4));
        RadioButton size320 = radio("320 快速");
        RadioButton size416 = radio("416 平衡");
        RadioButton size640 = radio("640 高精度");
        inputGroup.addView(size320);
        inputGroup.addView(size416);
        inputGroup.addView(size640);
        if (inputSize == 640) size640.setChecked(true);
        else if (inputSize == 416) size416.setChecked(true);
        else size320.setChecked(true);
        panel.addView(inputGroup);

        Switch labels = sw("06 // 显示目标标签", showLabels);
        Switch reticle = sw("07 // 显示火控准星", showReticle);
        Switch onlyVehicles = sw("08 // 只显示交通工具", vehicleOnly);
        Switch gpu = sw("09 // 尝试 GPU / Vulkan", useGpu);
        panel.addView(labels);
        panel.addView(reticle);
        panel.addView(onlyVehicles);
        panel.addView(gpu);

        TextView gpuHint = new TextView(this);
        gpuHint.setText("GPU 依赖手机 Vulkan 驱动；如果开启后模型加载失败，关闭该项即可。骁龙810建议 CPU + 320。 ");
        gpuHint.setTextSize(12f);
        gpuHint.setTextColor(0x9939ff14);
        gpuHint.setPadding(0, dp(5), 0, dp(12));
        panel.addView(gpuHint);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);
        buttons.setPadding(0, dp(10), 0, 0);
        Button cancel = tacticalButton("CANCEL", hudTypeface);
        Button reset = tacticalButton("RESET", hudTypeface);
        Button apply = tacticalButton("APPLY", hudTypeface);
        buttons.addView(cancel);
        buttons.addView(reset);
        buttons.addView(apply);
        panel.addView(buttons);

        cancel.setOnClickListener(v -> dialog.dismiss());
        reset.setOnClickListener(v -> {
            resetSettings();
            saveSettings();
            applySettings(inputSize != oldInputSize || useGpu != oldUseGpu);
            dialog.dismiss();
            Toast.makeText(this, "已恢复默认设置", Toast.LENGTH_SHORT).show();
        });
        apply.setOnClickListener(v -> {
            confThreshold = conf.value() / 100f;
            nmsThreshold = nms.value() / 100f;
            maxResults = max.value();
            inferIntervalMs = interval.value();
            showLabels = labels.isChecked();
            showReticle = reticle.isChecked();
            vehicleOnly = onlyVehicles.isChecked();
            useGpu = gpu.isChecked();
            if (size640.isChecked()) inputSize = 640;
            else if (size416.isChecked()) inputSize = 416;
            else inputSize = 320;
            saveSettings();
            applySettings(inputSize != oldInputSize || useGpu != oldUseGpu);
            dialog.dismiss();
        });

        dialog.setContentView(scrollView);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(Math.round(getResources().getDisplayMetrics().widthPixels * 0.92f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private GradientDrawable panelBg(int color, int strokeColor, int strokeDp) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(0f);
        bg.setStroke(dp(strokeDp), strokeColor);
        return bg;
    }

    private Button tacticalButton(String text, Typeface typeface) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(0xff39ff14);
        b.setTextSize(11.5f);
        b.setTypeface(typeface);
        b.setLetterSpacing(0.08f);
        b.setAllCaps(false);
        b.setPadding(dp(10), 0, dp(10), 0);
        b.setMinHeight(dp(34));
        b.setMinimumHeight(dp(34));
        b.setBackground(panelBg(0x33001600, 0xaa39ff14, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
        lp.setMargins(dp(7), 0, 0, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private TextView sectionTitle(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(14f);
        t.setTextColor(0xff39ff14);
        t.setGravity(Gravity.START);
        t.setPadding(0, dp(12), 0, dp(4));
        t.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        return t;
    }

    private RadioButton radio(String text) {
        RadioButton r = new RadioButton(this);
        r.setText(text);
        r.setTextSize(13.5f);
        r.setTextColor(0xffa8ff9c);
        r.setButtonTintList(tacticalTint());
        r.setPadding(0, 0, dp(10), 0);
        return r;
    }

    private Switch sw(String text, boolean checked) {
        Switch s = new Switch(this);
        s.setText(text);
        s.setTextSize(14f);
        s.setTextColor(0xffa8ff9c);
        s.setChecked(checked);
        s.setPadding(0, dp(6), 0, dp(6));
        s.setThumbTintList(tacticalTint());
        s.setTrackTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{0x7739ff14, 0x3339ff14}));
        return s;
    }

    private ColorStateList tacticalTint() {
        return new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{0xff39ff14, 0x8839ff14});
    }

    private SeekControl addSeek(LinearLayout parent, String title, int value, int min, int max, int step, String suffix) {
        TextView label = sectionTitle(title + " : " + value + suffix);
        parent.addView(label);
        SeekBar bar = new SeekBar(this);
        int progressMax = Math.max(1, (max - min) / step);
        bar.setMax(progressMax);
        int progress = Math.max(0, Math.min(progressMax, (value - min) / step));
        bar.setProgress(progress);
        bar.setProgressTintList(ColorStateList.valueOf(0xff39ff14));
        bar.setThumbTintList(ColorStateList.valueOf(0xffa8ff9c));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(0x4439ff14));
        SeekControl control = new SeekControl(bar, label, title, min, step, suffix);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                label.setText(title + " : " + control.value() + suffix);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        parent.addView(bar);
        return control;
    }

    private static class SeekControl {
        final SeekBar bar;
        final TextView label;
        final String title;
        final int min;
        final int step;
        final String suffix;
        SeekControl(SeekBar bar, TextView label, String title, int min, int step, String suffix) {
            this.bar = bar;
            this.label = label;
            this.title = title;
            this.min = min;
            this.step = step;
            this.suffix = suffix;
        }
        int value() {
            return min + bar.getProgress() * step;
        }
    }

    private void loadSettings() {
        confThreshold = prefs.getFloat(KEY_CONF, 0.25f);
        nmsThreshold = prefs.getFloat(KEY_NMS, 0.45f);
        maxResults = prefs.getInt(KEY_MAX, 40);
        inputSize = prefs.getInt(KEY_INPUT, 320);
        inferIntervalMs = prefs.getInt(KEY_INTERVAL, 180);
        showLabels = prefs.getBoolean(KEY_LABELS, true);
        showReticle = prefs.getBoolean(KEY_RETICLE, true);
        vehicleOnly = prefs.getBoolean(KEY_VEHICLE_ONLY, false);
        useGpu = prefs.getBoolean(KEY_GPU, false);
    }

    private void saveSettings() {
        prefs.edit()
                .putFloat(KEY_CONF, confThreshold)
                .putFloat(KEY_NMS, nmsThreshold)
                .putInt(KEY_MAX, maxResults)
                .putInt(KEY_INPUT, inputSize)
                .putInt(KEY_INTERVAL, inferIntervalMs)
                .putBoolean(KEY_LABELS, showLabels)
                .putBoolean(KEY_RETICLE, showReticle)
                .putBoolean(KEY_VEHICLE_ONLY, vehicleOnly)
                .putBoolean(KEY_GPU, useGpu)
                .apply();
    }

    private void resetSettings() {
        confThreshold = 0.25f;
        nmsThreshold = 0.45f;
        maxResults = 40;
        inputSize = 320;
        inferIntervalMs = 180;
        showLabels = true;
        showReticle = true;
        vehicleOnly = false;
        useGpu = false;
    }

    private void applySettings(boolean reinitModel) {
        overlayView.setRenderOptions(showLabels, showReticle);
        if (!reinitModel) {
            if (detectorReady) detector.setOptions(confThreshold, nmsThreshold, maxResults);
            Toast.makeText(this, "设置已应用", Toast.LENGTH_SHORT).show();
            return;
        }
        detectorReady = false;
        Toast.makeText(this, "正在重新加载模型", Toast.LENGTH_SHORT).show();
        cameraExecutor.execute(() -> {
            boolean ok;
            try {
                detector.release();
                ok = detector.init(getAssets(), useGpu, inputSize);
                if (ok) detector.setOptions(confThreshold, nmsThreshold, maxResults);
            } catch (Throwable t) {
                ok = false;
            }
            final boolean loaded = ok;
            detectorReady = loaded;
            runOnUiThread(() -> Toast.makeText(this, loaded ? "模型已重新加载" : "模型重新加载失败", Toast.LENGTH_SHORT).show());
        });
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static Bitmap rotateBitmap(Bitmap src, int degrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        Bitmap out = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), matrix, true);
        if (out != src) src.recycle();
        return out;
    }

    private static Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        Image image = imageProxy.getImage();
        if (image == null) throw new IllegalStateException("ImageProxy image is null");
        byte[] nv21 = yuv420888ToNv21(image);
        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 82, out);
        byte[] jpegBytes = out.toByteArray();
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length, opts);
    }

    private static byte[] yuv420888ToNv21(Image image) {
        int width = image.getWidth();
        int height = image.getHeight();
        Image.Plane[] planes = image.getPlanes();
        byte[] out = new byte[width * height * 3 / 2];

        ByteBuffer yBuffer = planes[0].getBuffer();
        int yRowStride = planes[0].getRowStride();
        int pos = 0;
        for (int row = 0; row < height; row++) {
            int rowStart = row * yRowStride;
            for (int col = 0; col < width; col++) {
                out[pos++] = yBuffer.get(rowStart + col);
            }
        }

        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();
        int uRowStride = planes[1].getRowStride();
        int vRowStride = planes[2].getRowStride();
        int uPixelStride = planes[1].getPixelStride();
        int vPixelStride = planes[2].getPixelStride();

        for (int row = 0; row < height / 2; row++) {
            for (int col = 0; col < width / 2; col++) {
                int vuPos = row * vRowStride + col * vPixelStride;
                int uuPos = row * uRowStride + col * uPixelStride;
                out[pos++] = vBuffer.get(vuPos);
                out[pos++] = uBuffer.get(uuPos);
            }
        }
        return out;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            Toast.makeText(this, "需要相机权限才能实时识别", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        detectorReady = false;
        if (detector != null) detector.release();
        if (cameraExecutor != null) cameraExecutor.shutdownNow();
    }
}
