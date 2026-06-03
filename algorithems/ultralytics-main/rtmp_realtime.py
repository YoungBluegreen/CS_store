#!/usr/bin/env python3
# 优化的实时RTMP推流系统，支持低延迟推流到远程服务器
import cv2
import time
import subprocess
import threading
import numpy as np
from ultralytics import YOLO
import queue
import logging
import torch

# 配置参数
SRC = "rtmp://47.97.196.173/live/stream1"  # 拉流地址
DST = "rtmp://47.97.196.173/live/yolo_output"  # 推流到同一服务器
MODEL = "best3.pt"

# 优化参数
CACHE_FRAMES = 10  # 增大缓存帧数，提高流畅性（允许延迟）
OUT_FPS = 15  # 输出帧率，与推流端匹配
IMG_SIZE = 640  # 检测图像尺寸
CONFIDENCE = 0.4  # 检测置信度阈值
SKIP_FRAMES = 0  # 每处理1帧，跳过N帧（如设为1，则每2帧处理1帧）

# 日志配置
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)


class RTMPYOLOStreamer:
    def __init__(self):
        # 检查 CUDA 是否可用
        if torch.cuda.is_available():
            logger.info(f"使用 GPU: {torch.cuda.get_device_name(0)}")
            self.device = 0  # 使用第一张 GPU
        else:
            logger.warning("CUDA 不可用，将使用 CPU")
            self.device = 'cpu'

        # 初始化 YOLO 模型并指定设备
        self.model = YOLO(MODEL).to(self.device)
        self.frame_queue = queue.Queue(maxsize=CACHE_FRAMES * 2)
        self.result_queue = queue.Queue(maxsize=CACHE_FRAMES)
        self.running = True
        self.cap = None
        self.ffmpeg_proc = None
        self.frame_skip_counter = 0
        self.input_fps = OUT_FPS  # 默认输出帧率

    def start_capture(self):
        """启动视频捕获线程"""

        def capture_worker():
            last_time = time.time()
            frame_count = 0

            while self.running:
                try:
                    # 重新初始化视频捕获
                    if self.cap is None or not self.cap.isOpened():
                        logger.info(f"尝试连接源流: {SRC}")
                        self.cap = cv2.VideoCapture(SRC, cv2.CAP_FFMPEG)

                        # 设置缓冲区大小为1，减少延迟
                        self.cap.set(cv2.CAP_PROP_BUFFERSIZE, 1)

                        if not self.cap.isOpened():
                            logger.error(f"无法连接到源流: {SRC}")
                            time.sleep(2)
                            continue

                    ret, frame = self.cap.read()
                    if not ret or frame is None or frame.size == 0:
                        logger.warning("捕获到空帧或读取失败，尝试重新连接...")
                        if self.cap:
                            self.cap.release()
                        self.cap = None
                        time.sleep(1)
                        continue

                    # 计算输入帧率（仅用于调试）
                    current_time = time.time()
                    frame_count += 1
                    if current_time - last_time >= 1.0:
                        self.input_fps = frame_count
                        logger.info(f"输入帧率: {self.input_fps} FPS")
                        frame_count = 0
                        last_time = current_time

                    # 调整帧大小以提高处理速度
                    frame = cv2.resize(frame, (1280, 720))

                    # 跳帧逻辑：每隔N帧处理一次
                    self.frame_skip_counter += 1
                    if self.frame_skip_counter <= SKIP_FRAMES:
                        continue
                    else:
                        self.frame_skip_counter = 0  # 重置计数器

                    # 非阻塞放入队列
                    try:
                        self.frame_queue.put_nowait(frame)
                    except queue.Full:
                        # 如果队列满了，丢弃最旧的帧，保持流畅
                        try:
                            self.frame_queue.get_nowait()
                            self.frame_queue.put_nowait(frame)
                        except queue.Empty:
                            pass

                except Exception as e:
                    logger.error(f"捕获线程错误: {e}")
                    time.sleep(1)

        threading.Thread(target=capture_worker, daemon=True).start()

    def start_inference(self):
        """启动推理线程"""

        def inference_worker():
            while self.running:
                try:
                    frame = self.frame_queue.get(timeout=1)
                    if frame is None:
                        continue

                    # 执行YOLO检测（GPU加速）
                    results = self.model(frame, imgsz=IMG_SIZE, conf=CONFIDENCE, device=self.device)
                    annotated_frame = results[0].plot()

                    # 非阻塞放入结果队列
                    try:
                        self.result_queue.put_nowait(annotated_frame)
                    except queue.Full:
                        # 如果队列满了，丢弃旧结果以保持流畅
                        try:
                            self.result_queue.get_nowait()
                            self.result_queue.put_nowait(annotated_frame)
                        except queue.Empty:
                            pass

                except queue.Empty:
                    continue
                except Exception as e:
                    logger.error(f"推理线程错误: {e}")

        threading.Thread(target=inference_worker, daemon=True).start()

    def start_streaming(self):
        """启动流媒体推送"""

        def streaming_worker():
            # 等待队列填充
            while self.running and self.result_queue.qsize() < 2:
                logger.info("等待缓冲区填充...")
                time.sleep(0.5)

            if not self.running:
                return

            # 获取第一帧以获取尺寸信息
            try:
                first_frame = self.result_queue.get(timeout=2)
                h, w = first_frame.shape[:2]
                logger.info(f"开始推流，帧尺寸: {w}x{h}, 输出帧率: {OUT_FPS}")

                # FFmpeg命令 - 修复后的参数
                cmd = [
                    "ffmpeg",
                    "-y",
                    "-f", "rawvideo",
                    "-pix_fmt", "bgr24",
                    "-s", f"{w}x{h}",
                    "-r", str(OUT_FPS),
                    "-i", "-",
                    "-f", "lavfi",
                    "-i", "anullsrc=channel_layout=stereo:sample_rate=44100",  # 空音频
                    "-c:v", "libx264",
                    "-pix_fmt", "yuv420p",
                    "-preset", "ultrafast",
                    "-tune", "zerolatency",
                    "-b:v", "2000k",
                    "-g", str(OUT_FPS),
                    "-keyint_min", str(OUT_FPS // 2),
                    "-sc_threshold", "0",
                    "-c:a", "aac",  # 音频编码器
                    "-b:a", "128k",
                    "-f", "flv",
                    "-fflags", "+genpts",
                    "-max_muxing_queue_size", "1024",
                    DST
                ]

                # 启动FFmpeg进程
                self.ffmpeg_proc = subprocess.Popen(
                    cmd,
                    stdin=subprocess.PIPE,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    bufsize=1
                )

                # 启动一个线程打印 FFmpeg 的日志
                def log_ffmpeg():
                    for line in self.ffmpeg_proc.stderr:
                        print("[FFmpeg]", line.strip())

                threading.Thread(target=log_ffmpeg, daemon=True).start()

                logger.info(f"开始推流到: {DST}")

                # 计算帧间隔时间（基于输出帧率）
                frame_interval = 1.0 / OUT_FPS
                last_frame_time = time.time()

                # 先推送第一帧
                self.ffmpeg_proc.stdin.write(first_frame.tobytes())
                logger.info("已推送第一帧")

                frame_count = 0
                while self.running:
                    try:
                        # 获取下一帧
                        frame = self.result_queue.get(timeout=1)

                        # 控制帧率以保持稳定输出
                        current_time = time.time()
                        time_diff = current_time - last_frame_time
                        sleep_time = frame_interval - time_diff

                        if sleep_time > 0:
                            time.sleep(sleep_time)

                        last_frame_time = time.time()

                        # 推送帧到FFmpeg
                        self.ffmpeg_proc.stdin.write(frame.tobytes())
                        frame_count += 1

                        # 每秒打印一次推送帧率（调试用）
                        if frame_count % OUT_FPS == 0:
                            logger.info(f"已推送 {frame_count} 帧")

                    except queue.Empty:
                        continue
                    except BrokenPipeError:
                        logger.error("FFmpeg管道损坏，尝试重启...")
                        break
                    except Exception as e:
                        logger.error(f"推流错误: {e}")
                        break

            except Exception as e:
                logger.error(f"流媒体启动错误: {e}")

        threading.Thread(target=streaming_worker, daemon=True).start()

    def start(self):
        """启动整个流媒体系统"""
        logger.info("启动RTMP YOLO流媒体系统...")
        logger.info(f"从 {SRC} 拉流")
        logger.info(f"推流到 {DST}")

        self.start_capture()
        self.start_inference()
        self.start_streaming()

        try:
            while True:
                time.sleep(1)
        except KeyboardInterrupt:
            logger.info("接收到停止信号...")
            self.stop()

    def stop(self):
        """停止系统"""
        logger.info("停止RTMP YOLO流媒体系统...")
        self.running = False

        # 释放视频捕获
        if self.cap:
            self.cap.release()

        # 终止FFmpeg进程
        if self.ffmpeg_proc:
            try:
                self.ffmpeg_proc.stdin.close()
                self.ffmpeg_proc.terminate()
                self.ffmpeg_proc.wait(timeout=2)
            except subprocess.TimeoutExpired:
                self.ffmpeg_proc.kill()


if __name__ == "__main__":
    streamer = RTMPYOLOStreamer()
    streamer.start()
