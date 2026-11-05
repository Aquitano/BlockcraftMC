import argparse
import itertools
import json
from pathlib import Path
import re
import subprocess
import tempfile
from zipfile import ZipFile


def variants():
    yield "minecraft:core/rendertype_outline", "minecraft:core/rendertype_outline", {}
    phases = [{}]
    for count in (4, 8, 16):
        for flags in ("OIT_ALPHA_ONLY OIT_DEPTH_BOUNDS", "OIT_ALPHA_ONLY OIT_TRANSMITTANCE",
                      "OIT_ACCUMULATE", "OIT_ACCUMULATE OIT_ADDITIVE", "OIT_ALPHA_ONLY OIT_ADDITIVE"):
            phases.append({"OIT": "", "OIT_COEFF_COUNT": str(count),
                           "OIT_COEFF_ATTACHMENT_COUNT": str(count // 4),
                           "OIT_WAVELET_RANK": str(count.bit_length() - 2), **dict.fromkeys(flags.split(), "")})
    for cutout, glint, phase in itertools.product(({}, {"ALPHA_CUTOUT": "0.1"}),
            ({}, {"GLINT": ""}, {"GLINT": "", "GLINT_SPECIAL": ""}), phases):
        yield "minecraft:core/item", "minecraft:core/item", {**cutout, **glint, **phase}
    for view, grayscale, phase in itertools.product(({}, {"IS_GUI": ""}, {"IS_SEE_THROUGH": ""}),
            ({}, {"IS_GRAYSCALE": ""}), phases):
        yield "minecraft:core/text", "minecraft:core/text", {**view, **grayscale, **phase}


def main():
    parser = argparse.ArgumentParser(description="Compile and link resource-pack shaders against a matching vanilla client.")
    parser.add_argument("--client", type=Path, required=True, help="Client JAR or extracted directory containing assets/")
    parser.add_argument("--glslang", default="glslangValidator", help="Path to glslangValidator; no automatic installation")
    args = parser.parse_args()
    source = Path(__file__).resolve().parent / "src"
    client = ZipFile(args.client) if args.client.is_file() else None

    def read(relative):
        local = source / relative
        if local.is_file():
            return local.read_text()
        return client.read(relative).decode() if client else (args.client / relative).read_text()

    def shader(name, suffix, including=()):
        namespace, path = name.split(":", 1)
        relative = f"assets/{namespace}/shaders/{path}{suffix}"
        if relative in including:
            raise ValueError(f"Recursive shader include: {relative}")

        def include(match):
            namespace, path = match.group(1).split(":", 1)
            return shader(f"{namespace}:include/{path}", "", (*including, relative))

        return re.sub(r"^\s*#include\s+<([^>]+)>\s*$", include, read(relative), flags=re.MULTILINE)

    cases = list(variants())
    post_pairs = set()
    for chain in (source / "assets").glob("*/post_effect/**/*.json"):
        for stage in json.loads(chain.read_text())["passes"]:
            post_pairs.add((stage["vertex_shader"], stage["fragment_shader"]))
    cases.extend((vertex, fragment, {}) for vertex, fragment in sorted(post_pairs))

    try:
        with tempfile.TemporaryDirectory(prefix="blockcraft-shaders-") as temporary:
            for index, (vertex, fragment, defines) in enumerate(cases):
                paths = []
                for name, extension, stage in ((vertex, ".vsh", "vert"), (fragment, ".fsh", "frag")):
                    lines = shader(name, extension).splitlines()
                    # RenderPearl's vertex-index spelling maps to the OpenGL builtin for this validator.
                    macros = {"gl_VertexIndex": "gl_VertexID", **defines}
                    lines[1:1] = [f"#define {key} {value}" for key, value in macros.items()]
                    target = Path(temporary) / f"{index}.{stage}"
                    target.write_text("\n".join(lines) + "\n")
                    paths.append(str(target))
                result = subprocess.run([args.glslang, "-l", *paths], capture_output=True, text=True)
                output = result.stdout + result.stderr
                if result.returncode or "WARNING:" in output:
                    raise SystemExit(f"{vertex} + {fragment} {defines}\n{output}")
    finally:
        if client:
            client.close()
    print(f"Compiled and linked {len(cases)} shader variants without warnings ({len(post_pairs)} post-process pairs).")


if __name__ == "__main__":
    main()
