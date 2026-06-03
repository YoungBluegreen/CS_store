import os

# 邮件配置
MAIL_SERVER = 'stmp.qq.com'  # 邮件服务器地址
MAIL_PORT = 587
MAIL_USE_TLS = True
MAIL_USERNAME = '2182939168@qq.com'  # 发件邮箱
MAIL_PASSWORD = 'jshnvdhffkbxeagi'     # 邮箱密码或授权码
MAIL_DEFAULT_SENDER = '2182939168@qq.com'  # 默认发件人

# 接收邮箱
RECIPIENT_EMAIL = 'nikn0926@163.com'

# 文件夹路径配置
BASE_DIR = r'C:\Users\Administrator\Desktop\cloud api\SAM\output_masks'  # 要监控的文件夹路径
SENT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'sent')  # 已发送文件夹

# 确保文件夹存在
os.makedirs(SENT_DIR, exist_ok=True)
os.makedirs(BASE_DIR, exist_ok=True)