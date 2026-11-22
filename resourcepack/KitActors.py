"""Original utility droids and desert wildlife; native cubes, BetterModel rigs and CPU previews."""
import argparse
import base64
import hashlib
import io
import json
from AssetFiles import write_json as write
import math
import uuid
from pathlib import Path

import numpy as np
import yaml
from PIL import Image, ImageDraw
from KitPixels import tile
from ModelSurfaces import SIDES as SURFACE_SIDES, cropped_uv, exposed, overlaps
from FurnitureCollision import actor as physical_actor
from SceneryLights import configure as scenery_lights

ROOT = Path(__file__).resolve().parent
PREFIX = 'sternenfront/kits/actors'
SIDES = ('north', 'south', 'east', 'west', 'up', 'down')
PALETTES = {
    'utility': ['c7c7b4', '70878a', '275969', '263239', 'd49d43', '91b9ba', '3a4549', 'a16741',
                'd4d2bb', '526267', '274b55', 'acb4ac', '1c292e', '6bb7bd', 'b7a17a', '7d6d58'],
    'desert': ['70503a', '997047', '48382d', 'c7ad7c', '657157', '8a9472', '3b4d3c', 'a39b76',
               '867462', 'b39b80', '50493e', '382c24', 'c3b29a', '7b4c31', '252c25', 'a37a50'],
}
NAMES = {'astromech': 'Astromech', 'gonk': 'Energiedroide', 'mouse': 'Mausdroide',
         'bantha': 'Bantha', 'dewback': 'Dewback', 'womp_rat': 'Wompratte'}


def stable(*parts):
    return str(uuid.uuid5(uuid.UUID('f3a062cf-d573-45cd-a1ba-bbb14356680d'), '/'.join(parts)))


def atlas(family):
    im = Image.new('RGB', (64, 64))
    roles = ('enamel', 'steel', 'panel', 'dark', 'light', 'steel', 'vent', 'steel', 'enamel', 'panel', 'coil', 'enamel', 'socket', 'screen', 'plaster', 'leather') if family == 'utility' else ('fur','fur','fur','horn','scale','scale','scale','horn','fur','fur','leather','dark','plaster','leather','socket','fur')
    for i, color in enumerate(PALETTES[family]):
        x, y = i % 4 * 16, i // 4 * 16
        im.paste(tile(color, roles[i], seed=i+71), (x,y))
    return im


class Actor:
    def __init__(self, name, family):
        self.name, self.family = name, family
        self.bones = {'root': {'pivot': [0, 0, 0], 'parent': None}}
        self.cubes = []
        self.anchors = {'ground': [0, 0, 0]}

    def bone(self, name, pivot, parent='root'):
        self.bones[name] = {'pivot': list(pivot), 'parent': parent}
        return name

    def box(self, name, low, high, material, bone='body'):
        self.cubes.append({'name': name, 'from': list(low), 'to': list(high), 'material': material, 'bone': bone})


