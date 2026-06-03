import os
import time
import cv2
import numpy as np
import torch
from PIL import Image
from pathlib import Path
from watchdog.observers import Observer
from watchdog.events import FileSystemEventHandler

from datasets.dataset_synapse import Synapse_dataset
from networks.vit_seg_modeling_OUR import VisionTransformer as ViT_seg
from networks.vit_seg_modeling_OUR import CONFIGS as CONFIGS_ViT_seg
from utilsOUR import test_single_volume
from torchvision import transforms

# ================= 配置 =================
INPUT_DIR = "input_images"
OUTPUT_DIR = "transfuse_results"
MODEL_PATH = r"F:\DJ\TransFuseNet\TransFuseNet\model\TU_Synapse224\transmission\epoch_99.pth"
MAX_SIZE = 2048  # 图片最大尺寸，超过则压缩
PATCH_SIZE = 224  # 分割 patch 大小
OVERLAP = 0  # 不重叠
NUM_CLASSES = 6
VIT_NAME = "R50-ViT-B_16"
# ======================================

# 加载模型
config = CONFIGS_ViT_seg[VIT_NAME]
config.n_classes = NUM_CLASSES
config.n_skip = 3
config.patches.size = (16, 16)
config.patches.grid = (PATCH_SIZE // 16, PATCH_SIZE // 16)
model = ViT_seg(config, img_size=PATCH_SIZE, num_classes=NUM_CLASSES).cuda()
model.load_state_dict(torch.load(MODEL_PATH))
model.eval()

# 图像预处理器
transform = transforms.Compose([
    transforms.Resize((PATCH_SIZE, PATCH_SIZE)),
    transforms.ToTensor()
])

def compress_image(image_path, max_size=MAX_SIZE):
    img = Image.open(image_path).convert("RGB")
    w, h = img.size
    if max(w, h) > max_size:
        scale = max_size / max(w, h)
        new_size = (int(w * scale), int(h * scale))
        img = img.resize(new_size, Image.BILINEAR)
    return np.array(img)

def split_image(img_np, patch_size=PATCH_SIZE, overlap=OVERLAP):
    h, w, _ = img_np.shape
    patches = []
    coords = []

    step = patch_size - overlap
    for y in range(0, h, step):
        for x in range(0, w, step):
            x_end = min(x + patch_size, w)
            y_end = min(y + patch_size, h)
            patch = img_np[y:y_end, x:x_end]
            if patch.shape[0] < patch_size or patch.shape[1] < patch_size:
                pad = ((0, patch_size - patch.shape[0]), (0, patch_size - patch.shape[1]), (0, 0))
                patch = np.pad(patch, pad, mode='constant', constant_values=0)
            patches.append(patch)
            coords.append((x, y))
    return patches, coords, img_np.shape

def merge_patches(patches, coords, original_shape, patch_size=PATCH_SIZE):
    h, w, _ = original_shape
    merged = np.zeros((h, w, 3), dtype=np.uint8)
    for patch, (x, y) in zip(patches, coords):
        ph, pw, _ = patch.shape
        ph = min(ph, h - y)
        pw = min(pw, w - x)
        merged[y:y+ph, x:x+pw] = patch[:ph, :pw]
    return merged

class TransFuseHandler(FileSystemEventHandler):
    def __init__(self, model, input_dir, output_dir):
        self.model = model
        self.input_dir = input_dir
        self.output_dir = output_dir

    def on_created(self, event):
        if event.is_directory:
            return
        file_path = Path(event.src_path)
        if file_path.suffix.lower() in [".jpg", ".png", ".jpeg", ".bmp"]:
            print(f"[+] 检测到新图片: {file_path.name}")
            time.sleep(0.5)
            self.process_image(file_path)

    def process_image(self, image_path):
        try:
            image_np = compress_image(image_path)
            patches, coords, orig_shape = split_image(image_np)

            result_patches = []
            for patch in patches:
                patch_tensor = torch.from_numpy(patch).permute(2, 0, 1).unsqueeze(0).float().cuda() / 255.0
                with torch.no_grad():
                    out = torch.argmax(torch.softmax(self.model(patch_tensor), dim=1), dim=1).squeeze(0).cpu().numpy()
                colored = self.colorize(out)
                result_patches.append(colored)

            merged = merge_patches(result_patches, coords, orig_shape)

            # ✅ 修改为：创建与图片同名的文件夹，并将结果保存在其中
            output_folder = Path(self.output_dir) / image_path.stem
            output_folder.mkdir(parents=True, exist_ok=True)
            output_path = output_folder / f"{image_path.stem}.png"

            Image.fromarray(merged).save(output_path)
            print(f"[✓] 分割完成: {output_path}")

        except Exception as e:
            print(f"[✗] 处理失败: {e}")

    def colorize(self, mask):
        colors = {
            0: [255, 255, 255],
            1: [255, 0, 0],
            2: [255, 255, 0],
            3: [0, 255, 0],
            4: [0, 255, 255],
            5: [0, 0, 255],
        }
        h, w = mask.shape
        img = np.zeros((h, w, 3), dtype=np.uint8)
        for cls, color in colors.items():
            img[mask == cls] = color
        return img

def start_transfuse_watcher(input_dir, output_dir):
    Path(input_dir).mkdir(parents=True, exist_ok=True)
    Path(output_dir).mkdir(parents=True, exist_ok=True)

    handler = TransFuseHandler(model, input_dir, output_dir)
    observer = Observer()
    observer.schedule(handler, input_dir, recursive=False)
    observer.start()

    print(f"[监控启动] 输入目录: {input_dir}")
    print(f"[监控启动] 输出目录: {output_dir}")
    print("按 Ctrl+C 停止监控")

    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        observer.stop()
        print("\n监控已停止")
    observer.join()

if __name__ == "__main__":
    start_transfuse_watcher(INPUT_DIR, OUTPUT_DIR)