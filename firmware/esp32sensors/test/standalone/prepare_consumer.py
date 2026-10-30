"""Create an independent PlatformIO project installing a copied library archive.

Run from any directory. Then: pio run -d .pio/standalone-consumer
The generated project uses only its own source/config/library, with dependencies
resolved from PhysicalLink/library.json. No symlinks or repository include paths.
"""
from pathlib import Path
import shutil
import zipfile
import argparse

firmware = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--output', type=Path, default=firmware / '.pio' / 'standalone-consumer')
consumer = parser.parse_args().output.resolve()
consumer.mkdir(parents=True, exist_ok=True)
(consumer / 'src').mkdir(exist_ok=True)
shutil.copyfile(Path(__file__).with_name('platformio.ini'), consumer / 'platformio.ini')
shutil.copyfile(Path(__file__).with_name('main.cpp'), consumer / 'src' / 'main.cpp')
library = firmware / 'lib' / 'PhysicalLink'
with zipfile.ZipFile(consumer / 'PhysicalLink.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
    for path in library.rglob('*'):
        if path.is_file():
            archive.write(path, path.relative_to(library).as_posix())
print(consumer)
