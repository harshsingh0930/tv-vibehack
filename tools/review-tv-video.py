"""Create a letterboxed contact sheet from a video; the source stays unchanged."""
import argparse
from pathlib import Path
import subprocess
import tempfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('source', type=Path)
parser.add_argument('output', type=Path)
args = parser.parse_args()
try:
    import imageio_ffmpeg
    from PIL import Image, ImageDraw, ImageOps
except ImportError:
    parser.error('Install optional video tools: python -m pip install -r tools/requirements-video.txt')
if not args.source.is_file():
    parser.error('The source video does not exist')
args.output.mkdir(parents=True, exist_ok=True)
with tempfile.TemporaryDirectory(prefix='tv-frames-', dir=args.output) as temp:
    frame_dir = Path(temp)
    subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), '-hide_banner', '-loglevel', 'error',
                    '-i', str(args.source), '-vf', 'fps=2,scale=480:-1', '-frames:v', '48',
                    str(frame_dir / 'frame-%03d.jpg')], check=True)
    frames = sorted(frame_dir.glob('frame-*.jpg'))
    if not frames:
        raise SystemExit('No video frames were decoded')
    canvas = Image.new('RGB', (480 * 4, 296 * ((len(frames) + 3) // 4)), '#171717')
    draw = ImageDraw.Draw(canvas)
    for i, path in enumerate(frames):
        with Image.open(path) as frame:
            thumb = ImageOps.contain(frame, (480, 270))
            x, y = i % 4 * 480, i // 4 * 296
            canvas.paste(thumb, (x + (480 - thumb.width) // 2, y + 22 + (270 - thumb.height) // 2))
            draw.text((x + 8, y + 4), f'{i / 2:.1f}s', fill='white')
    canvas.save(args.output / 'contact-sheet.jpg')
print(args.output / 'contact-sheet.jpg')