def actors():
    a = Actor('astromech', 'utility')
    a.bone('body', (0, 8, 0)); a.bone('head', (0, 16, 0), 'body')
    a.box('barrel', (-5, 4, -4), (5, 16, 4), 0)
    a.box('barrel_sides', (-4, 4, -5), (4, 16, 5), 0)
    a.box('dome_rim', (-5, 16, -5), (5, 18, 5), 1, 'head')
    a.box('dome_step', (-4, 18, -4), (4, 20, 4), 11, 'head')
    a.box('dome_cap', (-2.5, 20, -2.5), (2.5, 21, 2.5), 1, 'head')
    a.box('optic_band', (-3.5, 18, -4.2), (3.5, 19.5, -4), 2, 'head')
    a.box('optic', (-2.5, 18.2, -4.8), (-.5, 19.6, -4.2), 12, 'head')
    a.box('status', (1.5, 16.8, -5.3), (3, 17.6, -5), 13, 'head')
    for x in (-3, 1):
        a.box('service_panel', (x, 7, -5.2), (x+2, 13, -5), 2)
    a.box('port', (-1, 5, -5.3), (1, 7, -5), 3)
    for side, x in (('left', -7), ('right', 7)):
        b = a.bone('leg_'+side, (x, 13, 0))
        a.box('shoulder_'+side, (x-1.5, 11, -2), (x+1.5, 15, 2), 1, b)
        a.box('strut_'+side, (x-1, 2, -1), (x+1, 12, 1), 0, b)
        a.box('foot_'+side, (x-2, 0, -4), (x+2, 3, 3), 1, b)
    a.bone('caster', (0, 4, 0)); a.box('caster', (-1.5, 0, -2), (1.5, 4, 2), 6, 'caster')
    a.anchors['service'] = [0, 10, -5.3]
    yield a
    a = Actor('gonk', 'utility'); a.bone('body', (0, 9, 0))
    a.box('lower_shell', (-6, 7, -5), (6, 15, 5), 7)
    a.box('upper_shell', (-6.5, 15, -5.5), (6.5, 20, 5.5), 14)
    a.box('lid', (-6, 20, -5), (6, 21, 5), 9)
    a.box('belt', (-6.3, 13, -5.3), (6.3, 14, 5.3), 3)
    a.box('front_grille', (-4, 9, -5.4), (4, 12.5, -5), 6)
    a.box('socket', (-2.5, 16, -5.8), (.5, 18.5, -5.5), 12)
    a.box('indicator', (2, 17, -5.8), (3, 18, -5.5), 4)
    for side, x in (('left', -3.5), ('right', 3.5)):
        b = a.bone('leg_'+side, (x, 8, 0))
        a.box('leg_'+side, (x-1.2, 2, -1.4), (x+1.2, 8, 1.4), 9, b)
        a.box('boot_'+side, (x-2, 0, -3.5), (x+2, 2.5, 2.5), 3, b)
    a.anchors['power_socket'] = [-1, 17, -5.8]
    yield a
    a = Actor('mouse', 'utility'); a.bone('body', (0, 3, 0))
    a.box('chassis', (-4, 2, -7), (4, 5, 7), 3)
    a.box('tapered_upper', (-3, 5, -5), (3, 7, 5), 9)
    a.box('top', (-2, 7, -3), (2, 8, 3), 6)
    a.box('front_bumper', (-4.5, 2, -7.5), (4.5, 3, -7), 1)
    a.box('sensor', (-1.5, 4, -7.3), (1.5, 5, -7), 12)
    for side, x in (('left', -4.5), ('right', 4.5)):
        for end,z in (('front',-4.5),('back',4.5)):
            b = a.bone('wheel_'+end+'_'+side, (x, 1.5, z))
            a.box('wheel', (x-1, 0, z-1.5), (x+1, 3, z+1.5), 12, b)
            a.box('hub', (x-1.1, 1, z-.5), (x+1.1, 2, z+.5), 1, b)
    a.anchors['sensor'] = [0, 4.5, -7.3]
    yield a
    a = Actor('bantha', 'desert'); a.bone('body', (0, 20, 0))
    a.box('shaggy_bulk', (-13, 12, -16), (13, 36, 16), 0)
    a.box('shoulder_mane', (-14, 18, -17), (14, 37, 2), 2)
    a.box('back', (-10, 36, -13), (10, 40, 12), 1)
    for x in (-10, -5, 0, 5, 10):
        a.box('hanging_fleece', (x-1.5, 8+(x%3), -16.5), (x+1.5, 19, 16.5), 0)
    a.bone('head', (0, 27, -14), 'body')
    a.box('head', (-8, 21, -25), (8, 35, -14), 1, 'head')
    a.box('muzzle', (-6, 20, -28), (6, 26, -24), 2, 'head')
    for side in (-1, 1):
        x = side*7
        a.box('eye', (x-.7, 29, -25.3), (x+.7, 30.5, -25), 11, 'head')
        # Square spiral horns keep their curl readable at a distance.
        for j, (lo, hi) in enumerate([((7,29,-23),(12,34,-19)), ((10,25,-24),(15,31,-19)),
                                     ((11,22,-25),(16,27,-20)), ((8,21,-27),(14,24,-22)),
                                     ((7,23,-28),(10,26,-24))]):
            if side == -1:
                lo, hi = (-hi[0],lo[1],lo[2]), (-lo[0],hi[1],hi[2])
            a.box('horn_'+str(j), lo, hi, 3, 'head')
    for side, x in (('left', -8), ('right', 8)):
        for end, z in (('front', -10), ('back', 10)):
            b = a.bone('leg_'+end+'_'+side, (x, 16, z))
            a.box(b, (x-3, 2, z-3), (x+3, 16, z+3), 0, b)
            a.box('hoof', (x-3.2, 0, z-3.2), (x+3.2, 3, z+3.2), 2, b)
    a.bone('tail', (0, 24, 15), 'body'); a.box('tail', (-2, 15, 15), (2, 25, 23), 2, 'tail')
    a.anchors.update(back=[0, 40, 0], muzzle=[0, 23, -28])
    yield a
    a = Actor('dewback', 'desert'); a.bone('body', (0, 13, 0))
    a.box('belly', (-9, 7, -13), (9, 18, 15), 4)
    a.box('back', (-8, 18, -10), (8, 23, 11), 5)
    a.box('flank_left', (-11, 10, -8), (-8, 17, 12), 6)
    a.box('flank_right', (8, 10, -8), (11, 17, 12), 6)
    a.bone('head', (0, 17, -11), 'body')
    a.box('neck', (-6, 12, -19), (6, 21, -10), 4, 'head')
    a.box('broad_head', (-7, 13, -27), (7, 22, -17), 5, 'head')
    a.box('jaw', (-6, 11, -28), (6, 14, -17), 7, 'head')
    for x in (-7.2, 6.5):
        a.box('eye', (x, 18, -24), (x+.7, 19.5, -21), 14, 'head')
    a.bone('tail', (0, 13, 13), 'body')
    for i, (w, y, z, length) in enumerate(((6,10,14,10),(4,8,24,9),(2,6,33,9),(1,5,42,5))):
        a.box('tail_step_'+str(i), (-w,y,z), (w,y+5,z+length), 4 if i%2==0 else 5, 'tail')
    for side, x in (('left', -9), ('right', 9)):
        for end, z in (('front', -9), ('back', 10)):
            b = a.bone('leg_'+end+'_'+side, (x, 13, z))
            a.box(b, (x-2.5, 2, z-2.5), (x+2.5, 13, z+2.5), 4, b)
            a.box('foot', (x-3, 0, z-4), (x+3, 3, z+2), 7, b)
    a.bone('saddle', (0, 23, 1), 'body')
    a.box('saddle_blanket', (-7, 23, -5), (7, 24, 8), 13, 'saddle')
    a.box('saddle_seat', (-4.5, 24, -2), (4.5, 25, 5), 2, 'saddle')
    a.box('saddle_pommel', (-5, 24, -4), (5, 27, -2), 3, 'saddle')
    a.anchors.update(saddle=[0, 25, 1], muzzle=[0, 16, -28])
    yield a
    a = Actor('womp_rat', 'desert'); a.bone('body', (0, 6, 0))
    a.box('haunches', (-4.5, 3, -2), (4.5, 11, 8), 8)
    a.box('shoulders', (-3.5, 3, -6), (3.5, 9, 1), 9)
    a.bone('head', (0, 7, -5), 'body')
    a.box('head', (-3.5, 5, -11), (3.5, 10, -5), 8, 'head')
    a.box('snout', (-2, 4, -14), (2, 7, -10), 9, 'head')
    a.box('nose', (-1, 5, -14.5), (1, 6.5, -14), 11, 'head')
    for side, x in (('left', -2.5), ('right', 2.5)):
        b = a.bone('ear_'+side, (x, 9, -6), 'head')
        a.box('ear', (x-1.5, 9, -7), (x+1.5, 15, -5), 8, b)
        a.box('ear_inner', (x-1, 10, -7.2), (x+1, 14, -7), 13, b)
        a.box('eye', (x-.5, 8, -11.2), (x+.5, 9, -11), 11, 'head')
    for side, x in (('left', -3), ('right', 3)):
        for end, z in (('front', -4), ('back', 5)):
            b = a.bone('leg_'+end+'_'+side, (x, 5, z))
            a.box('paw', (x-1, 0, z-2), (x+1, 5, z+1), 10, b)
    a.bone('tail', (0, 5, 7), 'body')
    for i in range(4):
        a.box('tail_'+str(i), (-1+i*.2, 3-i*.5, 7+i*4), (1-i*.2, 5-i*.5, 12+i*4), 13, 'tail')
    a.anchors['nose'] = [0, 5.5, -14.5]
    yield a


