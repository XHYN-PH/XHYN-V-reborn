"""Run Android-view regressions against the actual patched DEX, before packaging.

Private input classes/assets stay in the build directory. Enjarify's translation
is a host-test representation; no converted class is copied into the APK/source.
"""
from pathlib import Path
import argparse
import concurrent.futures
import hashlib
import json
import os
import shutil
import struct
import subprocess
import urllib.parse
import zipfile

PACKAGE=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--work-dir',required=True,type=Path)
parser.add_argument('--tools-dir',required=True,type=Path)
parser.add_argument('--classpath-file',required=True,type=Path)
parser.add_argument('--enjarify-root',required=True,type=Path)
parser.add_argument('--classes',type=Path,help='Optional smoke-test output from javac; official build tests translated DEX only')
args=parser.parse_args()
WORK=args.work_dir.resolve();OUT=WORK/'launcher-ui-tests';OUT.mkdir(exist_ok=True)
TOOLS=args.tools_dir.resolve();ENJARIFY=args.enjarify_root.resolve()
manifest=json.loads((PACKAGE/'tests/ui/toolchain.json').read_text())
source_digest=hashlib.sha256()
for path in sorted(ENJARIFY.rglob('*.py')):
    source_digest.update(str(path.relative_to(ENJARIFY)).encode());source_digest.update(hashlib.sha256(path.read_bytes()).digest())
if source_digest.hexdigest()!=manifest['enjarify']['python_sources_digest']:raise RuntimeError('Enjarify sources differ from the pinned revision')

def run(label,command,cwd=None):
    result=subprocess.run([str(x) for x in command],cwd=cwd,capture_output=True,text=True)
    (OUT/(label+'.log')).write_text(result.stdout+result.stderr)
    if result.returncode:raise RuntimeError(label+' failed:\n'+(result.stdout+result.stderr)[-9000:])
    return result.stdout+result.stderr

def assemble(item):
    index,directory=item
    name='classes'+('' if index==1 else str(index))
    dex=OUT/(name+'.dex');jar=OUT/(name+'.jar')
    run(name+'-assemble',['java','-cp',str(TOOLS/'*'),'org.jf.smali.Main','assemble',directory,'-o',dex])
    translated=run(name+'-translate',['python3','-O','-m','enjarify.main',dex,'-o',jar,'-f'],ENJARIFY)
    if '0 classes had errors' not in translated:raise RuntimeError('A DEX class failed host translation: '+name)
    return jar

directories=sorted((WORK/'decoded').glob('smali*'))
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as executor:
    jars=list(executor.map(assemble,enumerate(directories,1)))
converted=OUT/'launcher-jvm.jar'
seen=set()
with zipfile.ZipFile(converted,'w',zipfile.ZIP_DEFLATED) as target:
    for jar in jars:
        with zipfile.ZipFile(jar) as archive:
            for name in archive.namelist():
                if not name.endswith('.class'):continue
                if name in seen:raise RuntimeError('Duplicate translated class: '+name)
                seen.add(name);data=bytearray(archive.read(name))
                # Enjarify emits Java 5 class versions. This original interface has
                # a Java 8 default no-op method, requiring version 52 on the JVM.
                # It has no branches/stack maps. Android DEX is left untouched.
                if name=='com/gtavsource/android/TrainerBridge$Endpoint.class':data[6:8]=struct.pack('>H',52)
                target.writestr(name,data)
assets=OUT/'test-assets.zip'
with zipfile.ZipFile(assets,'w',zipfile.ZIP_DEFLATED) as archive:
    for path in (WORK/'decoded/assets').rglob('*'):
        if path.is_file():archive.write(path,'assets/'+str(path.relative_to(WORK/'decoded/assets')))
classpath=[]
for name in args.classpath_file.read_text().strip().split(os.pathsep):
    path=Path(name)
    if not path.is_file():raise FileNotFoundError(path)
    if path.suffix=='.aar':
        jar=OUT/(path.stem+'.jar')
        with zipfile.ZipFile(path) as archive:jar.write_bytes(archive.read('classes.jar'))
        classpath.append(str(jar))
    else:classpath.append(str(path))
classes=OUT/'checks';classes.mkdir(exist_ok=True)
# Execute original launcher classes from patched DEX and our added helpers from
# the very same javac output used by D8. Enjarify cannot encode Java 8 static
# interface calls (e.g. Comparator.comparingLong) correctly for a JVM. Keeping
# javac's helper class files avoids changing app code to accommodate that tool.
helper_classes=args.classes or WORK/'build/classes'
classpath=[str(classes),str(helper_classes),str(converted),str(TOOLS/'android.jar')]+classpath
classpath=os.pathsep.join(classpath)
source=PACKAGE/'tests/ui/LauncherUiChecks.java'
# Enjarify does not retain Java InnerClasses metadata. Use javac's own helper
# classes for source type lookup only; execution below still uses translated DEX.
compile_classpath=classpath
run('compile',['javac','-encoding','UTF-8','-cp',compile_classpath,'-d',classes,source])
command=['java','-Duser.home='+str(OUT/'test-home'),
         '-Drobolectric.dependency.repo.url=https://repo.maven.apache.org/maven2',
         '-Dxhyn.test.root='+str(OUT),'-Dxhyn.test.apk='+os.environ['XHYN_INPUT_APK'],
         '-Dxhyn.test.ini='+str(WORK/'decoded/assets/default-android.ini'),
         '-Dxhyn.test.translations='+str(PACKAGE/'translations.json'),'-Dxhyn.test.assets='+str(assets),
         '-cp',classpath,'org.junit.runner.JUnitCore','com.gtavsource.android.LauncherUiChecks']
cache=args.classpath_file.resolve().parent/'test-home';cache.mkdir(exist_ok=True)
command[1]='-Duser.home='+str(cache)
proxy=urllib.parse.urlparse(os.environ.get('HTTPS_PROXY',''))
if proxy.hostname:
    command[1:1]=['-Dhttps.proxyHost='+proxy.hostname,'-Dhttps.proxyPort='+str(proxy.port or 8080),'-Dhttp.proxyHost='+proxy.hostname,'-Dhttp.proxyPort='+str(proxy.port or 8080)]
if Path('/etc/ssl/certs/java/cacerts').is_file():command.insert(1,'-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts')
output=run('android-view-checks',command)
(WORK/'reports/launcher-ui-checks.txt').write_text(output)
(WORK/'reports/launcher-ui-model.json').write_text(json.dumps({
    'result':'PASS','classes_translated':len(seen),'android_api':30,
    'framework':'Robolectric 4.14.1','enjarify_revision':'6364dfb30f7862873b84ec5fad1d1286c526ba0b',
    'translated_jar_sha256':hashlib.sha256(converted.read_bytes()).hexdigest(),
    'test_source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),
    'scope':'Original patched launcher DEX translated for host Android-view execution; new helpers use the exact javac output fed to D8. Game/native GPU/network downloads are not exercised.',
    'jvm_only_adjustment':'TrainerBridge.Endpoint class version 52 for its original default no-op method; Android bytecode unchanged'
},indent=2)+'\n')
print(output.strip())
