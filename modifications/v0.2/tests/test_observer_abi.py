"""Execute the exact loader-prologue trampoline ABI under ARM64 emulation."""
import io,json,os,struct
from pathlib import Path
from elftools.elf.elffile import ELFFile
from capstone import Cs,CS_ARCH_ARM64,CS_MODE_ARM
from unicorn import Uc,UC_ARCH_ARM64,UC_MODE_ARM,UC_HOOK_CODE
from unicorn.arm64_const import *

root=Path(os.environ['XHYN_WORK_DIR'])
data=(root/'decoded/lib/arm64-v8a/libgtav_vulkan.so').read_bytes();elf=ELFFile(io.BytesIO(data))
symbol=next(s for s in elf.get_section_by_name('.dynsym').iter_symbols() if s.name=='vkGetInstanceProcAddr')
address=symbol['st_value'];segment=next(s for s in elf.iter_segments() if s['p_type']=='PT_LOAD' and s['p_vaddr']<=address<s['p_vaddr']+s['p_filesz'])
offset=segment['p_offset']+address-segment['p_vaddr'];prefix=data[offset:offset+16]
assert address==0x80fa8 and prefix.hex()=='fd7bbda9f50b00f9f44f02a9fd030091'
instructions=list(Cs(CS_ARCH_ARM64,CS_MODE_ARM).disasm(prefix,address))
assert [i.mnemonic for i in instructions]==['stp','str','stp','mov']
checks=2
def jump(target):return struct.pack('<IIQ',0x58000050,0xd61f0200,target)
for base in [0x10000000,0x70000000]:
 for hooked in [False,True]:
  u=Uc(UC_ARCH_ARM64,UC_MODE_ARM);entry=base+address;trampoline=base+0x100000;wrapper=base+0x200000;stack=base+0x300000;end=base+0x400000
  for page in [entry&~4095,trampoline,wrapper,stack,end]:u.mem_map(page,4096)
  # Complete the original prologue with an ABI-preserving return stub. The
  # displaced original instructions are executed unchanged on the hooked path.
  continuation=bytes.fromhex('400580d2f44f42a9f50b40f9fd7bc3a8c0035fd6')
  u.mem_write(entry,prefix+continuation);u.mem_write(trampoline,prefix+jump(entry+16));u.mem_write(trampoline+64,jump(wrapper))
  stub=bytes.fromhex('fd7bbfa9fd030091')+struct.pack('<II',0x580000d0,0xd63f0200)+bytes.fromhex('fd7bc1a8c0035fd6')+b'\0'*8+struct.pack('<Q',trampoline)
  u.mem_write(wrapper,stub)
  if hooked:u.mem_write(entry,struct.pack('<I',0x14000000|(((trampoline+64-entry)>>2)&0x3ffffff)))
  originals={UC_ARM64_REG_X19:0x1919,UC_ARM64_REG_X20:0x2020,UC_ARM64_REG_X21:0x2121,UC_ARM64_REG_X29:0x2929}
  for reg,value in originals.items():u.reg_write(reg,value)
  u.reg_write(UC_ARM64_REG_SP,stack+2048);u.reg_write(UC_ARM64_REG_X30,end);u.reg_write(UC_ARM64_REG_X0,0x1111);u.reg_write(UC_ARM64_REG_X1,0x2222)
  visited=[]
  def observe(uc,pc,size,user):
   if pc==entry+16:assert uc.reg_read(UC_ARM64_REG_X0)==0x1111 and uc.reg_read(UC_ARM64_REG_X1)==0x2222;visited.append(pc)
  u.hook_add(UC_HOOK_CODE,observe);u.emu_start(entry,end,count=100)
  assert visited and u.reg_read(UC_ARM64_REG_X0)==42 and u.reg_read(UC_ARM64_REG_SP)==stack+2048
  for reg,value in originals.items():assert u.reg_read(reg)==value
  checks+=6
result={'result':'PASS','checks':checks,'scope':'Exact loader displaced prologue and absolute-jump ABI at two ASLR bases; original return/arguments/callee-saved registers/stack preserved. Emulation, not Android execution.'}
(root/'reports/native-observer-abi.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
