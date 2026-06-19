# VehicleInfoNcnn / Tactical Object Detector

ncnn + YOLOv8n NCNN Android 实时识别 Demo。

## V2 改动

- 不再只限制交通工具：默认显示 YOLOv8 COCO 可识别的全部目标类别。
- 仍保留设置项：可以切回“只显示交通工具”。
- 移除了界面底部统计文字，画面只保留相机预览和识别框。
- 长按屏幕打开设置。
- 新增战术 HUD 风格识别框：角标、锁定准星、置信度条、LOCK 标签。
- 内置硬朗战术像素字形渲染：直接在 `OverlayView.java` 里用 5×7 战术网格绘制标签，不依赖安卓系统字体，也不需要额外字体文件。

## 设置功能

长按屏幕打开设置，可调：

- 置信度阈值
- NMS 重叠过滤
- 最多显示目标数量
- 推理间隔
- 模型输入尺寸：320 / 416 / 640
- 显示/隐藏目标标签
- 显示/隐藏中心锁定准星
- 全类别 / 只显示交通工具
- 尝试使用 GPU / Vulkan

默认推荐：

- 输入尺寸：320
- 置信度：25%
- NMS：45%
- 推理间隔：180ms
- 全类别显示：开启

## 编译方式

上传到 GitHub 后运行 Actions：`Android APK`。

Actions 会自动：

1. 下载 ncnn Android 预编译包。
2. 下载 `yolov8n.ncnn.bin` 模型权重。
3. 编译 Debug 和已 debug 签名的 Release APK。
4. 输出可直接 `adb install` 的 APK。

优先安装：

```bat
adb install -r VehicleInfoNcnn-debug-installable.apk
```

如果签名冲突：

```bat
adb uninstall com.jlxc.vehicleinfoncnn
adb install -r VehicleInfoNcnn-debug-installable.apk
```

## 模型文件

工程内已放入：

- `app/src/main/assets/yolov8n.ncnn.param`

大文件由 Actions 下载：

- `app/src/main/assets/yolov8n.ncnn.bin`

如果你想本地编译，先执行：

```bash
bash scripts/setup_deps.sh
```

## 后续可扩展方向

- 换成更强模型，例如 YOLOv8s / YOLO11n 的 NCNN 版本。
- 换成专门训练的车辆属性模型，例如车牌、车型、颜色、车标。
- 接入视频流、录屏流或 UVC 摄像头。
- 做悬浮窗版本，覆盖在倒车影像或车机桌面上。
