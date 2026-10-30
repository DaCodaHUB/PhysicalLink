"""Cross-language fixtures: stdlib JSON/struct oracle checks production C++ bytes."""
import json
import pathlib
import struct
import subprocess
import sys


def unique(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate key: " + key)
        result[key] = value
    return result


fixture = json.loads((pathlib.Path(__file__).resolve().parents[4] / "test-fixtures/protocol-v2/numeric-v2.json").read_text(),
                    object_pairs_hook=unique)
executable = sys.argv[1]
lines = subprocess.check_output([executable, "emit"], text=True).splitlines()
actual = dict(line.split("\t", 1) for line in lines)
assert len(actual) == len(lines)
assert actual == fixture["wire"], "C++ output differs from independent golden wire fixtures"
for mode, manifest in fixture["manifestInputs"].items():
    raw = bytes.fromhex(actual["manifest." + mode])
    parsed = json.loads(raw.decode("utf-8"), object_pairs_hook=unique)
    assert parsed == manifest
    assert list(parsed) == ["version", "deviceId", "device", "epoch", "sensors"]
    pages = [bytes.fromhex(actual[f"page.{mode}.{i}"])
             for i in range((len(raw) + 495) // 496)]
    for index, page in enumerate(pages):
        magic, version, page_id, count, reserved, size, epoch = struct.unpack("<2sBBBBHQ", page[:16])
        assert (magic, version, page_id, count, reserved, size, epoch) == (
            b"PL", 2, index, len(pages), 0, len(raw), int(parsed["epoch"], 16))
        assert len(page) == 16 + min(496, len(raw) - index * 496) <= 512
    assert b"".join(page[16:] for page in pages) == raw
    if mode.startswith("b"):
        assert len(raw) == int(mode[1:])
    if mode == "max":
        assert len(raw) == 8192 and len(pages) == 17
    if mode == "utf8":
        assert pages[0][-1] == 0xc3 and pages[1][16] == 0xa9
        try:
            pages[0][16:].decode("utf-8")
        except UnicodeDecodeError:
            pass
        else:
            raise AssertionError("Fixture must split a multibyte UTF-8 scalar")
for key, encoded in fixture["wire"].items():
    if key.startswith("record."):
        assert subprocess.run([executable, "check-record", encoded], check=False).returncode == 0
for name, encoded in fixture["invalidRecords"].items():
    assert subprocess.run([executable, "check-record", encoded], check=False).returncode == 1, name
for count in (1, 16):
    image = bytes.fromhex(actual[f"image.{count}"])
    assert len(image) == 16 + 20 * count <= 336
    assert struct.unpack("<BBHQI", image[:16]) == (0x21, count, 1, 0xb398ca802d871ae0, 0)
    for channel in range(count):
        assert struct.unpack("<BBBBIId", image[16 + 20 * channel:36 + 20 * channel]) == (
            0x20, channel, 0, 0, 1, 100-channel, channel+0.5)
print(f"Validated {len(actual)} golden wire vectors, strict JSON reconstruction and corrupt records")
