#!/usr/bin/env python3
"""Rebuild the XHYN V 0.2 modification layer from the exact privately supplied APK."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

INPUT_SHA256 = 'da730ee5b153cdf9a162d2dacd7bec45f0bf8a143f928a29d359c5c97ae51191'
PACKAGE = Path(__file__).resolve().parent
REPOSITORY = PACKAGE.parents[1]


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input-apk', required=True, type=Path)
    parser.add_argument('--work-dir', required=True, type=Path, help='New build directory; must not exist')
    parser.add_argument('--tools-dir', required=True, type=Path, help='Jars from android-toolchain-manifest.json')
    parser.add_argument('--apktool', required=True, type=Path)
    parser.add_argument('--ndk-root', required=True, type=Path, help='Android NDK r27c (27.2.12479018)')
    parser.add_argument('--ui-classpath-file',required=True,type=Path,help='Robolectric/JUnit classpath resolved from tests/ui/pom.xml')
    parser.add_argument('--enjarify-root',required=True,type=Path,help='Pinned Enjarify checkout for prepackage Android-view checks')
    parser.add_argument('--verify-only',action='store_true',help='Run all checks and stop before APK packaging/signing')
    parser.add_argument('--java-home', type=Path, help='JDK 17; otherwise use PATH')
    parser.add_argument('--keystore', type=Path, help='Optional signing key; never copied into outputs')
    parser.add_argument('--key-alias')
    parser.add_argument('--password-file', type=Path, help='Password file passed directly to apksigner')
    args = parser.parse_args()
    original = args.input_apk.resolve(strict=True)
    tools = args.tools_dir.resolve(strict=True)
    apktool = args.apktool.resolve(strict=True)
    work = args.work_dir.resolve()
    if work.exists():
        parser.error('work directory already exists; choose a fresh directory')
    if digest(original) != INPUT_SHA256:
        parser.error('input APK does not match the exact supported SHA-256')
    manifest = json.loads((REPOSITORY / 'modifications/toolchain-manifest.json').read_text())
    if digest(apktool) != manifest['apktool']['sha256']:
        parser.error('apktool checksum does not match the repository manifest')
    for item in json.loads((REPOSITORY / 'modifications/android-toolchain-manifest.json').read_text()):
        path = tools / item['name']
        if not path.is_file() or digest(path) != item['sha256']:
            parser.error('missing or mismatching Android tool: ' + item['name'])
    signing = args.keystore is not None
    if any([args.key_alias, args.password_file]) and not signing:
        parser.error('signing options require --keystore')
    if signing and not all([args.key_alias, args.password_file]):
        parser.error('signing requires --key-alias and --password-file')
    if signing:
        args.keystore = args.keystore.resolve(strict=True)
        args.password_file = args.password_file.resolve(strict=True)
    env = os.environ.copy()
    if args.java_home:
        env['JAVA_HOME'] = str(args.java_home.resolve(strict=True))
        env['PATH'] = str(args.java_home.resolve() / 'bin') + os.pathsep + env['PATH']
    java = shutil.which('java', path=env['PATH'])
    javac = shutil.which('javac', path=env['PATH'])
    if not java or not javac:
        parser.error('a complete JDK 17 is required')
    version = subprocess.run([javac, '-version'], env=env, check=True, capture_output=True, text=True)
    if not re.search(r'javac 17(?:\.|\s|$)', version.stdout + version.stderr):
        parser.error('use JDK 17 for the documented build')
    for module in ['elftools', 'unicorn', 'capstone', 'androguard', 'PIL', 'loguru']:
        __import__(module)

    work.mkdir(parents=True)
    reports = work / 'reports'
    reports.mkdir()
    env.update(XHYN_WORK_DIR=str(work), XHYN_REPO_ROOT=str(REPOSITORY), XHYN_INPUT_APK=str(original), XHYN_NDK_ROOT=str(args.ndk_root.resolve(strict=True)))
    logs = reports / 'build-logs'
    logs.mkdir()

    def run(label, command):
        print(label, flush=True)
        result = subprocess.run([str(x) for x in command], env=env, capture_output=True, text=True)
        text = result.stdout + result.stderr
        (logs / (label + '.txt')).write_text(text)
        if result.returncode:
            print(text, file=sys.stderr)
            raise subprocess.CalledProcessError(result.returncode, command)
        return text

    shutil.copyfile(PACKAGE / 'translations.json', work / 'translations.json')
    for path in (PACKAGE / 'inputs').glob('*.json'):
        shutil.copyfile(path, reports / path.name)
    run('decode', [java, '-jar', apktool, 'd', original, '-o', work / 'decoded', '-p', work / 'framework'])
    run('native-patch', [sys.executable, PACKAGE / 'patch_native_english.py'])
    run('native-guards', [sys.executable, PACKAGE / 'patch_native_guards.py'])
    run('ui-patch', [sys.executable, PACKAGE / 'apply_ui_english.py'])
    run('runtime-patch', [sys.executable, PACKAGE / 'apply_runtime.py'])
    run('launcher-structure-checks',[sys.executable,PACKAGE/'tests/check_launcher_structure.py'])
    run('native-component', [sys.executable, PACKAGE / 'build_native.py'])
    run('native-component-host', [sys.executable, PACKAGE / 'tests/run_native_component.py'])

    for path in (PACKAGE / 'assets').rglob('*'):
        if path.is_file():
            target = work / 'decoded/assets' / path.relative_to(PACKAGE / 'assets')
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(path, target)

    classes = work / 'build/classes'
    dex = work / 'build/dex'
    smali = work / 'build/helper-smali'
    classes.mkdir(parents=True)
    dex.mkdir()
    stubs = work / 'build/stubs'
    stubs.mkdir()
    run('compile-interfaces', [javac, '--release', '8', '-encoding', 'UTF-8', '-cp', tools / 'android.jar', '-d', stubs, *sorted((PACKAGE / 'stubs').rglob('*.java'))])
    java_sources = sorted((PACKAGE / 'java').rglob('*.java'))
    run('compile-helpers', [javac, '--release', '8', '-encoding', 'UTF-8', '-cp', str(tools / 'android.jar') + os.pathsep + str(stubs), '-d', classes, *java_sources])
    run('dex-helpers', [java, '-cp', tools / 'r8.jar', 'com.android.tools.r8.D8', '--min-api', '30', '--release', '--lib', tools / 'android.jar', '--classpath', stubs, '--output', dex, *sorted(classes.rglob('*.class'))])
    run('disassemble-helpers', [java, '-cp', str(tools / '*'), 'org.jf.baksmali.Main', 'disassemble', dex / 'classes.dex', '-o', smali])
    for path in smali.rglob('*.smali'):
        if not path.name.startswith(('XhynEnglish', 'XhynV02')):
            raise ValueError('unexpected helper class: ' + path.name)
        target = work / 'decoded/smali_classes7' / path.relative_to(smali)
        if target.exists():
            raise FileExistsError(target)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(path, target)

    classpath = str(classes) + os.pathsep + str(tools / 'json.jar')
    run('compile-host-checks', [javac, '-encoding', 'UTF-8', '-cp', classpath, '-d', classes, PACKAGE / 'EnglishHostChecks.java', PACKAGE / 'tests/GraphicsHostChecks.java', PACKAGE / 'tests/DriverHostChecks.java', PACKAGE / 'tests/LogHostChecks.java',PACKAGE/'tests/AdviceHostChecks.java'])
    host = run('english-host-checks', [java, '-cp', classpath, 'EnglishHostChecks', work])
    (reports / 'english-host-checks.txt').write_text(host)
    run('native-english-tests', [sys.executable, PACKAGE / 'test_native_english.py'])
    run('graphics-host-checks', [java, '-cp', classpath, 'GraphicsHostChecks', work / 'graphics-host-tests'])
    run('driver-host-checks', [java, '-cp', classpath + os.pathsep + str(tools / 'android.jar'), 'DriverHostChecks', work / 'driver-host-tests'])
    run('log-host-checks', [java, '-cp', classpath, 'LogHostChecks', work / 'log-host-tests'])
    run('advice-host-checks',[java,'-cp',classpath,'AdviceHostChecks'])
    run('config-host-checks', [sys.executable, PACKAGE / 'tests/run_config_checks.py'])
    run('native-guard-tests', [sys.executable, PACKAGE / 'test_native_guards.py'])
    run('native-observer-abi',[sys.executable,PACKAGE/'tests/test_observer_abi.py'])
    run('launcher-ui-checks',[sys.executable,PACKAGE/'tests/run_launcher_ui.py','--work-dir',work,'--tools-dir',tools,'--classpath-file',args.ui_classpath_file.resolve(strict=True),'--enjarify-root',args.enjarify_root.resolve(strict=True)])
    if args.verify_only:
        print('All prepackage checks passed; APK packaging and signing were not started.',flush=True)
        return
    unsigned = work / 'XHYN-V-0.2-Hotfix2-unsigned.apk'
    run('package', [java, '-jar', apktool, 'b', work / 'decoded', '-p', work / 'framework', '-o', unsigned])
    final = unsigned
    if signing:
        final = work / 'XHYN-V-0.2-Hotfix2.apk'
        run('sign', [java, '-jar', tools / 'apksigner.jar', 'sign', '--ks', args.keystore, '--ks-key-alias', args.key_alias, '--ks-pass', 'file:' + str(args.password_file), '--v4-signing-enabled', 'false', '--alignment-preserved', 'false', '--lib-page-alignment', '16384', '--out', final, unsigned])
        signature = run('signature-verification', [java, '-jar', tools / 'apksigner.jar', 'verify', '--verbose', '--print-certs', final])
        (reports / 'signature-verification.txt').write_text(signature)
    else:
        print('Unsigned output: signature and final stored-entry alignment require signing with apksigner.', flush=True)
    env['XHYN_OUTPUT_APK'] = str(final)
    # apksigner supplies final alignment. The complete APK audit runs on signed builds.
    if signing:
        run('final-audit', [sys.executable, PACKAGE / 'audit_final.py'])
    (work / 'SHA256SUMS.txt').write_text(digest(final) + '  ' + final.name + '\n')
    print('Built ' + str(final), flush=True)
    print(host.strip(), flush=True)
    print('Signed and structurally audited' if signing else 'Unsigned; full final APK audit not run', flush=True)


if __name__ == '__main__':
    main()
