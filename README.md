# VehicleInfoNcnn Rear AI v6

ncnn + YOLOv8n 的安卓后置 AI 视觉节点版本。适合把一台安卓手机安装在车辆后方，实时拍摄后视画面，并把 AI 识别结果和左右侧风险状态通过局域网传给车机桌面（例如 MikuCarLauncher）。

## 核心功能

- CameraX 实时预览与 ncnn YOLOv8n 推理
- 默认 1280×720 相机分析分辨率，适配 2560×720 车机半屏显示
- 荧光绿战斗机 HUD 风格识别框
- 左右侧风险判断：只用交通相关类别参与“是否有车”判断
- 左/右侧有车时，手机屏幕和 MJPEG 画面都会叠加红色警告框
- 设置里可进入“风险阈值线编辑”，直接拖动两条竖线调整左/右侧判定范围
- UDP 控制/状态 + HTTP MJPEG 视频流
- 多摄兼容：AUTO、指定 Camera ID、0.6x/0.7x/0.8x 广角倍率

## 端口与协议

手机端启动后会监听：

- UDP 控制端口：`47210`
- HTTP/MJPEG 端口：`47211`

HTTP 地址：

- `/stream`：MJPEG 视频流，固定 1280×720
- `/snapshot.jpg`：当前快照
- `/status`：当前 JSON 状态

UDP 指令：

- `TURN_LEFT`
- `TURN_RIGHT`
- `TURN_OFF`
- `STREAM_ON`
- `STREAM_OFF`
- `PING`

状态码：

- `0`：左右两侧都有车
- `1`：左侧有车
- `2`：右侧有车
- `3`：两侧安全/未检测到风险

## GitHub Actions 打包

上传到 GitHub 后运行 Actions，下载 `VehicleInfoNcnn-installable-apks`，优先安装：

```bat
adb install -r VehicleInfoNcnn-debug-installable.apk
```

如果签名冲突：

```bat
adb uninstall com.jlxc.vehicleinfoncnn
adb install -r VehicleInfoNcnn-debug-installable.apk
```

## 依赖下载

Actions 会自动下载：

- ncnn Android 预编译库
- `yolov8n.ncnn.bin` 模型权重

`assets` 中已经保留 `yolov8n.ncnn.param` 和模型说明。
