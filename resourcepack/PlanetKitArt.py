"""Generate and verify the standalone native planet building kit."""
import argparse
import copy
import hashlib
import json
from AssetFiles import write_json
import math
from pathlib import Path

import numpy as np
import yaml
from PIL import Image, ImageDraw
from KitPixels import tile
from ModelSurfaces import exposed, overlaps
from FurnitureCollision import planet as physical_planet
from SceneryLights import configure as scenery_lights

ROOT = Path(__file__).resolve().parent
PREFIX = 'sternenfront/kits'
SIDES = ('south', 'north', 'east', 'west', 'up', 'down')
MATERIALS = ('body', 'trim', 'dark', 'metal', 'wear', 'seam', 'cloth', 'light', 'rock', 'strata', 'patch', 'vent', 'pipe', 'wet', 'edge', 'cap')
PALETTES = {
    'kamino': ('dfe5df', '748f9e', '293f4c', 'a2b5bc', '9caaa7', '425d6b', '648d9f', 'a6ede6', '87999e', '617984', 'bdc9c4', '354d59', '6d8b96', '508eaa', 'f0f1e6', 'bfcdc9'),
    'desert': ('d6b994', 'b88d65', '514137', '947767', 'a88a6c', '74533c', 'a75f45', 'edbc72', 'b77b57', '88533f', 'e7ccaa', '514943', '8b6951', '5d939e', 'efdab8', 'c6a27c'),
}
LABELS = {
    'window_mullion': 'Ozeanfenster · Mittelsteg', 'window_rib': 'Ozeanfenster · Stützrippe', 'window_corner': 'Ozeanfenster · Ecke',
    'platform_rim': 'Plattform · Rand', 'platform_corner': 'Plattform · Ecke', 'platform_rail': 'Plattform · Geländer', 'platform_light': 'Plattform · Randlicht',
    'storm_drain': 'Regenablauf', 'storm_streak': 'Regenstreifen', 'storm_seam': 'Undichte Fuge', 'storm_panel': 'Abgenutzte Verkleidung',
    'roof_rim': 'Dach · Traufe', 'roof_corner': 'Dach · Ecke', 'roof_step': 'Dach · Stufe', 'roof_dome': 'Dach · Kuppel',
    'wall_straight': 'Putzwand · Gerade', 'wall_corner': 'Putzwand · Ecke', 'wall_patch': 'Putzwand · Reparatur',
    'entrance_recess': 'Eingang · Tiefer Rahmen', 'entrance_frame': 'Eingang · Außenrahmen', 'entrance_shutter': 'Eingang · Seitenladen',
    'shade_awning': 'Markt · Sonnensegel', 'shade_pole': 'Markt · Stütze', 'shade_canopy': 'Markt · Baldachin',
    'water_vaporator': 'Feuchtigkeitsverdampfer', 'water_cistern': 'Wasserzisterne', 'water_pump': 'Wasserpumpe mit Anschluss',
    'power_generator': 'Lokaler Generator', 'power_distribution': 'Stromverteiler',
    'canyon_strata': 'Canyon · Gesteinsschicht', 'canyon_ledge': 'Canyon · Felsvorsprung', 'canyon_cave': 'Canyon · Höhlenrand',
}