def uv(material, factor=1):
    x, y = material%4*4, material//4*4
    return [v*factor for v in (x,y,x+4,y+4)]


def native(cubes, scale, pivot):
    return {'textures': {}, 'elements': [
        {'from': ((np.array(c['from'])-pivot)/scale+8).tolist(),
         'to': ((np.array(c['to'])-pivot)/scale+8).tolist(),
         'faces': {s: {'uv': uv(c['material']), 'texture': '#0', 'tintindex': 0} for s in SIDES}}
        for c in cubes]}


def animation(a, name, duration, loop, tracks, interpolation='linear'):
    return {'uuid': stable(a.name,name), 'name': name, 'length': duration, 'loop': loop, 'override': False,
            'animators': {stable(a.name,b): {'name': b, 'type': 'bone', 'keyframes': [
                {'channel': ch, 'time': t, 'interpolation': interpolation,
                 'data_points': [dict(zip('xyz', map(str, value)))]}
                for ch, keys in channels.items() for t,value in keys.items()]}
                for b,channels in tracks.items()}}


def clips(a):
    focus = 'head' if 'head' in a.bones else 'body'
    idle = {focus: {'rotation': {0:[0,-3,0], 1.5:[0,3,0], 3:[0,-3,0]}}}
    walk = {}
    for b in a.bones:
        if b.startswith('leg_'):
            phase = 1 if ('left' in b) == ('front' in b or a.family=='utility') else -1
            angle = 8 if a.name=='astromech' else 18
            walk[b] = {'rotation': {0:[phase*angle,0,0], .4:[-phase*angle,0,0], .8:[phase*angle,0,0]}}
        if b.startswith('wheel_'):
            walk[b] = {'rotation': {0:[0,0,0], .8:[-360,0,0]}}
    walk['body'] = {'position': {0:[0,0,0], .2:[0,.35,0], .4:[0,0,0], .6:[0,.35,0], .8:[0,0,0]}}
    result = [animation(a,'idle',3,'loop',idle), animation(a,'walk',.8,'loop',walk),
              animation(a,'turn',.6,'once',{focus:{'rotation':{0:[0,0,0],.3:[0,25,0],.6:[0,0,0]}}})]
    if a.name=='womp_rat':
        return womp_rat_clips(a, result)
    if a.family=='desert':
        result.extend([
            animation(a,'attack',.6,'once',{'head':{'rotation':{0:[0,0,0],.2:[-12,0,0],.35:[18,0,0],.6:[0,0,0]}}}),
            animation(a,'damage',.3,'once',{'body':{'rotation':{0:[0,0,0],.1:[0,0,5],.3:[0,0,0]}}}),
            animation(a,'death',1,'hold',{'root':{'rotation':{0:[0,0,0],.7:[0,0,85],1:[0,0,85]}}})])
    return result


