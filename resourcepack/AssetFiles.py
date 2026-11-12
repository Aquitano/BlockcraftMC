"""Shared serialization for native Minecraft assets."""
import json
from pathlib import Path


def write_json(path: Path, value: object, *, compact: bool = False, ensure_ascii: bool = False) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    options = {'separators': (',', ':')} if compact else {'indent': 2}
    path.write_text(json.dumps(value, ensure_ascii=ensure_ascii, **options) + '\n', encoding='utf-8')
