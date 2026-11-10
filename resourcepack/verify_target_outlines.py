import argparse
from array import array
import ctypes
import json
import math
from pathlib import Path
import re
import struct
from zipfile import ZipFile

import moderngl


SIZE = 64
IDENTITY = [1.0 if index % 5 == 0 else 0.0 for index in range(16)]


class OutlineFixture:
    def __init__(self, client):
        self.client = client
        self.source = Path(__file__).resolve().parent / "src"
        self.context = moderngl.create_standalone_context(require=450, backend="egl")
        egl = ctypes.CDLL("libEGL.so.1")
        egl.eglGetProcAddress.restype = ctypes.c_void_p
        clip_control = ctypes.CFUNCTYPE(None, ctypes.c_uint, ctypes.c_uint)(egl.eglGetProcAddress(b"glClipControl"))
        clip_control(0x8CA1, 0x935F)
        self.programs = {}
        self.core = self.program("minecraft:core/rendertype_outline", "minecraft:core/rendertype_outline")
        self.core["Projection"].binding = 0
        self.projection = self.context.buffer(struct.pack("16f", *IDENTITY))
        self.projection.bind_to_uniform_block(0)
        self.core["DynamicTransforms"].binding = 1
        self.transforms = self.context.buffer(struct.pack("40f", *IDENTITY, *IDENTITY, 1, 1, 1, 1, 0, 0, 0, 0))
        self.transforms.bind_to_uniform_block(1)
        self.white = self.context.texture((1, 1), 4, bytes([255] * 4))
        self.white.use(0)
        self.core["Sampler0"] = 0
        self.depth_program = self.context.program(vertex_shader=self.shader("minecraft:core/rendertype_outline", ".vsh"),
                fragment_shader="#version 330\nlayout(location=0) out float depth; void main() { depth = gl_FragCoord.z; }")
        self.depth_program["Projection"].binding = 0
        self.depth_program["DynamicTransforms"].binding = 1
        self.chain = json.loads(self.read("assets/minecraft/post_effect/entity_outline.json"))

    def read(self, path):
        local = self.source / path
        return local.read_text() if local.is_file() else self.client.read(path).decode()

    def shader(self, name, extension):
        namespace, path = name.split(":", 1)
        text = self.read(f"assets/{namespace}/shaders/{path}{extension}")

        def include(match):
            namespace, path = match.group(1).split(":", 1)
            return self.shader(f"{namespace}:include/{path}", "")

        return re.sub(r"^\s*#include\s+<([^>]+)>\s*$", include, text, flags=re.MULTILINE).replace("gl_VertexIndex", "gl_VertexID")

    def program(self, vertex, fragment):
        key = vertex, fragment
        if key not in self.programs:
            self.programs[key] = self.context.program(vertex_shader=self.shader(vertex, ".vsh"), fragment_shader=self.shader(fragment, ".fsh"))
        return self.programs[key]

    def texture(self, components=4, dtype="f1"):
        texture = self.context.texture((SIZE, SIZE), components, dtype=dtype)
        texture.filter = moderngl.NEAREST, moderngl.NEAREST
        texture.repeat_x = False
        texture.repeat_y = False
        return texture

    def geometry(self, vertices, color, program):
        data = array("f", (value for vertex in vertices for value in (*vertex, *color, 1.0, 0.5, 0.5)))
        buffer = self.context.buffer(data.tobytes())
        vao = self.context.vertex_array(program, [(buffer, "3f 4f 2f", "Position", "Color", "UV0")], skip_errors=True)
        vao.render(moderngl.TRIANGLES)
        vao.release()
        buffer.release()

    def quad(self, rectangle, depth):
        x0, y0, x1, y1 = [value / SIZE * 2 - 1 for value in rectangle]
        z = depth
        return [(x0, y0, z), (x1, y0, z), (x1, y1, z), (x0, y0, z), (x1, y1, z), (x0, y1, z)]

    def render(self, vertices, color, walls=(), projection=IDENTITY):
        self.projection.write(struct.pack("16f", *projection))
        raw = self.texture()
        framebuffer = self.context.framebuffer([raw])
        framebuffer.use()
        framebuffer.clear()
        self.context.disable(moderngl.DEPTH_TEST | moderngl.CULL_FACE | moderngl.BLEND)
        self.white.use(0)
        self.geometry(vertices, color, self.core)
        scene = self.texture(1, "f4")
        depth = self.context.depth_renderbuffer((SIZE, SIZE))
        framebuffer = self.context.framebuffer([scene], depth)
        framebuffer.use()
        framebuffer.clear(depth=0)
        self.context.enable(moderngl.DEPTH_TEST)
        self.context.depth_func = ">="
        self.geometry(vertices, color, self.depth_program)
        for rectangle, wall_depth in walls:
            self.geometry(self.quad(rectangle, wall_depth), color, self.depth_program)
        self.context.disable(moderngl.DEPTH_TEST)
        return raw, scene

    def process(self, raw, scene, chain=None):
        chain = chain or self.chain
        targets = {"minecraft:entity_outline": raw, "minecraft:main": scene}
        targets.update({name: self.texture() for name in chain["targets"]})
        for stage in chain["passes"]:
            program = self.program(stage["vertex_shader"], stage["fragment_shader"])
            output = targets[stage["output"]]
            self.context.framebuffer([output]).use()
            buffers = []
            if "SamplerInfo" in program:
                values = [float(SIZE)] * (2 + len(stage["inputs"]) * 2)
                values += [0.0] * (-len(values) % 4)
                buffers.append(self.context.buffer(struct.pack(f"{len(values)}f", *values)))
                program["SamplerInfo"].binding = 2
                buffers[-1].bind_to_uniform_block(2)
            for index, entry in enumerate(stage["inputs"]):
                texture = targets[entry["target"]]
                texture.filter = (moderngl.LINEAR, moderngl.LINEAR) if entry.get("bilinear") else (moderngl.NEAREST, moderngl.NEAREST)
                texture.use(index)
                program[entry["sampler_name"] + "Sampler"] = index
            for index, (name, entries) in enumerate(stage.get("uniforms", {}).items(), start=3):
                values = [value for entry in entries for value in (entry["value"] if isinstance(entry["value"], list) else [entry["value"]])]
                values += [0.0] * (-len(values) % 4)
                buffers.append(self.context.buffer(struct.pack(f"{len(values)}f", *values)))
                program[name].binding = index
                buffers[-1].bind_to_uniform_block(index)
            self.context.vertex_array(program, []).render(moderngl.TRIANGLES, vertices=3)
            for buffer in buffers:
                buffer.release()
        return targets["minecraft:entity_outline"].read()