def womp_rat_clips(a, shared):
    """Zombies' Kabelratte: a scurrying gait, a lunge, and the gnaw it plays at the generator. Positive x pitches down."""
    smooth = lambda name, duration, loop, tracks: animation(a, name, duration, loop, tracks, 'catmullrom')
    diagonal = lambda sign: {0:[35*sign,0,0], .125:[0,0,0], .25:[-35*sign,0,0], .375:[0,0,0], .5:[35*sign,0,0]}
    walk = {'leg_front_left': {'rotation': diagonal(1)}, 'leg_back_right': {'rotation': diagonal(1)},
            'leg_front_right': {'rotation': diagonal(-1)}, 'leg_back_left': {'rotation': diagonal(-1)},
            'body': {'rotation': {0:[0,0,0], .125:[0,0,3], .25:[0,0,0], .375:[0,0,-3], .5:[0,0,0]}},
            'root': {'position': {0:[0,0,0], .125:[0,.8,0], .25:[0,0,0], .375:[0,.8,0], .5:[0,0,0]}},
            'head': {'rotation': {0:[2,0,0], .125:[-3,0,0], .25:[2,0,0], .375:[-3,0,0], .5:[2,0,0]}},
            'tail': {'rotation': {0:[0,-12,0], .25:[0,12,0], .5:[0,-12,0]}},
            'ear_left': {'rotation': {0:[0,0,0], .25:[0,0,-8], .5:[0,0,0]}},
            'ear_right': {'rotation': {0:[0,0,0], .25:[0,0,8], .5:[0,0,0]}}}
    idle = {'body': {'rotation': {0:[0,0,0], 1.5:[-1.5,0,0], 3:[0,0,0]}},
            'root': {'position': {0:[0,0,0], 1.5:[0,.4,0], 3:[0,0,0]}},
            'head': {'rotation': {0:[0,-8,0], 1:[-3,6,0], 2:[2,-2,0], 3:[0,-8,0]}},
            'tail': {'rotation': {0:[0,-10,0], 1.5:[0,10,0], 3:[0,-10,0]}},
            'ear_left': {'rotation': {0:[0,0,0], 2.2:[0,0,-12], 2.4:[0,0,0], 3:[0,0,0]}}}
    attack = {'head': {'rotation': {0:[0,0,0], .15:[-22,0,0], .3:[30,0,0], .45:[10,0,0], .6:[0,0,0]}},
              'body': {'rotation': {0:[0,0,0], .15:[-6,0,0], .3:[8,0,0], .6:[0,0,0]}},
              'root': {'position': {0:[0,0,0], .15:[0,0,1], .3:[0,0,-2], .6:[0,0,0]}},
              'tail': {'rotation': {0:[0,0,0], .3:[0,20,0], .6:[0,0,0]}}}
    damage = {'root': {'rotation': {0:[0,0,0], .08:[0,0,7], .3:[0,0,0]}},
              'head': {'rotation': {0:[0,0,0], .08:[-12,0,0], .3:[0,0,0]}}}
    death = {'root': {'rotation': {0:[0,0,0], .3:[0,0,18], .8:[0,0,88], 1.2:[0,0,88]},
                      'position': {0:[0,0,0], .3:[0,2,0], .8:[0,0,0], 1.2:[0,0,0]}},
             'head': {'rotation': {0:[0,0,0], .8:[25,0,0], 1.2:[25,0,0]}},
             **{leg: {'rotation': {0:[0,0,0], .8:[35,0,0], 1.2:[35,0,0]}}
                for leg in ('leg_front_left','leg_front_right','leg_back_left','leg_back_right')}}
    gnaw = {'head': {'rotation': {0:[15,0,0], .12:[30,0,0], .25:[15,0,0], .37:[30,-6,0], .5:[15,0,0]}},
            'body': {'rotation': {0:[8,0,0], .25:[10,0,0], .5:[8,0,0]}},
            'tail': {'rotation': {0:[0,-15,0], .25:[0,15,0], .5:[0,-15,0]}}}
    turn = next(clip for clip in shared if clip['name']=='turn')
    return [smooth('idle',3,'loop',idle), smooth('walk',.5,'loop',walk), turn, smooth('attack',.6,'once',attack),
            smooth('damage',.3,'once',damage), smooth('death',1.2,'hold',death), smooth('gnaw',.5,'once',gnaw)]


