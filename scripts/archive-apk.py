#!/usr/bin/env python3
"""Archive a tested debug build without overwriting a previously published version."""
import hashlib
import json
import pathlib
import shutil
import subprocess

root = pathlib.Path(__file__).resolve().parent.parent
project = root / "kiekkopolku-android"
version = dict(line.split("=", 1) for line in (project / "version.properties").read_text().splitlines() if "=" in line)
name, code = version["versionName"], version["versionCode"]
source = project / "app/build/outputs/apk/debug/app-debug.apk"
metadata = json.loads((source.parent / "output-metadata.json").read_text())
element = metadata["elements"][0]
assert element["versionName"] == name and str(element["versionCode"]) == code, "Build/version mismatch"
target = root / "apk-releases" / name
target.mkdir(parents=True, exist_ok=True)
apk = target / f"kiekkopolku-{name}-debug.apk"
if apk.exists():
    raise SystemExit(f"Version already archived: {apk}. Bump versionName and versionCode first.")
shutil.copy2(source, apk)
sha = hashlib.sha256(apk.read_bytes()).hexdigest()
(target / "SHA256SUMS").write_text(f"{sha}  {apk.name}\n")
(target / "release.json").write_text(json.dumps({
    "applicationId": metadata["applicationId"], "versionName": name, "versionCode": int(code),
    "artifact": apk.name, "sha256": sha, "buildType": "debug", "minSdk": 26,
    "sourceTag": f"v{name}", "note": "Test APK. No Metrix network integration."
}, indent=2) + "\n")
print(apk)
