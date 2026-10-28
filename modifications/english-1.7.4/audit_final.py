from pathlib import Path
import zipfile,io,json,hashlib,zlib,struct,re,xml.etree.ElementTree as ET,os
from loguru import logger
logger.remove()
from androguard.core.apk import APK
from androguard.core.dex import DEX
from elftools.elf.elffile import ELFFile
from PIL import Image
R=Path(os.environ.get('XHYN_WORK_DIR',Path(__file__).resolve().parent));original=Path(os.environ['XHYN_INPUT_APK']);final=Path(os.environ['XHYN_OUTPUT_APK']);checks=[]
def check(value,label):
 if not value:raise AssertionError(label)
 checks.append(label)
def hash(data):return hashlib.sha256(data).hexdigest()
def dex_inventory(z):
 classes={};methods=set()
 for n in z.namelist():
  if not re.fullmatch(r'classes\d*\.dex',n):continue
  data=z.read(n);check(data[:8] in [b'dex\n035\0',b'dex\n037\0',b'dex\n038\0',b'dex\n039\0',b'dex\n040\0'],'DEX header '+n)
  check(struct.unpack_from('<I',data,8)[0]==zlib.adler32(data[12:]),'DEX checksum '+n);check(data[12:32]==hashlib.sha1(data[32:]).digest(),'DEX signature '+n)
  for c in DEX(data).get_classes():
   name=c.get_name();check(name not in classes,'Unique class '+name);classes[name]=c
   for m in c.get_methods():methods.add(name+'->'+m.get_name()+m.get_descriptor().replace(' ',''))
 return classes,methods
with zipfile.ZipFile(original) as a,zipfile.ZipFile(final) as b,final.open('rb') as stream:
 check(b.testzip() is None,'All final ZIP entries pass CRC')
 libs=[n for n in a.namelist() if n.startswith('lib/') and n.endswith('.so')];check(set(libs)=={n for n in b.namelist() if n.startswith('lib/') and n.endswith('.so')},'Native library membership unchanged')
 changed=[];alignments={}
 for n in libs:
  old=a.read(n);new=b.read(n)
  if old!=new:changed.append(n)
  e=ELFFile(io.BytesIO(new));alignments[n]=[s['p_align'] for s in e.iter_segments() if s['p_type']=='PT_LOAD']
  olde=ELFFile(io.BytesIO(old));check(e.header==olde.header,'ELF header unchanged '+n)
  check(list(e.iter_segments())!=[],'ELF segments exist '+n)
  for s1,s2 in zip(e.iter_segments(),olde.iter_segments()):check(s1.header==s2.header,'ELF segment layout unchanged '+n)
 check(changed==['lib/arm64-v8a/libgtav.so'],'Only engine language bytes changed; other 12 native libraries identical')
 patch=json.loads((R/'reports/native-language-patch.json').read_text());old=a.read('lib/arm64-v8a/libgtav.so');expected=bytearray(old)
 for edit in patch['edits']:
  off=edit['offset'];before=bytes.fromhex(edit['before']);after=bytes.fromhex(edit['after']);check(old[off:off+len(before)]==before,'Native patch before-bytes '+edit['label']);expected[off:off+len(after)]=after
 check(bytes(expected)==b.read('lib/arm64-v8a/libgtav.so'),'Every changed native byte is in the reviewed language/HUD edit list')
 check(hash(expected)==patch['patched_sha256'],'Patched engine SHA-256 matches report')
 for section in ['.eh_frame','.eh_frame_hdr','.rela.dyn','.rela.plt','.dynamic','.dynsym','.dynstr']:
  e=ELFFile(io.BytesIO(old));f=ELFFile(io.BytesIO(expected));check(e.get_section_by_name(section).data()==f.get_section_by_name(section).data(),'Native section unchanged '+section)
 for item in b.infolist():
  if item.compress_type!=zipfile.ZIP_STORED:continue
  stream.seek(item.header_offset);header=stream.read(30);nl,el=struct.unpack_from('<HH',header,26);offset=item.header_offset+30+nl+el;boundary=16384 if item.filename in libs else 4;check(offset%boundary==0,'Stored-entry alignment '+item.filename)
 original_ini=[s for s in a.read('assets/default-android.ini').decode().splitlines() if s.strip() and not s.lstrip().startswith(('#',';'))];new_ini=[s for s in b.read('assets/default-android.ini').decode().splitlines() if s.strip() and not s.lstrip().startswith(('#',';'))];check(original_ini==new_ini,'All default configuration keys and values unchanged')
 dictionary=json.loads(b.read('assets/xhyn-english.json'));check(dictionary==json.loads((R/'translations.json').read_text()),'Final APK contains tested dictionary')
 def layout(old,new,path):
  check(type(old)==type(new),'Layout type '+path)
  if isinstance(old,dict):
   check(old.keys()==new.keys(),'Layout keys '+path)
   for k in old:layout(old[k],new[k],path+'/'+k)
  elif isinstance(old,list):
   check(len(old)==len(new),'Layout length '+path)
   for i,(x,y) in enumerate(zip(old,new)):layout(x,y,path+'/'+str(i))
  elif old!=new:check(isinstance(old,str) and re.search('[\u3400-\u9fff]',old) and dictionary.get(old)==new,'Only mapped display text changed '+path)
 for name in a.namelist():
  if name.startswith('assets/touch-layouts/') and name.endswith('.json'):layout(json.loads(a.read(name)),json.loads(b.read(name)),name)
  elif name.startswith('assets/') and name not in ['assets/default-android.ini','assets/licenses/ffmpeg-build-notes.md']:check(a.read(name)==b.read(name),'Original non-UI asset unchanged '+name)
 for name in b.namelist():
  if name.startswith('assets/') and not name.endswith('/') and name.rsplit('.',1)[-1].lower() in ['txt','md','ini','json','html'] and name!='assets/xhyn-english.json':
   try:s=b.read(name).decode()
   except UnicodeDecodeError:continue
   check(not re.search('[\u3400-\u9fff]',s),'English human-readable asset '+name)
 logo=(Path(os.environ.get('XHYN_REPO_ROOT',Path(__file__).resolve().parents[2]))/'assets/xhyn-v.png').read_bytes()
 ui=json.loads((R/'reports/ui-patch.json').read_text())
 for path in ui['logo_resources']:
  reference_image=Image.open(io.BytesIO(logo)).convert('RGBA');packaged_image=Image.open(io.BytesIO(b.read(path))).convert('RGBA')
  check(reference_image.size==packaged_image.size,'Repository logo dimensions '+path)
  check(reference_image.getchannel('A').tobytes()==packaged_image.getchannel('A').tobytes(),'Repository logo alpha preserved '+path)
  # aapt2 can normalize RGB under fully transparent pixels. Compare visible rendering.
  for background in [(0,0,0,255),(255,255,255,255)]:
   left=Image.alpha_composite(Image.new('RGBA',reference_image.size,background),reference_image)
   right=Image.alpha_composite(Image.new('RGBA',packaged_image.size,background),packaged_image)
   check(left.tobytes()==right.tobytes(),'Repository logo appearance preserved '+path)
  for corner in [(0,0),(reference_image.width-1,0),(0,reference_image.height-1),(reference_image.width-1,reference_image.height-1)]:
   check(packaged_image.getchannel('A').getpixel(corner)==0,'Transparent logo corner '+path)
 oc,om=dex_inventory(a);nc,nm=dex_inventory(b);check(set(oc)<=set(nc),'All original DEX classes retained');check(om<=nm,'All original DEX method signatures retained')
 for target in json.loads((R/'reports/hook-targets.json').read_text()).values():check(target in nm,'Hook target exists '+target)
 for path in (R/'build/helper-smali').rglob('*.smali'):
  s=path.read_text();name=s.splitlines()[0].split()[-1];check(name in nc,'Compiled English helper included '+name)
 class_count=len(nc);method_count=len(nm);original_class_count=len(oc)
