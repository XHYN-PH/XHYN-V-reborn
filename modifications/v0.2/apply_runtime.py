"""Exact method wrappers for the 1.7.26 base; run after its English layer."""
import os
import re
import json
import zipfile
from pathlib import Path
import xml.etree.ElementTree as ET

PACKAGE = Path(__file__).resolve().parent
WORK = Path(os.environ['XHYN_WORK_DIR'])
D = WORK / 'decoded'
changes = []

def path(name):
    matches = list(D.glob('smali*/com/gtavsource/android/' + name + '.smali'))
    assert len(matches) == 1, (name, matches)
    return matches[0]

def method(name, signature, transform):
    p = path(name)
    text = p.read_text()
    matches = list(re.finditer(r'^\.method [^\n]* ' + re.escape(signature) + r'\n.*?^\.end method', text, re.M | re.S))
    assert len(matches) == 1, (name, signature)
    match = matches[0]
    before = match.group()
    after = transform(before)
    assert after != before, (name, signature)
    p.write_text(text[:match.start()] + after + text[match.end():])
    changes.append(name + '->' + signature)

def body(name, signature, source):
    method(name, signature, lambda old: old.splitlines()[0] + '\n' + source + '\n.end method')

def after(name, signature, call):
    def patch(old):
        # Preserve original methods' register frames in a wrapper; pN may be reused by original code.
        original_name = signature.split('(')[0]
        renamed = 'xhynOriginal' + original_name[0].upper() + original_name[1:]
        original = old.replace(' ' + signature, ' ' + renamed + signature[len(original_name):], 1)
        header = old.splitlines()[0]
        static = ' static ' in header
        args = signature[signature.index('(')+1:signature.index(')')]
        assert not any(c in args for c in 'JD'), 'Only single-slot wrappers are used'
        count = len(re.findall(r'\[*L[^;]*;|\[*[ZBCSIF]', args)) + (0 if static else 1)
        regs = ', '.join('p%d' % i for i in range(count))
        opcode = 'invoke-static' if static else 'invoke-direct' if ' private ' in header else 'invoke-virtual'
        target = 'Lcom/gtavsource/android/' + name + ';->' + renamed + signature[len(original_name):]
        assert signature.endswith(')V')
        return original + '\n\n' + header + '\n    .locals 0\n    ' + opcode + ' {' + regs + '}, ' + target + '\n    ' + call + '\n    return-void\n.end method'
    method(name, signature, patch)

after('StableFrameProfile', 'apply(Lcom/gtavsource/android/GTAVActivity;Lcom/gtavsource/android/RuntimeConfig;)V',
      'invoke-static {p0, p1}, Lcom/gtavsource/android/XhynV02Runtime;->configure(Landroid/app/Activity;Lcom/gtavsource/android/RuntimeConfig;)V')
body('StableFrameProfile', 'limit(Lcom/gtavsource/android/RuntimeConfig;)I', '''    .locals 1
    invoke-static {p0}, Lcom/gtavsource/android/XhynV02Runtime;->frameLimit(Lcom/gtavsource/android/RuntimeConfig;)I
    move-result v0
    return v0''')
body('StableFrameProfile', 'menu(Lcom/gtavsource/android/GameMenuActivity;)V', '''    .locals 0
    invoke-static {p0}, Lcom/gtavsource/android/XhynV02PerformanceActivity;->addToPage(Lcom/gtavsource/android/GameMenuActivity;)V
    return-void''')
# All frame-generation decisions share this function, including upgraded INIs with no new key.
# The base implementation starts with true, then checks the config. Replacing only its initial
# constant would also turn true into false: replace it with a direct, unambiguous getter.
body('StableFrameProfile', 'framegen(Lcom/gtavsource/android/RuntimeConfig;)Z', '''    .locals 3
    const/4 v0, 0x0
    if-eqz p0, :done
    const-string v1, "performance"
    const-string v2, "frame_generation_2x"
    invoke-virtual {p0, v1, v2, v0}, Lcom/gtavsource/android/RuntimeConfig;->getBoolean(Ljava/lang/String;Ljava/lang/String;Z)Z
    move-result v0
    :done
    return v0''')

def driver_prepare(old):
    marker = '    .locals 17\n'
    assert old.count(marker) == 1
    return old.replace(marker, marker + '\n    invoke-static/range {p0 .. p1}, Lcom/gtavsource/android/XhynV02Runtime;->beforeDriver(Landroid/content/Context;Lcom/gtavsource/android/RuntimeConfig;)V\n', 1)
