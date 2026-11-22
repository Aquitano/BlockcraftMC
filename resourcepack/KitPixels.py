"""Material tiles for the native construction kits."""
import random

from PIL import Image, ImageDraw


def tile(color, material, size=16, seed=0, phase=0):
    rgb = tuple(bytes.fromhex(color))
    image = Image.new('RGB', (16, 16), rgb)
    draw = ImageDraw.Draw(image)
    rng = random.Random(seed)

    def tone(delta, tint=(0, 0, 0)):
        return tuple(max(0, min(255, c + delta + t)) for c, t in zip(rgb, tint))

    if material in ('plaster', 'patch', 'rock', 'strata', 'fur', 'scale', 'leather', 'horn'):
        for i in range(14 if material in ('fur', 'scale') else 9):
            x, y = rng.randrange(-2, 14), rng.randrange(-2, 14)
            w, h = rng.randrange(3, 7), rng.randrange(2, 5)
            delta = (-16, -9, 7, 13)[i % 4]
            draw.polygon([(x, y+1), (x+1, y), (x+w-1, y), (x+w, y+1),
                          (x+w-1, y+h), (x+1, y+h), (x+1, y+h-1), (x, y+h-1)], fill=tone(delta))
        if material in ('plaster', 'patch'):
            for x, y, w in ((1, 12, 4), (10, 7, 3), (6, 2, 2)):
                draw.line((x, y, x+w, y, x+w, y+2), fill=tone(-28, (4, -2, -5)))
                draw.line((x+1, y-1, x+w, y-1), fill=tone(22, (3, 0, -4)))
            for x, width in ((1,4),(10,3)):
                draw.rectangle((x,14,x+width,15), fill=tone(-12))
            for x, y in ((1, 13), (2, 12), (8, 14), (12, 13)):
                draw.point((x, y), fill=tone(-24))
        elif material in ('rock', 'strata'):
            for y in (3, 8, 13):
                draw.line((0, y, 4, y, 5, y+1, 11, y+1, 12, y, 15, y), fill=tone(-32))
                draw.line((1, y-1, 5, y-1, 6, y, 11, y), fill=tone(20))
            draw.line((5, 4, 5, 6, 7, 6, 7, 8), fill=tone(-22))
        elif material == 'fur':
            for y in (1, 6, 11):
                for x in range(1 + (y % 3), 14, 4):
                    draw.line((x, y, x, y+2, x+1, y+2, x+1, y+4), fill=tone(-26))
                    draw.line((x+1, y, x+2, y), fill=tone(21))
        elif material == 'scale':
            for y in range(1, 15, 4):
                for x in range(-2 + (y // 4 % 2)*3, 16, 6):
                    draw.line((x, y, x+3, y, x+4, y+1, x+3, y+3, x+1, y+3), fill=tone(-26))
                    draw.line((x+1, y+1, x+3, y+1), fill=tone(22))
        elif material == 'horn':
            for y in (3, 8, 13):
                draw.line((0, y, 6, y, 8, y+1, 15, y+1), fill=tone(-24))
                draw.line((1, y-1, 6, y-1), fill=tone(21))
        else:
            draw.rectangle((1, 1, 14, 14), outline=tone(-24))
            for x in (3, 7, 11):
                draw.line((x, 2, x+1, 2), fill=tone(28))
                draw.line((x, 13, x+1, 13), fill=tone(28))
    elif material == 'water':
        draw.rectangle((0, 0, 15, 15), fill=tone(-14, (-8, 1, 10)))
        for x, length in ((2, 7), (7, 11), (11, 5), (14, 9)):
            y = (phase*4 + x*3) % 16
            for d in range(length):
                draw.point((x, (y+d) % 16), fill=tone(22 if d < 2 else 4, (-2, 10, 15)))
                if d < 3 and x < 15:
                    draw.point((x+1, (y+d) % 16), fill=tone(45, (0, 8, 10)))
    elif material == 'cloth':
        for y in range(16):
            if y % 4 == 0:
                draw.line((0, y, 15, y), fill=tone(-13))
        for x in (2, 10):
            draw.rectangle((x, 0, x+1, 15), fill=tone(18))
        draw.line((0, 14, 15, 14), fill=tone(-30))
        for x in range(1, 15, 3):
            draw.point((x, 13), fill=tone(34))
    elif material == 'light':
        draw.rectangle((0, 0, 15, 15), fill=tone(-36))
        draw.rectangle((1, 2, 14, 13), fill=tone(-12))
        draw.rectangle((2, 3, 13, 7), fill=tone(25))
        draw.line((3, 4, 12, 4), fill=tone(55))
        for x in (5, 10):
            draw.line((x, 2, x, 13), fill=tone(-28))
    elif material == 'hazard':
        for y in range(16):
            for x in range(16):
                if (x+y) % 8 < 4:
                    draw.point((x, y), fill='#26383f')
        draw.line((0, 0, 15, 0), fill=tone(24))
        draw.line((0, 15, 15, 15), fill=tone(-34))
    else:
        draw.rectangle((0, 0, 15, 15), outline=tone(-28))
        draw.line((1, 1, 14, 1), fill=tone(22))
        draw.line((1, 1, 1, 13), fill=tone(11))
        draw.line((2, 14, 14, 14), fill=tone(-15))
        if material in ('enamel', 'steel', 'panel'):
            for x, y, w, h, delta in ((3, 3, 5, 2, 6), (8, 7, 5, 3, -7), (2, 10, 5, 2, -5)):
                draw.rectangle((x, y, x+w, y+h), fill=tone(delta))
            for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
                draw.point((x, y), fill=tone(-42))
                draw.point((x, y-1), fill=tone(30))
            draw.line((10, 4, 12, 4, 12, 5), fill=tone(30))
            if material == 'enamel':
                for x,y,length in ((3,3,4),(12,8,3)):
                    draw.line((x,y,x,y+length),fill=tone(-16,(-1,2,3)))
                    draw.line((x+1,y,x+1,y+length-1),fill=tone(8))
        if material in ('vent', 'coil'):
            for y in range(3, 14, 3):
                draw.rectangle((2, y, 13, y+1), fill=tone(-38))
                draw.line((3, y-1, 12, y-1), fill=tone(29))
        elif material == 'pipe':
            for x, delta in ((2, -24), (4, 20), (7, 7), (12, -30)):
                draw.line((x, 1, x, 14), fill=tone(delta))
            for y in (3, 12):
                draw.line((1, y, 14, y), fill=tone(-22))
                draw.line((2, y-1, 13, y-1), fill=tone(23))
        elif material == 'screen':
            draw.rectangle((2, 2, 13, 13), fill=tone(-25))
            draw.line((3, 10, 5, 10, 5, 6, 10, 6, 10, 4, 12, 4), fill=tone(55, (-8, 16, 17)))
            draw.line((3, 3, 6, 3), fill='#dfb36a')
        elif material == 'keys':
            for y in (3, 7, 11):
                for x in (2, 6, 10):
                    draw.rectangle((x, y, x+2, y+1), fill=tone(42))
                    draw.line((x, y+2, x+2, y+2), fill=tone(-34))
        elif material == 'socket':
            draw.rectangle((3, 3, 12, 12), outline=tone(42))
            draw.rectangle((5, 5, 10, 10), fill=tone(-28))
        elif material == 'scar':
            draw.line((2, 1, 8, 6, 6, 10, 13, 14), fill=tone(-46), width=2)
            draw.line((1, 1, 7, 6, 5, 10), fill=tone(24))
    return image.resize((size, size), Image.Resampling.NEAREST)
