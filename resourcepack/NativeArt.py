"""Shared cuboid construction, UVs and software previews for scenery."""
import json
import math

import numpy as np
from PIL import Image
from ModelSurfaces import exposed
from AssetFiles import write_json

MATERIALS = ("ivory", "steel", "dark", "copper", "vent", "service", "screen", "keys", "cyan", "amber", "hazard", "scar", "bolt", "coil", "socket", "seam")
COLORS = ("c9cdc3", "596c76", "25323b", "aa8164", "344650", "aebbb6", "163b45", "324955", "73d9d5", "edb76a", "bc995b", "433b39", "8d9da0", "6b8b93", "1e2931", "41545e")
DIRECTIONS = ("south", "north", "east", "west", "up", "down")


def uv(material):
    i = MATERIALS.index(material)
    return [i % 4 * 4, i // 4 * 4, i % 4 * 4 + 4, i // 4 * 4 + 4]


def box(name, low, high, material="steel", *, front=None, top=None, turn=None, glow=False, density=1):
    element = {"name": name, "from": list(low), "to": list(high), "faces": {
        side: {"uv": uv(front if side == "south" and front else top if side == "up" and top else material), "texture": "#atlas"}
        for side in DIRECTIONS}}
    dx,dy,dz=[b-a for a,b in zip(low,high)]
    dimensions={'north':(dx,dy),'south':(dx,dy),'east':(dz,dy),'west':(dz,dy),'up':(dx,dz),'down':(dx,dz)}
    for side,face in element['faces'].items():
        paint=front if side=='south' and front else top if side=='up' and top else material
        if paint in ('steel','ivory','copper','dark'):
            u,v,U,V=face['uv']; w,h=dimensions[side]
            inset_u=(4-min(16,w*density)/4)/2
            inset_v=(4-min(16,h*density)/4)/2
            face['uv']=[u+inset_u,v+inset_v,U-inset_u,V-inset_v]
    if turn:
        origin, axis, angle = turn
        element["rotation"] = {"origin": origin, "axis": axis, "angle": angle, "rescale": False}
    if glow:
        element["light_emission"] = 12
        element["shade"] = False
    return element


def corners(element, side):
    a, b = element["from"], element["to"]
    x, y, z = a
    X, Y, Z = b
    points = dict(zip(DIRECTIONS, (
        [(x,Y,Z),(X,Y,Z),(X,y,Z),(x,y,Z)], [(X,Y,z),(x,Y,z),(x,y,z),(X,y,z)],
        [(X,Y,Z),(X,Y,z),(X,y,z),(X,y,Z)], [(x,Y,z),(x,Y,Z),(x,y,Z),(x,y,z)],
        [(x,Y,z),(X,Y,z),(X,Y,Z),(x,Y,Z)], [(x,y,Z),(X,y,Z),(X,y,z),(x,y,z)])))[side]
    p = np.array(points, dtype=float)
    if r := element.get("rotation"):
        axis = "xyz".index(r["axis"])
        i, j = ((1, 2), (2, 0), (0, 1))[axis]
        v = p - r["origin"]
        c, s = math.cos(math.radians(r["angle"])), math.sin(math.radians(r["angle"]))
        p[:, i] = r["origin"][i] + v[:, i] * c - v[:, j] * s
        p[:, j] = r["origin"][j] + v[:, i] * s + v[:, j] * c
    return p


def remove_hidden(elements):
    return exposed(elements)[0]


def resolve(path, model_dir):
    d = json.loads(path.read_text())
    if parent := d.get("parent"):
        base = resolve(model_dir / (parent.split("/")[-1] + ".json"), model_dir)
        return base | d | {"textures": base.get("textures", {}) | d.get("textures", {})}
    return d


def render(model, texture, path, az=.65, size=1000):
    el = .55
    right = np.array([math.cos(az), 0, -math.sin(az)])
    up = np.array([-math.sin(az)*math.sin(el), math.cos(el), -math.cos(az)*math.sin(el)])
    toward = np.cross(right, up)
    basis = np.array([right, -up, toward]).T
    image = np.full((size, size, 3), [24, 34, 43], dtype=np.uint8)
    depth = np.full((size, size), -np.inf)
    tex = np.array(texture)
    for e in model["elements"]:
        for side, face in e["faces"].items():
            xyz = corners(e, side)
            normal = np.cross(xyz[1] - xyz[0], xyz[3] - xyz[0])
            normal /= np.linalg.norm(normal)
            # Corner winding is inward; rasterize the outward-facing side only.
            if normal @ toward >= 0:
                continue
            p = xyz @ basis
            p[:, :2] = (p[:, :2] - np.array([8, 18, 8]) @ basis[:, :2]) * (size / 43) + [size / 2, size * .52]
            u, v, U, V = np.array(face["uv"]) * 4
            coords = np.array([(u,v),(U,v),(U,V),(u,V)])
            shade = 1 if e.get("light_emission", 0) else .66 + .30 * max(0, -normal @ np.array([.3,.8,.52]))
            for tri in ((0,1,2),(0,2,3)):
                q, t = p[list(tri)], coords[list(tri)]
                a, b, c = q[:, :2]
                den = (b-a)[0]*(c-a)[1] - (b-a)[1]*(c-a)[0]
                if abs(den) < 1e-8:
                    continue
                lo = np.maximum(np.floor(q[:, :2].min(0)).astype(int), 0)
                hi = np.minimum(np.ceil(q[:, :2].max(0)).astype(int), size - 1)
                X, Y = np.meshgrid(np.arange(lo[0],hi[0]+1)+.5, np.arange(lo[1],hi[1]+1)+.5)
                r = np.stack([X,Y], -1) - a
                wb = (r[...,0]*(c-a)[1]-r[...,1]*(c-a)[0])/den
                wc = ((b-a)[0]*r[...,1]-(b-a)[1]*r[...,0])/den
                wa = 1-wb-wc
                z = wa*q[0,2]+wb*q[1,2]+wc*q[2,2]
                tx = wa[...,None]*t[0]+wb[...,None]*t[1]+wc[...,None]*t[2]
                color = tex[np.clip(tx[...,1].astype(int), int(v), int(V)-1), np.clip(tx[...,0].astype(int), int(u), int(U)-1)]
                sl = np.s_[lo[1]:hi[1]+1,lo[0]:hi[0]+1]
                mask = (wa>=-1e-7)&(wb>=-1e-7)&(wc>=-1e-7)&(z>depth[sl])
                image[sl][mask] = (color * shade).astype(np.uint8)[mask]
                depth[sl][mask] = z[mask]
    Image.fromarray(image).save(path)
