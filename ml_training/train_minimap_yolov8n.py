"""Validate an annotated Wild Rift minimap dataset, train YOLOv8n, and export a model.
No screenshots or trained weights are bundled with this project.
"""
from pathlib import Path
import argparse
import json
import hashlib
import sys

CLASSES = ['aliado', 'inimigo', 'objetivo_ativo', 'onda_minions']


def validate_dataset(yaml_path):
    import yaml
    data = yaml.safe_load(yaml_path.read_text(encoding='utf-8'))
    names = data.get('names')
    if isinstance(names, dict):
        names = [names.get(i, names.get(str(i))) for i in range(4)]
    if names != CLASSES:
        raise ValueError('Classes must be exactly: ' + ', '.join(CLASSES))
    root = Path(data.get('path', '.')).expanduser()
    if not root.is_absolute():
        root = (yaml_path.parent / root).resolve()
    counts = {}
    split_hashes = {}
    for split in ['train', 'val']:
        folder = root / data.get(split, f'images/{split}')
        if not folder.is_dir():
            raise ValueError(f'Dataset missing: {folder}. Supply labeled minimap images; no model was trained.')
        images = [p for p in folder.rglob('*') if p.suffix.lower() in {'.png', '.jpg', '.jpeg'}]
        if not images:
            raise ValueError(f'No images in {folder}')
        split_hashes[split] = set()
        counts[split] = len(images)
        for image in images:
            split_hashes[split].add(hashlib.sha256(image.read_bytes()).hexdigest())
            relative = image.relative_to(root / 'images')
            label = root / 'labels' / relative.with_suffix('.txt')
            if not label.is_file():
                raise ValueError(f'Missing YOLO label file: {label}')
            for line in label.read_text().splitlines():
                fields = line.split()
                if len(fields) != 5:
                    raise ValueError(f'Invalid label row in {label}')
                category = int(fields[0])
                coords = list(map(float, fields[1:]))
                if category not in range(4) or not all(0 <= n <= 1 for n in coords) or coords[2] <= 0 or coords[3] <= 0:
                    raise ValueError(f'Invalid class or normalized box in {label}')
    if split_hashes['train'] & split_hashes['val']:
        raise ValueError('Training/validation images overlap; split by match before measuring accuracy.')
    data['path'] = str(root)
    return data, counts


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--data', type=Path, default=Path(__file__).with_name('dataset_minimap.yaml'))
    parser.add_argument('--device', default='cpu', help='cpu or a CUDA device index such as 0')
    parser.add_argument('--epochs', type=int, default=80)
    parser.add_argument('--batch', type=int, default=8)
    parser.add_argument('--check-only', action='store_true')
    parser.add_argument('--output', type=Path, default=Path(__file__).parent / 'runs')
    args = parser.parse_args()
    try:
        data, counts = validate_dataset(args.data.resolve())
    except (ValueError, FileNotFoundError, KeyError) as error:
        parser.exit(2, str(error) + '\n')
    print(json.dumps({'dataset': counts, 'classes': CLASSES}, ensure_ascii=False))
    if args.check_only:
        return
    import yaml
    from ultralytics import YOLO
    args.output.mkdir(parents=True, exist_ok=True)
    manifest = args.output / 'dataset-resolved.yaml'
    manifest.write_text(yaml.safe_dump(data, allow_unicode=True))
    model = YOLO('yolov8n.pt')
    model.train(data=str(manifest), epochs=args.epochs, imgsz=320, batch=args.batch,
                device=args.device, project=str(args.output), name='minimap', exist_ok=False)
    # Export the checkpoint selected on the validation split, not arbitrary pretrained weights.
    trained = YOLO(str(model.trainer.best))
    metrics = trained.val(data=str(manifest), imgsz=320, device=args.device)
    exported = trained.export(format='litert', imgsz=320, batch=1, quantize=8,
                               data=str(manifest), nms=False, device='cpu')
    metadata = {'schemaVersion': 1, 'classes': CLASSES, 'coordinates': 'normalized',
                'inputRange': 'zero_to_one', 'datasetImages': counts,
                'validationMetrics': {k: float(v) for k, v in metrics.results_dict.items()},
                'checkpointSha256': hashlib.sha256(Path(model.trainer.best).read_bytes()).hexdigest()}
    (args.output / 'minimap_model.json').write_text(json.dumps(metadata, indent=2), encoding='utf-8')
    print(f'Export result: {exported}')
    print('Validate the INT8 artifact on held-out match images and confirm its tensor contract and Android runtime compatibility.')
    print('Only then copy the chosen model and minimap_model.json to app/src/main/assets/.')


if __name__ == '__main__':
    main()
