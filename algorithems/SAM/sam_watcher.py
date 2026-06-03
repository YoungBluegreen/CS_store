import cv2
import numpy as np
import torch
import time
import os
from watchdog.observers import Observer
from watchdog.events import FileSystemEventHandler
from pathlib import Path
from segment_anything import sam_model_registry, SamAutomaticMaskGenerator


# 初始化 SAM
def init_sam(model_path="F:/DJ/SAM/checkpoints/sam_vit_b_01ec64.pth",
             model_type="vit_b"):
    device = "cuda" if torch.cuda.is_available() else "cpu"
    sam = sam_model_registry[model_type](checkpoint=model_path)
    sam.to(device=device)
    mask_generator = SamAutomaticMaskGenerator(sam)
    return mask_generator


# 分割图像并保存掩码
def segment_image(image_path, output_dir, mask_generator):
    image = cv2.imread(image_path)
    if image is None:
        print(f"Error: Could not read image {image_path}")
        return

    image = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
    masks = mask_generator.generate(image)

    # 为当前图片创建专属输出目录
    image_name = Path(image_path).stem
    image_output_dir = os.path.join(output_dir, image_name)
    os.makedirs(image_output_dir, exist_ok=True)

    # 合并所有掩码（只要出现一次白色就算前景）
    final_mask = None
    for mask in masks:
        m = mask["segmentation"].astype(np.uint8) * 255
        if final_mask is None:
            final_mask = m
        else:
            final_mask = cv2.bitwise_or(final_mask, m)

    # 保存合并后的掩码到同名文件夹内
    merged_path = os.path.join(image_output_dir, f"{image_name}_merged.png")
    cv2.imwrite(merged_path, final_mask)

    # 删除原单张掩码
    for f in Path(image_output_dir).glob("mask_*.png"):
        f.unlink()

    print(f"Segmented {image_path} -> merged mask saved to {merged_path}")


# 监听文件夹变化
class ImageHandler(FileSystemEventHandler):
    def __init__(self, mask_generator, input_dir, output_dir):
        self.mask_generator = mask_generator
        self.input_dir = input_dir
        self.output_dir = output_dir

    def on_created(self, event):
        if event.is_directory:
            return
        file_path = Path(event.src_path)
        if file_path.suffix.lower() in [".jpg", ".png", ".jpeg"]:
            time.sleep(1)  # 等待文件完全写入
            segment_image(str(file_path), self.output_dir, self.mask_generator)


def start_watching(input_dir, output_dir):
    mask_generator = init_sam()
    event_handler = ImageHandler(mask_generator, input_dir, output_dir)
    observer = Observer()
    observer.schedule(event_handler, input_dir, recursive=False)
    observer.start()
    print(f"Watching {input_dir} for new images... (Press Ctrl+C to stop)")
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        observer.stop()
    observer.join()


if __name__ == "__main__":
    input_dir = "input_images"  # 监听的文件夹
    output_dir = "output_masks"  # 分割结果保存路径

    # 创建目录（如果不存在）
    Path(input_dir).mkdir(exist_ok=True)
    Path(output_dir).mkdir(exist_ok=True)

    # 启动监听
    start_watching(input_dir, output_dir)