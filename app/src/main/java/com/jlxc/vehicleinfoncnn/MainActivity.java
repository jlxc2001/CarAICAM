package com.jlxc.vehicleinfoncnn;

import android.Manifest;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

        ScrollView scrollView = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        box.setPadding(pad, pad, pad, pad);
        scrollView.addView(box, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView hint = new TextView(this);
        hint.setText("默认显示 YOLOv8 COCO 全类别。识别框、准星和标签为战术 HUD 风格；底部统计文字已隐藏。设置保存后立即生效。");
        hint.setTextSize(14f);
        hint.setTextColor(0xff444444);
        hint.setPadding(0, 0, 0, dp(12));
        box.addView(hint);

        SeekControl conf = addSeek(box, "置信度阈值", Math.round(confThreshold * 100f), 5, 90, 1, "%");
        SeekControl nms = addSeek(box, "NMS重叠过滤", Math.round(nmsThreshold * 100f), 10, 90, 1, "%");
        SeekControl max = addSeek(box, "最多显示目标", maxResults, 1, 80, 1, "个");
        SeekControl interval = addSeek(box, "推理间隔", inferIntervalMs, 60, 500, 20, "ms");

        TextView inputTitle = sectionTitle("模型输入尺寸");
        box.addView(inputTitle);
        RadioGroup inputGroup = new RadioGroup(this);
        inputGroup.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton size320 = radio("320 快速");
        RadioButton size416 = radio("416 平衡");
        RadioButton size640 = radio("640 更准");
        inputGroup.addView(size320);
        inputGroup.addView(size416);
        inputGroup.addView(size640);
        if (inputSize == 640) size640.setChecked(true);
        else if (inputSize == 416) size416.setChecked(true);
        else size320.setChecked(true);
        box.addView(inputGroup);

        Switch labels = sw("显示目标标签", showLabels);
        Switch reticle = sw("显示中心锁定准星", showReticle);
        Switch onlyVehicles = sw("只显示交通工具", vehicleOnly);
        Switch gpu = sw("尝试使用 GPU / Vulkan", useGpu);
        box.addView(labels);
        box.addView(reticle);
        box.addView(onlyVehicles);
        box.addView(gpu);

        TextView gpuHint = new TextView(this);
        gpuHint.setText("GPU 开关取决于 ncnn 预编译包和手机 Vulkan 支持；如果打开后黑屏或加载失败，长按进入设置关闭即可。");
        gpuHint.setTextSize(12f);
        gpuHint.setTextColor(0xff666666);
        gpuHint.setPadding(0, dp(4), 0, 0);
        box.addView(gpuHint);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("战术识别设置")
                .setView(scrollView)
                .setPositiveButton("保存应用", null)
                .setNegativeButton("取消", null)
                .setNeutralButton("恢复默认", null)
                .create();

        dialog.setOnShowListener(dlg -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
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
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                resetSettings();
                saveSettings();
                applySettings(inputSize != oldInputSize || useGpu != oldUseGpu);
                dialog.dismiss();
                Toast.makeText(this, "已恢复默认设置", Toast.LENGTH_SHORT).show();
            });
        });
        dialog.show();
    }

    private TextView sectionTitle(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(15f);
        t.setTextColor(0xff111111);
        t.setGravity(Gravity.START);
        t.setPadding(0, dp(14), 0, dp(4));
        return t;
    }

    private RadioButton radio(String text) {
        RadioButton r = new RadioButton(this);
        r.setText(text);
        r.setTextSize(14f);
        return r;
    }

    private Switch sw(String text, boolean checked) {
        Switch s = new Switch(this);
        s.setText(text);
        s.setTextSize(15f);
        s.setChecked(checked);
        s.setPadding(0, dp(6), 0, dp(6));
        return s;
    }

    private SeekControl addSeek(LinearLayout parent, String title, int value, int min, int max, int step, String suffix) {
        TextView label = sectionTitle(title + "：" + value + suffix);
        parent.addView(label);
        SeekBar bar = new SeekBar(this);
        int progressMax = Math.max(1, (max - min) / step);
        bar.setMax(progressMax);
        int progress = Math.max(0, Math.min(progressMax, (value - min) / step));
        bar.setProgress(progress);
        SeekControl control = new SeekControl(bar, label, title, min, step, suffix);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                label.setText(title + "：" + control.value() + suffix);
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
