import json
import pathlib
import subprocess
import sys
fixture = json.loads((pathlib.Path(__file__).parent / "../protocol_v2/fixtures/numeric-v2.json").read_text())
lines = subprocess.check_output([sys.argv[1], "emit"], text=True).splitlines()
assert len(lines) == 2
for line in lines:
    name, value = line.split("\t")
    assert fixture["wire"][name] == value, name
print("Session pages match existing golden wire fixtures")
