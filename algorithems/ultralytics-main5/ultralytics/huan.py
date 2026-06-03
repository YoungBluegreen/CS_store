import os
import xml.etree.ElementTree as ET
import shutil
from tqdm import tqdm


def convert_voc_to_yolo(xml_dir, img_dir, output_dir, class_list):
    """
    将VOC格式数据集转换为YOLO格式

    参数:
        xml_dir: XML标注文件目录
        img_dir: 原始图片目录
        output_dir: 输出目录
        class_list: 类别列表
    """
    # 创建输出目录结构
    os.makedirs(os.path.join(output_dir, 'images'), exist_ok=True)
    os.makedirs(os.path.join(output_dir, 'labels'), exist_ok=True)

    # 类别到索引的映射
    class_to_idx = {name: idx for idx, name in enumerate(class_list)}

    # 处理每个XML文件
    for xml_file in tqdm(os.listdir(xml_dir)):
        if not xml_file.endswith('.xml'):
            continue

        # 解析XML文件
        xml_path = os.path.join(xml_dir, xml_file)
        tree = ET.parse(xml_path)
        root = tree.getroot()

        # 获取图片文件名
        img_filename = root.find('filename').text
        img_path = os.path.join(img_dir, img_filename)

        # 如果图片不存在则跳过
        if not os.path.exists(img_path):
            print(f"警告: 图片 {img_path} 不存在，跳过")
            continue

        # 获取图片尺寸
        size = root.find('size')
        img_width = int(size.find('width').text)
        img_height = int(size.find('height').text)

        # 准备输出TXT文件
        txt_filename = os.path.splitext(img_filename)[0] + '.txt'
        txt_path = os.path.join(output_dir, 'labels', txt_filename)

        with open(txt_path, 'w') as f:
            # 遍历所有object标签
            for obj in root.iter('object'):
                # 获取类别
                cls_name = obj.find('name').text
                if cls_name not in class_to_idx:
                    print(f"警告: 类别 '{cls_name}' 不在class_list中，跳过")
                    continue

                cls_id = class_to_idx[cls_name]

                # 获取边界框坐标
                bndbox = obj.find('bndbox')
                xmin = float(bndbox.find('xmin').text)
                ymin = float(bndbox.find('ymin').text)
                xmax = float(bndbox.find('xmax').text)
                ymax = float(bndbox.find('ymax').text)

                # 转换为YOLO格式
                x_center = (xmin + xmax) / 2 / img_width
                y_center = (ymin + ymax) / 2 / img_height
                width = (xmax - xmin) / img_width
                height = (ymax - ymin) / img_height

                # 写入TXT文件
                f.write(f"{cls_id} {x_center:.6f} {y_center:.6f} {width:.6f} {height:.6f}\n")

        # 复制图片到输出目录
        output_img_path = os.path.join(output_dir, 'images', img_filename)
        shutil.copy(img_path, output_img_path)

    print(f"转换完成! YOLO格式数据集已保存到: {output_dir}")
    print(f"类别映射: {class_to_idx}")


# 使用示例
convert_voc_to_yolo(
    xml_dir='/home/ahu/桌面/ultralytics-main/ultralytics/data/Annotations',
    img_dir='/home/ahu/桌面/ultralytics-main/ultralytics/data/images',
    output_dir='/home/ahu/桌面/ultralytics-main/ultralytics/data/Annotation',
    class_list=['bottle', 'plastic-bag', 'leaf', 'branch', 'milk-box', 'plastic-garbage', 'grass', 'ball']
)