# VehicleInfoNcnn

基于 **ncnn + YOLOv8n NCNN** 的安卓端车辆/交通目标识别 Demo。

当前版本识别并显示以下 COCO 交通类别：

- 小汽车 car
- 摩托车 motorcycle
- 公交车 bus
- 卡车 truck
- 自行车 bicycle

> 说明：这是一版优先“能跑通、能直接上手机测试”的通用车辆检测底座。模型来自 COCO 预训练 YOLOv8n，所以它不能识别品牌、车型、车牌号码、车身颜色。后续如果你训练了专用车辆属性模型，可以直接替换 assets 中的 `*.param` / `*.bin`，再同步修改后处理类别即可。

## 工程结构

```text
VehicleInfoNcnn/
├─ app/
│  ├─ src/main/java/com/jlxc/vehicleinfoncnn/
│  │  ├─ MainActivity.java          # CameraX 相机预览 + 实时推理
│  │  ├─ OverlayView.java           # 识别框绘制
│  │  ├─ VehicleDetector.java       # JNI 声明
│  │  └─ Detection.java             # 识别结果结构
│  ├─ src/main/cpp/
│  │  ├─ native-lib.cpp             # ncnn 加载、YOLOv8 后处理、NMS
│  │  └─ CMakeLists.txt
│  └─ src/main/assets/
│     ├─ yolov8n.ncnn.param         # 小模型参数文件，已放入
│     └─ yolov8n.ncnn.bin           # 大模型权重，由 scripts/setup_deps.sh 下载后打包进 APK
├─ scripts/setup_deps.sh            # 下载 ncnn Android 预编译库和模型权重
└─ .github/workflows/android.yml    # GitHub Actions 自动打包 APK
```

## 直接在 GitHub Actions 打包

1. 新建 GitHub 仓库。
2. 把本工程所有文件上传到仓库根目录。
3. 打开仓库的 **Actions**。
4. 运行 **Android APK** 工作流。
5. 编译完成后，在 Artifact 里下载 `VehicleInfoNcnn-release-apk`。

工作流会自动执行：

```bash
bash scripts/setup_deps.sh
gradle assembleRelease --stacktrace --no-daemon
```

其中 `setup_deps.sh` 会把以下内容下载到工程内：

- `app/src/main/jni/ncnn-20260526-android/`
- `app/src/main/assets/yolov8n.ncnn.bin`

最终 APK 会把 `yolov8n.ncnn.param` 和 `yolov8n.ncnn.bin` 都打包到 assets。

## 本地 Android Studio 编译

首次编译前执行：

```bash
bash scripts/setup_deps.sh
```

然后用 Android Studio 打开项目，或执行：

```bash
gradle assembleDebug
```

## 默认性能配置

- 推理后端：CPU
- 输入尺寸：320
- ABI：`armeabi-v7a` + `arm64-v8a`
- minSdk：23，也就是 Android 6.0+
- 相机：CameraX 后置摄像头

旧安卓手机建议先保持输入尺寸 320；如果你想提高识别精度，可以把 `MainActivity.java` 里的：

```java
detectorReady = detector.init(getAssets(), false, 320);
```

改成：

```java
detectorReady = detector.init(getAssets(), false, 640);
```

但 640 在老手机上会明显降低帧率。

## 识别结果说明

底部信息栏会显示：

- 单帧推理耗时
- 当前识别到的交通目标数量
- 不同类别数量统计
- 前 3 个最大目标的类别、置信度、框尺寸、占画面比例

画面上会绘制识别框和类别标签。

## 模型来源

- ncnn：Tencent/ncnn `20260526` Android 预编译包
- 模型：nihui/ncnn-android-yolov8 中的 `yolov8n.ncnn.param` / `yolov8n.ncnn.bin`

## 后续可扩展方向

1. **车牌识别**：增加车牌检测 + OCR 两阶段模型。
2. **车型/品牌识别**：替换为专门训练的车辆属性分类模型。
3. **车身颜色识别**：在检测框区域内加入颜色聚类或训练颜色分类器。
4. **车机录屏/USB 摄像头输入**：把 CameraX 输入替换成录屏帧或 UVC 摄像头帧。
5. **悬浮窗识别框**：如果用于车机倒车画面，可以把 OverlayView 改成系统悬浮窗。
