"""Exact method wrappers for the 1.7.57 base; run after its English layer."""
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
    relative = name if '/' in name else 'com/gtavsource/android/' + name
    matches = list(D.glob('smali*/' + relative + '.smali'))
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
        return original + '\n\n' + header + '\n    .locals 1\n    ' + opcode + ' {' + regs + '}, ' + target + '\n    ' + call + '\n    return-void\n.end method'
    method(name, signature, patch)

after('StableFrameProfile', 'apply(Lcom/gtavsource/android/GTAVActivity;Lcom/gtavsource/android/RuntimeConfig;)V',
      'invoke-static {p0, p1}, Lcom/gtavsource/android/XhynV02Runtime;->configure(Landroid/app/Activity;Lcom/gtavsource/android/RuntimeConfig;)V')
body('StableFrameProfile', 'limit(Lcom/gtavsource/android/RuntimeConfig;)I', '''    .locals 1
    invoke-static {p0}, Lcom/gtavsource/android/XhynV02Runtime;->frameLimit(Lcom/gtavsource/android/RuntimeConfig;)I
    move-result v0
    return v0''')
body('StableFrameProfile', 'menu(Lcom/gtavsource/android/GameMenuActivity;)V', '    .locals 0\n    return-void')
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
# Preserve first-attempt errors even when SDL catches a later failure itself.
method('GTAVActivity','onCreate(Landroid/os/Bundle;)V',lambda s:s.replace('    .locals 3\n','    .locals 3\n    invoke-static {p0}, Lcom/gtavsource/android/XhynV02Launch;->begin(Landroid/content/Context;)V\n',1))
def record_load_failure(old):
    original=old.replace(' loadLibraries()V',' xhynOriginalLoadLibraries()V',1)
    return original+'''

.method public loadLibraries()V
    .locals 2
    :xhyn_load_start
    invoke-virtual {p0}, Lcom/gtavsource/android/GTAVActivity;->xhynOriginalLoadLibraries()V
    :xhyn_load_end
    .catch Ljava/lang/LinkageError; {:xhyn_load_start .. :xhyn_load_end} :xhyn_load_failed
    .catch Ljava/lang/RuntimeException; {:xhyn_load_start .. :xhyn_load_end} :xhyn_load_failed
    return-void
    :xhyn_load_failed
    move-exception v0
    const-string v1, "Game libraries or renderer initialization"
    invoke-static {p0, v0, v1}, Lcom/gtavsource/android/XhynV02Launch;->record(Landroid/content/Context;Ljava/lang/Throwable;Ljava/lang/String;)V
    throw v0
.end method'''
method('GTAVActivity','loadLibraries()V',record_load_failure)
# Publish the first activity only after SDL's version and JNI setup succeeded.
# A failed load can then retry through the original loader without calling JNI.
def mark_sdl_creation_after_setup(old):
    mark = '    sput-boolean p1, Lorg/libsdl/app/SDLActivity;->mActivityCreated:Z'
    loaded = '    invoke-virtual {p0}, Lorg/libsdl/app/SDLActivity;->loadLibraries()V'
    setup = '    invoke-static {}, Lorg/libsdl/app/SDL;->initialize()V'
    mark_block = '    .line 369\n'+mark+'\n\n'
    assert all(old.count(line)==1 for line in (mark,loaded,setup))
    assert old.count(mark_block)==1
    assert old.index(mark) < old.index(loaded) < old.index(setup)
    return old.replace(mark_block,'',1).replace(setup,setup+'\n\n'+mark,1)

method('org/libsdl/app/SDLActivity','onCreate(Landroid/os/Bundle;)V',mark_sdl_creation_after_setup)
def startup_title(old):
    marker='    const-string v0, "\\u6e38\\u620f\\u76ee\\u5f55\\u65e0\\u6548"'
    assert old.count(marker)==1
    return old.replace(marker,'''    iget-object v0, p0, Lcom/gtavsource/android/GTAVActivity;->launchPathFailure:Ljava/lang/RuntimeException;
    invoke-static {p0, v0}, Lcom/gtavsource/android/XhynV02Runtime;->launchFailureTitle(Landroid/content/Context;Ljava/lang/RuntimeException;)Ljava/lang/String;
    move-result-object v0''',1)
