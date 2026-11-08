"""Small original pixel sprites; deterministic, no external texture dependencies."""
from pathlib import Path
import struct,zlib,random
out=Path(__file__).resolve().parents[1]/'src/main/resources/assets/halloween/textures'
def png(path,w,h,pixels):
 def chunk(t,b):return struct.pack('>I',len(b))+t+b+struct.pack('>I',zlib.crc32(t+b)&0xffffffff)
 raw=b''.join(b'\0'+bytes(v for px in pixels[y*w:(y+1)*w] for v in px)for y in range(h));path.parent.mkdir(parents=True,exist_ok=True)
 path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',w,h,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw))+chunk(b'IEND',b''))
# Coordinates are the surfaces actually sampled by the 64x64 villager model.
# The custom forearm uses the body cloth patch at u=30.5..33.5, v=31.5..36.5.
marks = [
    [(17, 12, 2, 1), (27, 29, 3, -1), (7, 32, 2, 1), (31, 34, 2, 1)],
    [(6, 13, 3, 1), (19, 33, 4, 1), (43, 30, 4, -1), (10, 39, 3, -1),
     (2, 34, 3, 1), (31, 33, 2, -1), (22, 41, 4, 1), (28, 43, 3, -1)],
    [(19, 8, 3, 1), (12, 13, 3, -1), (34, 27, 4, 1), (42, 37, 5, 1),
     (21, 38, 5, 1), (29, 33, 5, -1), (5, 37, 3, 1), (13, 43, 3, -1),
     (31, 35, 3, 1), (23, 45, 3, -1), (35, 46, 3, 1), (8, 35, 3, -1)],
]
for stage in (1,2,3):
    pixels=[(0,0,0,0)]*(64*64)
    rng=random.Random(710)
    def paint(x,y,color):
        if 0<=x<64 and 0<=y<64:
            pixels[y*64+x]=color
    for layer in marks[:stage]:
        for x,y,length,direction in layer:
            for step in range(length):
                px=x+direction*(step//2)
                py=y+step
                paint(px-1,py,(67,25,49,103+rng.randrange(50)))
                paint(px+1,py,(107,38,58,125+rng.randrange(65)))
                paint(px,py,(126+rng.randrange(55),21+rng.randrange(22),31+rng.randrange(22),218))
                if stage>1 and step==length-1:
                    paint(px,py+1,(103,21,31,168))
    png(out/'entity/villager'/f'wounds_{stage}.png',64,64,pixels)
p=[(0,0,0,0)]*256
for x,y,r in [(7,8,3),(11,4,1),(3,3,1),(12,12,1),(4,13,1)]:
 for xx in range(x-r,x+r+1):
  for yy in range(y-r,y+r+1):
   if (xx-x)**2+(yy-y)**2<=r*r and 0<=xx<16 and 0<=yy<16:p[yy*16+xx]=(165,20,34,255)
png(out/'particle/blood_splash.png',16,16,p)
print('Generated 3 wound stages and blood splash sprite')