def atlas(planet):
    image = Image.new('RGB', (128, 512))
    roles = ('enamel' if planet == 'kamino' else 'plaster', 'steel', 'dark', 'steel', 'plaster', 'vent',
             'cloth', 'light', 'rock', 'strata', 'patch', 'vent', 'pipe', 'water', 'enamel', 'steel')
    for phase in range(4):
        for i, color in enumerate(PALETTES[planet]):
            image.paste(tile(color, roles[i], 32, i+17, phase), (i % 4 * 32, phase*128 + i // 4 * 32))
    frames=np.array(image).reshape(4,128,128,3)
    mask=np.ones((128,128),dtype=bool); mask[96:128,32:64]=False
    assert all(np.array_equal(frames[0][mask],frame[mask]) for frame in frames[1:])
    assert all(not np.array_equal(frames[0][~mask],frame[~mask]) for frame in frames[1:])
    return image


def box(name, a, b, material='body', glow=False):
    i = MATERIALS.index(material)
    u, v = i % 4 * 4, i // 4 * 4
    dx, dy, dz = [hi-lo for lo, hi in zip(a, b)]
    dimensions = {'south': (dx,dy), 'north': (dx,dy), 'east': (dz,dy), 'west': (dz,dy), 'up': (dx,dz), 'down': (dx,dz)}
    faces = {s: {'uv': [u+(32-min(w,32))/16, v+(32-min(h,32))/16,
                        u+(32+min(w,32))/16, v+(32+min(h,32))/16], 'texture': '#atlas'} for s,(w,h) in dimensions.items()}
    e = {'name': name, 'from': [a[0] + 8, a[1], a[2] + 8], 'to': [b[0] + 8, b[1], b[2] + 8], 'faces': faces}
    if glow:
        e['light_emission'] = 5
    return e


def modules():
    result = {}
    def add(planet, name, family, parts, span, connectors, support, opening=None, variant=None):
        parts,_=exposed(parts)
        assert not overlaps(parts),(planet,name,'coplanar surface overlap')
        result[planet + '/' + name] = dict(planet=planet, name=name, family=family, elements=parts, span=span,
            connectors=connectors, support=support, opening=opening, variant=variant)
    def joins(width=32, y=0, z=0):
        return {'left': [-width / 2, y, z], 'right': [width / 2, y, z]}
    def frame(depth=8, mullion=False):
        p = [box('left rib', (-16, 0, -depth/2), (-14, 32, depth/2), 'trim'),
             box('right rib', (14, 0, -depth/2), (16, 32, depth/2), 'trim'),
             box('sill', (-14, 0, -depth/2), (14, 2, depth/2)),
             box('lintel', (-14, 30, -depth/2), (14, 32, depth/2))]
        for x in (-16,14.5):
            p.append(box('outer enamel casing',(x,0,depth/2-.5),(x+1.5,32,depth/2+.5),'edge'))
        for x in (-14.5,14):
            p.append(box('recessed glazing gasket',(x,2,depth/2),(x+.5,30,depth/2+.25),'dark'))
        p += [box('sloped sill step',(-14,0,depth/2),(14,1,depth/2+1),'edge'),
              box('lintel shadow groove',(-14,30,depth/2),(14,30.5,depth/2+.25),'dark'),
              box('lintel raised edge',(-14,31,depth/2),(14,32,depth/2+.75),'edge')]
        if mullion:
            p += [box('central mullion',(-.7,2,-depth/2+.5),(.7,30,depth/2),'trim'),
                  box('central mullion enamel face',(-.4,2,depth/2),(.4,30,depth/2+.25),'edge')]
        return p
    p = frame(mullion=True)
    add('kamino', 'window_mullion', 'ocean_windows', p, [32,32,9], joins(), 'Native sill and outer wall blocks only; keep glazing opening air.', [-14,2,14,30])
    p = frame(depth=10)
    p += [box('rib foot',(-16,0,-5),(-12,5,6),'trim'),box('rib shoulder',(-16,25,-5),(-12,30,6),'trim'),
          box('structural rib spine',(-15,5,5),(-12,25,6),'edge'),
          box('rib recessed channel',(-13,6,6),(-12,24,6.25),'dark')]
    add('kamino','window_rib','ocean_windows',p,[32,32,11.25],joins(),'Native sill and outer wall blocks only.',[-12,2,14,30])
    p = frame()
    p += [box('return sill', (12,0,2),(16,2,16)), box('return lintel',(12,30,2),(16,32,16)), box('return post',(12,2,14),(16,30,16),'trim')]
    add('kamino','window_corner','ocean_windows',p,[32,32,20],{'left':[-16,0,0],'return':[14,0,16]},'Native supporting corners only; open return bay.',[-14,2,12,30])
    rim = [box('rim fascia',(-16,0,-2),(16,4,2),'trim'), box('ivory nosing',(-16,4,-3),(16,6,3)),box('lower drip',(-16,0,2),(16,1,3),'dark')]
    for name in ('platform_rim','platform_corner','platform_rail','platform_light'):
        p = copy.deepcopy(rim)
        if name == 'platform_corner':
            p += [box('return fascia',(12,0,3),(16,4,16),'trim'),box('return nosing',(11,4,3),(16,6,16))]
        if name == 'platform_rail':
            for x in (-15,14): p.append(box('rail post',(x,6,-1),(x+1,22,1),'metal'))
            p += [box('handrail',(-16,22,-1.5),(16,23,1.5)),box('lower rail',(-16,12,-.5),(16,13,.5),'trim')]
        if name == 'platform_light':
            p += [box('lamp recess',(-5,1,2),(5,4,3),'dark'),box('lamp',(-4,2,3),(4,3,3.25),'light',True)]
        add('kamino',name,'platform_edges',p,[32,23 if name.endswith('rail') else 6,19 if name.endswith('corner') else 6.25 if name.endswith('light') else 6],
            {'left':[-16,0,0],'return':[14,0,16]} if name.endswith('corner') else joins(), 'Native platform backing supports the rim; rails and curbs use the authored collision recipe.')
    for name in ('storm_drain','storm_streak','storm_seam','storm_panel'):
        p = [box('mounting panel',(-8,0,-1),(8,16,1),'wear' if name=='storm_panel' else 'body')]
        if name == 'storm_drain':
            p += [box('drain collar',(-5,10,1),(5,14,2),'trim')]
            for x in range(-4,5,2): p.append(box('drain slot',(x,11,2),(x+1,13,2.125),'dark'))
            p += [box('outlet',(-2,4,1),(2,10,4),'metal'),box('outlet shadow',(-1.5,4,3.8),(1.5,6,4.1),'dark'),
                  box('draining water',(-.8,0,3.8),(.8,5,4.25),'wet'),box('wet outlet lip',(-1.25,3.8,3.8),(1.25,4.25,4.4),'wet')]
        if name == 'storm_streak':
            for x,h in ((-6,10),(-2,14),(3,8),(6,12)):
                p += [box('mineral deposit',(x-.5,0,1),(x+1,h+1,1.15),'wear'),
                      box('rain streak',(x,0,1.15),(x+.5,h,1.3),'wet')]
        if name == 'storm_seam':
            p += [box('joint',(-1,0,1),(1,16,1.3),'dark'),box('leaking lip',(-3,12,1),(3,13,2),'trim'),
                  box('mineral trail',(-2,0,1.3),(1.5,12,1.5),'wear'),box('running leak',(-.6,0,1.5),(.6,12,1.8),'wet'),
                  box('pooled outlet',(-2,0,1.5),(2,.5,3.5),'wet')]
        if name == 'storm_panel':
            p += [box('repair plate',(-6,4,1),(3,13,1.5),'patch'),box('exposed seam',(5,0,1),(6,16,1.25),'seam')]
        add('kamino',name,'storm_wear',p,[16,16,6],joins(16),'Mount against a native wall; animated water is decorative and does not place liquid blocks.')
    for name in ('roof_rim','roof_corner','roof_step','roof_dome'):
        p = [box('roof rim',(-16,0,-16),(16,2,16),'trim')]
        if name == 'roof_rim':
            p = [box('straight cornice',(-16,0,-3),(16,2,3),'trim'),box('raised rim',(-16,2,-1),(16,4,3))]
        elif name == 'roof_corner':
            p = [box('straight cornice',(-16,0,-3),(16,2,3),'trim'),box('return cornice',(12,0,3),(16,2,16),'trim'),box('corner cap',(11,2,-1),(16,4,16))]
        elif name == 'roof_step':
            for i in range(4): p.append(box('roof terrace',(-16,i*3+2,-16+i*8),(16,i*3+5,-8+i*8),'body'))
        else:
            for i,(half,h) in enumerate(((14,4),(12,4),(9,3),(5,2))):
                y = (2,6,10,13)[i]
                p.append(box('faceted dome course',(-half,y,-half),(half,y+h,half)))
                p.append(box('dome course shadow',(-half-.25,y,-half-.25),(half+.25,y+.25,half+.25),'wear'))
            p.append(box('dome crown',(-2,15,-2),(2,16,2),'cap'))
        add('desert',name,'roof_dome',p,[32,16 if name=='roof_dome' else 14 if name=='roof_step' else 4,32 if name in ('roof_dome','roof_step') else 19 if name=='roof_corner' else 6],
            {'west':[-16,0,0],'east':[16,0,0],'north':[0,0,-16],'south':[0,0,16]} if name in ('roof_dome','roof_step') else {'left':[-16,0,0],'return':[14,0,16]} if name=='roof_corner' else joins(),
            'Seat on native roof blocks; stepped dome is a two-block accent, repeat rims on 32 units.')
    wall = [box('plaster facing',(-16,0,-2),(16,32,2)),box('weathered footing',(-16,0,2),(16,3,2.5),'wear'),box('coping',(-16,30,-2.5),(16,32,2.5),'cap'),
            box('recessed foundation course',(-16,3,2),(16,3.6,2.15),'seam'),
            box('broken plaster left',(-13,3,2.05),(-8,6,2.65),'patch'),
            box('exposed masonry',(-12.5,3.3,2.65),(-10,4.8,2.75),'strata'),
            box('flaked plaster edge',(9,17,2.05),(12,21,2.4),'patch')]
    for name in ('wall_straight','wall_corner','wall_patch'):
        p = copy.deepcopy(wall)
        if name=='wall_corner': p += [box('return wall',(12,0,2),(16,32,16)),box('return foot',(11.5,0,2.5),(12,3,16),'wear')]
        if name=='wall_patch':
            for a,b in (((-11,5,2),(-4,14,2.25)),((0,0,2),(10,9,2.25)),((7,21,2),(14,28,2.25))): p.append(box('plaster repair',a,b,'patch'))
        add('desert',name,'weathered_plaster',p,[32,32,18.5 if name=='wall_corner' else 5.5],{'left':[-16,0,0],'return':[14,0,16]} if name=='wall_corner' else joins(),'Use native wall blocks behind facing for physical walls.')
    for name in ('entrance_recess','entrance_frame','entrance_shutter'):
        p=[]
        if name != 'entrance_shutter':
            depth=12 if name=='entrance_recess' else 4
            p = [box('left reveal',(-16,0,-depth/2),(-11,30,depth/2)),box('right reveal',(11,0,-depth/2),(16,30,depth/2)),box('lintel',(-16,30,-depth/2),(16,32,depth/2),'cap')]
            for x in (-13,11): p.append(box('frame edge',(x,0,depth/2),(x+2,30,depth/2+.5),'trim'))
            for x in (-16,13):
                p.append(box('projecting reveal footing',(x,0,depth/2),(x+3,3,depth/2+.5),'wear'))
            p.append(box('lintel relief',(-11,30,depth/2),(11,31,depth/2+.4),'patch'))
        else:
            p = [box('parked shutter',(-16,0,-2),(-10,28,2),'metal'),box('shutter track',(-16,28,-2),(16,30,2),'dark')]
            for y in range(2,27,3): p.append(box('shutter slat',(-16,y,2),(-10,y+1,2.25),'trim'))
        add('desert',name,'recessed_entrances',p,[32,32 if name!='entrance_shutter' else 30,12.5 if name=='entrance_recess' else 4.5],joins(),
            'Native blocks only outside clear opening. No threshold block or Nexo barriers.',[-10,0,10,28] if name=='entrance_shutter' else [-11,0,11,30])
    for name in ('shade_awning','shade_pole','shade_canopy'):
        p=[]
        if name=='shade_pole':
            p=[box('pole foot',(-2,0,-2),(2,2,2),'trim'),box('mast',(-.6,2,-.6),(.6,30,.6),'pipe'),box('cross tie',(-4,28,-1),(4,29,1),'metal')]
        else:
            cloth_heights=[]
            for i in range(4):
                y=28-abs(i-1.5)*1.5 if name=='shade_canopy' else 30-i
                cloth_heights.append(y)
                p.append(box('cloth section',(-16,y,-16+i*8),(16,y+.5,-8+i*8),'cloth'))
            for i in range(1,4):
                low,high=sorted(cloth_heights[i-1:i+1])
                if high>low:
                    p.append(box('folded cloth join',(-16,low,-16+i*8-.125),(16,high+.5,-16+i*8+.125),'cloth'))
            p += [box('rear batten',(-16,29,-16),(16,30,-15),'pipe'),box('front batten',(-16,26,15),(16,27,16),'pipe')]
            if name=='shade_awning': p.append(box('valance',(-16,23,15.5),(16,26,16),'cloth'))
        add('desert',name,'shade_market',p,[8,30,4] if name=='shade_pole' else [32,31,32], {'top':[0,28,0]} if name=='shade_pole' else {'left':[-16,28,0],'right':[16,28,0],'rear':[0,28,-16],'front':[0,28,16]}, 'Cloth has no collision; use pole modules or native wall anchors.')
    for name in ('water_vaporator','water_cistern','water_pump','power_generator','power_distribution'):
        p=[box('plinth',(-7,0,-7),(7,2,7),'trim')]
        if name=='water_vaporator':
            p += [box('condenser mast',(-2,2,-2),(2,30,2),'metal'),box('collection tank',(-5,2,-5),(5,9,5))]
            for y in (12,16,20,24):
                p += [box('cooling fin',(-7,y,-4),(7,y+1,4),'metal'),box('fin collar',(-3,y+1,-3),(3,y+2,3),'trim')]
            p += [box('collector crown',(-5,29,-5),(5,31,5),'cap')]
        elif name=='water_cistern':
            for a,b,mat in (((-7,2,-7),(7,4,7),'trim'),((-6,4,-6),(6,18,6),'body'),((-7,18,-7),(7,20,7),'trim'),((-3,20,-3),(3,22,3),'cap')): p.append(box('cistern course',a,b,mat))
            p += [box('gauge recess',(2.5,4,6),(6,17,6.3),'dark'),box('level gauge',(3.5,5,6.3),(5,15,6.6),'wet'),
                  box('gauge reflection',(3.5,6,6.6),(3.8,15,6.75),'edge'),box('inspection hatch',(-4,7,6),(.5,13,6.5),'metal')]
            for x in (-5,5):
                p.append(box('band fastener',(x,18,6),(x+.75,19,6.5),'metal'))
        elif name=='water_pump':
            p += [box('pump housing',(-5,2,-4),(4,10,4)),box('pump motor',(-4,10,-3),(2,14,3),'metal'),box('valve wheel',(-1,14,-2),(3,15,2),'trim'),
                  box('service hatch recess',(-3,3,4),(2,8,4.3),'dark'),box('service hatch',(-2.5,3.5,4.3),(1.5,7.5,4.6),'metal'),
                  box('flow indicator',(-1.5,9,4),(.5,10,4.3),'wet')]
        elif name=='power_generator':
            p += [box('generator casing',(-7,2,-6),(7,12,6)),box('service lid',(-5,12,-5),(5,14,5),'metal')]
            for x in range(-5,6,2): p.append(box('heat sink',(x,3,-7),(x+1,10,-6),'vent'))
            p += [box('exhaust',(4,12,-4),(6,20,-2),'metal'),box('indicator',(-4,8,6),(-3,9,6.25),'light',True)]
        else:
            p += [box('distribution cabinet',(-5,2,-3),(5,20,3)),box('recessed service panel',(-4,5,3),(4,17,3.25),'dark'),box('door plate',(-3,6,3.25),(3,16,3.5),'metal'),box('indicator',(-2,18,3),(-1,19,3.25),'light',True)]
        p += [box('connection pipe',(4,3,-1),(8,5,1),'pipe'),box('inlet pipe',(-8,3,-1),(-4,5,1),'pipe')]
        add('desert',name,'water_infrastructure' if name.startswith('water') else 'power_infrastructure',p,[16,31 if name=='water_vaporator' else 22 if name=='water_cistern' else 20 if name.startswith('power') else 15,14],{'service':[8,4,0],'inlet':[-8,4,0]},'Place on a native foundation. FurnitureSolids supplies the authored housing collision.')
    for name in ('canyon_strata','canyon_ledge','canyon_cave'):
        p=[]
        if name=='canyon_strata':
            for i in range(4): p.append(box('stratified facing',(-16,i*4,-2-i%2),(16,i*4+4,2+i%2),'rock' if i%2 else 'strata'))
        elif name=='canyon_ledge':
            p=[box('ledge root',(-16,0,-4),(16,5,2),'strata'),box('overhang',(-14,5,-4),(14,9,8),'rock'),box('eroded lip',(-11,9,-3),(12,11,6),'rock')]
        else:
            p=[box('left rock jamb',(-16,0,-4),(-11,30,4),'rock'),box('right rock jamb',(11,0,-4),(16,30,4),'strata'),box('cave brow',(-16,30,-4),(16,32,5),'rock'),box('brow ledge',(-12,30,0),(12,31,5),'strata')]
        add('desert',name,'canyon_rock',p,[32,32 if name=='canyon_cave' else 16 if name=='canyon_strata' else 11,9 if name=='canyon_cave' else 12 if name=='canyon_ledge' else 6],joins(),
            'Block-backed terrain trim; cave aperture remains clear.',[-11,0,11,30] if name=='canyon_cave' else None)
    return result


def corners(e, side):
    x,y,z=e['from']; X,Y,Z=e['to']
    return np.array({'south':[(X,Y,Z),(x,Y,Z),(x,y,Z),(X,y,Z)],'north':[(x,Y,z),(X,Y,z),(X,y,z),(x,y,z)],
        'east':[(X,Y,z),(X,Y,Z),(X,y,Z),(X,y,z)],'west':[(x,Y,Z),(x,Y,z),(x,y,z),(x,y,Z)],
        'up':[(x,Y,Z),(X,Y,Z),(X,Y,z),(x,Y,z)],'down':[(x,y,z),(X,y,z),(X,y,Z),(x,y,Z)]}[side],dtype=float)


def resolve(path, assets):
    d=json.loads(path.read_text())
    if parent:=d.get('parent'):
        base=resolve(assets/'models'/(parent.split(':')[1]+'.json'),assets)
        return base | d | {'textures':base.get('textures',{}) | d.get('textures',{})}
    return d


def render(instances, assets, path, size=700, az=.65):
    geometry=[]
    for route,offset in instances:
        model=resolve(assets/'models/item'/PREFIX/(route+'.json'),assets)
        tex=np.array(Image.open(assets/'textures'/(model['textures']['atlas'].split(':')[1]+'.png')))
        for e in model['elements']:
            for side,face in e['faces'].items():
                xyz=corners(e,side)-[8,0,8]+offset
                geometry.append((xyz,face,tex,e.get('light_emission',0)))
    right=np.array([math.cos(az),0,-math.sin(az)])
    up=np.array([-math.sin(az)*math.sin(.5),math.cos(.5),-math.cos(az)*math.sin(.5)])
    toward=np.cross(right,up); basis=np.array([right,-up,toward]).T
    points=np.concatenate([g[0] for g in geometry])@basis
    low,high=points[:,:2].min(0),points[:,:2].max(0)
    scale=(size-64)/max(high-low); center=(low+high)/2
    image=np.full((size,size,3),[29,38,44],dtype=np.uint8); depth=np.full((size,size),-np.inf)
    for xyz,face,tex,glow in geometry:
        normal=np.cross(xyz[1]-xyz[0],xyz[3]-xyz[0]); normal/=np.linalg.norm(normal)
        if normal@toward<=0: continue
        p=xyz@basis; p[:,:2]=(p[:,:2]-center)*scale+size/2
        u,v,U,V=np.array(face['uv'])*8
        coords=np.array([(u,v),(U,v),(U,V),(u,V)])
        shade=1 if glow else .65+.32*max(0,normal@np.array([.3,.8,.52]))
        for tri in ((0,1,2),(0,2,3)):
            q,t=p[list(tri)],coords[list(tri)]; a,b,c=q[:,:2]
            den=(b-a)[0]*(c-a)[1]-(b-a)[1]*(c-a)[0]
            if abs(den)<1e-8: continue
            lo=np.maximum(np.floor(q[:,:2].min(0)).astype(int),0); hi=np.minimum(np.ceil(q[:,:2].max(0)).astype(int),size-1)
            X,Y=np.meshgrid(np.arange(lo[0],hi[0]+1)+.5,np.arange(lo[1],hi[1]+1)+.5)
            r=np.stack([X,Y],-1)-a
            wb=(r[...,0]*(c-a)[1]-r[...,1]*(c-a)[0])/den
            wc=((b-a)[0]*r[...,1]-(b-a)[1]*r[...,0])/den; wa=1-wb-wc
            z=wa*q[0,2]+wb*q[1,2]+wc*q[2,2]
            tx=wa[...,None]*t[0]+wb[...,None]*t[1]+wc[...,None]*t[2]
            color=tex[np.clip(tx[...,1].astype(int),int(v),int(V)-1),np.clip(tx[...,0].astype(int),int(u),int(U)-1)]
            sl=np.s_[lo[1]:hi[1]+1,lo[0]:hi[0]+1]
            mask=(wa>=-1e-7)&(wb>=-1e-7)&(wc>=-1e-7)&(z>depth[sl])
            image[sl][mask]=(color*shade).astype(np.uint8)[mask]; depth[sl][mask]=z[mask]
    Image.fromarray(image).save(path)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pack',type=Path,default=ROOT/'src')
    parser.add_argument('--output',type=Path,default=ROOT/'art/planet-kit')
    parser.add_argument('--nexo',type=Path,default=ROOT.parent/'nexo/items/blockcraft_starwars_planet_kit.yml')
    args=parser.parse_args(); assets=args.pack/'assets/blockcraft'; out=args.output; out.mkdir(parents=True,exist_ok=True)
    kit=modules(); assert len(kit)==32
    manifest={'pack_layer':'starwars','front':'+Z','model_ground_origin':[8,0,8],'units_per_block':16,'snap_grid_units':16,
        'structural_span_units':32,'scale':[1,1,1],'raw_display_position_above_ground_blocks':.5,
        'nexo_translation':[0,0,0],'collision':'Authored blockcraft_collision recipes provide native backing, glazing and bounded smaller colliders through FurnitureSolids.',
        'objects':{},'assemblies':{}}
    nexo=[]; paths=[]
    for planet in PALETTES:
        path=assets/'textures/item'/PREFIX/planet/'atlas.png'; path.parent.mkdir(parents=True,exist_ok=True); atlas(planet).save(path); paths.append(path)
        metadata=Path(str(path)+'.mcmeta')
        write_json(metadata,{'animation':{'frametime':4,'width':128,'height':128,'interpolate':False}}); paths.append(metadata)
    for route,m in kit.items():
        path=assets/'models/item'/PREFIX/(route+'.json')
        texture='blockcraft:item/'+PREFIX+'/'+m['planet']+'/atlas'
        model={'ambientocclusion':True,'textures':{'atlas':texture,'particle':texture},'elements':m['elements']}
        if m['name']=='wall_patch':
            model={'parent':'blockcraft:item/'+PREFIX+'/desert/wall_straight','elements':m['elements']}
        write_json(path,model); paths.append(path)
        item=assets/'items'/PREFIX/(route+'.json')
        write_json(item,{'model':{'type':'minecraft:model','model':'blockcraft:item/'+PREFIX+'/'+route}}); paths.append(item)
        resolved=resolve(path,assets); assert not resolved.get('display')
        loaded_item=json.loads(item.read_text())
        assert loaded_item['model']['model']=='blockcraft:item/'+PREFIX+'/'+route
        loaded_texture=Image.open(assets/'textures'/(resolved['textures']['atlas'].split(':')[1]+'.png'))
        assert loaded_texture.mode=='RGB' and loaded_texture.size==(128,512)
        metadata=json.loads(Path(str(assets/'textures'/(resolved['textures']['atlas'].split(':')[1]+'.png'))+'.mcmeta').read_text())
        assert metadata['animation']['width']==metadata['animation']['height']==128
        allpoints=[]
        for e in resolved['elements']:
            assert all(-16<=a<b<=32 for a,b in zip(e['from'],e['to'])),route
            for side,f in e['faces'].items():
                assert f['texture']=='#atlas' and all(0<=n<=16 for n in f['uv'])
                u,v,U,V=f['uv']; assert u<U and v<V
                assert int(u//4)==int((U-1e-8)//4) and int(v//4)==int((V-1e-8)//4)
                allpoints.extend(corners(e,side).tolist())
            if m['opening']:
                x,y,X,Y=m['opening']; a=np.array(e['from'])-[8,0,8]; b=np.array(e['to'])-[8,0,8]
                if m['name']=='window_mullion' and e['name'].startswith('central mullion'): continue
                assert not (a[0]<X and b[0]>x and a[1]<Y and b[1]>y), (route,e['name'],'obstructed aperture')
        points=np.array(allpoints)-[8,0,8]; bounds=[points.min(0).tolist(),points.max(0).tolist()]
        extents=(points.max(0)-points.min(0)).tolist()
        for label,p in m['connectors'].items(): assert all(-16<=v<=32 for v in p), (route,label)
        assert all(e<=s for e,s in zip(extents,m['span'])), (route,extents,m['span'])
        manifest['objects'][route]={'family':m['family'],'name_de':LABELS[m['name']],'item_model':'blockcraft:'+PREFIX+'/'+route,'bounds_from_ground_units':bounds,
            'span_units':m['span'],'extents_units':extents,'connectors_from_ground_units':m['connectors'],'opening_xy_units':m['opening'],
            'support':m['support'],'elements':len(m['elements']),'barriers':[]}
        nexo += [f'blockcraft_planet_{m["planet"]}_{m["name"]}:',f'  itemname: \'<white>{LABELS[m["name"]]}\'', '  material: PAPER','  Components:',f'    item_model: blockcraft:{PREFIX}/{route}',
            '  Pack:',f'    model: blockcraft:item/{PREFIX}/{route}','  Mechanics:','    furniture:','      properties:','        display_transform: NONE','        cullable: false','        translation: 0,0,0','      restricted_rotation: VERY_STRICT','      hitbox:','        barriers: []','']
        render([(route,[0,0,0])],assets,out/(route.replace('/','_')+'.png'),360)
    definitions=yaml.safe_load('\n'.join(nexo))
    for route in kit:
        collision,properties=physical_planet(route)
        furniture=definitions['blockcraft_planet_'+route.replace('/','_')]['Mechanics']['furniture']
        furniture['blockcraft_collision']=collision
        model=json.loads((assets/'models/item'/PREFIX/(route+'.json')).read_text())
        light,light_paths=scenery_lights(assets,PREFIX+'/'+route,model)
        if light:furniture['lights']=light
        paths.extend(light_paths)
        furniture['properties'].update(properties)
        manifest['objects'][route]['physical_collision']=collision
        manifest['objects'][route]['nexo_properties']=furniture['properties']
    args.nexo.parent.mkdir(parents=True,exist_ok=True); args.nexo.write_text(yaml.safe_dump(definitions,sort_keys=False,allow_unicode=True)); paths.append(args.nexo)
    scenes={
        'kamino_assembly': [('kamino/window_mullion',[-32,0,0]),('kamino/window_rib',[0,0,0]),('kamino/window_corner',[32,0,0]),('kamino/platform_rail',[-32,0,16]),('kamino/platform_light',[0,0,16]),('kamino/platform_corner',[32,0,16]),('kamino/storm_drain',[-32,0,-4]),('kamino/storm_streak',[0,0,-4])],
        'desert_assembly': [('desert/entrance_recess',[0,0,0]),('desert/wall_patch',[-32,0,0]),('desert/wall_corner',[32,0,0]),('desert/roof_dome',[-32,32,0]),('desert/roof_rim',[0,32,0]),('desert/roof_corner',[32,32,0]),('desert/shade_awning',[0,0,22]),('desert/shade_pole',[-16,0,38]),('desert/shade_pole',[16,0,38]),('desert/water_cistern',[40,0,28]),('desert/water_pump',[24,0,28]),('desert/water_vaporator',[-40,0,28])],
        'storm_assembly': [('kamino/storm_drain',[-20,0,0]),('kamino/storm_seam',[0,0,0]),('kamino/storm_streak',[20,0,0]),('kamino/storm_panel',[40,0,0])],
        'water_assembly': [('desert/water_vaporator',[-32,0,0]),('desert/water_pump',[0,0,0]),('desert/water_cistern',[16,0,0])],
    }
    for name,instances in scenes.items():
        manifest['assemblies'][name]=[{'module':r,'ground_offset_units':p,'yaw_degrees':0} for r,p in instances]
        render(instances,assets,out/(name+'.png'),1500)
        render(instances,assets,out/(name+'_rear.png'),1500,math.pi+.65)
    connections=[('kamino_assembly',0,'right',1,'left'),('kamino_assembly',1,'right',2,'left'),
        ('kamino_assembly',3,'right',4,'left'),('kamino_assembly',4,'right',5,'left'),
        ('desert_assembly',10,'service',9,'inlet'),('water_assembly',1,'service',2,'inlet')]
    for scene,a,ca,b,cb in connections:
        ra,pa=scenes[scene][a]; rb,pb=scenes[scene][b]
        assert np.array_equal(np.array(pa)+kit[ra]['connectors'][ca],np.array(pb)+kit[rb]['connectors'][cb]),(scene,ra,rb)
    manifest['verified_assembly_connections']=connections
    for planet in PALETTES:
        entries=[r for r in kit if r.startswith(planet+'/')]; cols=4; rows=math.ceil(len(entries)/cols)
        sheet=Image.new('RGB',(cols*360,rows*390),(29,38,44)); draw=ImageDraw.Draw(sheet)
        for i,r in enumerate(entries):
            x,y=i%cols*360,i//cols*390
            sheet.paste(Image.open(out/(r.replace('/','_')+'.png')),(x,y)); draw.text((x+8,y+365),r.split('/')[1],fill='#e0dfce')
        sheet.save(out/(planet+'_contact.png'))
    paths.extend(sorted(out.glob('*.png')))
    manifest['materials']={'uv_origin':'top-left','atlas_size':[128,512],'animation_frame_size':[128,128],'animation_frames':4,'frame_ticks':4,'tile_size_pixels':32,'texels_per_model_unit':1,'tile_order':list(MATERIALS),'alpha':'RGB fully opaque; window openings are empty geometry, no glass stacks.','water':'Animated decorative runoff and level gauges; no world liquid simulation.'}
    manifest['validation']={'module_count':len(kit),'families':sorted(set(m['family'] for m in kit.values())),'native_bounds':'Every exported cuboid in [-16,32]','uv':'Face UVs retain texel density inside their material tile. Top origin.','apertures':'No elements overlap declared XY opening, except the named window central mullion.','routes':'Every item resolves to exported model and palette atlas.'}
    write_json(out/'manifest.json',manifest)
    hashes={p.relative_to(ROOT.parent).as_posix() if p.is_relative_to(ROOT.parent) else p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}
    write_json(out/'sha256.json',hashes)
    print(json.dumps(manifest['validation'],indent=2))


if __name__=='__main__':
    main()
