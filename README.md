# VehicleInfoNcnn v5 compat

基于 ncnn + YOLOv8n NCNN 的安卓实时目标识别 Demo。该版本在 v4.1 safe 的基础上重点增强多设备相机兼容性，继续保留荧光绿战斗机 HUD 风格。

## v5 主要更新

- 新增多摄兼容策略：CameraX 绑定失败会按 `指定 Camera ID -> 默认后摄 -> 默认前摄` 自动降级。
- 新增广角倍率模式：`OFF/1.0x`、`0.6x`、`0.7x`、`0.8x`。
- Android 11+ 会尝试通过 Camera2 `CONTROL_ZOOM_RATIO` 设置 0.6x/0.7x/0.8x，适配“系统相机通过 0.6x 触发超广角”的手机。
- 新增相机分析分辨率：`640x480`、`960x540`、`1280x720`、`AUTO`。
- 新增兼容预览模式开关：老机、魔改系统、多摄异常时建议打开。
- 启动默认仍使用 AUTO 默认后摄，避免隐藏物理镜头导致闪退。
- 保留设置中的 Camera ID 列表，可手动尝试 CAM-4 这类隐藏/辅助镜头。
- 保留全屏沉浸式、隐藏状态栏和虚拟键。
- 保留全类别识别、交通工具过滤、GPU/Vulkan 开关、HUD Overlay。

## 针对你这台设备的建议

根据你导出的 `media_camera_dump.txt`，系统相机广角状态显示：

- Camera ID: 4
- zoomRatio: 0.6
- focalLength: 1.65mm

因此 v5 推荐先这样测试：

1. 摄像头选择：`AUTO DEFAULT BACK`
2. 广角倍率：`0.6x`
3. 相机分析分辨率：`960x540` 或 `1280x720`
4. 兼容预览模式：先打开

如果 AUTO + 0.6x 没有广角效果，再试：

1. 摄像头选择：`CAM-4`
2. 广角倍率：`OFF/1.0x` 或 `0.6x`
3. 如果黑屏/失败，会自动回退默认后摄

## 构建

上传到 GitHub 后运行 Actions：

- Workflow: `Android APK`
- 下载 artifact: `VehicleInfoNcnn-installable-apks`
- 优先安装：`VehicleInfoNcnn-debug-installable.apk`

```bat
adb install -r VehicleInfoNcnn-debug-installable.apk
```

签名冲突：

```bat
adb uninstall com.jlxc.vehicleinfoncnn
adb install -r VehicleInfoNcnn-debug-installable.apk
```

## 日志

如果某个相机模式黑屏或崩溃，导出：

```bat
adb logcat -c
adb shell am start -n com.jlxc.vehicleinfoncnn/.MainActivity
adb logcat -d -v threadtime > vehicle_ncnn_v5_log.txt
adb shell dumpsys media.camera > media_camera_dump.txt
```
