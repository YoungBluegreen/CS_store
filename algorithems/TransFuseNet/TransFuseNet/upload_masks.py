import os
from ftplib import FTP
import time
import shutil  # 新增模块用于删除文件夹

# 配置信息
FTP_HOST = "47.97.196.173"
FTP_USER = "masks"
FTP_PASS = "nzPzmcj6nJFpXben"
REMOTE_DIR = "output4"
LOCAL_DIR = r"F:\DJ\TransFuseNet\TransFuseNet\transfuse_results"
INPUT_IMAGES_DIR = r"F:\DJ\TransFuseNet\TransFuseNet\input_images"  # 新增输入目录路径


def upload_directory(ftp, local_path, remote_path):
    """递归上传整个目录"""
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

    # 获取远程已有的掩码文件夹
    remote_folders = set(ftp.nlst())
    uploaded_folders = []  # 记录成功上传的文件夹

    # 检查本地新生成的掩码文件夹
    for folder in os.listdir(LOCAL_DIR):
        local_folder = os.path.join(LOCAL_DIR, folder)

        if os.path.isdir(local_folder) and folder not in remote_folders:
            try:
                # 创建远程文件夹
                try:
                    ftp.mkd(folder)
                except:
                    pass

                # 上传整个文件夹
                upload_directory(ftp, local_folder, folder)
                print(f"Uploaded: {folder}")
                uploaded_folders.append(folder)

            except Exception as e:
                print(f"Error uploading {folder}: {e}")

    ftp.quit()
    return uploaded_folders  # 返回成功上传的文件夹列表


def delete_uploaded_items(uploaded_folders):
    """删除已上传的本地文件和文件夹"""
    for folder in uploaded_folders:
        # 删除output_masks中的文件夹
        mask_dir = os.path.join(LOCAL_DIR, folder)
        if os.path.exists(mask_dir):
            shutil.rmtree(mask_dir)
            print(f"Deleted mask folder: {mask_dir}")

        # 删除input_images中的原始图片
        image_path = find_image_file(folder)
        if image_path:
            os.remove(image_path)
            print(f"Deleted original image: {image_path}")


def find_image_file(folder_name):
    """在输入目录中查找对应的图片文件"""
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