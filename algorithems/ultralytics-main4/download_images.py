import os
from ftplib import FTP
import time
import cv2
import numpy as np
import shutil
from PIL import Image

# 配置信息
FTP_HOST = "47.97.196.173"
FTP_USER = "masks"
FTP_PASS = "nzPzmcj6nJFpXben"
REMOTE_DIR = "input6"
LOCAL_DIR = r"F:\DJ\ultralytics-main4\input_images"
TEMP_DIR = r"F:\DJ\ultralytics-main4\temp_download"
MAX_SIZE_MB = 10  # 仅做日志提示，不再强制压缩
COMPRESSION_QUALITY = 70  # 保留，但不使用


def ensure_directories():
    os.makedirs(LOCAL_DIR, exist_ok=True)
    os.makedirs(TEMP_DIR, exist_ok=True)

def process_temp_files():
    """处理临时目录中的所有文件：通道转换 + 移动到最终目录"""
    for filename in os.listdir(TEMP_DIR):
        temp_path = os.path.join(TEMP_DIR, filename)
        final_path = os.path.join(LOCAL_DIR, filename)

        # 只处理图像文件
        if not filename.lower().endswith(('.png', '.jpg', '.jpeg', '.tif', '.tiff')):
            continue

        # 打印文件大小日志
        file_size = os.path.getsize(temp_path) / (1024 * 1024)
        print(f"移动文件: {filename} ({file_size:.1f}MB)")

        # 移动到最终目录
        shutil.move(temp_path, final_path)


def download_new_images():
    ftp = FTP(FTP_HOST)
    ftp.login(user=FTP_USER, passwd=FTP_PASS)
    ftp.cwd(REMOTE_DIR)

    remote_files = set(ftp.nlst())
    local_files = set(os.listdir(LOCAL_DIR))
    new_files = remote_files - local_files
    downloaded_files = []

    # 清空临时目录
    for f in os.listdir(TEMP_DIR):
        os.remove(os.path.join(TEMP_DIR, f))

    # 下载新文件
    for filename in new_files:
        if filename.lower().endswith(('.png', '.jpg', '.jpeg', '.tif', '.tiff')):
            temp_path = os.path.join(TEMP_DIR, filename)
            try:
                with open(temp_path, 'wb') as f:
                    ftp.retrbinary(f"RETR {filename}", f.write)
                print(f"下载完成: {filename}")
                downloaded_files.append(filename)
            except Exception as e:
                print(f"下载错误 {filename}: {e}")
                if os.path.exists(temp_path):
                    os.remove(temp_path)

    # 处理临时文件
    if downloaded_files:
        print(f"开始处理 {len(downloaded_files)} 个下载文件...")
        process_temp_files()

    # 删除远程服务器上的文件
    for filename in downloaded_files:
        try:
            ftp.delete(filename)
            print(f"删除远程文件: {filename}")
        except Exception as e:
            print(f"删除远程文件错误 {filename}: {e}")

    ftp.quit()


if __name__ == "__main__":
    print("图像下载器已启动（保留原始格式，4 通道 tif → 3 通道）")
    ensure_directories()

    while True:
        try:
            download_new_images()
            time.sleep(10)
        except Exception as e:
            print(f"错误: {e}")
            import traceback
            traceback.print_exc()
            time.sleep(60)