method('DriverManager', 'prepare(Landroid/content/Context;Lcom/gtavsource/android/RuntimeConfig;Z)Ljava/lang/String;', driver_prepare)
method('DriverPackage', 'prepare(Ljava/io/File;Ljava/lang/String;Ljava/io/File;I)Lcom/gtavsource/android/DriverPackage;', lambda s: re.sub(r'(    \.locals \d+\n)', r'\1\n    invoke-static/range {p0 .. p3}, Lcom/gtavsource/android/XhynV02DriverPackage;->checkPrepare(Ljava/io/File;Ljava/lang/String;Ljava/io/File;I)V\n', s, count=1))

method('GTAVActivity', 'loadLibraries()V', lambda s: s.replace('    invoke-static {p0, v0}, Lcom/gtavsource/android/DxvkManager;->initialize(Landroid/content/Context;Lcom/gtavsource/android/RuntimeConfig;)V', '    invoke-static {p0, v0}, Lcom/gtavsource/android/DxvkManager;->initialize(Landroid/content/Context;Lcom/gtavsource/android/RuntimeConfig;)V\n\n    invoke-static {p0}, Lcom/gtavsource/android/XhynV02Runtime;->librariesLoaded(Landroid/app/Activity;)V'))
after('GTAVActivity','onDestroy()V','invoke-static {}, Lcom/gtavsource/android/XhynV02Runtime;->gameStopped()V')
method('FrameGenerationOverlay', 'attach(Landroid/app/Activity;)V', lambda s: s.replace('    .locals 4\n', '''    .locals 4
    invoke-static {}, Lcom/gtavsource/android/XhynV02Runtime;->config()Lcom/gtavsource/android/RuntimeConfig;
    move-result-object v0
    invoke-static {v0}, Lcom/gtavsource/android/StableFrameProfile;->framegen(Lcom/gtavsource/android/RuntimeConfig;)Z
    move-result v0
    if-nez v0, :xhyn_fg_enabled
    invoke-static {p0}, Lcom/gtavsource/android/FrameGenerationOverlay;->detach(Landroid/app/Activity;)V
    return-void
    :xhyn_fg_enabled
''', 1))
after('GameMenuActivity', 'performance()V', 'invoke-static {p0}, Lcom/gtavsource/android/XhynV02PerformanceActivity;->addToPage(Lcom/gtavsource/android/GameMenuActivity;)V')
after('GameMenuActivity', 'drivers()V', 'invoke-static {p0}, Lcom/gtavsource/android/XhynV02PerformanceActivity;->addToPage(Lcom/gtavsource/android/GameMenuActivity;)V')
after('GameMenuActivity', 'showPage(IZ)V', 'invoke-static {p0}, Lcom/gtavsource/android/XhynV02Menu;->decorate(Landroid/app/Activity;)V')
after('GameMenuActivity', 'onResume()V', 'invoke-static {p0}, Lcom/gtavsource/android/XhynV02Runtime;->refreshMenu(Lcom/gtavsource/android/GameMenuActivity;)V')
body('GameMenuActivity', 'about()V', '''    .locals 0
    invoke-static {p0}, Lcom/gtavsource/android/XhynV02Menu;->about(Landroid/app/Activity;)V
    return-void''')
body('RemoteNoticeContent', 'fetch()Ljava/lang/String;', '''    .locals 1
    invoke-static {}, Lcom/gtavsource/android/XhynV02Information;->notice()Ljava/lang/String;
    move-result-object v0
    return-object v0''')
# Asset catalog parsing is immutable and independent of imported renderer verification.
p = path('DxvkBuiltins'); s = p.read_text(); s = s.replace('# instance fields', '.field private static volatile xhynCatalog:Lcom/gtavsource/android/DxvkBuiltins;\n\n# instance fields', 1)
assert 'xhynCatalog:' in s
p.write_text(s)
def catalog(old):
    original = old.replace(' load(', ' xhynOriginalLoad(', 1)
    return original + '''

.method public static load(Landroid/content/Context;)Lcom/gtavsource/android/DxvkBuiltins;
    .locals 1
    sget-object v0, Lcom/gtavsource/android/DxvkBuiltins;->xhynCatalog:Lcom/gtavsource/android/DxvkBuiltins;
    if-nez v0, :done
    invoke-static {p0}, Lcom/gtavsource/android/DxvkBuiltins;->xhynOriginalLoad(Landroid/content/Context;)Lcom/gtavsource/android/DxvkBuiltins;
    move-result-object v0
    sput-object v0, Lcom/gtavsource/android/DxvkBuiltins;->xhynCatalog:Lcom/gtavsource/android/DxvkBuiltins;
    :done
    return-object v0
.end method'''
method('DxvkBuiltins', 'load(Landroid/content/Context;)Lcom/gtavsource/android/DxvkBuiltins;', catalog)

