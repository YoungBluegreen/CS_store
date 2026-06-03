import os
from ftplib import FTP
import time
import cv2
import numpy as np
import shutil

# 配置信息
FTP_HOST = "47.97.196.173"
FTP_USER = "masks"
FTP_PASS = "nzPzmcj6nJFpXben"
REMOTE_DIR = "input"
LOCAL_DIR = r"F:\DJ\SAM\input_images"  # 最终监听目录
TEMP_DIR = r"F:\DJ\SAM\temp_download"  # 新增临时下载目录
MAX_SIZE_MB = 2.0  # 压缩阈值（MB）
COMPRESSION_QUALITY = 60  # JPEG压缩质量（0-100）


def compress_image(input_path, output_path):
    """压缩大图并保存到输出路径"""
    try:
        # 读取图像
        img = cv2.imread(input_path)
        if img is None:
            print(f"错误: 无法读取图像 {input_path}")
            return False

        # 获取原始文件大小
        orig_size = os.path.getsize(input_path) / (1024 * 1024)  # MB

        # 保存为JPEG格式
        cv2.imwrite(output_path, img, [int(cv2.IMWRITE_JPEG_QUALITY), COMPRESSION_QUALITY])

        # 计算压缩率
        comp_size = os.path.getsize(output_path) / (1024 * 1024)  # MB
        ratio = comp_size / orig_size

        print(f"压缩完成: {orig_size:.1f}MB → {comp_size:.1f}MB (比率: {ratio:.2f})")

        return True
    except Exception as e:
        print(f"压缩错误: {e}")
        return False


def ensure_directories():
    """确保所需目录存在"""
    os.makedirs(LOCAL_DIR, exist_ok=True)
    os.makedirs(TEMP_DIR, exist_ok=True)


def process_temp_files():
    """处理临时目录中的所有文件"""
    for filename in os.listdir(TEMP_DIR):
        temp_path = os.path.join(TEMP_DIR, filename)
        final_path = os.path.join(LOCAL_DIR, filename)

        # 只处理图像文件
        if not filename.lower().endswith(('.png', '.jpg', '.jpeg')):
            continue

        # 检查文件大小
        file_size = os.path.getsize(temp_path) / (1024 * 1024)  # MB

        if file_size > MAX_SIZE_MB:
            # 压缩大图
            print(f"压缩大图: {filename} ({file_size:.1f}MB)")
            compressed = compress_image(temp_path, final_path)

            if not compressed:
                # 压缩失败，直接移动文件
                shutil.move(temp_path, final_path)
                print(f"压缩失败，直接移动文件: {filename}")
        else:
            # 小文件直接移动
            shutil.move(temp_path, final_path)
            print(f"移动小文件: {filename} ({file_size:.1f}MB)")

        # 确保删除临时文件
        if os.path.exists(temp_path):
            os.remove(temp_path)


def download_new_images():
    ftp = FTP(FTP_HOST)
    ftp.login(user=FTP_USER, passwd=FTP_PASS)
    ftp.cwd(REMOTE_DIR)

    remote_files = set(ftp.nlst())
    local_files = set(os.listdir(LOCAL_DIR))
    new_files = remote_files - local_files
    downloaded_files = []

    # 确保临时目录为空
    for f in os.listdir(TEMP_DIR):
        os.remove(os.path.join(TEMP_DIR, f))

    # 下载新文件到临时目录
    for filename in new_files:
        if filename.lower().endswith(('.png', '.jpg', '.jpeg')):
            temp_path = os.path.join(TEMP_DIR, filename)

            try:
                # 下载文件到临时目录
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
    print(f"图像下载器已启动 (压缩阈值: {MAX_SIZE_MB}MB, 质量: {COMPRESSION_QUALITY})")
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