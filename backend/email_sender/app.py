from flask import Flask, jsonify
from flask_mail import Mail, Message
from config import MAIL_SERVER, MAIL_PORT, MAIL_USE_TLS, MAIL_USERNAME, MAIL_PASSWORD, RECIPIENT_EMAIL, BASE_DIR, \
    SENT_DIR
import os
from datetime import datetime
import threading
import time
from flask_cors import CORS

app = Flask(__name__)
CORS(app)

# 邮件配置
app.config['MAIL_SERVER'] = MAIL_SERVER
app.config['MAIL_PORT'] = MAIL_PORT
app.config['MAIL_USE_TLS'] = MAIL_USE_TLS
app.config['MAIL_USERNAME'] = MAIL_USERNAME
app.config['MAIL_PASSWORD'] = MAIL_PASSWORD

mail = Mail(app)


def find_latest_image_folder():
    """查找最新的包含图片的子文件夹"""
    folders = [f for f in os.listdir(BASE_DIR) if os.path.isdir(os.path.join(BASE_DIR, f))]
    if not folders:
        return None

    # 按修改时间排序获取最新文件夹
    folders.sort(key=lambda f: os.path.getmtime(os.path.join(BASE_DIR, f)), reverse=True)
    latest_folder = os.path.join(BASE_DIR, folders[0])

    # 获取所有图片文件
    image_files = []
    for ext in ['.jpg', '.jpeg', '.png', '.gif', '.bmp']:
        image_files.extend([f for f in os.listdir(latest_folder) if f.lower().endswith(ext)])

    return os.path.join(BASE_DIR, folders[0]), image_files


def send_email_with_attachments(folder_path, files):
    """发送带附件的邮件"""
    try:
        msg = Message(
            subject=f'图片自动发送 - {datetime.now().strftime("%Y-%m-%d %H:%M")}',
            recipients=[RECIPIENT_EMAIL],
            body='系统自动发送的图片附件，请查收。'
        )

        # 添加所有图片作为附件
        for file in files:
            with open(os.path.join(folder_path, file), 'rb') as fp:
                msg.attach(file, 'image/jpeg', fp.read())

        mail.send(msg)

        # 移动已发送文件夹
        folder_name = os.path.basename(folder_path)
        os.rename(folder_path, os.path.join(SENT_DIR, folder_name))

        return True, None
    except Exception as e:
        return False, str(e)


@app.route('/send-latest', methods=['GET'])
def send_latest():
    """API接口：发送最新图片"""
    result = find_latest_image_folder()

    if not result:
        return jsonify({'status': 'error', 'message': '未找到图片文件夹'}), 404

    folder_path, image_files = result

    if not image_files:
        return jsonify({'status': 'error', 'message': '文件夹中没有图片文件'}), 400

    success, error = send_email_with_attachments(folder_path, image_files)

    if success:
        return jsonify({
            'status': 'success',
            'message': '邮件发送成功',
            'folder': os.path.basename(folder_path),
            'images': image_files
        })
    else:
        return jsonify({
            'status': 'error',
            'message': f'发送失败: {error}',
            'folder': os.path.basename(folder_path)
        }), 500


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)