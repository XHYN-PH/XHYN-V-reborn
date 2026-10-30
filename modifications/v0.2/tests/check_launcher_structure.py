"""Check original page routing and constructor legality before packaging."""
import os
import re
from pathlib import Path

root=Path(os.environ['XHYN_WORK_DIR'])/'decoded'
checks=0
for path in root.glob('smali*/**/*.smali'):
    source=path.read_text()
    owner=re.search(r'^\.class .* (L[^;]+;)',source,re.M)
    parent=re.search(r'^\.super (L[^;]+;)',source,re.M)
    if not owner or not parent:continue
    for block in re.findall(r'^\.method [^\n]* <init>\([^\n]*\n.*?^\.end method',source,re.M|re.S):
        uninitialized={'p0'}
        for line in block.splitlines():
            move=re.search(r'\bmove-object(?:/from16|/16)? ([pv]\d+), ([pv]\d+)',line)
            if move:
                target,value=move.groups()
                if value in uninitialized:uninitialized.add(target)
                else:uninitialized.discard(target)
            new=re.search(r'\bnew-instance ([pv]\d+),',line)
            if new:uninitialized.discard(new[1])
            call=re.search(r'invoke-direct(?:/range)? \{([pv]\d+)[^}]*\}, (L[^;]+;)-><init>',line)
            if call and call[1] in uninitialized:
                assert call[2] in {owner[1],parent[1]},f'{path}: initializer targets {call[2]} but superclass is {parent[1]}'
                uninitialized.clear();checks+=1
menu=next(root.glob('smali*/com/gtavsource/android/GameMenuActivity.smali')).read_text()
show=re.search(r'^\.method private showPage\(IZ\)V.*?^\.end method',menu,re.M|re.S)[0]
table=re.search(r'\.packed-switch 0x0(.*?)\.end packed-switch',show,re.S)[1].split()
assert len(table)==22 and table[9]==':pswitch_2' and table[18]==':pswitch_5' and table[21]==':xhyn_about'
checks+=1
assert 'Lcom/deepseek/quarkdownload/ResourceDownloadPage;->attach' in menu
checks+=1
about=re.search(r'^\.method private xhynOriginalAbout\(\)V.*?^\.end method',menu,re.M|re.S)[0]
assert 'Config/telemetry-status.txt' in about and 'Config/driver-status.txt' in about
assert 'XhynV02Menu;->about' not in about and 'XhynV02Menu;->render' not in about
checks+=1
assert 'XhynV02PerformanceActivity' not in (root/'AndroidManifest.xml').read_text()
checks+=1
for page in ['performance','display','drivers','dxvkVersions','about']:
    block=re.search(r'^\.method private '+page+r'\(\)V.*?^\.end method',menu,re.M|re.S)[0]
    assert 'XhynV02Settings;->append' in block,page
    checks+=1
output=f'PASS {checks} constructor and launcher-route structural checks before packaging\n'
(Path(os.environ['XHYN_WORK_DIR'])/'reports/launcher-structure-checks.txt').write_text(output)
print(output,end='')
