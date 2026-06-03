import time
import os
import cv2
import csv
import exifread
import rasterio
from pathlib import Path
from rasterio.warp import transform
from ultralytics.models import YOLO
from watchdog.observers import Observer
from watchdog.events import FileSystemEventHandler
from PIL import Image
import tempfile
import numpy as np

def project_to_wgs84(src, easting, northing):
    """将投影坐标转换为经纬度（WGS84）"""
    lon, lat = transform(src.crs, 'EPSG:4326', [easting], [northing])
    return lon[0], lat[0]

def get_lon_lat_from_geotiff_pixel(image_path, x_norm, y_norm):
    """
    根据归一化的像素坐标，计算 GeoTIFF 中的经纬度
    """
    with rasterio.open(image_path) as src:
        # 归一化转像素坐标
        x_px = x_norm * src.width
        y_px = y_norm * src.height
        # 像素坐标转地理坐标
        lon, lat = src.xy(y_px, x_px)  # 注意顺序：row, col
        return lon, lat

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
            # 读取图像并强制转换为 RGB
            img_np = load_image_as_bgr_uint8(image_path)
            # 传入 NumPy 数组，避免路径读取 4 通道
            results = self.model(source=img_np, stream=True)

            for r in results:
                csv_path = image_output_dir / f"{image_stem}.csv"
                with open(csv_path, 'w', newline='', encoding='utf-8-sig') as csvfile:
                    writer = csv.writer(csvfile)
                    writer.writerow(['ID', 'class', 'confidence',
                                     'longitude', 'latitude', 'area_m2'])

                    # 判断是否为有效 GeoTIFF
                    is_geotiff = False
                    try:
                        with rasterio.open(image_path) as src:
                            if src.crs is not None and src.transform is not None:
                                is_geotiff = True
                    except Exception:
                        pass

                    for idx, box in enumerate(r.boxes):
                        cls_id = int(box.cls[0].cpu().numpy())
                        cls_name = r.names[cls_id]
                        if isinstance(cls_name, bytes):
                            cls_name = cls_name.decode('utf-8')
                        conf = float(box.conf[0].cpu().numpy())

                        x_norm, y_norm, w_norm, h_norm = box.xywhn[0].cpu().numpy()

                        longitude, latitude, area_m2 = "", "", ""

                        if is_geotiff:
                            try:
                                with rasterio.open(image_path) as src:
                                    res_x = abs(src.transform[0])
                                    res_y = abs(src.transform[4])
                                    w_px = w_norm * src.width
                                    h_px = h_norm * src.height
                                    area_m2 = w_px * h_px * res_x * res_y

                                    x_px = x_norm * src.width
                                    y_px = y_norm * src.height
                                    easting, northing = src.xy(y_px, x_px)
                                    longitude, latitude = project_to_wgs84(
                                        src, easting, northing)
                            except Exception:
                                pass  # 下面统一兜底
                        else:
                            longitude, latitude = get_gps_from_image(image_path)

                        # 兜底：只要经纬度或面积为空，就填提示
                        if not longitude or not latitude:
                            longitude = latitude = "图像没有地理参考信息"
                        if not area_m2 and area_m2 != 0:  # 0 是有效面积
                            area_m2 = "图像没有地理参考信息"
                        if area_m2 >= 1000:
                            area_m2 = "面积过大，可能有误"

                        writer.writerow([
                            idx,
                            cls_name,
                            f"{conf:.4f}",
                            longitude if isinstance(longitude, str) else f"{longitude:.6f}",
                            latitude if isinstance(latitude, str) else f"{latitude:.6f}",
                            area_m2 if isinstance(area_m2, str) else f"{area_m2:.2f}"
                        ])

                # 画框保存略（保持不变）
                img = img_np.copy()
                colors = [(0, 255, 255), (0, 255, 0), (255, 0, 0), (255, 0, 255)]
                for idx, box in enumerate(r.boxes):
                    x1, y1, x2, y2 = map(int, box.xyxy[0].tolist())
                    color = colors[idx % len(colors)]
                    cv2.rectangle(img, (x1, y1), (x2, y2), color, 2)
                    label = f"ID:{idx}"
                    (tw, th), _ = cv2.getTextSize(label, cv2.FONT_HERSHEY_SIMPLEX, 2.0, 3)
                    tx, ty = x1, max(y1 - 5, th + 2)
                    overlay = img.copy()
                    cv2.rectangle(overlay, (tx - 2, ty - th - 2),
                                  (tx + tw + 2, ty + 2), (0, 0, 0), -1)
                    cv2.addWeighted(overlay, 0.4, img, 0.6, 0, img)
                    cv2.putText(img, label, (tx, ty), cv2.FONT_HERSHEY_SIMPLEX,
                                1.0, (255, 255, 255), 3, cv2.LINE_AA)
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

def load_image_as_bgr_uint8(path: Path) -> np.ndarray:
    import rasterio
    with rasterio.open(str(path)) as src:
        # 如果是多页/子数据集，只取第一页
        if src.subdatasets:
            src = rasterio.open(src.subdatasets[0])  # 第一页
        # 读 3 通道（不足补灰）
        if src.count == 1:
            r = g = b = src.read(1, out_dtype='float32')
        else:
            r = src.read(1, out_dtype='float32')
            g = src.read(2, out_dtype='float32') if src.count >= 2 else r
            b = src.read(3, out_dtype='float32') if src.count >= 3 else r
    rgb = np.stack([r, g, b], axis=-1)
    rgb = (rgb / rgb.max() * 255).clip(0, 255).astype(np.uint8)
    return cv2.cvtColor(rgb, cv2.COLOR_RGB2BGR)

if __name__ == "__main__":
    INPUT_DIR = "input_images"
    OUTPUT_DIR = "yolo_results"
    MODEL_PATH = "geotiff.pt"

    start_yolo_watcher(INPUT_DIR, OUTPUT_DIR, MODEL_PATH)