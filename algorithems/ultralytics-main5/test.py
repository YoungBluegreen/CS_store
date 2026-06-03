from ultralytics import YOLO
yolo = YOLO("best3.pt", task="detect")
result = yolo(source="./ultralytics/assets/2231.jpeg", save=True)