# This base reports every preparation RuntimeException as a folder failure.
# Keep actual path validation, while naming our graphics/config failures correctly.
_gtav=path('GTAVActivity').read_text()
_signature=next(block.splitlines()[0].split()[-1] for block in _gtav.split('.method ')[1:] if 'const-string v0, "\\u6e38\\u620f\\u76ee\\u5f55\\u65e0\\u6548"' in block.split('.end method')[0])
method('GTAVActivity',_signature,startup_title)
after('GTAVActivity','onDestroy()V','invoke-static {}, Lcom/gtavsource/android/XhynV02Runtime;->gameStopped()V')
after('GTAVActivity','onResume()V','invoke-static {p0}, Lcom/gtavsource/android/XhynV02Runtime;->resumeGame(Landroid/app/Activity;)V')
after('GTAVActivity','onPause()V','invoke-static {p0}, Lcom/gtavsource/android/XhynV02Runtime;->pauseGame(Landroid/app/Activity;)V')
for signature in ['surfaceCreated(Landroid/view/SurfaceHolder;)V','surfaceChanged(Landroid/view/SurfaceHolder;III)V']:
    after('GTAVActivity$GameSurface',signature,'invoke-static {p0}, Lcom/gtavsource/android/XhynV02Runtime;->surface(Landroid/view/View;)V')
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
# Add a controller field with no change to the existing Activity lifecycle or navigation views.
p=path('GameMenuActivity');text=p.read_text()
text=text.replace('# instance fields','# instance fields\n.field xhynSettings:Ljava/lang/Object;\n',1);p.write_text(text)
for method_name,page in [('performance',0),('drivers',1),('display',4),('dxvkVersions',10)]:
    after('GameMenuActivity',method_name+'()V','const/16 v0, '+hex(page)+'\n    invoke-static {p0, v0}, Lcom/gtavsource/android/XhynV02Settings;->append(Lcom/gtavsource/android/GameMenuActivity;I)V')
# Diagnostic page 9 is the original about() method. Preserve its telemetry and driver actions.
def diagnostics(old):
    start=old.index('    .line 668\n');end=old.index('    .line 669\n',start)
    assert '"capture"' in old[start:end]
    return old[:start]+'    move-object v5, p0\n\n'+old[end:]
method('GameMenuActivity','about()V',diagnostics)
after('GameMenuActivity','about()V','const/16 v0, 0x9\n    invoke-static {p0, v0}, Lcom/gtavsource/android/XhynV02Settings;->append(Lcom/gtavsource/android/GameMenuActivity;I)V')
after('GameMenuActivity','onResume()V','invoke-static {p0}, Lcom/gtavsource/android/XhynV02Runtime;->refreshMenu(Lcom/gtavsource/android/GameMenuActivity;)V\n    invoke-static {p0}, Lcom/gtavsource/android/XhynV02Settings;->resume(Lcom/gtavsource/android/GameMenuActivity;)V')
after('GameMenuActivity','onDestroy()V','invoke-static {p0}, Lcom/gtavsource/android/XhynV02Settings;->release(Lcom/gtavsource/android/GameMenuActivity;)V')
def results(old):
    marker='    .locals 1\n'
    assert old.count(marker)==1
    return old.replace(marker,marker+"""
    invoke-static/range {p0 .. p3}, Lcom/gtavsource/android/XhynV02Settings;->result(Lcom/gtavsource/android/GameMenuActivity;IILandroid/content/Intent;)Z
    move-result v0
    if-eqz v0, :xhyn_original_result
    return-void
    :xhyn_original_result
""",1)
method('GameMenuActivity','onActivityResult(IILandroid/content/Intent;)V',results)
def driver_picker(old):
    start = old.index('    .line 1292\n')
    end = old.index('    :cond_0\n', start)
    assert 'const/16 v1, 0x15' in old[:start] and '->work(' in old[start:end]
    return old[:start] + (
        '    invoke-static {p0, v0}, Lcom/gtavsource/android/XhynV02Settings;->importDriver'
        '(Lcom/gtavsource/android/GameMenuActivity;Landroid/net/Uri;)V\n\n    return-void\n\n'
    ) + old[end:]
