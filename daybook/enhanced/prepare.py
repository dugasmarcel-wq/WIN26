"""Apply isolated frontend additions to the verified Daybook 1.13 assets.

This is an intermediate development patch, not a distributable build.
"""
from pathlib import Path
import hashlib
import shutil
import sys
import zipfile

apk = Path(sys.argv[1])
assets = Path(sys.argv[2])
expected = "d42f3f48800ac75edfdd433dce8f06b778cb971782cf564fe820b82b7cd9faf0"
if hashlib.sha256(apk.read_bytes()).hexdigest() != expected:
    raise SystemExit("Refusing to patch anything other than the original 1.13 APK")
assets.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(apk) as z:
    for name in z.namelist():
        if name.startswith("assets/") and not name.endswith("/"):
            relative = Path(name).relative_to("assets")
            if ".." in relative.parts:
                raise ValueError("Invalid asset path")
            out = assets / relative
            out.parent.mkdir(parents=True, exist_ok=True)
            out.write_bytes(z.read(name))
def replace_once(path, old, new):
    text = path.read_text()
    if text.count(old) != 1:
        raise ValueError(f"Unexpected 1.13 source in {path}: {old}")
    path.write_text(text.replace(old, new, 1))
replace_once(assets / "modules.js", "length:1189,pace:3", "length:1189,pace:2")
replace_once(assets / "index.html", "</body>", '<script src="enhanced.js"></script></body>')
shutil.copyfile(Path(__file__).with_name("enhanced.js"), assets / "enhanced.js")
with (assets / "style.css").open("a") as css:
    css.write("\n" + Path(__file__).with_name("enhanced.css").read_text())
