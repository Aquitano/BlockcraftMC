"""Remove hidden and competing faces without changing a cuboid union's outline."""
import copy

SIDES = {'west': (0, -1), 'east': (0, 1), 'down': (1, -1), 'up': (1, 1), 'north': (2, -1), 'south': (2, 1)}
UV_AXES = {'north': (0, 1, True, True), 'south': (0, 1, False, True),
           'east': (2, 1, True, True), 'west': (2, 1, False, True),
           'up': (0, 2, False, True), 'down': (0, 2, False, False)}
EPSILON = 1e-7


def subtract(rect, cut):
    x, y, X, Y = rect
    a, b, A, B = max(x, cut[0]), max(y, cut[1]), min(X, cut[2]), min(Y, cut[3])
    if A-a <= EPSILON or B-b <= EPSILON:
        return [rect]
    return [r for r in ((x,y,a,Y), (A,y,X,Y), (a,y,A,b), (a,B,A,Y))
            if r[2]-r[0] > EPSILON and r[3]-r[1] > EPSILON]


def cropped_uv(original, fragment, side, face):
    uaxis, vaxis, flip_u, flip_v = UV_AXES[side]
    def interval(axis, flip):
        lo, hi = original['from'][axis], original['to'][axis]
        a = (fragment['from'][axis]-lo)/(hi-lo)
        b = (fragment['to'][axis]-lo)/(hi-lo)
        return (1-b, 1-a) if flip else (a,b)
    a,A = interval(uaxis,flip_u)
    b,B = interval(vaxis,flip_v)
    rotation = face.get('rotation',0)
    if rotation == 90:
        a,b,A,B = b,1-A,B,1-a
    elif rotation == 180:
        a,b,A,B = 1-A,1-B,1-a,1-b
    elif rotation == 270:
        a,b,A,B = 1-B,a,1-b,A
    u,v,U,V = face['uv']
    return [u+(U-u)*a, v+(V-v)*b, u+(U-u)*A, v+(V-v)*B]


def exposed(elements):
    result, sources = [], []
    for index, original in enumerate(elements):
        if original.get('rotation'):
            result.append(copy.deepcopy(original)); sources.append(index)
            continue
        kept = copy.deepcopy(original)
        patches = []
        for side, face in original['faces'].items():
            axis, sign = SIDES[side]
            tangents = [k for k in range(3) if k != axis]
            plane = original['to' if sign>0 else 'from'][axis]
            rect = tuple(original[key][k] for key in ('from','to') for k in tangents)
            visible = [rect]
            for other_index, other in enumerate(elements):
                if other_index == index or other.get('rotation'):
                    continue
                lo, hi = other['from'][axis], other['to'][axis]
                occludes = lo <= plane+EPSILON and hi > plane+EPSILON if sign>0 else hi >= plane-EPSILON and lo < plane-EPSILON
                coplanar = abs((hi if sign>0 else lo)-plane) <= EPSILON
                if not occludes and not (coplanar and other_index>index):
                    continue
                cut = tuple(other[key][k] for key in ('from','to') for k in tangents)
                visible = [piece for fragment in visible for piece in subtract(fragment,cut)]
                if not visible:
                    break
            if visible == [rect]:
                continue
            del kept['faces'][side]
            for piece in visible:
                fragment = copy.deepcopy(original)
                for n,k in enumerate(tangents):
                    fragment['from'][k], fragment['to'][k] = piece[n],piece[n+2]
                fragment['faces'] = {side:dict(face,uv=cropped_uv(original,fragment,side,face))}
                patches.append(fragment)
        if kept['faces']:
            result.append(kept); sources.append(index)
        result.extend(patches); sources.extend([index]*len(patches))
    return result, sources


def overlaps(elements):
    conflicts = []
    for i,a in enumerate(elements):
        if a.get('rotation'):
            continue
        for j in range(i+1,len(elements)):
            b = elements[j]
            if b.get('rotation'):
                continue
            for side,(axis,sign) in SIDES.items():
                if side not in a['faces'] or side not in b['faces']:
                    continue
                key = 'to' if sign>0 else 'from'
                if abs(a[key][axis]-b[key][axis]) > EPSILON:
                    continue
                if all(min(a['to'][k],b['to'][k])-max(a['from'][k],b['from'][k])>EPSILON for k in range(3) if k != axis):
                    conflicts.append((i,j,side))
    return conflicts
