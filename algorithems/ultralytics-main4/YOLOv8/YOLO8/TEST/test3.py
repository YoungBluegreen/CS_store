import cv2  
from ultralytics import YOLO  
import os
 
current_dir = os.path.dirname(os.path.abspath(__file__))

# 加载模型  
model = YOLO('/home/ahu/桌面/ultralytics-main/runs/runs/train/C2f/weights/best.pt')
  
# 视频路径
video_path = os.path.join(current_dir, 'datasets', 'test.mp4')

if not os.path.exists(video_path):
    print(f"错误：找不到视频文件：{video_path}")
    exit()

# 读取视频
cap = cv2.VideoCapture(video_path)

# 获取原视频的帧率和尺寸
fps = int(cap.get(cv2.CAP_PROP_FPS))
width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))

# 创建视频写入对象
output_path = os.path.join(current_dir, 'results', 'result.mp4')
os.makedirs(os.path.dirname(output_path), exist_ok=True)
fourcc = cv2.VideoWriter_fourcc(*'mp4v')
out = cv2.VideoWriter(output_path, fourcc, fps, (width, height))

# 创建可调整大小的显示窗口
cv2.namedWindow('Detection Result', cv2.WINDOW_NORMAL)

while cap.isOpened():
    # 读取视频帧
    success, frame = cap.read()
    if not success:
        break
        
    # 进行目标检测
    results = model(frame)
    
    # 在帧上绘制检测结果
    for result in results:
        boxes = result.boxes
        for box in boxes:
            # 获取边界框坐标
            x1, y1, x2, y2 = box.xyxy[0]
            x1, y1, x2, y2 = int(x1), int(y1), int(x2), int(y2)
            
            # 绘制边界框
            cv2.rectangle(frame, (x1, y1), (x2, y2), (0, 255, 0), 2)
            
            # 添加类别标签和置信度
            conf = float(box.conf)
            cls = int(box.cls)
            label = f'{result.names[cls]} {conf:.2f}'
            cv2.putText(frame, label, (x1, y1 - 10), 
                       cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 255, 0), 2)
    
    # 写入处理后的帧
    out.write(frame)
    
    # 显示处理后的帧
    cv2.imshow('Detection Result', frame)
    
    # 按 'q' 键退出
    if cv2.waitKey(1) & 0xFF == ord('q'):
        break

# 释放资源
cap.release()
out.release()
cv2.destroyAllWindows()