#!/usr/bin/env python3
"""Stage the product's shared data for RimesBroker, without installing it.

The reviewed cross-platform data policy supplies the RIMES Lua/schema closure.
An explicit OpenCC build supplies the external s2t runtime tables and license.
No user-data directory is read, modified, or included.
"""

from __future__ import annotations

import argparse
import importlib.util
import json
from pathlib import Path, PurePosixPath
import re
import shutil
import sys
import tempfile

REPO = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location("rimes_preview", REPO / "scripts/platform-preview/preview.py")
assert SPEC is not None and SPEC.loader is not None
preview = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(preview)
MANIFEST = "NATIVE-DATA-MANIFEST.json"
LICENSE = "LICENSE-OpenCC.txt"
WINDOWS_PATCHES = {
    "wubi86.custom.yaml": """# Windows settings: traditional output without changing shared schemas.
patch:
  engine/filters/@before 0: simplifier@traditionalize
  switches/@next:
    name: traditionalization
    states: [简, 繁]
  traditionalize:
    option_name: traditionalization
    opencc_config: s2t.json
    tips: none
"""
}


def regular_file(path: Path) -> None:
    if path.is_symlink() or not path.is_file():
        preview.fail(f"missing regular file (symlinks are not accepted): {path}")
    if not 0 < path.stat().st_size <= preview.MAX_FILE_BYTES:
        preview.fail(f"empty or oversized runtime file: {path.name}")


def opencc_closure(directory: Path) -> list[str]:
    if directory.is_symlink() or not directory.is_dir():
        preview.fail("OpenCC data must be a regular directory")
    config = directory / "s2t.json"
    regular_file(config)
    if config.stat().st_size > 64 * 1024:
        preview.fail("OpenCC s2t.json exceeds 64 KiB")
    try:
        data = json.loads(config.read_text(encoding="utf-8"))
    except (ValueError, OSError) as error:
        preview.fail(f"cannot read OpenCC s2t.json: {error}")
    files: set[str] = set()

    def visit(node: object) -> None:
        if isinstance(node, dict):
            if "file" in node:
                name = preview.canonical_relative_path(node["file"], "OpenCC dictionary")
                if "/" in name or PurePosixPath(name).suffix not in {".ocd2", ".txt"}:
                    preview.fail(f"OpenCC dictionaries must be local .ocd2 or .txt files: {name}")
                regular_file(directory / name)
                files.add(name)
            for value in node.values():
                visit(value)
        elif isinstance(node, list):
            for value in node:
                visit(value)

    if not isinstance(data, dict) or not data.get("conversion_chain"):
        preview.fail("OpenCC s2t.json must contain a conversion_chain")
    visit(data)
    if not files:
        preview.fail("OpenCC s2t.json references no dictionaries")
    return ["s2t.json", *sorted(files)]


def expected_runtime(root: Path) -> set[str]:
    return {f"opencc/{name}" for name in opencc_closure(root / "opencc")} | {LICENSE}


def verify(root: Path) -> dict:
    if root.is_symlink() or not root.is_dir():
        preview.fail("shared-data root must be a regular directory")
    regular_file(root / MANIFEST)
    try:
        manifest = json.loads((root / MANIFEST).read_text(encoding="utf-8"))
    except (ValueError, OSError) as error:
        preview.fail(f"cannot read native-data manifest: {error}")
    if manifest.get("formatVersion") != 1 or manifest.get("kind") != "rimes-windows-native-shared-data":
        preview.fail("unsupported native-data manifest")
    policy = preview.load_policy()
    if manifest.get("policySha256") != preview.sha256_file(preview.policy_path()):
        preview.fail("native-data manifest belongs to a different reviewed data policy")
    expected = set(policy["include"]) | expected_runtime(root) | set(WINDOWS_PATCHES)
    actual, symlinks = preview.scan_source_tree(root)
    if symlinks or actual != expected | {MANIFEST}:
        preview.fail(f"shared-data inventory mismatch: missing={sorted(expected - actual)}, "
                     f"extra={sorted(actual - expected - {MANIFEST})}, symlinks={symlinks}")
    entries = manifest.get("files")
    if not isinstance(entries, list) or len(entries) != len(expected):
        preview.fail("native-data manifest has an incorrect file count")
    seen: set[str] = set()
    total = 0
    for entry in entries:
        if not isinstance(entry, dict):
            preview.fail("native-data file entry must be an object")
        name = preview.canonical_relative_path(entry.get("path"), "manifest entry")
        if name not in expected or name.casefold() in seen:
            preview.fail(f"unexpected or duplicate manifest file: {name}")
        seen.add(name.casefold())
        path = root / name
        regular_file(path)
        size = path.stat().st_size
        total += size
        if size != entry.get("bytes") or preview.sha256_file(path) != entry.get("sha256"):
            preview.fail(f"native-data checksum mismatch: {name}")
    if total > preview.MAX_TOTAL_BYTES:
        preview.fail("native shared data exceeds the package size limit")
    return {"files": len(entries), "bytes": total, "productSchemas": manifest["productSchemas"]}


