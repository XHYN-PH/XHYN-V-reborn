"""Audit the signed artifact, exact native edits, helper linkage and original features."""
from pathlib import Path
import os, io, json, re, struct, zipfile, hashlib, zlib
import xml.etree.ElementTree as ET
from loguru import logger
logger.remove()
from androguard.core.apk import APK
from androguard.core.dex import DEX
from elftools.elf.elffile import ELFFile
from PIL import Image

R=Path(os.environ['XHYN_WORK_DIR']); original=Path(os.environ['XHYN_INPUT_APK']); final=Path(os.environ['XHYN_OUTPUT_APK'])
checks=[]
def check(value,label):
    if not value: raise AssertionError(label)
    checks.append(label)
def digest(data): return hashlib.sha256(data).hexdigest()
def inventory(archive):
    classes={};methods=set();references=[]
    for name in archive.namelist():
        if not re.fullmatch(r'classes\d*\.dex',name): continue
        data=archive.read(name)
        check(struct.unpack_from('<I',data,8)[0]==zlib.adler32(data[12:]),'DEX checksum '+name)
        check(data[12:32]==hashlib.sha1(data[32:]).digest(),'DEX signature '+name)
        dex=DEX(data)
        for c in dex.get_classes():
            check(c.get_name() not in classes,'Unique class '+c.get_name()); classes[c.get_name()]=c
            for m in c.get_methods():
                methods.add(c.get_name()+'->'+m.get_name()+m.get_descriptor().replace(' ',''))
                if m.get_code():
                    for instruction in m.get_instructions():
                        if instruction.get_name().startswith('invoke-'):
                            match=re.search(r'(L[^ ]+;)->([^ ]+)\(([^)]*)\)([^ ]+)',instruction.get_output())
                            if match: references.append((c.get_name(),''.join([match[1],'->',match[2],'(',match[3].replace(' ',''),')',match[4]])))
    return classes,methods,references

with zipfile.ZipFile(original) as a,zipfile.ZipFile(final) as b,final.open('rb') as stream:
    check(b.testzip() is None,'ZIP CRCs')
    oldlibs={n for n in a.namelist() if n.startswith('lib/') and n.endswith('.so')}
    newlibs={n for n in b.namelist() if n.startswith('lib/') and n.endswith('.so')}
    check(newlibs==oldlibs|{'lib/arm64-v8a/libxhyn_v02.so'},'Original native library set plus one own helper')
    language=json.loads((R/'reports/native-language-patch.json').read_text())
    guards=json.loads((R/'reports/native-guards.json').read_text())
    # Compose the staged edit reports. Every altered native byte must be accounted for.
    records=guards.get('images',guards)
    print('Native guard report keys:',list(guards),flush=True)
    for name in sorted(oldlibs):
        old=a.read(name);expected=bytearray(old)
        reports=[]
        if name.endswith('/libgtav.so'): reports.append(language)
        for key,value in records.items() if isinstance(records,dict) else []:
            if isinstance(value,dict) and 'edits' in value and (key==name.rsplit('/',1)[-1] or key=='engine' and name.endswith('/libgtav.so') or key=='dxvk' and name.endswith('/libdxvk_d3d11.so')): reports.append(value)
        for report in reports:
            for edit in report['edits']:
                offset=edit['offset'];before=bytes.fromhex(edit['before']);after=bytes.fromhex(edit['after'])
                check(bytes(expected[offset:offset+len(before)])==before,'Expected native bytes '+edit['label'])
                expected[offset:offset+len(after)]=after
        check(bytes(expected)==b.read(name),'All changed bytes accounted for '+name)
        elf=ELFFile(io.BytesIO(old));now=ELFFile(io.BytesIO(b.read(name)))
        check(elf.header==now.header,'Original ELF header '+name)
        check([s.header for s in elf.iter_segments()]==[s.header for s in now.iter_segments()],'Original ELF segment layout '+name)
        for section in ['.dynamic','.dynsym','.dynstr','.rela.dyn','.rela.plt','.eh_frame_hdr']:
            x=elf.get_section_by_name(section);y=now.get_section_by_name(section)
            if x is not None: check(y is not None and x.data()==y.data(),'Original runtime linkage/index '+name+' '+section)
    own=b.read('lib/arm64-v8a/libxhyn_v02.so'); ownelf=ELFFile(io.BytesIO(own))
    check(ownelf.header.e_machine=='EM_AARCH64','Own native helper ARM64')
    check(all(s['p_align']>=16384 for s in ownelf.iter_segments() if s['p_type']=='PT_LOAD'),'Own helper 16 KiB segment alignment')
    check(digest(own)==json.loads((R/'reports/native-component-build.json').read_text())['library']['sha256'],'Own native helper matches tested build')
    for item in b.infolist():
        if item.compress_type!=zipfile.ZIP_STORED: continue
        stream.seek(item.header_offset);header=stream.read(30);nl,el=struct.unpack_from('<HH',header,26)
        offset=item.header_offset+30+nl+el
        check(offset%(16384 if item.filename in newlibs else 4)==0,'Stored APK entry alignment '+item.filename)
    for name in a.namelist():
        if name.startswith('assets/') and not name.endswith('/') and not name.startswith('assets/touch-layouts/') and name not in ['assets/default-android.ini','assets/licenses/ffmpeg-build-notes.md']:
            check(a.read(name)==b.read(name),'Preserved original asset '+name)
    ini=b.read('assets/default-android.ini').decode();check('frame_generation_2x=false' in ini,'Fresh frame generation opt-in')
    origini=[s for s in a.read('assets/default-android.ini').decode().splitlines() if s.strip() and not s.lstrip().startswith(('#',';'))]
    newini=[s for s in ini.splitlines() if s.strip() and not s.lstrip().startswith(('#',';'))]
    check([s.replace('frame_generation_2x=true','frame_generation_2x=false') for s in origini]==newini,'Only selected frame-generation default changes')
    dictionary=json.loads(b.read('assets/xhyn-english.json'));check(dictionary==json.loads((R/'translations.json').read_text()),'Packaged tested English dictionary')
    check(not re.search('[\u3400-\u9fff]',ini),'English default INI comments')
    logo=Image.open(Path(os.environ['XHYN_REPO_ROOT'])/'assets/xhyn-v.png').convert('RGBA')
    for name in json.loads((R/'reports/ui-patch.json').read_text())['logo_resources']:
        icon=Image.open(io.BytesIO(b.read(name))).convert('RGBA')
        check(icon.size==logo.size and icon.getchannel('A').tobytes()==logo.getchannel('A').tobytes(),'Transparent repository icon '+name)
        for color in [(0,0,0,255),(255,255,255,255)]:
            check(Image.alpha_composite(Image.new('RGBA',logo.size,color),logo).tobytes()==Image.alpha_composite(Image.new('RGBA',icon.size,color),icon).tobytes(),'Icon appearance '+name+str(color))
    oc,om,unused=inventory(a);nc,nm,refs=inventory(b)
    check(set(oc)<=set(nc),'All original classes retained');check(om<=nm,'All original method signatures retained')
    # Interface-only build stubs must never replace the original implementations.
    for name in ['RuntimeConfig','GTAVActivity','GameMenuActivity','DriverManager','StableFrameProfile']:
        key='Lcom/gtavsource/android/'+name+';'
        check(len(nc[key].get_methods())>=len(oc[key].get_methods()),'Original class implementation '+name)
    for target in json.loads((R/'reports/hook-targets.json').read_text()).values(): check(target in nm,'English presentation target '+target)
    for caller,target in refs:
        if target.startswith('Lcom/gtavsource/android/Xhyn') or caller.startswith('Lcom/gtavsource/android/Xhyn') and target.startswith('Lcom/gtavsource/android/'):
            owner,signature=target.split('->',1)
            resolved=target in nm
            while not resolved and owner in nc:
                owner=nc[owner].get_superclassname()
                resolved=owner+'->'+signature in nm
            # javac resolves inherited platform methods against the pinned Android API jar.
            resolved=resolved or owner.startswith(('Landroid/','Ljava/'))
            check(resolved,'Helper and app method linkage '+target)
    check('Lcom/gtavsource/android/XhynV02Menu;->about(Landroid/app/Activity;)V' in nm,'About dialog packaged')
    check('https://t.me/xhynv' in str(nc['Lcom/gtavsource/android/XhynV02Menu;'].get_fields()) or b'https://t.me/xhynv' in b.read('classes7.dex'),'Telegram link packaged')