def alpha(image, x, y):
    return image[(y * SIZE + x) * 4 + 3]


def cube():
    points = []
    for x, y, z in ((-1, -1, -1), (1, -1, -1), (1, 1, -1), (-1, 1, -1),
                    (-1, -1, 1), (1, -1, 1), (1, 1, 1), (-1, 1, 1)):
        x, z = x * math.cos(0.5) + z * math.sin(0.5), -x * math.sin(0.5) + z * math.cos(0.5)
        y, z = y * math.cos(0.25) - z * math.sin(0.25), y * math.sin(0.25) + z * math.cos(0.25)
        points.append((x, y, z - 6))
    faces = ((4, 5, 6, 7), (1, 0, 3, 2), (5, 1, 2, 6), (0, 4, 7, 3), (7, 6, 2, 3), (0, 1, 5, 4))
    triangles = [[points[index] for index in triangle] for a, b, c, d in faces for triangle in ((a, b, c), (a, c, d))]

    def facing(triangle):
        a, b, c = [(x / -z, y / -z) for x, y, z in triangle]
        return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])

    triangles.sort(key=facing, reverse=True)
    projection = [0.0] * 16
    projection[0] = projection[5] = 2.5
    projection[11] = -1.0
    projection[14] = 0.05
    return [vertex for triangle in triangles for vertex in triangle], projection


def main():
    parser = argparse.ArgumentParser(description="Render outline occlusion fixtures with ModernGL and an EGL OpenGL context.")
    parser.add_argument("--client", type=Path, required=True, help="Minecraft 26.3 client JAR")
    args = parser.parse_args()
    with ZipFile(args.client) as client:
        fixture = OutlineFixture(client)
        cases = 0
        for distance in (1, 16, 160, 256):
            depth = 0.05 / distance
            vertices = fixture.quad((16, 16, 48, 48), depth)
            for color in ((0, 170 / 255, 170 / 255), (170 / 255, 0, 0)):
                raw, scene = fixture.render(vertices, color)
                visible = fixture.process(raw, scene)
                assert alpha(visible, 16, 32), f"Visible target missing at distance {distance}"
                assert not alpha(visible, 32, 32), "Interior pixels must remain clear"
                raw, scene = fixture.render(vertices, color, [((0, 0, SIZE, SIZE), 0.05 / (distance - 0.01))])
                hidden = fixture.process(raw, scene)
                assert not any(hidden[3::4]), f"Target leaked through 0.01-block cover at distance {distance}"
                raw, scene = fixture.render(vertices, color, [((0, 0, SIZE, 32), 0.05 / (distance - 0.01))])
                partial = fixture.process(raw, scene)
                assert alpha(partial, 16, 40), "Exposed silhouette disappeared"
                assert not any(alpha(partial, x, y) for y in range(32) for x in range(SIZE)), "Hidden pixels leaked"
                assert not alpha(partial, 32, 32), "Occlusion created an artificial edge across the body"
                raw, scene = fixture.render(vertices, color, [((15, 0, 16, SIZE), 0.05 / (distance - 0.01))])
                edge = fixture.process(raw, scene)
                assert alpha(edge, 16, 32), "Visible edge disappeared next to thin cover"
                assert not any(alpha(edge, 15, y) for y in range(SIZE)), "Outline dilation spilled over cover"
                cases += 4
        vertices, projection = cube()
        raw, scene = fixture.render(vertices, (0, 170 / 255, 170 / 255), projection=projection)
        silhouette = raw.read()
        rendered = fixture.process(raw, scene)
        edge_pixels = []
        for y in range(1, SIZE - 1):
            for x in range(1, SIZE - 1):
                occupied = alpha(silhouette, x, y) != 0
                edge = any(occupied != (alpha(silhouette, x + dx, y + dy) != 0)
                        for dx in range(-1, 2) for dy in range(-1, 2))
                if edge:
                    edge_pixels.append((x, y))
        assert edge_pixels, "Rotated cube did not render"
        assert all(alpha(rendered, x, y) for x, y in edge_pixels), "Back faces erased the visible cube outline"
        cases += 1
        vertices = fixture.quad((16, 16, 48, 48), 0.01)
        raw, scene = fixture.render(vertices, (1, 1, 1), [((0, 0, SIZE, SIZE), 0.02)])
        original = raw.read()
        vanilla = json.loads(client.read("assets/minecraft/post_effect/entity_outline.json"))
        expected = fixture.process(raw, scene, vanilla)
        raw.write(original)
        actual = fixture.process(raw, scene)
        assert actual == expected, "Unmarked vanilla glow changed"
        assert any(actual[3::4]), "Vanilla glow should retain its through-wall behavior"
        cases += 1
        print(f"Passed {cases} GPU outline fixtures on {fixture.context.info['GL_RENDERER']}.")


if __name__ == "__main__":
    main()
