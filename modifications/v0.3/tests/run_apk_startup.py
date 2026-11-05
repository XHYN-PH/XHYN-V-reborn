"""Run the real Application startup from the final APK's DEX and resources.

This is host Android execution, not a physical Android or GPU qualification.
Unlike menu fixtures, it does not borrow resources from the original input APK.
"""
import argparse
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import zipfile

PACKAGE = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--apk', required=True, type=Path)
parser.add_argument('--output-dir', required=True, type=Path)
parser.add_argument('--tools-dir', required=True, type=Path)
parser.add_argument('--classpath-file', required=True, type=Path)
parser.add_argument('--enjarify-root', required=True, type=Path)
args = parser.parse_args()
out=args.output_dir.resolve()
out.mkdir(parents=True,exist_ok=True)
apk=args.apk.resolve(strict=True)
enjarify=args.enjarify_root.resolve(strict=True)
source_digest=hashlib.sha256()
for path in sorted(enjarify.rglob('*.py')):
    source_digest.update(str(path.relative_to(enjarify)).encode())
    source_digest.update(hashlib.sha256(path.read_bytes()).digest())
expected=json.loads((PACKAGE/'tests/ui/toolchain.json').read_text())['enjarify']['python_sources_digest']
if source_digest.hexdigest()!=expected:
    raise RuntimeError('Enjarify differs from the pinned source')

def run(label,command,cwd=None):
    result=subprocess.run([str(x) for x in command],cwd=cwd,capture_output=True,text=True)
    (out/(label+'.log')).write_text(result.stdout+result.stderr)
    if result.returncode:
        raise RuntimeError(label+' failed:\n'+(result.stdout+result.stderr)[-12000:])
    return result.stdout+result.stderr

with zipfile.ZipFile(apk) as archive:
    dexfiles=[]
    for name in archive.namelist():
        if re.fullmatch(r'classes\d*\.dex',name):
            path=out/name
            path.write_bytes(archive.read(name))
            dexfiles.append(path)

def translate(path):
    jar=path.with_suffix('.jar')
    output=run(path.stem+'-translate',['python3','-O','-m','enjarify.main',path,'-o',jar,'-f'],enjarify)
    if '0 classes had errors' not in output:
        raise RuntimeError('DEX translation failed: '+path.name)
    return jar

with concurrent.futures.ThreadPoolExecutor(max_workers=3) as executor:
    jars=list(executor.map(translate,dexfiles))
converted=out/'release-jvm.jar'
seen=set()
with zipfile.ZipFile(converted,'w',zipfile.ZIP_DEFLATED) as target:
    for jar in jars:
        with zipfile.ZipFile(jar) as archive:
            for name in archive.namelist():
                if not name.endswith('.class'):
                    continue
                if name in seen:
                    raise RuntimeError('Duplicate class: '+name)
                seen.add(name)
                data=bytearray(archive.read(name))
                if name in {'com/gtavsource/android/TrainerBridge$Endpoint.class','com/gtavsource/android/lan/LanTransport$Source.class'}:
                    data[6:8]=struct.pack('>H',52)
                target.writestr(name,data)
classpath=[]
for entry in args.classpath_file.read_text().strip().split(os.pathsep):
    path=Path(entry).resolve(strict=True)
    if path.suffix=='.aar':
        jar=out/(path.stem+'.jar')
        with zipfile.ZipFile(path) as archive:
            jar.write_bytes(archive.read('classes.jar'))
        path=jar
    classpath.append(str(path))
classes=out/'checks'
classes.mkdir(exist_ok=True)
base_classpath=os.pathsep.join([str(classes),str(converted),str(args.tools_dir.resolve()/'android.jar')]+classpath)
compatibility=PACKAGE/'tests/ui/EnjarifyJvmCompatibility.java'
run('compile-jvm-compatibility',['javac','-cp',base_classpath,'-d',classes,compatibility])
compatible=out/'release-jvm-compatible.jar'
run('jvm-compatibility',['java','-cp',base_classpath,'EnjarifyJvmCompatibility',converted,compatible])
classpath=os.pathsep.join([str(classes),str(compatible),str(args.tools_dir.resolve()/'android.jar')]+classpath)
test=PACKAGE/'tests/ui/ApkStartupChecks.java'
run('compile',['javac','-encoding','UTF-8','-cp',classpath,'-d',classes,test])
cache=args.classpath_file.resolve().parent/'test-home'
runtime_jars=out/'runtime-jars'
runtime_jars.mkdir(exist_ok=True)
for path in (cache/'.m2/repository').rglob('android-all-instrumented-*.jar'):
    link=runtime_jars/path.name
    if not link.exists():
        link.symlink_to(path)
result=run('application-startup',['java','--add-opens=java.base/java.io=ALL-UNNAMED',
    '-Duser.home='+str(cache),'-Dxhyn.test.apk='+str(apk),
    '-Drobolectric.offline=true','-Drobolectric.dependency.dir='+str(runtime_jars),
    '-cp',classpath,'org.junit.runner.JUnitCore','com.gtavsource.android.ApkStartupChecks'])
(out/'result.json').write_text(json.dumps({'result':'PASS','apk_sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),
    'test_sha256':hashlib.sha256(test.read_bytes()).hexdigest(),
    'jvm_compatibility_source_sha256':hashlib.sha256(compatibility.read_bytes()).hexdigest(),
    'android_apis':[30,35], 'tests':6,
    'jvm_only_adjustments':'Static-interface references and stack maps corrected in the translated host JAR; original default no-op interface versions adjusted. APK DEX is unchanged.',
    'scope':'Actual final APK Application.onCreate, artwork authentication/render, dictionary and launcher lifecycle; host Android APIs 30/35, not physical gameplay'},indent=2)+'\n')
print(result.strip())
