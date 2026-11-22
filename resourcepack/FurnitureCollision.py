"""Authored block backing and bounded native collider recipes for scenery."""
import itertools
import math


def cube_fill(low, high):
    size=max(min(high[0]-low[0],high[2]-low[2]),.625)
    axes=[]
    for axis,(a,b) in enumerate(zip(low,high)):
        count=max(1,math.ceil((b-a)/size-1e-8))
        start=b-size if axis==1 else (a+b-size)/2
        axes.append([start] if count==1 else [a+i*(b-a-size)/(count-1) for i in range(count)])
    result=[]
    for x,y,z in itertools.product(*axes):
        result.append(f'{x+size/2:.6f},{y:.6f},{z+size/2:.6f} {size:.6f}')
    assert len(result)<=32,(low,high,len(result))
    return result


def recipe(blocks=(), boxes=(), *, grid=False, shift=None, keep=False, mode='solid'):
    result={'mode':mode,'blocks':list(blocks),'cubes':[c for low,high in boxes for c in cube_fill(low,high)]}
    if grid:result['grid']=True
    if shift:result['model_shift']=','.join(map(str,shift))
    if keep:result['keep_existing']=True
    return result


def shared(kind, cores):
    if cores:
        return recipe([','.join(map(str,c))+' minecraft:barrier' for c in cores])
    if kind in ('floor_panel','floor_grate','service_strip','damaged_tile'):
        return recipe(['0,-1,0 minecraft:smooth_stone'],keep=True)
    if kind=='door_open':
        return recipe(mode='open-door-preview')
    if kind=='door_jammed':
        return recipe(boxes=[((-.5,.875,-.5),(.5,1.875,.5))])
    if kind in ('wall_lamp','ceiling_lamp','landing_lamp','warning_beacon'):
        return recipe(mode='decoration')
    raise KeyError(kind)


def planet(name):
    theme='minecraft:smooth_quartz' if name.startswith('kamino/') else 'minecraft:smooth_sandstone'
    kind=name.split('/')[1]
    shift=(-.499,.001,-.499)
    blocks=[];boxes=[]
    if kind.startswith('window_'):
        blocks=[f'{x},{y},-1 minecraft:light_blue_stained_glass' for x in (-1,0) for y in (0,1)]
    elif kind.startswith('wall_'):
        blocks=[f'{x},{y},-1 {theme}' for x in (-1,0) for y in (0,1)]
        if kind=='wall_corner':blocks += [f'0,{y},0 {theme}' for y in (0,1)]
    elif kind.startswith('storm_'):
        shift=(0,.001,-.499)
        return recipe(['0,0,-1 '+theme],grid=False,shift=shift,keep=True,mode='wall-detail'),{'translation':'0,0.001,-0.499'}
    elif kind.startswith('entrance_') or kind=='canyon_cave':
        boxes=[((x,0,-.15625),(x+.3125,1.875,.15625)) for x in (-1,.6875)]
        blocks=[f'{x},2,-1 {theme}' for x in (-1,0)]
        if kind=='entrance_shutter':boxes=boxes[:1]
    elif kind.startswith('platform_'):
        blocks=[f'{x},-1,-1 {theme}' for x in (-1,0)]
        if kind=='platform_rail':boxes=[((-1,.375,-.5),(1,1.375,.5))]
        else:boxes=[((-1,0,-.125),(1,.25,.125))]
    elif kind.startswith('roof_'):
        blocks=[f'{x},-1,{z} {theme}' for x in (-1,0) for z in (-1,0)]
        if kind=='roof_dome':boxes=[((-.5,0,-.5),(.5,1,.5))]
        elif kind=='roof_step':
            boxes=[((-1,0,-1),(1,.5,0)),((-1,0,0),(1,.875,1))]
    elif kind.startswith('canyon_'):
        blocks=[f'{x},0,-1 minecraft:red_sandstone' for x in (-1,0)]
        if kind=='canyon_ledge':boxes=[((-.75,0,0),(.75,.5,.5))]
    elif kind.startswith('shade_'):
        if kind=='shade_pole':boxes=[((-.125,0,-.125),(.125,2,.125))]
        else:
            blocks=[f'{x},-1,{z} {theme}' for x in (-1,0) for z in (-1,0)]
        return recipe(blocks,boxes,grid=True,shift=shift,keep=True,mode='canopy' if kind!='shade_pole' else 'post'),{'translation':'-0.499,0.001,-0.499'}
    else:
        bounds={'water_vaporator':((-.3125,0,-.3125),(.3125,.625,.3125)),
                'water_cistern':((-.375,0,-.375),(.375,1.375,.375)),
                'water_pump':((-.3125,0,-.3125),(.3125,.875,.3125)),
                'power_generator':((-.4375,0,-.375),(.4375,.875,.375)),
                'power_distribution':((-.3125,0,-.25),(.3125,1.25,.25))}
        return recipe(boxes=[bounds[kind]]),{}
    return recipe(blocks,boxes,grid=True,shift=shift,keep=True),{'translation':'-0.499,0.001,-0.499'}


def actor(name):
    bounds={'astromech':((-.3125,.25,-.25),(.3125,1.25,.25)),
            'gonk':((-.375,.4375,-.375),(.375,1.3125,.375)),
            'mouse':((-.25,.125,-.375),(.25,.5,.375)),
            'bantha':((-.75,.5,-1),(.75,2.25,1)),
            'dewback':((-.5,.4375,-.75),(.5,1.375,.75)),
            'womp_rat':((-.25,.1875,-.375),(.25,.625,.5))}
    return recipe(boxes=[bounds[name]],mode='static-actor')
