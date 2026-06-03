from ultralytics import YOLO
yolo = YOLO("best2.pt", task="detect")
result = yolo(source="./ultralytics/assets/0002.jpg", save=True)