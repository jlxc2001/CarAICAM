# VehicleInfoNcnn Green HUD V3

基于 **ncnn + YOLOv8n NCNN + CameraX** 的安卓端实时目标识别 Demo。

这版是 **荧光绿战斗机 HUD 风**：黑底、细线网格、扫描线、火控锁定框、目标编号、置信度轨道、内置 HUD 字体、长按设置面板。

## 这一版新增 / 修改

- 默认显示 YOLOv8n / COCO 能识别到的所有类别，不再只限制交通工具。
- 设置里仍保留“只显示交通工具”开关，方便车载场景快速过滤。
- 去掉底部统计文字栏，主画面只保留相机预览 + HUD 覆盖层。
- 识别框改为荧光绿战斗机火控风：
  - 分离式切角锁定框
  - 目标编号 `TGT-01`
  - `LOCK` 置信度信息
  - 右侧分段置信度轨道
  - 中心准星 / 目标准星
  - 全屏网格、边缘刻度和扫描线
- 设置页改为黑底绿字的战术控制台风格。
- 内置 `JLXC HUD Vector` 字体：`app/src/main/assets/fonts/jlxc_hud_vector.ttf`，随 APK 打包，不依赖系统字体。
- GitHub Actions 继续产出可直接安装的 debug / debugSigned release APK。

## 工程结构

```text
VehicleInfoNcnn_green_hud_v3/
├─ app/
│  ├─ src/main/java/com/jlxc/vehicleinfoncnn/
│  │  ├─ MainActivity.java          # CameraX 相机预览 + 设置面板 + 实时推理
│  │  ├─ OverlayView.java           # 荧光绿战斗机 HUD 绘制层
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

## 直接在 GitHub Actions 打包

1. 新建 GitHub 仓库。
2. 把本工程所有文件上传到仓库根目录。
3. 打开仓库的 **Actions**。
4. 运行 **Android APK** 工作流。
5. 编译完成后，在 Artifact 里下载 `VehicleInfoNcnn-installable-apks`。
6. 优先安装 `VehicleInfoNcnn-debug-installable.apk`；也可以安装 `VehicleInfoNcnn-release-debugSigned-installable.apk`。

工作流会自动执行：

```bash
bash scripts/setup_deps.sh
gradle assembleDebug assembleRelease --stacktrace --no-daemon
```

其中 `setup_deps.sh` 会把以下内容下载到工程内：

- `app/src/main/jni/ncnn-20260526-android/`
- `app/src/main/assets/yolov8n.ncnn.bin`

最终 APK 会把 `yolov8n.ncnn.param`、`yolov8n.ncnn.bin` 和内置 HUD 字体都打包进去。

## 推荐安装命令

```bat
adb install -r VehicleInfoNcnn-debug-installable.apk
```

如果之前装过旧版本但签名不同，先卸载旧包：

```bat
adb uninstall com.jlxc.vehicleinfoncnn
adb install -r VehicleInfoNcnn-debug-installable.apk
```

## 最低系统与性能建议

- `minSdk 23`，也就是 Android 6.0+。
- 默认输入尺寸：320。
- 默认后端：CPU。
- 默认类别：全部 COCO 类别。
- ABI：`armeabi-v7a` + `arm64-v8a`。

骁龙 810 机器建议：

```text
模型输入尺寸：320
推理间隔：180ms 或更高
GPU / Vulkan：关闭
最多显示目标：20~40
置信度阈值：0.30~0.45
```

如果是骁龙 855 / 865 / 870 以上，可以在设置里尝试 416 或 640。

## 长按设置

运行后长按画面任意位置打开设置，包含：

- 置信度阈值
- NMS 重叠过滤
- 最多显示目标
- 推理间隔
- 模型输入尺寸：320 / 416 / 640
- 显示目标标签
- 显示火控准星
- 只显示交通工具
- 尝试 GPU / Vulkan

## 模型说明

当前底座模型是 COCO 预训练 YOLOv8n，所以它能检测通用物体，但不能直接识别车辆品牌、具体车型、车牌号码、车身颜色。后续如果要“车辆信息识别”，建议追加：

1. 车牌检测 + OCR。
2. 车型 / 品牌分类模型。
3. 车身颜色分类或颜色聚类。
4. 车机录屏 / UVC 摄像头输入替换 CameraX。

## 安装失败排查

如果看到：

```text
ERROR: "adb install" returned with value 1
Failed to install ... app-release-unsigned.apk
```

不要安装 `app-release-unsigned.apk`，请安装工作流产出的：

```text
VehicleInfoNcnn-debug-installable.apk
VehicleInfoNcnn-release-debugSigned-installable.apk
```

常见原因：

- `INSTALL_FAILED_UPDATE_INCOMPATIBLE`：旧版本签名不同，先 `adb uninstall com.jlxc.vehicleinfoncnn`。
- `INSTALL_FAILED_NO_MATCHING_ABIS`：手机 CPU 架构不匹配；本工程默认支持 `armeabi-v7a` 和 `arm64-v8a`。
- `INSTALL_FAILED_OLDER_SDK`：系统版本低于 Android 6.0；本工程 `minSdk 23`。
