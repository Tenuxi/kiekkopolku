#!/usr/bin/env python3
"""Archive both tested builds without replacing any published version."""
import hashlib
import json
import pathlib
import shutil

root = pathlib.Path(__file__).resolve().parent.parent
project = root / "kiekkopolku-android"
version = dict(line.split("=", 1) for line in (project / "version.properties").read_text().splitlines() if "=" in line)
name, code = version["versionName"], int(version["versionCode"])
target = root / "apk-releases" / name
if target.exists():
    raise SystemExit(f"Version already archived: {target}. Bump the version first.")
artifacts = []
sources = []
for build_type in ("debug", "release"):
    source = project / f"app/build/outputs/apk/{build_type}/app-{build_type}.apk"
    metadata = json.loads((source.parent / "output-metadata.json").read_text())
    element = metadata["elements"][0]
    assert element["versionName"] == name and element["versionCode"] == code, "Build/version mismatch"
    assert source.is_file(), f"Signed build missing: {source}"
    filename = f"kiekkopolku-{name}-{build_type}.apk"
    artifacts.append({"artifact": filename, "buildType": build_type, "applicationId": metadata["applicationId"],
        "sha256": hashlib.sha256(source.read_bytes()).hexdigest()})
    sources.append(source)
target.mkdir(parents=True)
for source, artifact in zip(sources, artifacts):
    shutil.copy2(source, target / artifact["artifact"])
(target / "SHA256SUMS").write_text("".join(f'{a["sha256"]}  {a["artifact"]}\n' for a in artifacts))
(target / "release.json").write_text(json.dumps({"versionName": name, "versionCode": code,
    "minSdk": 26, "sourceTag": f"v{name}", "artifacts": artifacts,
    "note": "Debug updates existing test installations. Release uses a separate application ID and production signing key; profiles must be added again."
}, indent=2) + "\n")
print(target)
