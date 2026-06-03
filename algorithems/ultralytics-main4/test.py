from ultralytics import YOLO
yolo = YOLO("geotiff.pt", task="detect")
result = yolo(source="./ultralytics/assets/11441.tif", save=True)