method('GameMenuActivity','handlePicked(ILjava/util/List;)V',driver_picker)
after('GameMenuActivity','controls(Lcom/gtavsource/android/GameMenuActivity$ControlLibrary;)V',
      'invoke-static {p0}, Lcom/gtavsource/android/XhynV02Controls;->appendControllerTest(Lcom/gtavsource/android/GameMenuActivity;)V')

def editor_toolbar(old):
    marker = '    .line 72\n    new-instance v12, Landroid/widget/HorizontalScrollView;'
    assert old.count(marker) == 1
    return old.replace(marker, (
        '    iget-object v3, v0, Lcom/gtavsource/android/LayoutEditor;->canvas:Lcom/gtavsource/android/LayoutEditor$EditorCanvas;\n'
        '    invoke-static {v5, v1, v3}, Lcom/gtavsource/android/XhynV02Controls;->editorTools'
        '(Landroid/content/Context;Landroid/widget/LinearLayout;Landroid/view/View;)V\n\n'
    ) + marker, 1)
method('LayoutEditor','<init>(Lcom/gtavsource/android/GameMenuActivity;Lcom/gtavsource/android/ControlProfile;Ljava/io/File;Ljava/lang/String;)V',
       editor_toolbar)
def editor_draw(old):
    marker = '    invoke-super {p0, p1}, Lcom/gtavsource/android/ControlCanvas;->onDraw(Landroid/graphics/Canvas;)V\n'
    assert old.count(marker) == 1
    return old.replace(marker, marker + (
        '    invoke-static {p0, p1}, Lcom/gtavsource/android/XhynV02Controls;->drawGuides'
        '(Landroid/view/View;Landroid/graphics/Canvas;)V\n'
    ), 1)
method('LayoutEditor$EditorCanvas','onDraw(Landroid/graphics/Canvas;)V',editor_draw)
def editor_drag(old):
    marker = '    invoke-virtual {v3, v2, p1, v0, v1}, Lcom/gtavsource/android/ControlProfile$Control;->moveTo(FFII)V'
    assert old.count(marker) == 1
    return old.replace(marker, (
        '    invoke-static {p0, v2}, Lcom/gtavsource/android/XhynV02Controls;->snap(Landroid/view/View;F)F\n'
        '    move-result v2\n'
        '    invoke-static {p0, p1}, Lcom/gtavsource/android/XhynV02Controls;->snap(Landroid/view/View;F)F\n'
        '    move-result p1\n\n'
    ) + marker, 1)
method('LayoutEditor$EditorCanvas','onTouchEvent(Landroid/view/MotionEvent;)Z',editor_drag)

def lan_details(old):
    marker = '    invoke-direct {v1, p0, p1}, Lcom/gtavsource/android/LanPage;-><init>(Landroid/app/Activity;I)V\n'
    assert old.count(marker) == 1
    return old.replace(marker, marker + (
        '    invoke-static {p0, v1}, Lcom/gtavsource/android/XhynV02Lan;->addToPage'
        '(Landroid/app/Activity;Landroid/widget/LinearLayout;)V\n'
    ), 1)
method('LanPage','build(Landroid/app/Activity;I)V',lan_details)

def lan_send(old):
    start = old.index('    new-instance v1, Ljava/net/DatagramPacket;')
    end = old.index('    iget-wide v0, p0, Lcom/gtavsource/android/lan/LanTransport;->sentBytes:J', start)
    assert '->send(Ljava/net/DatagramPacket;)V' in old[start:end]
    return old[:start] + (
        '    invoke-static {v0, p1, p2}, Lcom/gtavsource/android/XhynV02Lan;->send'
        '(Ljava/net/DatagramSocket;[BLjava/net/InetSocketAddress;)V\n\n'
    ) + old[end:]
method('com/gtavsource/android/lan/LanTransport','sendBytes([BLjava/net/InetSocketAddress;)V',lan_send)

def lan_pack(old):
    marker = '    invoke-static/range {v1 .. v11}, Lcom/gtavsource/android/lan/LanTransport;->statePacketsWithMac'
    result = '    move-result-object p2\n'
    assert old.count(marker) == 1 and old.count(result) == 1
    return old.replace(marker, '    invoke-static {}, Lcom/gtavsource/android/XhynV02Lan;->packing()V\n\n' + marker, 1).replace(
        result, result + '    invoke-static {p2}, Lcom/gtavsource/android/XhynV02Lan;->packed([[B)V\n', 1)
