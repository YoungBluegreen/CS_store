import cv2
from ultralytics import YOLO


def yolo_pre():
    yolo = YOLO('/home/ahu/桌面/ultralytics-main/yolov8n.pt')
    video_path = 'C:\\Users\\25055\\Desktop\\测试2.mp4'  # 检测视频的地址
    cap = cv2.VideoCapture(video_path)  # 创建一个 VideoCapture 对象，用于从视频文件中读取帧
    # 获取视频帧的维度
    frame_width = int(cap.get(3))
    frame_height = int(cap.get(4))
    # 创建VideoWriter对象
    fourcc = cv2.VideoWriter_fourcc(*'mp4v')
    out = cv2.VideoWriter('C:\\Users\\25055\Desktop\\output.mp4', fourcc, 20.0,
                          (frame_width, frame_height))  # 保存检测后视频的地址

    while cap.isOpened():
        status, frame = cap.read()  # 使用 cap.read() 从视频中读取每一帧
        if not status:
            break
        result = yolo.predict(source=frame, save=True)
        result = result[0]
        anno_frame = result.plot()
        # cv2.imshow('行人', anno_frame)
        out.write(anno_frame)  # 写入保存
        # 注释的框架是通过调用 result.plot() 获得的，它会在框架上绘制边界框和标签。
        # 带注释的框架使用 cv2.imshow() 窗口名称“行人”显示。
        if cv2.waitKey(1) & 0xFF == ord('q'):
            break
    cap.release()
    cv2.destroyAllWindows()
    print('保存完成')
    video_yolo_path = 'C:\\Users\\25055\Desktop\\output.mp4'
    return video_yolo_path