import unittest

from ModelSurfaces import exposed, overlaps, cropped_uv


def cube(a,b,material='body'):
    return {'from':list(a),'to':list(b),'faces':{s:{'uv':[0,0,16,16],'texture':'#'+material}
            for s in ('north','south','east','west','up','down')}}


class ModelSurfacesTest(unittest.TestCase):
    def test_crops_keep_the_original_pixels_on_each_minecraft_face(self):
        original=cube((0,0,0),(16,16,16))
        expected={'north':(0,[12,0,16,16]),'south':(0,[0,0,4,16]),
                  'east':(2,[12,0,16,16]),'west':(2,[0,0,4,16]),
                  'up':(2,[0,12,16,16]),'down':(2,[0,0,16,4])}
        for side,(axis,uv) in expected.items():
            with self.subTest(side=side):
                fragment=cube((0,0,0),(16,16,16));fragment['to'][axis]=4
                self.assertEqual(cropped_uv(original,fragment,side,original['faces'][side]),uv)

    def test_partial_overlay_has_one_visible_surface_and_preserves_uv_scale(self):
        body=cube((0,0,0),(16,16,16))
        trim=cube((4,4,14),(12,12,16),'trim')
        self.assertTrue(overlaps([body,trim]))
        result,sources=exposed([body,trim])
        self.assertEqual(overlaps(result),[])
        front=[(part,sources[i]) for i,part in enumerate(result) if 'south' in part['faces']]
        self.assertEqual(sum((p['to'][0]-p['from'][0])*(p['to'][1]-p['from'][1]) for p,_ in front),256)
        for p,source in front:
            if source == 0:
                u,v,U,V=p['faces']['south']['uv']
                self.assertEqual((U-u,V-v),(p['to'][0]-p['from'][0],p['to'][1]-p['from'][1]))

    def test_touching_cubes_have_no_internal_faces(self):
        result,_=exposed([cube((0,0,0),(8,16,16)),cube((8,0,0),(16,16,16))])
        self.assertFalse(any('east' in p['faces'] and p['to'][0] == 8 for p in result))
        self.assertFalse(any('west' in p['faces'] and p['from'][0] == 8 for p in result))

    def test_coincident_boxes_keep_the_later_material(self):
        result,sources=exposed([cube((0,0,0),(16,16,16)),cube((0,0,0),(16,16,16),'trim')])
        self.assertEqual(sources,[1])
        self.assertEqual(len(result),1)
        self.assertEqual(overlaps(result),[])


if __name__=='__main__':
    unittest.main()
