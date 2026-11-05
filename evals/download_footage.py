"""Download the open-licence eval clips from Wikimedia Commons into scratch/footage/ (git-ignored).

    python evals/download_footage.py            # all clips
    python evals/download_footage.py assam      # only names containing "assam"

Needs opencv (already installed with ai-service). Each clip is trimmed to its first 15 s and at most 960 px wide.
Several source files are large (up to 300 MB): the first run takes a while. Licences are in evals/footage.md.
The "river_approach_15s.mp4" clip is NOT here: its source and licence are unknown, so it is not redistributed.
"""
import json
import sys
import urllib.parse
import urllib.request
from pathlib import Path

import cv2

OUT = Path(__file__).resolve().parents[1] / "scratch" / "footage"
UA = {"User-Agent": "GajaMitraEvals/1.0 (research; see evals/footage.md)"}  # Commons refuses anonymous clients

# output name -> (Commons file title, licence)
CLIPS = {
    "hurulu_15s.mp4": ("Sri Lankan Elephants in Hurulu Eco Park 01.webm", "CC BY-SA 3.0"),
    "kaziranga_1.mp4": ("Indian elephant in Kaziranga National Park March 2025 by Tisha Mukherjee 01.mpg", "CC BY-SA 4.0"),
    "kaziranga_2.mp4": ("Indian elephant in Kaziranga National Park March 2025 by Tisha Mukherjee 02.mpg", "CC BY-SA 4.0"),
    "pinnawala_15s.mp4": ("Elephants in Sri Lanka by McKay Savage.ogv", "CC BY 2.0"),
    "pinnawala_bathing_15s.mp4": ("Pinnawala-Elephant-Orphanage-03.webm", "CC BY-SA 3.0"),
    "assam_herd_15s.mp4": ("A herd of elephant of Hoollongapar Gibbon Sanctuary, Jorhat - Assam.webm", "CC BY 4.0"),
    "paddy_terraces.mp4": ("Paddy Fields.webm", "CC BY-SA 4.0"),
    "grass_field.mp4": ("A rice field.webm", "CC BY-SA 3.0"),
    "empty_forest_road_15s.mp4": ("Elephants crossing road.webm", "CC0"),
}


def commons_url(title):
    query = urllib.parse.urlencode({"action": "query", "titles": f"File:{title}", "prop": "imageinfo", "iiprop": "url", "format": "json"})
    with urllib.request.urlopen(urllib.request.Request(f"https://commons.wikimedia.org/w/api.php?{query}", headers=UA)) as r:
        page = next(iter(json.load(r)["query"]["pages"].values()))
    return page["imageinfo"][0]["url"]


def trim(src, dst):
    cap = cv2.VideoCapture(str(src))
    fps = cap.get(cv2.CAP_PROP_FPS) or 25
    w, h = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH)), int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    nw = min(960, w)
    size = (nw, int(nw * h / w))
    out = cv2.VideoWriter(str(dst), cv2.VideoWriter_fourcc(*"mp4v"), fps, size)
    for _ in range(int(fps * 15)):
        ok, frame = cap.read()
        if not ok:
            break
        out.write(cv2.resize(frame, size))
    out.release()


def main(pattern=""):
    OUT.mkdir(parents=True, exist_ok=True)
    for name, (title, licence) in CLIPS.items():
        if pattern not in name or (OUT / name).exists():
            continue
        print(f"{name}: downloading {title} ({licence})")
        raw = OUT / f"_download_{Path(title).suffix}"
        with urllib.request.urlopen(urllib.request.Request(commons_url(title), headers=UA)) as r, open(raw, "wb") as f:
            while chunk := r.read(1 << 20):
                f.write(chunk)
        trim(raw, OUT / name)
        raw.unlink()


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "")