for base in D.glob('smali*'):
    for p in base.rglob('*.smali'):
        s = p.read_text()
        new = s.replace('com.gtavsource.android.lsfgv2026.modspawn', 'com.xhynph.xhynv.reborn174.modspawn')
        # Catch the TextPaint measurement owner too; layout must use translated string widths.
        new = re.sub(r'invoke-virtual(/range)? (\{[^}]+\}), Landroid/text/TextPaint;->measureText\(Ljava/lang/String;\)F', r'invoke-static\1 \2, Lcom/gtavsource/android/XhynEnglishUi;->measure(Landroid/graphics/Paint;Ljava/lang/String;)F', new)
        if new != s: p.write_text(new)

ini = D / 'assets/default-android.ini'
s = ini.read_text().replace('frame_generation_2x=true', 'frame_generation_2x=false')
comments = iter((PACKAGE / 'ini-comments-en.txt').read_text().splitlines())
lines = []
for line in s.splitlines():
    if line.lstrip().startswith(('#', ';')) and re.search('[\u3400-\u9fff]', line): line = next(comments)
    lines.append(line)
assert next(comments, None) is None
ini.write_text('\n'.join(lines) + '\n')
(D / 'apktool.yml').write_text(re.sub(r'versionName: .*', 'versionName: "0.2"', (D/'apktool.yml').read_text()))

a = '{http://schemas.android.com/apk/res/android}'
ET.register_namespace('android', a[1:-1])
tree = ET.parse(D/'AndroidManifest.xml'); app = tree.getroot().find('application')
for name, process in [('XhynV02PerformanceActivity', None), ('XhynV02ProbeActivity', ':xhyn_probe')]:
    attrs = {a+'name':'com.gtavsource.android.'+name, a+'exported':'false', a+'screenOrientation':'sensorLandscape', a+'theme':'@style/GameMenuTheme', a+'configChanges':'keyboardHidden|orientation|screenSize|smallestScreenSize|density'}
    if process: attrs[a+'process'] = process
    ET.SubElement(app, 'activity', attrs)
for element in list(app):
    if element.tag == 'provider': element.set(a+'authorities', element.get(a+'authorities').replace('com.gtavsource.android.lsfgv2026', 'com.xhynph.xhynv.reborn174'))
tree.write(D/'AndroidManifest.xml', encoding='utf-8', xml_declaration=True)

catalog = json.loads((D/'assets/dxvk/builtins.json').read_text())
options = {'defaultId':catalog['defaultId']}
for entry in catalog['runtimes']:
    if entry['storage'] == 'native':
        data = b'\0'.join(p.read_bytes() for p in (D/'lib/arm64-v8a').glob('libdxvk_*.so'))
    else:
        with zipfile.ZipFile(D/'assets'/entry['asset']) as archive:
            data = b'\0'.join(archive.read(n) for n in archive.namelist() if n.endswith('.so'))
    keys = sorted(set(x.decode('ascii') for x in re.findall(rb'(?:^|(?<=\x00))((?:dxvk|dxgi|d3d11)\.[A-Za-z][A-Za-z0-9_.]*)(?=\x00)', data)))
    assert len(keys) > 40, (entry['id'], len(keys))
    options[entry['id']] = keys
(D/'assets/xhyn-dxvk-options.json').write_text(json.dumps(options, indent=2)+'\n')
(WORK/'reports/runtime-patch.json').write_text(json.dumps({'methods':changes, 'option_counts':{k:len(v) for k,v in options.items() if isinstance(v,list)}, 'game_download_component':'original page and download components retained at the user’s request'}, indent=2)+'\n')
print('Connected', len(changes), 'runtime methods; generated per-renderer option catalogs.')
