"""
Script de Treinamento & Exportação TFLite INT8 — Minimapa Wild Rift (Fase 3)
Classes:
  0: aliado
  1: inimigo
  2: objetivo_ativo
  3: onda_minions
"""
from ultralytics import YOLO

def train_and_export():
    model = YOLO("yolov8n.pt")
    model.train(
        data="dataset_minimap.yaml",
        epochs=80,
        imgsz=320,
        batch=32,
        device=0,
        project="wr_minimap_detector",
        name="yolov8n_320"
    )
    model.export(
        format="tflite",
        imgsz=320,
        int8=True
    )
    print("Modelo exportado! Copie o arquivo .tflite para: app/src/main/assets/yolov8n_wr_minimap_int8.tflite")

if __name__ == "__main__":
    train_and_export()
