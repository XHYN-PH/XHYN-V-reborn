#!/usr/bin/env python3
"""Prepare the English Bridge source and exact, unchanged Android networking backend."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import tarfile
import zipfile

PACKAGE = Path(__file__).resolve().parent
INPUTS = json.loads((PACKAGE / 'inputs.json').read_text())


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--upstream-checkout', type=Path, required=True)
    parser.add_argument('--input-apk', type=Path, required=True)
    parser.add_argument('--work-dir', type=Path, required=True)
    args = parser.parse_args()
    if sha(args.input_apk) != INPUTS['input_apk_sha256']:
        parser.error('The companion APK does not match the supported input hash')
    revision = INPUTS['upstream']['commit']
    actual = subprocess.check_output(
        ['git', '-C', str(args.upstream_checkout), 'rev-parse', revision + '^{commit}'],
        text=True,
    ).strip()
    if actual != revision:
        parser.error('The exact upstream source revision is unavailable')
    work = args.work_dir.resolve()
    if work.exists():
        parser.error('Choose a new work directory')
    work.mkdir(parents=True)
    source = work / 'source'
    source.mkdir()
    archive = work / 'upstream.tar'
    subprocess.run(['git', '-C', str(args.upstream_checkout), 'archive', revision,
                    '--output', str(archive)], check=True)
    with tarfile.open(archive) as stream:
        stream.extractall(source, filter='data')
    archive.unlink()
    subprocess.run(['git', 'apply', '--check', str(PACKAGE / 'bridge.patch')],
                   cwd=source, check=True)
    subprocess.run(['git', 'apply', str(PACKAGE / 'bridge.patch')],
                   cwd=source, check=True)
    for name in ['ASTRAL-LICENSE.txt', 'UPSTREAM-NOTICES.txt']:
        shutil.copyfile(PACKAGE / name, source / 'assets' / name)
    (source / 'test').mkdir(exist_ok=True)
    shutil.copyfile(PACKAGE / 'bridge_compatibility_test.dart',
                    source / 'test/bridge_compatibility_test.dart')
    logo = PACKAGE.parents[1] / 'assets/xhyn-v.png'
    shutil.copyfile(logo, source / 'assets/logo.png')
    for icon in (source / 'android/app/src/main/res').glob('mipmap-*/ic_launcher.png'):
        shutil.copyfile(logo, icon)
    backend = work / 'native/arm64-v8a/librust_lib_astral.so'
    backend.parent.mkdir(parents=True)
    with zipfile.ZipFile(args.input_apk) as apk:
        backend.write_bytes(apk.read('lib/arm64-v8a/librust_lib_astral.so'))
    if sha(backend) != INPUTS['native_backend_sha256']:
        raise ValueError('The native backend hash does not match the source bindings')
    report = dict(INPUTS, patch_sha256=sha(PACKAGE / 'bridge.patch'),
                  logo_sha256=sha(logo), prepared=True, build_and_tests_run=False)
    (work / 'build-inputs.json').write_text(json.dumps(report, indent=2) + '\n')
    print('Prepared ' + str(source))
    print('Set XHYN_BRIDGE_NATIVE_DIR=' + str(work / 'native'))


if __name__ == '__main__':
    main()
