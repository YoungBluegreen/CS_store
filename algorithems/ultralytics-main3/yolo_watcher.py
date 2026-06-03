import time
import os
import cv2
import csv
import exifread
from pathlib import Path
from datetime import datetime

from ultralytics.models import YOLO
from watchdog.observers import Observer
from watchdog.events import FileSystemEventHandler


def get_exif_data(image_path):
    """从图像 EXIF 中提取经纬度和拍摄时间"""
    with open(image_path, 'rb') as f:
        tags = exifread.process_file(f)

    def convert_to_degrees(value):
        d, m, s = [float(x.num) / float(x.den) for x in value.values]
        return d + (m / 60.0) + (s / 3600.0)

    lat, lon, timestamp = None, None, None

    try:
        lat_ref = str(tags['GPS GPSLatitudeRef'])
        lat = convert_to_degrees(tags['GPS GPSLatitude'])
        if lat_ref != 'N':
            lat = -lat

        lon_ref = str(tags['GPS GPSLongitudeRef'])
        lon = convert_to_degrees(tags['GPS GPSLongitude'])
        if lon_ref != 'E':
            lon = -lon
    except KeyError:
        pass

    try:
        date_str = str(tags['EXIF DateTimeOriginal'])
        timestamp = datetime.strptime(date_str, '%Y:%m:%d %H:%M:%S').strftime('%Y-%m-%d %H:%M:%S')
    except KeyError:
        pass

    return timestamp, lat, lon


class YOLODetectionHandler(FileSystemEventHandler):
    def __init__(self, model, input_dir, output_dir):
        self.model = model
        self.input_dir = input_dir
        self.output_dir = output_dir

    def on_created(self, event):
        if event.is_directory:
            return

        file_path = Path(event.src_path)
        if file_path.suffix.lower() in [".jpg", ".png", ".jpeg", ".bmp"]:
            print(f"检测到新图片: {file_path.name}")
            time.sleep(0.5)
            self.process_image(file_path)

    def process_image(self, image_path):
        try:
            image_stem = image_path.stem
            image_output_dir = Path(self.output_dir) / image_stem
            os.makedirs(image_output_dir, exist_ok=True)

            # 获取 EXIF 信息
            timestamp, lat, lon = get_exif_data(image_path)

            # 使用 YOLO 检测
            results = self.model(source=str(image_path), stream=True)

            for r in results:
                csv_path = image_output_dir / f"{image_stem}.csv"
                with open(csv_path, 'w', newline='', encoding='utf-8') as csvfile:
                    writer = csv.writer(csvfile)
                    # 写入图片信息
                    writer.writerow(['Image Timestamp', 'Latitude', 'Longitude'])
                    writer.writerow([timestamp or 'N/A', lat or 'N/A', lon or 'N/A'])
                    writer.writerow([])  # 空行
                    writer.writerow(['ID', 'Class', 'Confidence'])

                    # 写入检测信息
                    for idx, box in enumerate(r.boxes):
                        cls_id = int(box.cls[0])
                        cls_name = r.names[cls_id]
                        conf = float(box.conf[0])
                        writer.writerow([idx, cls_name, f"{conf:.4f}"])

                # 画框并添加水印
                img = cv2.imread(str(image_path))
                img = img.copy()
                colors = [(0, 255, 255), (0, 255, 0), (255, 0, 0), (255, 0, 255)]

                for idx, box in enumerate(r.boxes):
                    x1, y1, x2, y2 = map(int, box.xyxy[0])
                    color = colors[idx % len(colors)]
                    cv2.rectangle(img, (x1, y1), (x2, y2), color, 2)
                    label = f"{cls_name}--{idx}  {conf:.2f}"
                    cv2.putText(img, label, (x1, y1 - 10), cv2.FONT_HERSHEY_SIMPLEX, 0.9, color, 2, cv2.LINE_AA)

                # === 超大水印：经纬度 + 时间 ===
                if lat is not None and lon is not None:
                    watermark_text = f"Lat: {lat:.6f}, Lon: {lon:.6f}"
                    if timestamp:
                        watermark_text += f" | {timestamp}"
                else:
                    watermark_text = "No GPS or Time Data"

                font = cv2.FONT_HERSHEY_DUPLEX
                font_scale = 7.2  # 原来 1.8 的 2 倍（整体放大 3.6 倍）
                thickness = 16     # 同步加粗
                (w, h), _ = cv2.getTextSize(watermark_text, font, font_scale, thickness)

                img_h, img_w = img.shape[:2]
                margin = 80  # 边距也放大
                x1_bg = img_w - w - margin * 2
                y1_bg = img_h - h - margin * 2
                x2_bg = img_w - margin
                y2_bg = img_h - margin

                # 半透明黑色背景
                overlay = img.copy()
                cv2.rectangle(overlay, (x1_bg, y1_bg), (x2_bg, y2_bg), (0, 0, 0), -1)
                alpha = 0.7  # 背景稍暗一点，文字更突出
                cv2.addWeighted(overlay, alpha, img, 1 - alpha, 0, img)

                # 白色文字
                cv2.putText(img, watermark_text,
                            (x1_bg + margin // 2, y2_bg - margin // 2),
                            font, font_scale, (255, 255, 255), thickness, cv2.LINE_AA)

                result_img_path = image_output_dir / f"{image_stem}.jpg"
                cv2.imwrite(str(result_img_path), img)

            print(f"处理完成: {image_path.name} -> 保存到 {image_output_dir}")
            return True
        except Exception as e:
            print(f"处理图片 {image_path} 时出错: {str(e)}")
            return False


def start_yolo_watcher(input_dir, output_dir, model_path="best.pt"):
    Path(input_dir).mkdir(parents=True, exist_ok=True)
    Path(output_dir).mkdir(parents=True, exist_ok=True)

    print("正在加载YOLO模型...")
    yolo = YOLO(model_path, task="detect")

    event_handler = YOLODetectionHandler(yolo, input_dir, output_dir)
    observer = Observer()
    observer.schedule(event_handler, input_dir, recursive=False)
    observer.start()

    print(f"监控已启动，正在监视目录: {input_dir}")
    print(f"检测结果将保存到: {output_dir}")
    print("按 Ctrl+C 停止监控")

    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        observer.stop()
        print("\n监控已停止")
    observer.join()


if __name__ == "__main__":
    INPUT_DIR = "input_images"  # 待检测图片目录
    OUTPUT_DIR = "yolo_results"  # 检测结果保存目录
    MODEL_PATH = "best3.pt"  # YOLO 模型路径

    start_yolo_watcher(INPUT_DIR, OUTPUT_DIR, MODEL_PATH)