def make_rig(a, texture):
    elements = []
    for i,c in enumerate(a.cubes):
        elements.append({'name': c['name'], 'uuid': stable(a.name,str(i)), 'type':'cube', 'box_uv':False,
                         'from':c['from'], 'to':c['to'], 'origin':a.bones[c['bone']]['pivot'], 'rotation':[0,0,0],
                         'faces':{s:{'uv':uv(c['material'],4), 'texture':0} for s in SIDES}})
    def branch(b):
        return {'uuid':stable(a.name,b), 'children':
                [stable(a.name,str(i)) for i,c in enumerate(a.cubes) if c['bone']==b]+
                [branch(k) for k,v in a.bones.items() if v['parent']==b]}
    buf = io.BytesIO(); texture.save(buf, format='PNG')
    return {'meta':{'format_version':'5.0','model_format':'free','box_uv':False}, 'name':'kit_'+a.name,
            'resolution':{'width':64,'height':64}, 'elements':elements,
            'groups':[{'name':b, 'uuid':stable(a.name,b), 'origin':v['pivot'], 'rotation':[0,0,0], 'export':True}
                      for b,v in a.bones.items()], 'outliner':[branch('root')],
            'textures':[{'name':a.family+'.png','id':'0','uuid':stable(a.family,'texture'), 'width':64,'height':64,
                         'uv_width':64,'uv_height':64,'source':'data:image/png;base64,'+base64.b64encode(buf.getvalue()).decode()}],
            'animations':clips(a)}


def rotation(value):
    x,y,z = np.radians(value)
    cx,cy,cz = np.cos([x,y,z]); sx,sy,sz = np.sin([x,y,z])
    return np.array([[cz,-sz,0],[sz,cz,0],[0,0,1]]) @ np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]]) @ np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]])


def pose(rig, clip, time):
    selected = next(c for c in rig['animations'] if c['name']==clip)
    matrices = {}
    for b in rig['groups']:
        mat = np.eye(4); pivot = np.array(b['origin'])
        channels = selected['animators'].get(b['uuid'],{}).get('keyframes',[])
        values = {}
        for channel in ('rotation','position'):
            keys = sorted((k for k in channels if k['channel']==channel), key=lambda k:k['time'])
            if not keys:
                values[channel] = np.zeros(3); continue
            times = [k['time'] for k in keys]
            points = np.array([[float(k['data_points'][0][axis]) for axis in 'xyz'] for k in keys])
            values[channel] = np.array([np.interp(time,times,points[:,i]) for i in range(3)])
        mat[:3,:3] = rotation(values['rotation'])
        mat[:3,3] = pivot - mat[:3,:3] @ pivot + values['position']
        matrices[b['uuid']] = mat
    transformed = []
    by_id = {e['uuid']:e for e in rig['elements']}
    def visit(branch, parent):
        mat = parent @ matrices[branch['uuid']]
        for child in branch['children']:
            if isinstance(child,dict):
                visit(child, mat)
            else:
                transformed.append((by_id[child],mat))
    visit(rig['outliner'][0],np.eye(4))
    return transformed


