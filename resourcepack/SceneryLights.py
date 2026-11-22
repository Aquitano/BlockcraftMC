"""Nexo light sources and matching unlit native item models."""
import copy
import json
import math


def configure(assets, route, model, scale=1, blocked=()):
    emitters=[e for e in model.get('elements',[]) if e.get('light_emission',0)>0]
    if not emitters:
        return None,[]
    strength=max(e['light_emission'] for e in emitters)
    emitter=max(emitters,key=lambda e:e['light_emission'])
    center=[(a+b)/2 for a,b in zip(emitter['from'],emitter['to'])]
    offset=[math.floor((v-8)*scale/16+(.5 if axis!=1 else 0)) for axis,v in enumerate(center)]
    offset[0]=-offset[0]
    while tuple(offset) in blocked:
        offset[1]+=1
    off=copy.deepcopy(model)
    for part in off.get('elements',[]):
        if part.pop('light_emission',0):part['shade']=True
    paths=[]
    for kind,value in [('models/item',off),('items',{'model':{'type':'minecraft:model','model':'blockcraft:item/'+route+'_off'}})]:
        p=assets/kind/(route+'_off.json');p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(value,indent=2)+'\n');paths.append(p)
    return {'lights':[','.join(map(str,offset))+' '+str(max(7,min(15,strength)))], 'toggleable':True,
            'toggled_item_model':'blockcraft:'+route+'_off'},paths
