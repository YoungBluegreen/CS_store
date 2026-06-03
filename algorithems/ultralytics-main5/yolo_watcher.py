import time
import os
import cv2
import csv
import exifread
import rasterio
from pathlib import Path

from ultralytics.models import YOLO
from watchdog.observers import Observer
from watchdog.events import FileSystemEventHandler


def get_gps_from_image(image_path):
    """从图像 EXIF 中提取经纬度"""
    with open(image_path, 'rb') as f:
        tags = exifread.process_file(f)

    def convert_to_degrees(value):
        """将 GPS 度分秒转换为十进制度"""
        d, m, s = [float(x.num) / float(x.den) for x in value.values]
        return d + (m / 60.0) + (s / 3600.0)

    try:
        lat_ref = str(tags['GPS GPSLatitudeRef'])
        lat = convert_to_degrees(tags['GPS GPSLatitude'])
        if lat_ref != 'N':
            lat = -lat

        lon_ref = str(tags['GPS GPSLongitudeRef'])
        lon = convert_to_degrees(tags['GPS GPSLongitude'])
        if lon_ref != 'E':
            lon = -lon

        return lon, lat
    except KeyError:
        return None, None

def get_gsd_and_area_from_geotiff(image_path, box_xywh):
    """
    从 GeoTIFF 中提取像素分辨率，并计算目标面积（单位：m²）
    :param image_path: GeoTIFF 图像路径
    :param box_xywh: [x_center, y_center, width, height] 归一化坐标
    :return: 面积（m²）
    """
    with rasterio.open(image_path) as src:
        # 像素分辨率（单位：米/像素）
        res_x = abs(src.transform[0])  # 横向分辨率
        res_y = abs(src.transform[4])  # 纵向分辨率
        width_px = box_xywh[2] * src.width
        height_px = box_xywh[3] * src.height
        area_m2 = width_px * height_px * res_x * res_y
        return area_m2

class YOLODetectionHandler(FileSystemEventHandler):
    def __init__(self, model, input_dir, output_dir):
        self.model = model
        self.input_dir = input_dir
        self.output_dir = output_dir

    def on_created(self, event):
        """当新文件被创建时调用"""
        if event.is_directory:
            return

        file_path = Path(event.src_path)
        if file_path.suffix.lower() in [".jpg", ".png", ".jpeg", ".bmp", ".tif"]:
            print(f"检测到新图片: {file_path.name}")
            time.sleep(0.5)  # 等待文件完全写入
            self.process_image(file_path)

    def process_image(self, image_path):
        """使用YOLO模型处理单张图片"""
        try:
            image_stem = image_path.stem
            image_output_dir = Path(self.output_dir) / image_stem
            os.makedirs(image_output_dir, exist_ok=True)

            # 使用YOLO进行检测
            results = self.model(source=str(image_path), stream=True)

            for r in results:
                # 保存置信度表格
                csv_path = image_output_dir / f"{image_stem}.csv"
                with open(csv_path, 'w', newline='', encoding='utf-8') as csvfile:
                    writer = csv.writer(csvfile)
                    writer.writerow(['ID', 'class', 'confidence', 'lantitude', 'longitude', 'area_m2'])

                    # 判断是否为 GeoTIFF
                    is_geotiff = image_path.suffix.lower() == ".tif"

                    for idx, box in enumerate(r.boxes):
                        cls_id = int(box.cls[0])
                        cls_name = r.names[cls_id]
                        conf = float(box.conf[0])

                        longitude, latitude, area_m2 = "", "", ""

                        if is_geotiff:
                            try:
                                import rasterio
                                with rasterio.open(image_path) as src:
                                    res_x = abs(src.transform[0])
                                    res_y = abs(src.transform[4])
                                    w_px = float(box.xywhn[0][2]) * src.width
                                    h_px = float(box.xywhn[0][3]) * src.height
                                    area_m2 = w_px * h_px * res_x * res_y
                            except Exception as e:
                                print(f"GeoTIFF 面积计算失败: {e}")
                        else:
                            longitude, latitude = get_gps_from_image(image_path)

                        writer.writerow([
                            idx,
                            cls_name,
                            f"{conf:.4f}",
                            longitude if longitude else "",
                            latitude if latitude else "",
                            f"{area_m2:.2f}" if area_m2 else ""
                        ])

                # 获取带标注的图像
                img = cv2.imread(str(image_path))
                img = img.copy()

                colors = [(0, 255, 255), (0, 255, 0), (255, 0, 0), (255, 0, 255)]

                for idx, box in enumerate(r.boxes):
                    x1, y1, x2, y2 = box.xyxy[0].tolist()
                    x1, y1, x2, y2 = int(x1), int(y1), int(x2), int(y2)
                    color = colors[idx % len(colors)]

                    cv2.rectangle(img, (x1, y1), (x2, y2), color, 2)
                    label = f"ID:{idx}"
                    font = cv2.FONT_HERSHEY_SIMPLEX
                    font_scale = 1.0
                    thickness = 2
                    (tw, th), _ = cv2.getTextSize(label, font, font_scale, thickness)
                    tx, ty = x1, max(y1 - 5, th + 2)

                    overlay = img.copy()
                    cv2.rectangle(overlay, (tx - 2, ty - th - 2),
                                  (tx + tw + 2, ty + 2), (0, 0, 0), -1)
                    cv2.addWeighted(overlay, 0.4, img, 0.6, 0, img)
                    cv2.putText(img, label, (tx, ty), font, font_scale,
                                (255, 255, 255), thickness, cv2.LINE_AA)

                result_img_path = image_output_dir / f"{image_stem}.jpg"
                cv2.imwrite(str(result_img_path), img)

            print(f"处理完成: {image_path.name} -> 保存到 {image_output_dir}")
            return True
        except Exception as e:
            print(f"处理图片 {image_path} 时出错: {str(e)}")
            return False


def start_yolo_watcher(input_dir, output_dir, model_path="best.pt"):
    """启动YOLO检测监控"""
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
    INPUT_DIR = "input_images"
    OUTPUT_DIR = "yolo_results"
    MODEL_PATH = "jubu.pt"

    start_yolo_watcher(INPUT_DIR, OUTPUT_DIR, MODEL_PATH)