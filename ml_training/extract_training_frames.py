"""Extract review frames from exported MP4 recordings. Requires ffmpeg; creates no class labels."""
import argparse
import json
import subprocess
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('video', type=Path)
    parser.add_argument('--every', type=float, default=5, help='Seconds between frames')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.every <= 0 or not args.video.is_file():
        parser.error('Supply an existing video and a positive sampling interval.')
    if args.output.exists() and any(args.output.iterdir()):
        parser.error('Use an empty output folder to avoid mixing sessions or overwriting frames.')
    args.output.mkdir(parents=True, exist_ok=True)
    subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-n', '-i', str(args.video),
                    '-vf', f'fps=1/{args.every}', '-start_number', '0', str(args.output / 'frame_%06d.png')], check=True)
    frames = sorted(args.output.glob('frame_*.png'))
    (args.output / 'extraction.json').write_text(json.dumps({
        'schemaVersion': 1, 'sourceVideo': args.video.name, 'sampleIntervalSeconds': args.every,
        'annotationsAreGroundTruth': False,
        'frames': [{'file': f.name, 'approximateVideoTimeSeconds': i * args.every} for i, f in enumerate(frames)]
    }, indent=2))
    print(f'{len(frames)} frames extracted for review/annotation. Keep segments from one match in the same dataset split.')


if __name__ == '__main__':
    main()