A=APK(str(original));B=APK(str(final));meta={'package':B.get_package(),'label':B.get_app_name(),'version_name':B.get_androidversion_name(),'version_code':B.get_androidversion_code(),'min_sdk':B.get_min_sdk_version(),'target_sdk':B.get_target_sdk_version()}
check(meta['package']=='com.xhynph.xhynv.reborn174' and meta['label']=='XHYN V','English app identity')
check(set(A.get_permissions())==set(B.get_permissions()),'Original permissions unchanged');check(set(A.get_activities())==set(B.get_activities()),'Original activities unchanged');check(set(A.get_services())==set(B.get_services()),'Original services unchanged');check(A.get_min_sdk_version()==B.get_min_sdk_version() and A.get_target_sdk_version()==B.get_target_sdk_version(),'Android SDK requirements unchanged')
check(B.get_android_manifest_xml().find('application').get('{http://schemas.android.com/apk/res/android}name')=='com.gtavsource.android.XhynEnglishApp','English application initializes dictionary')
def public(apk):
 x=ET.fromstring(apk.get_android_resources().get_public_resources(apk.get_package()));return {(n.get('type'),n.get('name'),n.get('id')) for n in x}
check(public(A)==public(B),'All public Android resource IDs preserved')
result={'result':'PASS','checks':len(checks),'file':str(final),'bytes':final.stat().st_size,'sha256':hash(final.read_bytes()),'metadata':meta,'original_classes':original_class_count,'final_classes':class_count,'final_method_signatures':method_count,'dictionary_entries':len(dictionary),'only_changed_native_library':changed,'native_segment_alignments':alignments,'device_gameplay_tested':False,'android_ui_execution_tested':False,'scope':'Structural, translation and preservation checks; not Android execution or gameplay'}
(R/'reports/final-audit.json').write_text(json.dumps(result,indent=2)+'\n');(R/'reports/final-audit-checks.txt').write_text('\n'.join(checks)+'\n');print(json.dumps(result,indent=2))
