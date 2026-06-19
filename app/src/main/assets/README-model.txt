Model: YOLOv8n NCNN detection model from nihui/ncnn-android-yolov8 assets.
Source: https://github.com/nihui/ncnn-android-yolov8
Files expected by app:
- yolov8n.ncnn.param
- yolov8n.ncnn.bin

The large .bin file is downloaded by scripts/setup_deps.sh before build and then packaged into APK assets.

Overlay label style:
- Tactical 5x7 pixel lettering is rendered directly in OverlayView.java.
- No external/system font file is required.
