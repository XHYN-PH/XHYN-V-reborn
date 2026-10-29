from pathlib import Path
import hashlib,io,json,struct,os
from elftools.elf.elffile import ELFFile
R=Path(os.environ.get('XHYN_WORK_DIR',Path(__file__).resolve().parent));p=R/'decoded/lib/arm64-v8a/libgtav.so';src=p.read_bytes();expected='e8c6210a675c0741f7d46c77c0c41cf90698d94f1f954527f4cf856fb2dc16be'
assert hashlib.sha256(src).hexdigest()==expected,'This patch only supports the supplied exact engine'
e=ELFFile(io.BytesIO(src));out=bytearray(src);gate=0xfceca4;font=gate+0x100;archive=gate+0x120;edits=[]
def offset(a,n):
 for s in e.iter_segments():
  if s['p_type']=='PT_LOAD' and s['p_vaddr']<=a and a+n<=s['p_vaddr']+s['p_filesz']:return s['p_offset']+a-s['p_vaddr']
 raise ValueError(hex(a))
def pair(pc,target,r):
 im=(((target&~4095)-(pc&~4095))>>12)&0x1fffff
 return struct.pack('<II',0x90000000|((im&3)<<29)|((im>>2)<<5)|r,0x91000000|((target&4095)<<10)|(r<<5)|r)
def replace(label,a,before,after):
 assert len(before)==len(after);o=offset(a,len(before));assert src[o:o+len(before)]==before,label;out[o:o+len(after)]=after;edits.append({'label':label,'address':hex(a),'offset':o,'before':before.hex(),'after':after.hex()})
sec=e.get_section_by_name('.text');lo=sec['sh_addr'];raw=sec.data();inside=[]
for o in range(0,len(raw),4):
 w=struct.unpack_from('<I',raw,o)[0]
 if w&0x7c000000!=0x14000000:continue
 im=w&0x3ffffff;im=im-(1<<26) if im&(1<<25) else im;a=lo+o;dest=a+4*im
 if gate<=dest<gate+0x400 and not gate<=a<gate+0x400:inside.append((a,dest,w))
assert len(inside)==2 and all(t==gate and w&0xfc000000==0x94000000 for a,t,w in inside),inside
for s in e.iter_sections():
 if s['sh_type']=='SHT_RELA':
  for rel in s.iter_relocations():
   assert not gate<=rel['r_addend']<gate+0x400
   assert not font<=rel['r_offset']<archive+16
assert src[offset(gate,8):offset(gate,8)+8]==bytes.fromhex('ffc302d1fd7b08a9')
for a,_,w in inside:replace('Accept all Android system languages',a,struct.pack('<I',w),bytes.fromhex('20008052'))
replace('Use non-Asian text formatting',0x23204d0,bytes.fromhex('20008052'),bytes.fromhex('00008052'))
for a in [0x23204d8,0x23204ec]:replace('Select English language ID 1',a,bytes.fromhex('a0018052'),bytes.fromhex('20008052'))
replace('Accept English language ID',0x2320524,bytes.fromhex('1f340071'),bytes.fromhex('1f040071'))
for a,val in [(font,b'efigs'),(archive,b'american')]:replace('Dedicated language name in unreachable locale gate',a,src[offset(a,16):offset(a,16)+16],val.ljust(16,b'\0'))
for a,before,r,target in [(0x232069c,0x65cbfb,0,font),(0x23206b4,0x660c40,0,font)]+[(a,0x660c40,3,archive) for a in [0x2321dcc,0x2321e2c,0x2321e4c,0x2323358,0x2323398,0x23233d8]]:replace('Use English font/text resource name',a,pair(a,before,r),pair(a,target,r))
dictionary=json.loads((R/'translations.json').read_text());hud=next(k for k in dictionary if k.startswith('HUD notification %u:') and any('\u3400'<=c<='\u9fff' for c in k));before=hud.encode();after=dictionary[hud].encode();assert len(after)<=len(before);assert src.count(before+b'\0')==1;hud_offset=src.index(before+b'\0')
hud_address=next(seg['p_vaddr']+hud_offset-seg['p_offset'] for seg in e.iter_segments() if seg['p_type']=='PT_LOAD' and seg['p_offset']<=hud_offset<seg['p_offset']+seg['p_filesz'])
replace('English native HUD test notification',hud_address,before,after.ljust(len(before),b'\0'))
patched=ELFFile(io.BytesIO(out))
for name in ['.eh_frame','.eh_frame_hdr','.rela.dyn','.rela.plt','.dynamic']:
 assert e.get_section_by_name(name).data()==patched.get_section_by_name(name).data(),name
p.write_bytes(out)
report={'source_sha256':expected,'patched_sha256':hashlib.sha256(out).hexdigest(),'locale_gate':hex(gate),'removed_locale_callers':[hex(a) for a,_,_ in inside],'font_storage':hex(font),'archive_storage':hex(archive),'edits':edits,'unwind_dynamic_and_relocation_sections_unchanged':True,'size_unchanged':len(src)==len(out),'scope':'Exact-build English language selectors, Android locale gate and native HUD test message'}
(R/'reports/native-language-patch.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps({k:v for k,v in report.items() if k!='edits'},indent=2))
