from pathlib import Path
import io,json,struct,os
from elftools.elf.elffile import ELFFile
from unicorn import Uc,UC_ARCH_ARM64,UC_MODE_ARM
from unicorn.arm64_const import UC_ARM64_REG_X0,UC_ARM64_REG_W0,UC_ARM64_REG_LR,UC_ARM64_REG_PC
R=Path(os.environ.get('XHYN_WORK_DIR',Path(__file__).resolve().parent));data=(R/'decoded/lib/arm64-v8a/libgtav.so').read_bytes();e=ELFFile(io.BytesIO(data));report=json.loads((R/'reports/native-language-patch.json').read_text());u=Uc(UC_ARCH_ARM64,UC_MODE_ARM);mapped=set();checks=[];end=0x70000000;u.mem_map(end,4096)
def load(a):
 page=a&~4095
 if page in mapped:return
 mapped.add(page);u.mem_map(page,4096)
 for s in e.iter_segments():
  if s['p_type']!='PT_LOAD':continue
  lo=max(page,s['p_vaddr']);hi=min(page+4096,s['p_vaddr']+s['p_filesz'])
  if lo<hi:o=s['p_offset']+lo-s['p_vaddr'];u.mem_write(lo,data[o:o+hi-lo])
def run(a,arg=0,stop=None):
 load(a);load(a+16);u.reg_write(UC_ARM64_REG_X0,arg);u.reg_write(UC_ARM64_REG_LR,end);u.emu_start(a,stop or end,count=40);assert u.reg_read(UC_ARM64_REG_PC)==(stop or end);return u.reg_read(UC_ARM64_REG_W0)
for a,expected in [(0x23204d0,0),(0x23204d8,1)]:assert run(a)==expected;checks.append(hex(a)+' getter')
assert run(0x23204ec,stop=0x23204f0)==1;checks.append('language-code formatting selects English')
for i in range(14):assert run(0x2320524,i)==(i==1);checks.append('supported language '+str(i))
for a in map(lambda s:int(s,16),report['removed_locale_callers']):assert run(a,stop=a+4)==1;checks.append('Android locale gate removed at '+hex(a))
for a,reg,name in [(0x232069c,0,'efigs'),(0x23206b4,0,'efigs')]+[(a,3,'american') for a in [0x2321dcc,0x2321e2c,0x2321e4c,0x2323358,0x2323398,0x23233d8]]:
 run(a,stop=a+8);ptr=u.reg_read(UC_ARM64_REG_X0+reg);load(ptr);value=bytes(u.mem_read(ptr,16)).split(b'\0')[0].decode();assert value==name,(hex(a),value);checks.append(hex(a)+' '+name)
result={'result':'PASS','checks':len(checks),'details':checks,'scope':'Actual patched ARM64 getter/selector instructions executed in Unicorn; Android loading, real resources, graphics and gameplay not tested'};(R/'reports/native-english-tests.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