method('com/gtavsource/android/lan/LanTransport','sendFrame(Lcom/gtavsource/android/lan/LanTransport$Peer;ILcom/gtavsource/android/lan/LanTransport$Frame;)V',lan_pack)

def avoid_duplicate_lan_teardown(old):
    marker = '    .line 90\n'
    assert old.count(marker) == 1
    return old.replace(marker, (
        '    sget-boolean v0, Lcom/gtavsource/android/lan/LanSession;->cleanup:Z\n'
        '    if-eqz v0, :xhyn_lan_disconnect\n'
        '    sget-boolean v0, Lcom/gtavsource/android/lan/LanSession;->active:Z\n'
        '    if-nez v0, :xhyn_lan_disconnect\n'
        '    return-void\n'
        '    :xhyn_lan_disconnect\n'
    ) + marker, 1)
method('com/gtavsource/android/lan/LanSession','disconnect()V',avoid_duplicate_lan_teardown)
# Register About as page 21 inside System, immediately after Game Resources (18).
def register_pages(old):
    assert old.count('    return-void')==1
    return old.replace('    return-void',"""    sget-object v0, Lcom/gtavsource/android/MenuPages;->TITLES:[Ljava/lang/String;
    invoke-static {v0}, Lcom/gtavsource/android/XhynV02Menu;->titles([Ljava/lang/String;)[Ljava/lang/String;
    move-result-object v0
    sput-object v0, Lcom/gtavsource/android/MenuPages;->TITLES:[Ljava/lang/String;
    sget-object v0, Lcom/gtavsource/android/MenuPages;->CHILDREN:[[I
    invoke-static {v0}, Lcom/gtavsource/android/XhynV02Menu;->children([[I)[[I
    move-result-object v0
    sput-object v0, Lcom/gtavsource/android/MenuPages;->CHILDREN:[[I
    return-void""")
method('MenuPages','<clinit>()V',register_pages)
def navigation(old):
    marker='    const/16 v2, 0x16\n'
    assert old.count(marker)==1
    return old.replace(marker,"""    const/16 v2, 0x19
    if-ne v1, v2, :xhyn_original_pages
    invoke-static {v0}, Lcom/gtavsource/android/XhynV02Menu;->render(Lcom/gtavsource/android/GameMenuActivity;)V
    goto :goto_6
    :xhyn_original_pages
"""+marker,1)
method('GameMenuActivity','liquidOriginalShowPage(IZ)V',navigation)
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
scope = json.loads((PACKAGE / 'scope.json').read_text())
apktool_config = D / 'apktool.yml'
stamp = re.sub(r'versionName: .*', 'versionName: ' + json.dumps(scope['release']), apktool_config.read_text())
apktool_config.write_text(re.sub(r'versionCode: .*', 'versionCode: ' + str(scope['versionCode']), stamp))

a = '{http://schemas.android.com/apk/res/android}'
ET.register_namespace('android', a[1:-1])
tree = ET.parse(D/'AndroidManifest.xml'); app = tree.getroot().find('application')
if app.find('profileable') is None:ET.SubElement(app,'profileable',{a+'shell':'true'})
for name, process in [('XhynV02ProbeActivity', ':xhyn_probe')]:
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
# The new base appends hidden memory overrides after StableFrameProfile.apply.
# Our editor/tuning controls already expose these keys through one effective file.
# Keep the original entry points, but prevent a second DXVK_CONFIG writer and
# remove its duplicate UI section from the Liquid launcher.
body('MemoryBudget','apply(Landroid/app/Activity;)V','    .locals 0\n    return-void')
body('MemoryBudgetControls','install(Landroid/app/Activity;Lcom/gtavsource/android/LiquidLauncher$Root;)V','    .locals 0\n    return-void')

(WORK/'reports/runtime-patch.json').write_text(json.dumps({'methods':changes, 'option_counts':{k:len(v) for k,v in options.items() if isinstance(v,list)}, 'game_download_component':'original page and download components retained at the user’s request'}, indent=2)+'\n')
print('Connected', len(changes), 'runtime methods; generated per-renderer option catalogs.')
