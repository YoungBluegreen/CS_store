@echo off
REM 切换到项目目录
cd /d "F:\DJ\SAM"

REM 初始化conda环境（根据您的conda安装路径调整）
call "F:\Anaconda\Scripts\activate.bat"

REM 激活指定的conda环境
call activate sam

REM 启动三个程序（每个在单独窗口中运行）
start "Image Downloader" python "F:\DJ\SAM\download_images.py"
start "Mask Generator" python "F:\DJ\SAM\sam_watcher.py"
start "Result Uploader" python "F:\DJ\SAM\upload_masks.py"

REM 显示启动信息
echo All processes started successfully.
echo Keep this window open to maintain the environment.
echo Press Ctrl+C to terminate all processes.
pause