def stage(repo: Path, output: Path, opencc_data: Path, opencc_license: Path, revision: str) -> dict:
    if output.exists() or output.is_symlink():
        preview.fail(f"output already exists; choose a new staging directory: {output}")
    if not re.fullmatch(r"[0-9a-f]{40}", revision):
        preview.fail("OpenCC revision must be its full 40-character source commit")
    regular_file(opencc_license)
    runtime = opencc_closure(opencc_data)
    result = preview.validate_repo(repo)
    if set(result["external"]) != {"opencc/s2t.json"}:
        preview.fail("the shared-data policy has new external dependencies; update this packager first")
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = Path(tempfile.mkdtemp(prefix=".rimes-native-data-", dir=output.parent))
    try:
        preview.stage_preview(repo, temporary)
        for name in runtime:
            destination = temporary / "opencc" / name
            if destination.exists():
                preview.fail(f"OpenCC runtime would overwrite reviewed RIMES data: {name}")
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(opencc_data / name, destination)
        shutil.copyfile(opencc_license, temporary / LICENSE)
        for name, content in WINDOWS_PATCHES.items():
            (temporary / name).write_text(content, encoding="utf-8")
        files = sorted(set(result["included"]) | expected_runtime(temporary) | set(WINDOWS_PATCHES))
        manifest = {
            "formatVersion": 1,
            "kind": "rimes-windows-native-shared-data",
            "nativeApplicationIncluded": False,
            "productSchemas": list(preview.EXPECTED_SCHEMAS),
            "policySha256": preview.sha256_file(preview.policy_path()),
            "provenanceGroups": result["policy"]["provenanceGroups"],
            "opencc": {
                "source": "https://github.com/BYVoid/OpenCC",
                "revision": revision,
                "license": "Apache-2.0",
                "files": sorted(expected_runtime(temporary)),
            },
            "runtimeRequirements": result["policy"]["runtimeRequirements"],
            "files": [{"path": name, "bytes": (temporary / name).stat().st_size,
                       "sha256": preview.sha256_file(temporary / name)} for name in files],
        }
        (temporary / MANIFEST).write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        summary = verify(temporary)
        if output.exists() or output.is_symlink():
            preview.fail("output appeared during staging; refusing to replace it")
        temporary.rename(output)
        return summary
    finally:
        if temporary.exists():
            shutil.rmtree(temporary)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    build = commands.add_parser("stage", help="create a new shared-data directory")
    build.add_argument("--repo-root", type=Path, default=REPO)
    build.add_argument("--output", type=Path, required=True)
    build.add_argument("--opencc-data", type=Path, required=True)
    build.add_argument("--opencc-license", type=Path, required=True)
    build.add_argument("--opencc-revision", required=True)
    check = commands.add_parser("verify", help="verify inventory and hashes after transfer")
    check.add_argument("directory", type=Path)
    args = parser.parse_args()
    try:
        summary = (verify(args.directory) if args.command == "verify" else stage(
            args.repo_root, args.output, args.opencc_data, args.opencc_license, args.opencc_revision))
        print(json.dumps({"ok": True, **summary}, ensure_ascii=False))
        return 0
    except (preview.PreviewError, OSError) as error:
        print(f"native-data error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