A=APK(str(original));B=APK(str(final));android='{http://schemas.android.com/apk/res/android}'
meta={'package':B.get_package(),'label':B.get_app_name(),'version_name':B.get_androidversion_name(),'version_code':B.get_androidversion_code(),'min_sdk':B.get_min_sdk_version(),'target_sdk':B.get_target_sdk_version()}
check(meta['package']=='com.xhynph.xhynv.reborn174' and meta['label']=='XHYN V' and meta['version_name']=='0.2' and meta['version_code']=='200','XHYN V 0.2 application identity')
check(set(A.get_permissions())==set(B.get_permissions()),'No added Android permissions')
check(set(A.get_activities())|{'com.gtavsource.android.XhynV02PerformanceActivity','com.gtavsource.android.XhynV02ProbeActivity'}==set(B.get_activities()),'Original activities plus own settings/probe activities')
check(set(A.get_services())==set(B.get_services()),'Original download and diagnostic services retained')
app=B.get_android_manifest_xml().find('application')
probe=next(x for x in app.findall('activity') if x.get(android+'name')=='com.gtavsource.android.XhynV02ProbeActivity')
check(probe.get(android+'process')==':xhyn_probe' and probe.get(android+'exported')=='false','Driver probe in a separate private process')
check(app.get(android+'name')=='com.gtavsource.android.XhynEnglishApp','Application initializes helpers')
provider=app.find('provider');check(provider.get(android+'authorities')=='com.xhynph.xhynv.reborn174.modspawn','Provider authority unique to XHYN V')
check(A.get_min_sdk_version()==B.get_min_sdk_version() and A.get_target_sdk_version()==B.get_target_sdk_version(),'Original Android API requirements')
def resources(apk):
    x=ET.fromstring(apk.get_android_resources().get_public_resources(apk.get_package()))
    return {(n.get('type'),n.get('name'),n.get('id')) for n in x}
check(resources(A)==resources(B),'Original public resource IDs retained')
result={'result':'PASS','checks':len(checks),'file':str(final),'bytes':final.stat().st_size,'sha256':digest(final.read_bytes()),'metadata':meta,'original_classes':len(oc),'final_classes':len(nc),'original_native_libraries':len(oldlibs),'dictionary_entries':len(dictionary),'device_gameplay_tested':False,'android_ui_execution_tested':False,'physical_gpu_probe_tested':False,'scope':'Signed artifact structure, exact native modifications, preserved features, helper linkage and host/ARM64 checks; not gameplay qualification'}
(R/'reports/final-audit.json').write_text(json.dumps(result,indent=2)+'\n')
(R/'reports/final-audit-checks.txt').write_text('\n'.join(checks)+'\n')
print(json.dumps(result,indent=2))
