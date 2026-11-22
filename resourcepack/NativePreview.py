"""Render finished native model JSON and PNGs without the retired GLB converters."""
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image
from NativeArt import corners


def resource(pack: Path, identifier: str, kind: str, suffix: str) -> Path:
    namespace, separator, route = identifier.partition(':')
    if not separator:
        namespace, route = 'minecraft', namespace
    path = pack / 'assets' / namespace / kind / (route + suffix)
    if not path.resolve().is_relative_to((pack / 'assets').resolve()):
        raise ValueError(f'Invalid asset identifier: {identifier}')
    return path


def resolved(pack: Path, identifier: str, visiting: frozenset[str] = frozenset()) -> dict:
    if identifier in visiting:
        raise ValueError(f'Circular model parent: {identifier}')
    model = json.loads(resource(pack, identifier, 'models', '.json').read_text())
    if parent := model.get('parent'):
        base = resolved(pack, parent, visiting | {identifier})
        return base | model | {'textures': base.get('textures', {}) | model.get('textures', {})}
    return model


def preview(pack: Path, identifier: str, output: Path, yaw: float = 37) -> None:
    model = resolved(pack, identifier)
    textures, materials = [], {}
    meshes = []
    for element in model['elements']:
        for side, face in element['faces'].items():
            if face.get('texture') is None:
                continue
            texture = face['texture']
            visited = set()
            while texture.startswith('#'):
                if texture in visited:
                    raise ValueError(f'Circular texture variable: {texture}')
                visited.add(texture)
                texture = model['textures'][texture[1:]]
            if texture not in materials:
                path = resource(pack, texture, 'textures', '.png')
                with Image.open(path) as image:
                    image = image.convert('RGBA')
                metadata = path.with_suffix('.png.mcmeta')
                if metadata.exists():
                    animation = json.loads(metadata.read_text()).get('animation', {})
                    width = animation.get('width', image.width)
                    height = animation.get('height', width)
                    image = image.crop((0, 0, width, height))
                materials[texture] = len(textures)
                textures.append(image)
            rotation = element.get('rotation', {})
            if 'x' in rotation:
                points = corners(element | {'rotation': None}, side)
                center = np.array(rotation['origin'])
                points -= center
                for axis in 'xyz':
                    i, j = {'x': (1, 2), 'y': (2, 0), 'z': (0, 1)}[axis]
                    c, s = math.cos(math.radians(rotation[axis])), math.sin(math.radians(rotation[axis]))
                    a, b = points[:, i].copy(), points[:, j].copy()
                    points[:, i], points[:, j] = a*c-b*s, a*s+b*c
                points += center
            else:
                points = corners(element, side)
            u, v, U, V = np.array(face['uv']) / 16
            uv = np.roll(np.array([(u,v), (U,v), (U,V), (u,V)]), face.get('rotation', 0) // 90, axis=0)
            meshes.append((points, uv, np.array([0,1,2,2,3,0]), materials[texture]))
    if not meshes:
        raise ValueError(f'Model has no visible faces: {identifier}')
    output.parent.mkdir(parents=True, exist_ok=True)
    raster(meshes, textures, output, az=math.radians(yaw))


def raster(meshes, textures, path, size=720, az=0.65):
    el = 0.55
    right = np.array([np.cos(az), 0, -np.sin(az)])
    up = np.array([-np.sin(az) * np.sin(el), np.cos(el), -np.cos(az) * np.sin(el)])
    toward = np.cross(right, up)
    basis = np.array([right, -up, toward]).T
    allp = np.concatenate([m[0] for m in meshes]) @ basis
    lo = allp[:, :2].min(0)
    hi = allp[:, :2].max(0)
    scale = (size - 40) / max(hi - lo)
    center = (hi + lo) / 2
    out = np.full((size, size, 4), [35, 39, 47, 255], dtype=np.uint8)
    depth = np.full((size, size), -np.inf)
    for xyz, uv, ids, mat in meshes:
        p = xyz @ basis
        p[:, :2] = (p[:, :2] - center) * scale + size / 2
        tex = np.array(textures[mat])
        h, w = tex.shape[:2]
        for tri in ids.reshape(-1, 3):
            q = p[tri]
            a, b, c = q[:, :2]
            den = (b - a)[0] * (c - a)[1] - (b - a)[1] * (c - a)[0]
            if abs(den) < 1e-08:
                continue
            low = np.maximum(np.floor(q[:, :2].min(0)).astype(int), 0)
            high = np.minimum(np.ceil(q[:, :2].max(0)).astype(int), size - 1)
            X, Y = np.meshgrid(np.arange(low[0], high[0] + 1) + 0.5, np.arange(low[1], high[1] + 1) + 0.5)
            r = np.stack([X, Y], -1) - a
            vb = (r[..., 0] * (c - a)[1] - r[..., 1] * (c - a)[0]) / den
            vc = ((b - a)[0] * r[..., 1] - (b - a)[1] * r[..., 0]) / den
            va = 1 - vb - vc
            z = va * q[0, 2] + vb * q[1, 2] + vc * q[2, 2]
            t = va[..., None] * uv[tri[0]] + vb[..., None] * uv[tri[1]] + vc[..., None] * uv[tri[2]]
            colors = tex[np.clip((t[..., 1] * h).astype(int), 0, h - 1), np.clip((t[..., 0] * w).astype(int), 0, w - 1)].copy()
            normal = np.cross(xyz[tri[1]] - xyz[tri[0]], xyz[tri[2]] - xyz[tri[0]])
            normal /= max(np.linalg.norm(normal), 1e-08)
            shade = 0.65 + 0.35 * abs(normal @ np.array([0.3, 0.8, 0.52]))
            colors[:, :, :3] = (colors[:, :, :3] * shade).astype(np.uint8)
            sl = np.s_[low[1]:high[1] + 1, low[0]:high[0] + 1]
            mask = (va >= -1e-06) & (vb >= -1e-06) & (vc >= -1e-06) & (z > depth[sl]) & (colors[:, :, 3] > 127)
            out[sl][mask] = colors[mask]
            depth[sl][mask] = z[mask]
    Image.fromarray(out).save(path)
