import os
from ftplib import FTP
import time
import shutil

# 配置信息
FTP_HOST = "47.97.196.173"
FTP_USER = "masks"
FTP_PASS = "nzPzmcj6nJFpXben"
REMOTE_DIR = "output7"
LOCAL_DIR = r"F:\DJ\ultralytics-main5\yolo_results"
INPUT_IMAGES_DIR = r"F:\DJ\ultralytics-main5\input_images"


def upload_directory(ftp, local_path, remote_path):
    """递归上传整个目录（含 CSV）"""
    for name in os.listdir(local_path):
        local = os.path.join(local_path, name)
        remote = f"{remote_path}/{name}"

        if os.path.isfile(local):
            with open(local, 'rb') as f:
                ftp.storbinary(f"STOR {remote}", f)
        else:
            try:
                ftp.mkd(remote)
            except:
                pass
            upload_directory(ftp, local, remote)


def upload_new_masks():
    ftp = FTP(FTP_HOST)
    ftp.login(user=FTP_USER, passwd=FTP_PASS)
    ftp.cwd(REMOTE_DIR)

    remote_folders = set(ftp.nlst())
    uploaded_folders = []

    for folder in os.listdir(LOCAL_DIR):
        local_folder = os.path.join(LOCAL_DIR, folder)

        if os.path.isdir(local_folder) and folder not in remote_folders:
            try:
                try:
                    ftp.mkd(folder)
                except:
                    pass

                upload_directory(ftp, local_folder, folder)
                print(f"Uploaded: {folder}")
                uploaded_folders.append(folder)

            except Exception as e:
                print(f"Error uploading {folder}: {e}")

    ftp.quit()
    return uploaded_folders


def delete_uploaded_items(uploaded_folders):
    """删除已上传的本地文件夹、CSV 及原图"""
    for folder in uploaded_folders:
        # 删除 yolo_results 中的整个文件夹（含 CSV 与 JPG）
        mask_dir = os.path.join(LOCAL_DIR, folder)
        if os.path.exists(mask_dir):
            shutil.rmtree(mask_dir)
            print(f"Deleted mask folder: {mask_dir}")

        # 删除 input_images 中的原始图片
        image_path = find_image_file(folder)
        if image_path:
            os.remove(image_path)
            print(f"Deleted original image: {image_path}")

        # 额外删除可能存在的 CSV 文件（若放在与图片同级目录）
        csv_path = os.path.join(LOCAL_DIR, folder + ".csv")
        if os.path.exists(csv_path):
            os.remove(csv_path)
            print(f"Deleted CSV: {csv_path}")


def find_image_file(folder_name):
    for ext in ['.jpg', '.jpeg', '.png']:
        image_path = os.path.join(INPUT_IMAGES_DIR, folder_name + ext)
        if os.path.exists(image_path):
            return image_path
    return None


if __name__ == "__main__":
    while True:
        try:
            uploaded = upload_new_masks()
            if uploaded:
                delete_uploaded_items(uploaded)
            time.sleep(10)
        except Exception as e:
            print(f"Error: {e}")
            time.sleep(60)