1、环境准备：
python>=3.8,pytorch>=1.7 and torchvision>=0.8 
pip install  opencv-python watchdog segment-anything

2、 SAM 预训练模型
放在了checkpoints文件夹下目录

3、使用说明：监听input_images文件夹的更新，将结果写入output_masks文件夹，一张图片生成一个同名文件夹。我放了一张cat图片的实例。