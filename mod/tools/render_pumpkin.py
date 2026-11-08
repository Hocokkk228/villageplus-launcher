"""Render the exported Minecraft JSON itself, including its UVs and light-emitting faces.
This is an asset preview, not an in-game screenshot. No alternate display geometry.
"""
from pathlib import Path
import json
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from zipfile import ZipFile
from io import BytesIO

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/halloween"


def render(yaw, elevation, output, night=False, model_name="pumpkin_lantern", scale=43, center_y=7.5, model_data=None):
    model = model_data if model_data is not None else json.loads((ASSETS / f"models/block/{model_name}.json").read_text(encoding="utf-8"))
    textures = {}
    for k,v in model["textures"].items():
        if v.startswith("minecraft:"):
            with ZipFile(Path.home()/".gradle/caches/fabric-loom/1.21.11/minecraft-client.jar") as archive:
                im=Image.open(BytesIO(archive.read("assets/minecraft/textures/"+v.split(":")[1]+".png")))
        else:
            im=Image.open(ASSETS/("textures/"+v.split(":")[1]+".png"))
        textures[k]=np.asarray(im.convert("RGB"))
    W, H = 1000, 940
    yy, xx = np.mgrid[:H, :W]
    vignette = np.exp(-((xx-W*.5)**2/(W*.65)**2 + (yy-H*.45)**2/(H*.6)**2))
    bg = np.array([15, 19, 24]) + vignette[..., None] * np.array([16, 15, 16])
    rgb = bg.astype(float)
    depth = np.full((H, W), -np.inf)
    emission = np.zeros((H, W, 3), dtype=np.uint8)
    yaw, elevation = math.radians(yaw), math.radians(elevation)
    eye = np.array([math.sin(yaw)*math.cos(elevation), math.sin(elevation), -math.cos(yaw)*math.cos(elevation)])
    right = np.array([math.cos(yaw), 0, math.sin(yaw)])
    up = np.cross(right, eye)
    normals = {"north": [0,0,-1], "south": [0,0,1], "east": [1,0,0], "west": [-1,0,0], "up": [0,1,0], "down": [0,-1,0]}
    light = np.array([-.35, .8, -.55])
    light /= np.linalg.norm(light)
    shadow = Image.new("RGBA", (W, H))
    d = ImageDraw.Draw(shadow)
    d.ellipse((210, 703, 790, 825), fill=(0,0,0,110))
    shadow = shadow.filter(ImageFilter.GaussianBlur(24))
    rgb = np.asarray(Image.alpha_composite(Image.fromarray(rgb.astype(np.uint8)).convert("RGBA"), shadow).convert("RGB")).astype(float).copy()
    for elem in model["elements"]:
        x,y,z = elem["from"]
        X,Y,Z = elem["to"]
        verts = {
            "north": [[X,Y,z],[x,Y,z],[x,y,z],[X,y,z]],
            "south": [[x,Y,Z],[X,Y,Z],[X,y,Z],[x,y,Z]],
            "west": [[x,Y,z],[x,Y,Z],[x,y,Z],[x,y,z]],
            "east": [[X,Y,Z],[X,Y,z],[X,y,z],[X,y,Z]],
            "up": [[x,Y,z],[X,Y,z],[X,Y,Z],[x,Y,Z]],
            "down": [[x,y,Z],[X,y,Z],[X,y,z],[x,y,z]],
        }
        rotation=np.eye(3)
        origin=np.zeros(3)
        if elem.get('rotation'):
            r=elem['rotation'];angle=math.radians(r['angle']);c,s=math.cos(angle),math.sin(angle)
            origin=np.array(r['origin'])
            rotation=np.array([[c,0,s],[0,1,0],[-s,0,c]]) if r['axis']=='y' else np.array([[1,0,0],[0,c,-s],[0,s,c]]) if r['axis']=='x' else np.array([[c,-s,0],[s,c,0],[0,0,1]])
        for direction, face in elem["faces"].items():
            normal = rotation @ np.array(normals[direction])
            if normal @ eye <= 0:
                continue
            pts = (np.array(verts[direction])-origin) @ rotation.T + origin - np.array([8,center_y,8])
            screen = np.column_stack((W/2 + pts@right*scale, H*.49 - pts@up*scale, pts@eye))
            u,v,U,V = face["uv"]
            uv = np.array([[u,v],[U,v],[U,V],[u,V]]) / 16
            texname = face["texture"][1:]
            tex = textures[texname]
            glow = elem.get("light_emission", 0) == 15
            brightness = 1 if glow else (.30 if night else .55) + (.28 if night else .4) * max(0, float(normal @ light))
            for tri in ([0,1,2], [0,2,3]):
                p = screen[tri]
                t = uv[tri]
                xmin = max(0, int(np.floor(p[:,0].min())))
                xmax = min(W-1, int(np.ceil(p[:,0].max())))
                ymin = max(0, int(np.floor(p[:,1].min())))
                ymax = min(H-1, int(np.ceil(p[:,1].max())))
                if xmax < xmin or ymax < ymin:
                    continue
                gx, gy = np.meshgrid(np.arange(xmin,xmax+1)+.5, np.arange(ymin,ymax+1)+.5)
                denominator = (p[1,1]-p[2,1])*(p[0,0]-p[2,0]) + (p[2,0]-p[1,0])*(p[0,1]-p[2,1])
                if abs(denominator) < 1e-9:
                    continue
                a = ((p[1,1]-p[2,1])*(gx-p[2,0]) + (p[2,0]-p[1,0])*(gy-p[2,1]))/denominator
                b = ((p[2,1]-p[0,1])*(gx-p[2,0]) + (p[0,0]-p[2,0])*(gy-p[2,1]))/denominator
                c = 1-a-b
                zz = a*p[0,2]+b*p[1,2]+c*p[2,2]
                target = np.s_[ymin:ymax+1,xmin:xmax+1]
                mask = (a>=-1e-8)&(b>=-1e-8)&(c>=-1e-8)&(zz>depth[target])
                tx = np.floor((a*t[0,0]+b*t[1,0]+c*t[2,0])*tex.shape[1]).astype(int) % tex.shape[1]
                ty = np.floor((a*t[0,1]+b*t[1,1]+c*t[2,1])*tex.shape[0]).astype(int) % tex.shape[0]
                color = tex[ty,tx].astype(float) * brightness
                rgb[target][mask] = color[mask]
                depth[target][mask] = zz[mask]
                emission[target][mask] = tex[ty,tx][mask] if glow else 0
    # Subtle photographic bloom is confined to the preview; vanilla uses emissive faces.
    bloom = np.asarray(Image.fromarray(emission).filter(ImageFilter.GaussianBlur(10))).astype(float)
    rgb = np.clip(rgb + bloom*.16, 0, 255).astype(np.uint8)
    Image.fromarray(rgb).save(ROOT / "art" / output)


if __name__ == "__main__":
    render(24, 19, "pumpkin-preview.png")
    render(0, 8, "pumpkin-front.png", night=True)
    render(140, 24, "pumpkin-back.png")
    print("Rendered model previews: perspective, front/night, rear.")
