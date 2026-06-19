# VehicleInfoNcnn Green HUD v4

基于 **ncnn + YOLOv8n NCNN + CameraX** 的安卓端实时识别 Demo。

这一版是 **荧光绿战斗机 HUD 风 v4**，重点更新：

- 启动后强制全屏运行，隐藏状态栏和虚拟键。
- 长按屏幕打开战术风设置面板。
- 设置中加入 **摄像头选择**，适配多摄手机，可手动切换主摄/超广角/长焦/前摄。
- 摄像头选择会保存，下次打开自动沿用。
- 默认显示 YOLOv8n / COCO 能识别的全部类别。
- 仍保留“只显示交通工具”开关。
- 继续使用黑底 + 荧光绿 + 火控锁定框 + 中心准星 + 扫描线 HUD 风格。

> 说明：YOLOv8n COCO 预训练模型可以识别通用物体类别，但不能识别车辆品牌、具体车型、车牌号码、车身颜色。后续如果你训练了专用车辆属性模型，可以替换 assets 中的模型文件，并同步修改 native 后处理类别。

## 新增功能说明

### 1. 沉浸式全屏

`MainActivity` 会在以下时机重新隐藏系统栏：

- `onCreate`
- `onResume`
- `onWindowFocusChanged`
- 设置面板关闭后
- 相机重新绑定后

这样从设置面板返回后，状态栏和底部虚拟键也会尽量保持隐藏。

### 2. 多摄像头选择

设置路径：

```text
长按屏幕 → FCS / HUD CONFIG → 06 // 摄像头选择
```

列表会读取 Camera2 的摄像头 ID、前后置和焦距，例如：

```text
CAM-0  BACK   FOCAL 5.50mm  // 后置
CAM-1  FRONT  FOCAL 2.70mm
CAM-2  BACK   FOCAL 1.95mm  // 可能是超广角
```

一般来说：

- 焦距数字越小，视角通常越广。
- `BACK` 是后置摄像头。
- `FRONT` 是前置摄像头。
- 很多手机 `CAM-0` 是主摄，其他 ID 可能是超广角、长焦或前摄。
- 多摄 Android 机型厂商差异很大，所以建议你逐个切换测试画面视角。

保存设置后会立即重新绑定相机。

## 工程结构

```text
VehicleInfoNcnn_green_hud_v4/
├─ app/
│  ├─ src/main/java/com/jlxc/vehicleinfoncnn/
│  │  ├─ MainActivity.java          # CameraX 相机预览、全屏、设置、多摄切换
│  │  ├─ OverlayView.java           # 荧光绿战斗机 HUD 识别框
│  │  ├─ VehicleDetector.java       # JNI 声明
│  │  └─ Detection.java             # 识别结果结构
│  ├─ src/main/cpp/
│  │  ├─ native-lib.cpp             # ncnn 加载、YOLOv8 后处理、NMS
│  │  └─ CMakeLists.txt
│  └─ src/main/assets/
│     ├─ yolov8n.ncnn.param         # 小模型参数文件，已放入
│     ├─ yolov8n.ncnn.bin           # 大模型权重，由 scripts/setup_deps.sh 下载后打包进 APK
│     └─ fonts/jlxc_hud_vector.ttf  # 内置 HUD 字体
├─ scripts/setup_deps.sh            # 下载 ncnn Android 预编译库和模型权重
└─ .github/workflows/android.yml    # GitHub Actions 自动打包 APK
```

## GitHub Actions 打包

1. 新建 GitHub 仓库。
2. 把本工程所有文件上传到仓库根目录。
3. 打开仓库的 **Actions**。
4. 运行 **Android APK** 工作流。
5. 编译完成后，在 Artifact 里下载：

```text
VehicleInfoNcnn-installable-apks
```

里面会有：

```text
VehicleInfoNcnn-debug-installable.apk
VehicleInfoNcnn-release-debugSigned-installable.apk
```

优先安装：

```bat
adb install -r VehicleInfoNcnn-debug-installable.apk
```

如果签名冲突：

```bat
adb uninstall com.jlxc.vehicleinfoncnn
adb install -r VehicleInfoNcnn-debug-installable.apk
```

## 默认性能配置

- 推理后端：CPU
- 输入尺寸：320
- ABI：`armeabi-v7a` + `arm64-v8a`
- minSdk：23，也就是 Android 6.0+
- 摄像头：自动选择后置中焦距最小的 CameraX 可用镜头；也可以在设置里手动切换

### 老设备建议

骁龙 810 / 845 这类机器建议：

```text
模型输入尺寸：320
推理间隔：2 或 3 档位，对应 180ms 以上
GPU/Vulkan：先关闭
最多显示目标：10～30
置信度阈值：0.35～0.45
```

### 新旗舰建议

天玑 9500 / 骁龙 8 系旗舰可以尝试：

```text
模型输入尺寸：640
推理间隔：60～120ms
GPU/Vulkan：打开测试
最多显示目标：80
置信度阈值：0.25～0.35
```

如果打开 Vulkan 后黑屏、闪退或模型加载失败，就切回 CPU。

## 模型来源

- ncnn：Tencent/ncnn `20260526` Android 预编译包
- 模型：nihui/ncnn-android-yolov8 中的 `yolov8n.ncnn.param` / `yolov8n.ncnn.bin`

## 安装失败排查

不要安装 `app-release-unsigned.apk`。Android 不能安装未签名包。

常见失败原因：

- `INSTALL_FAILED_UPDATE_INCOMPATIBLE`：旧版本签名不同，先卸载旧包。
- `INSTALL_FAILED_NO_MATCHING_ABIS`：CPU 架构不匹配；本工程默认支持 `armeabi-v7a` 和 `arm64-v8a`。
- `INSTALL_FAILED_OLDER_SDK`：系统版本低于 Android 6.0；本工程 `minSdk 23`。
