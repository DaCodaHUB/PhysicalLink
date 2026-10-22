"""Parse production serializer output using Python's standard JSON decoder."""

import json
import subprocess
import sys


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"Duplicate JSON key: {key}")
        result[key] = value
    return result


def main():
    output = subprocess.check_output([sys.argv[1], "--json-fixtures"])
    # Decode strictly; split lines only after control characters are escaped.
    documents = [json.loads(line.decode("utf-8"), object_pairs_hook=unique_object)
                 for line in output.splitlines()]
    escaped = "".join(chr(c) for c in range(1, 32)) + '"\\/é€🌡'
    first = {"id": "temperature", "name": "Temperature", "unit": "C", "type": "number"}
    expected = [
        {"schemaVersion": 1, "sensors": []},
        {"schemaVersion": 1, "sensors": [first]},
        {"schemaVersion": 1, "sensors": [
            first,
            {"id": "a.escaped", "name": escaped, "unit": '"\\\b\f\n\r\t', "type": "number"},
            {"id": "optional", "name": "optional", "unit": "", "type": "number"},
        ]},
        {"schemaVersion": 1, "sensors": [
            {"id": "a" * 31 + chr(ord("a") + i), "name": "\x01" * 64,
             "unit": "\x1f" * 16, "type": "number"}
            for i in range(16)
        ]},
    ]
    if documents != expected:
        raise AssertionError("Decoded capability metadata differs from registered inputs")
    print("Four JSON fixtures parsed; metadata, types, order and escaping preserved.")


if __name__ == "__main__":
    main()
