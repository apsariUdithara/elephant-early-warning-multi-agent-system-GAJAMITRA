"""Prototype web app (dashboard + /analyze). Replaced by Spring Boot and React in Phases 3 and 5."""
import os
import shutil
import tempfile
from pathlib import Path

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.responses import FileResponse

from gajamitra.agent import analyze
from gajamitra.perception import PerceptionError

app = FastAPI(title="GajaMitra")


@app.get("/")
def index():
    return FileResponse(Path(__file__).with_name("index.html"))


@app.post("/analyze")
def analyze_video(video: UploadFile = File(...), boundary: str = Form(...)):
    try:
        b = [float(v) for v in boundary.split(",")]
    except ValueError:
        b = []
    if len(b) != 6 or not all(0 <= v <= 1 for v in b):
        raise HTTPException(400, "boundary must be six 0-1 numbers: ax,ay,bx,by,px,py")
    with tempfile.NamedTemporaryFile(suffix=Path(video.filename or "clip.mp4").suffix, delete=False) as tmp:
        shutil.copyfileobj(video.file, tmp)
    try:
        return analyze(tmp.name, b)
    except PerceptionError as e:
        # Never answer "no elephants" when the camera analysis failed (safety invariant 1).
        raise HTTPException(502, f"Perception failed, so elephants may be present: treat the risk as unknown "
                                 f"and check the camera. ({e})") from e
    finally:
        os.unlink(tmp.name)
