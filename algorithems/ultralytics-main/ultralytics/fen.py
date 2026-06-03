import os
import shutil

# 定义源文件夹路径（存放所有图像和标签）
source_images_folder = "/home/ahu/桌面/ultralytics-main/ultralytics/data/Annotation/images"  # 替换为你的图像文件夹路径
source_labels_folder = "/home/ahu/桌面/ultralytics-main/ultralytics/data/Annotation/labels"  # 替换为你的标签文件夹路径

# 定义目标文件夹路径
target_folder = "/home/ahu/桌面/ultralytics-main/ultralytics/data/piao"  # 最终的文件夹结构将存放在这个路径下

# 定义 train.txt, valid.txt, test.txt 文件的路径
train_txt_path = "/home/ahu/桌面/ultralytics-main/ultralytics/data/ImageSets/train.txt"  # 替换为 train.txt 文件的路径
valid_txt_path = "/home/ahu/桌面/ultralytics-main/ultralytics/data/ImageSets/valid.txt"  # 替换为 valid.txt 文件的路径
test_txt_path = "/home/ahu/桌面/ultralytics-main/ultralytics/data/ImageSets/test.txt"    # 替换为 test.txt 文件的路径



# 创建目标文件夹结构
os.makedirs(os.path.join(target_folder, "train/images"), exist_ok=True)
os.makedirs(os.path.join(target_folder, "train/labels"), exist_ok=True)
os.makedirs(os.path.join(target_folder, "valid/images"), exist_ok=True)
os.makedirs(os.path.join(target_folder, "valid/labels"), exist_ok=True)
os.makedirs(os.path.join(target_folder, "test/images"), exist_ok=True)
os.makedirs(os.path.join(target_folder, "test/labels"), exist_ok=True)

# 定义划分函数
def copy_files(file_list, source_images, source_labels, target_images, target_labels):
    for line in file_list:
        file_name = line.strip()  # 假设文件名不包含后缀
        image_name = file_name + ".jpg"  # 图像文件后缀为 .jpg
        label_name = file_name + ".txt"  # 标签文件后缀为 .txt

        image_path = os.path.join(source_images, image_name)
        label_path = os.path.join(source_labels, label_name)

        # 检查文件是否存在
        if not os.path.exists(image_path):
            print(f"Warning: Image file not found: {image_path}")
            continue
        if not os.path.exists(label_path):
            print(f"Warning: Label file not found: {label_path}")
            continue

        shutil.copy(image_path, os.path.join(target_images, image_name))
        shutil.copy(label_path, os.path.join(target_labels, label_name))

# 读取 train.txt, valid.txt, test.txt 文件
def process_split_file(file_path):
    with open(file_path, "r") as f:
        file_list = f.readlines()
    return file_list

# 处理 train 文件
train_file_list = process_split_file(train_txt_path)
copy_files(train_file_list, source_images_folder, source_labels_folder,
           os.path.join(target_folder, "train/images"),
           os.path.join(target_folder, "train/labels"))

# 处理 valid 文件
valid_file_list = process_split_file(valid_txt_path)
copy_files(valid_file_list, source_images_folder, source_labels_folder,
           os.path.join(target_folder, "valid/images"),
           os.path.join(target_folder, "valid/labels"))

# 处理 test 文件
test_file_list = process_split_file(test_txt_path)
copy_files(test_file_list, source_images_folder, source_labels_folder,
           os.path.join(target_folder, "test/images"),
           os.path.join(target_folder, "test/labels"))

print("文件划分完成！")