def render(rig, texture, view='three', clip='idle', time=0, size=384, pixels_per_unit=None):
    angles = {'front':(0,180,0), 'side':(0,90,0), 'three':(-18,145,0)}
    camera = rotation(angles[view])
    all_points = np.array([e[k] for e in rig['elements'] for k in ('from','to')])
    low,high = all_points.min(0),all_points.max(0)
    center = (low+high)/2
    factor = pixels_per_unit or (size-62)/max(32, np.linalg.norm((high-low)[[0,2]]),high[1]-low[1])
    out = np.full((size,size,3), (229,226,215), dtype=np.uint8)
    depth = np.full((size,size), -np.inf)
    pixels = np.asarray(texture.convert('RGB'))
    for e,mat in pose(rig,clip,time):
        x,y,z=e['from']; X,Y,Z=e['to']
        faces = {
            'north':[(X,Y,z),(x,Y,z),(x,y,z),(X,y,z)],
            'south':[(x,Y,Z),(X,Y,Z),(X,y,Z),(x,y,Z)],
            'east':[(X,Y,Z),(X,Y,z),(X,y,z),(X,y,Z)],
            'west':[(x,Y,z),(x,Y,Z),(x,y,Z),(x,y,z)],
            'up':[(x,Y,z),(X,Y,z),(X,Y,Z),(x,Y,Z)],
            'down':[(x,y,Z),(X,y,Z),(X,y,z),(x,y,z)],
        }
        for side, points in faces.items():
            face = e['faces'][side]
            if face['texture'] is None:
                continue
            world = np.array(points) @ mat[:3,:3].T + mat[:3,3]
            p = (world-center) @ camera.T
            normal = np.cross(p[1]-p[0],p[2]-p[0]); normal /= max(np.linalg.norm(normal),1e-10)
            shade = .65 + .35*abs(normal @ np.array([.3,.8,.52]))
            screen = p.copy(); screen[:,0] = size/2+p[:,0]*factor; screen[:,1]=size/2-p[:,1]*factor
            u,v,U,V=face['uv']; tex = np.array([[u,v],[U,v],[U,V],[u,V]])
            for ids in ((0,1,2),(0,2,3)):
                tri = screen[list(ids)]; tuv=tex[list(ids)]
                lo=np.maximum(np.floor(tri[:,:2].min(0)).astype(int),0)
                hi=np.minimum(np.ceil(tri[:,:2].max(0)).astype(int),size-1)
                if np.any(lo>hi):continue
                xx,yy=np.meshgrid(np.arange(lo[0],hi[0]+1)+.5,np.arange(lo[1],hi[1]+1)+.5)
                a,b,c=tri
                den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
                if abs(den)<1e-8:continue
                wa=((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/den
                wb=((c[1]-a[1])*(xx-c[0])+(a[0]-c[0])*(yy-c[1]))/den
                wc=1-wa-wb
                zs=wa*a[2]+wb*b[2]+wc*c[2]
                uvv=wa[...,None]*tuv[0]+wb[...,None]*tuv[1]+wc[...,None]*tuv[2]
                ix=np.clip(uvv[...,0].astype(int),0,63); iy=np.clip(uvv[...,1].astype(int),0,63)
                region=np.s_[lo[1]:hi[1]+1,lo[0]:hi[0]+1]
                mask=(wa>=0)&(wb>=0)&(wc>=0)&(zs>depth[region])
                out[region][mask]=(pixels[iy,ix]*shade).astype(np.uint8)[mask]
                depth[region][mask]=zs[mask]
    image=Image.fromarray(out)
    draw=ImageDraw.Draw(image)
    height=(high[1]-low[1])/16
    draw.text((12,10),rig['name']+' / '+view,fill='#263239')
    draw.text((12,size-22),f'{height:.2f} blocks tall | {clip} {time:.2f}s',fill='#263239')
    # A 1.8-block reference bar uses the same projection scale as the model.
    bar=1.8*16*factor
    bottom=size-38
    draw.line((size-20,bottom,size-20,max(30,bottom-bar)),fill='#6d7370',width=2)
    draw.text((size-66,max(30,bottom-bar)-12),'1.8 m',fill='#525b57')
    return image


def validate(root, manifest):
    assets=root/'src/assets/blockcraft'
    for row in manifest['actors']:
        rig=json.loads((root/row['rig']).read_text())
        ids={g['uuid'] for g in rig['groups']}
        assert len(ids)==len(rig['groups'])<=12
        element_ids={e['uuid'] for e in rig['elements']}
        assigned=[]
        def branch(node):
            assert node['uuid'] in ids
            for child in node['children']:
                if isinstance(child,dict):branch(child)
                else:assigned.append(child)
        branch(rig['outliner'][0])
        assert len(assigned)==len(element_ids) and set(assigned)==element_ids
        for e in rig['elements']:
            assert set(e['faces'])==set(SIDES)
            assert np.all(np.array(e['from'])<np.array(e['to']))
            for face in e['faces'].values():
                assert face['texture'] is None or face['texture']==0
                assert all(0<=v<=64 for v in face['uv'])
        for clip in rig['animations']:
            assert clip['length']>0
            for bone,animator in clip['animators'].items():
                assert bone in ids
                for k in animator['keyframes']:
                    assert 0<=k['time']<=clip['length']
                    assert all(math.isfinite(float(v)) for v in k['data_points'][0].values())
                for channel in {k['channel'] for k in animator['keyframes']}:
                    keys=sorted((k for k in animator['keyframes'] if k['channel']==channel),key=lambda k:k['time'])
                    assert keys[0]['time']==0 and keys[-1]['time']==clip['length']
                    assert len({k['time'] for k in keys})==len(keys)
                    if clip['loop']=='loop':
                        start=np.array([float(keys[0]['data_points'][0][axis]) for axis in 'xyz'])
                        end=np.array([float(keys[-1]['data_points'][0][axis]) for axis in 'xyz'])
                        delta=(end-start+180)%360-180 if channel=='rotation' else end-start
                        assert np.allclose(delta,0)
        for spec in row['native_parts']:
            model=json.loads((assets/('models/item/'+spec['path']+'.json')).read_text())
            item=json.loads((assets/('items/'+spec['path']+'.json')).read_text())
            assert item['model']['model']=='blockcraft:item/'+spec['path']
            for texture in model['textures'].values():
                path=assets/('textures/'+texture.split(':')[1]+'.png')
                assert path.exists()
                assert Image.open(path).convert('RGBA').getextrema()[3]==(255,255)
            for i,e in enumerate(model['elements']):
                for key in ('from','to'):
                    assert all(-16<=v<=32 for v in e[key])
                for face in e['faces'].values():
                    assert face['texture'].removeprefix('#') in model['textures']
                    assert all(0<=v<=16 for v in face['uv'])
                original=spec['source_elements'][i]
                for key in ('from','to'):
                    decoded=(np.array(e[key])-8)*spec['scale']+spec['pivot_pixels']
                    expected=rig['elements'][original][key]
                    assert np.all(decoded >= np.array(expected)-1e-6) if key=='from' else np.all(decoded <= np.array(expected)+1e-6)
                for side,face in e['faces'].items():
                    source_face=rig['elements'][original]['faces'][side]
                    assert source_face['texture'] is not None
                    bounds={k:((np.array(rig['elements'][original][k])-spec['pivot_pixels'])/spec['scale']+8).tolist() for k in ('from','to')}
                    axis,sign=SURFACE_SIDES[side]; key='to' if sign>0 else 'from'
                    assert abs(e[key][axis]-bounds[key][axis])<1e-6
                    expected_uv=cropped_uv(bounds,e,side,dict(source_face,uv=(np.array(source_face['uv'])/4).tolist()))
                    assert np.allclose(face['uv'],expected_uv)
            assert not overlaps(model['elements'])
            if spec['bone'] is not None:
                route=root/f'build/kit_actors/pack-overlay/assets/bettermodel/items/model/kit_{row["id"]}_{spec["bone"]}.json'
                assert json.loads(route.read_text())['model']['model']=='blockcraft:item/'+spec['path']
    return {'actors':len(manifest['actors']), 'native_geometry':'bounded; visible surfaces preserve source planes and UV scale',
            'references':'items, models, textures, bones and clips closed', 'bbmodel_faces':'six keys per element',
            'textures':'opaque; top-origin; 64x64', 'runtime':'not exercised'}


def generate(args):
    assets=ROOT/'src/assets/blockcraft'; output=ROOT/'build/kit_actors'
    output.mkdir(parents=True,exist_ok=True)
    manifest={'format':1, 'target':'Minecraft Java 26.3', 'units':'blocks; rig coordinates are 16 pixels/block',
              'front':'north (-Z)', 'origin':'ground at torso center', 'collision':'none in static models; runtime owns mob collision and damage', 'actors':[]}
    textures={k:atlas(k) for k in PALETTES}
    for family,texture in textures.items():
        dest=assets/f'textures/item/{PREFIX}/{family}.png'; dest.parent.mkdir(parents=True,exist_ok=True); texture.save(dest)
    config=[]; tiles=[]
    for a in actors():
        rig=make_rig(a,textures[a.family]); rig_path=f'bettermodel/kit_{a.name}.bbmodel'
        write(ROOT/rig_path,rig)
        low=np.array([c['from'] for c in a.cubes]).min(0); high=np.array([c['to'] for c in a.cubes]).max(0)
        scale=max(1,math.ceil(max(abs(low).max(),abs(high).max())/24))
        specs=[]
        for bone in (None,*a.bones):
            indices=[i for i,c in enumerate(a.cubes) if bone is None or c['bone']==bone]
            if not indices:continue
            cubes=[a.cubes[i] for i in indices]
            pivot=[0,0,0] if bone is None else a.bones[bone]['pivot']
            part_scale=scale if bone is None else max(1,float(max(abs(np.array(c[k])-pivot).max() for c in cubes for k in ('from','to'))/16))
            path=PREFIX+'/'+a.name+('' if bone is None else '/rig/'+bone)
            model=native(cubes,part_scale,pivot)
            model['elements'],source_indices=exposed(model['elements'])
            model['textures']={'0':f'blockcraft:item/{PREFIX}/{a.family}', 'particle':f'blockcraft:item/{PREFIX}/{a.family}'}
            write(assets/('models/item/'+path+'.json'),model)
            write(assets/('items/'+path+'.json'),{'model':{'type':'minecraft:model','model':'blockcraft:item/'+path}})
            if bone is not None:
                route = f'assets/bettermodel/items/model/kit_{a.name}_{bone}.json'
                definition = {'model':{'type':'minecraft:model','model':'blockcraft:item/'+path,
                                      'tints':[{'type':'minecraft:custom_model_data','default':16777215}]}}
                write(output/'pack-overlay'/route, definition)
                write(ROOT/'src'/route, definition)
            specs.append({'bone':bone, 'path':path,'scale':part_scale,'pivot_pixels':pivot,'source_elements':[indices[i] for i in source_indices]})
        dimensions=((high-low)/16).tolist()
        manifest['actors'].append({'id':a.name,'family':a.family,'name_de':NAMES[a.name], 'rig':rig_path,
            'static_item':'blockcraft:'+PREFIX+'/'+a.name, 'display_scale':[scale]*3,
            'height':dimensions[1], 'footprint_xz':[dimensions[0],dimensions[2]], 'bounds_blocks':[(low/16).tolist(),(high/16).tolist()],
            'interaction':{'width':max(dimensions[0],dimensions[2]),'height':dimensions[1], 'center_offset_blocks':((low+high)/32).tolist()},
            'cubes':len(a.cubes), 'bones':a.bones,'anchors_blocks':{k:(np.array(v)/16).tolist() for k,v in a.anchors.items()},
            'animations':[{k:c[k] for k in ('name','length','loop')} for c in rig['animations']], 'native_parts':specs})
        config.extend([f'blockcraft_kit_{a.name}:', f'  itemname: "<white>{NAMES[a.name]}"','  material: PAPER',
            '  Components:',f'    item_model: blockcraft:{PREFIX}/{a.name}','  Pack:',f'    model: blockcraft:item/{PREFIX}/{a.name}',
            '  Mechanics:','    furniture:','      properties:','        display_transform: NONE','        cullable: false',
            '        translation: 0,-0.5,0',f'        scale: {scale},{scale},{scale}','        view_range: 1.0',
            '      restricted_rotation: VERY_STRICT','      preview_placement: true','      hitbox:',
            f'        interaction: {(low[0]+high[0])/32:.4f},0,{(low[2]+high[2])/32:.4f} {max(dimensions[0],dimensions[2]):.4f},{dimensions[1]:.4f}',''])
        if not args.no_renders:
            for view in ('front','side','three'):
                image=render(rig,textures[a.family],view)
                image.save(output/(a.name+'_'+view+'.png'))
                if view=='three':tiles.append(render(rig,textures[a.family],pixels_per_unit=4.2))
            frames=[render(rig,textures[a.family],clip='walk',time=i*.8/12,size=288) for i in range(12)]
            frames[0].save(output/(a.name+'_walk.gif'),save_all=True,append_images=frames[1:],duration=[70,70,60]*4,loop=0)
    if tiles:
        sheet=Image.new('RGB',(384*3,384*2))
        for i,tile in enumerate(tiles):sheet.paste(tile,(i%3*384,i//3*384))
        sheet.save(output/'contact_sheet.png')
    write(output/'manifest.json',manifest)
    nexo=ROOT.parent/'nexo/items/blockcraft_starwars_kit_actors.yml'
    definitions=yaml.safe_load('\n'.join(config))
    for a in manifest['actors']:
        furniture=definitions['blockcraft_kit_'+a['id']]['Mechanics']['furniture']
        furniture['blockcraft_collision']=physical_actor(a['id'])
        route=a['static_item'].split(':',1)[1]
        light,_=scenery_lights(ROOT/'src/assets/blockcraft',route,json.loads((ROOT/'src/assets/blockcraft/models/item'/(route+'.json')).read_text()))
        if light:furniture['lights']=light
    nexo.parent.mkdir(parents=True,exist_ok=True); nexo.write_text(yaml.safe_dump(definitions,sort_keys=False,allow_unicode=True))
    report=validate(ROOT,manifest)
    generated=[p for folder in (assets,ROOT/'bettermodel') for p in folder.rglob('*') if p.is_file() and (PREFIX in p.as_posix() or p.name.startswith('kit_'))]
    generated.extend((nexo,ROOT/'KitActors.py',ROOT/'art/kit_actors/README.md'))
    generated.extend(p for p in output.rglob('*') if p.is_file() and p.name!='validation.json')
    report['sha256']={str(p.relative_to(ROOT.parent)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(generated)}
    write(output/'validation.json',report)
    print(json.dumps({k:v for k,v in report.items() if k!='sha256'},indent=2))


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--no-renders',action='store_true')
    args=parser.parse_args()
    generate(args)
