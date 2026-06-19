package com.jlxc.vehicleinfoncnn;

import android.Manifest;
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
import android.view.ViewGroup;
import android.widget.FrameLayout;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends ComponentActivity {
    private static final int REQ_CAMERA = 1001;

    private PreviewView previewView;
    private OverlayView overlayView;
    private TextView infoView;
    private ExecutorService cameraExecutor;
    private VehicleDetector detector;
    private volatile boolean detectorReady = false;
    private long lastInferMs = 0L;
    private int frameCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();

        cameraExecutor = Executors.newSingleThreadExecutor();
        detector = new VehicleDetector();
        // 320: 更适合旧手机；想要更准可以改成 640，但帧率会明显下降。
        detectorReady = detector.init(getAssets(), false, 320);
        if (!detectorReady) {
            Toast.makeText(this, "模型加载失败：请确认 assets 中有 yolov8n.ncnn.param / yolov8n.ncnn.bin", Toast.LENGTH_LONG).show();
            infoView.setText("模型未加载\n运行 scripts/setup_deps.sh 后重新编译");
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        }
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        previewView = new PreviewView(this);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        overlayView = new OverlayView(this);
        infoView = new TextView(this);
        infoView.setTextColor(0xffffffff);
        infoView.setTextSize(15f);
        infoView.setGravity(Gravity.START);
        infoView.setPadding(20, 16, 20, 16);
        infoView.setBackgroundColor(0x77000000);
        infoView.setText("车辆识别 ncnn\n初始化中...");

        root.addView(previewView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(overlayView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams infoLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START);
        root.addView(infoView, infoLp);
        setContentView(root);
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
                infoView.setText("车辆识别 ncnn\nYOLOv8n / CPU / 输入320 / 只显示交通目标");
            } catch (Exception e) {
                infoView.setText("相机启动失败：" + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        long now = System.currentTimeMillis();
        // 限制推理频率，避免旧手机持续满载。想更实时可改成 80~120ms。
        if (!detectorReady || now - lastInferMs < 180) {
            imageProxy.close();
            return;
        }
        lastInferMs = now;

        try {
            Bitmap bitmap = imageProxyToBitmap(imageProxy);
            int rotation = imageProxy.getImageInfo().getRotationDegrees();
            if (rotation != 0) bitmap = rotateBitmap(bitmap, rotation);

            long t0 = System.nanoTime();
            Detection[] arr = detector.detect(bitmap);
            long costMs = Math.round((System.nanoTime() - t0) / 1_000_000.0);

            List<Detection> list = new ArrayList<>();
            if (arr != null) {
                for (Detection d : arr) list.add(d);
            }
            int bw = bitmap.getWidth();
            int bh = bitmap.getHeight();
            runOnUiThread(() -> {
                overlayView.setDetections(list, bw, bh);
                infoView.setText(makeInfoText(list, costMs));
            });
            frameCount++;
        } catch (Throwable t) {
            runOnUiThread(() -> infoView.setText("识别异常：" + t.getMessage()));
        } finally {
            imageProxy.close();
        }
    }

    private String makeInfoText(List<Detection> list, long costMs) {
        Map<String, Integer> counts = new HashMap<>();
        for (Detection d : list) counts.put(d.label, counts.getOrDefault(d.label, 0) + 1);
        StringBuilder sb = new StringBuilder();
        sb.append("车辆识别 ncnn  |  ").append(costMs).append("ms  |  目标数 ").append(list.size()).append('\n');
        if (counts.isEmpty()) {
            sb.append("未识别到车辆 / 摩托 / 公交 / 卡车 / 自行车");
        } else {
            sb.append("统计：");
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                sb.append(e.getKey()).append('×').append(e.getValue()).append(' ');
            }
            int lines = Math.min(3, list.size());
            for (int i = 0; i < lines; i++) {
                sb.append('\n').append(i + 1).append('.').append(list.get(i).infoLine());
            }
        }
        return sb.toString();
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
        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
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
        if (detector != null) detector.release();
        if (cameraExecutor != null) cameraExecutor.shutdown();
    }
}
