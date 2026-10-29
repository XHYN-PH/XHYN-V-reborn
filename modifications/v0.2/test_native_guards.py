"""Execute patched ARM64 guards, original failure cases, and dispatch ABI checks."""
from pathlib import Path
import bisect
import io
import json
import os
import struct
from elftools.elf.elffile import ELFFile
from elftools.dwarf.callframe import CallFrameInfo
from elftools.dwarf.structs import DWARFStructs
from unicorn import Uc, UcError, UC_ARCH_ARM64, UC_MODE_ARM, UC_HOOK_MEM_UNMAPPED, UC_HOOK_CODE
from unicorn.arm64_const import *

ROOT = Path(os.environ['XHYN_WORK_DIR'])
report = json.loads((ROOT / 'reports/native-guards.json').read_text())
checks = []


def check(value, description):
    assert value, description
    checks.append(description)


class Runner:
    def __init__(self, data, base):
        self.base = base
        self.data = data
        self.elf = ELFFile(io.BytesIO(data))
        self.u = Uc(UC_ARCH_ARM64, UC_MODE_ARM)
        self.pages = set()
        self.logs = []
        self.callbacks = {}
        self.end = base + 0x70000000
        self.heap = base + 0x60000000
        self.stack = base + 0x62000000
        for address in [self.end, self.end + 4096, self.heap, self.heap + 4096, self.stack]:
            self.map(address)
        self.u.mem_write(self.end + 4096, struct.pack('<I', 0xd65f03c0))
        self.u.hook_add(UC_HOOK_MEM_UNMAPPED, self.missing)
        self.u.hook_add(UC_HOOK_CODE, self.code)
        self.u.reg_write(UC_ARM64_REG_SP, self.stack + 2048)
        self.u.reg_write(UC_ARM64_REG_X29, self.stack + 2112)

    def map(self, address):
        page = address & ~4095
        if page in self.pages:
            return
        self.pages.add(page)
        self.u.mem_map(page, 4096)
        for segment in self.elf.iter_segments():
            if segment['p_type'] != 'PT_LOAD':
                continue
            low = max(page, self.base + segment['p_vaddr'])
            high = min(page + 4096, self.base + segment['p_vaddr'] + segment['p_filesz'])
            if low < high:
                offset = segment['p_offset'] + low - self.base - segment['p_vaddr']
                self.u.mem_write(low, self.data[offset:offset + high - low])

    def missing(self, uc, access, address, size, value, user):
        # Address-zero ELF fixtures still model Android's unmapped null page.
        if address < 4096:
            return False
        for segment in self.elf.iter_segments():
            if segment['p_type'] == 'PT_LOAD' and self.base + segment['p_vaddr'] <= address < self.base + segment['p_vaddr'] + segment['p_memsz']:
                self.map(address)
                if size > 1 and (address + size - 1) >> 12 != address >> 12:
                    self.map(address + size - 1)
                return True
        return False

    def code(self, uc, address, size, user):
        callback = self.callbacks.get(address)
        if callback:
            callback(self)

    def register(self, number, value):
        self.u.reg_write(UC_ARM64_REG_X0 + number, value)

    def read_register(self, number):
        return self.u.reg_read(UC_ARM64_REG_X0 + number)

    def run(self, pc, stop=None):
        self.map(self.base + pc)
        self.u.reg_write(UC_ARM64_REG_LR, self.end)
        self.u.emu_start(self.base + pc, stop or self.end, count=300)
        assert self.u.reg_read(UC_ARM64_REG_PC) == (stop or self.end)
        return self.u.reg_read(UC_ARM64_REG_W0)


engine = (ROOT / 'decoded/lib/arm64-v8a/libgtav.so').read_bytes()
original_engine = (ROOT.parent / 'base/libgtav.so').read_bytes()
dxvk = (ROOT / 'decoded/lib/arm64-v8a/libdxvk_d3d11.so').read_bytes()
original_dxvk = (ROOT.parent / 'base/libdxvk_d3d11.so').read_bytes()

for base in [0, 0x7000000000]:
    r = Runner(original_engine, base)
    r.register(0, r.heap)
    r.u.mem_write(r.heap, b'\0' * 8)
    try:
        r.run(0x2980424)
        raise AssertionError('Original null-header fault was not reproduced')
    except UcError:
        check(True, f'{base:x}: original null-header crash reproduced')

    for object_pointer, header_pointer, flags, expected in [
        (0, 0, 0, 0xffffffff), (1, 0, 0, 0xffffffff),
        (1, 1, 0, 0), (1, 1, 1 << 17, 1), (1, 1, 1 << 18, 2),
    ]:
        r = Runner(engine, base)
        r.register(0, r.heap if object_pointer else 0)
        r.u.mem_write(r.heap, struct.pack('<Q', r.heap + 4096 if header_pointer else 0))
        r.u.mem_write(r.heap + 4096, struct.pack('<II', 0x54414441, flags))
        check(r.run(0x2980424) == expected, f'{base:x}: packing object={object_pointer} header={header_pointer} flags={flags}')

    r = Runner(engine, base)
    r.register(0, 0)
    r.run(0x2c5a418, base + 0x2c5a420)
    check(r.read_register(0) == 0 and r.read_register(19) == 0,
          f'{base:x}: failed allocation skips constructor and preserves null result')
    r = Runner(engine, base)
    r.register(0, r.heap)
    r.run(0x2c5a418, base + 0x2c5a1a0)
    check(r.read_register(0) == r.heap and r.u.reg_read(UC_ARM64_REG_LR) == base + 0x2c5a420,
          f'{base:x}: valid allocation tail-calls the original constructor')

    table_low, table_high = [base + int(v, 16) for v in report['engine']['vtable_range']]
    code_low, code_high = [base + int(v, 16) for v in report['engine']['method_range']]
    for vtable, method, valid in [
        (0, 0, False), (0xc74645dab0e171ce, 0, False),
        (table_low - 8, 0, False), (table_high, 0, False),
        (table_low + 128, 0, False), (table_low + 128, code_high, False),
        (table_low + 128, code_low + 128, True),
    ]:
        r = Runner(engine, base)
        if table_low <= vtable < table_high:
            r.map(vtable)
            r.u.mem_write(vtable + 0x38, struct.pack('<Q', method))
        r.register(9, vtable)
        r.register(0, r.heap)
        r.register(19, r.heap + 64)
        r.register(20, 0x12345678)
        before_sp = r.u.reg_read(UC_ARM64_REG_SP)
        def logger(machine):
            machine.logs.append(machine.read_register(0))
            machine.u.reg_write(UC_ARM64_REG_PC, machine.u.reg_read(UC_ARM64_REG_LR))
        r.callbacks[base + int(report['engine']['audio_logger'], 16)] = logger
        stop = base + (0x29ecddc if valid else 0x29eced4)
        r.run(0x29ecdd8, stop)
        check(r.u.reg_read(UC_ARM64_REG_SP) == before_sp and r.read_register(20) == 0x12345678,
              f'{base:x}: audio guard preserves frame and TLS state for {vtable:x}/{method:x}')
        check((r.read_register(9) == method and not r.logs) if valid else r.logs == [5],
              f'{base:x}: audio guard forwards valid methods or takes the existing early exit')

    r = Runner(original_dxvk, base)
    r.register(8, 0)
    try:
        r.run(0x27d468, base + 0x27d46c)
        raise AssertionError('Original optional-command fault was not reproduced')
    except UcError:
        check(True, f'{base:x}: original null Vulkan command fault reproduced')
    for function_exists in [False, True]:
        snapshots = []
        for data in ([original_dxvk, dxvk] if function_exists else [dxvk]):
            r = Runner(data, base)
            for reg in range(29):
                r.register(reg, 0x10000 + reg * 32)
            r.register(8, r.end + 4096 if function_exists else 0)
            r.run(0x27d468, base + 0x27d46c)
            snapshots.append(([r.read_register(reg) for reg in range(29)],
                              r.u.reg_read(UC_ARM64_REG_SP), r.u.reg_read(UC_ARM64_REG_LR)))
        check(not function_exists or snapshots[0] == snapshots[1],
              f'{base:x}: supported Vulkan dispatch preserves the original register ABI')
        if not function_exists:
            check(True, f'{base:x}: missing optional Vulkan function returns without calling zero')

elf = ELFFile(io.BytesIO(engine))
eh = elf.get_section_by_name('.eh_frame')
cfi = CallFrameInfo(io.BytesIO(eh.data()), eh['sh_size'], eh['sh_addr'],
                   DWARFStructs(little_endian=True, dwarf_format=32, address_size=8), for_eh_frame=True)
fde = cfi._parse_entry_at(0x328)
table = fde.get_decoded().table
for pc, reg, offset in [(0xfceca4 + 0x180, 31, 0), (0xfceca4 + 0x1d0, 31, 0),
                        (0xfceca4 + 0x200, 29, 32), (0xfceca4 + 0x300, 31, 0)]:
    row = [row for row in table if row['pc'] <= pc][-1]
    check(row['cfa'].reg == reg and row['cfa'].offset == offset, f'accurate unwind CFA at {pc:x}')
    if reg == 29:
        check(row[30].arg == -24 and row[29].arg == -32 and row[19].arg == -8,
              'mixer guard retains the original saved-register unwind rules')

result = {'result': 'PASS', 'checks': len(checks), 'details': checks,
          'scope': 'Original faults reproduced and actual patched ARM64 executed at two ASLR bases; no Android GPU or gameplay validation.'}
(ROOT / 'reports/native-guard-tests.json').write_text(json.dumps(result, indent=2) + '\n')
print('PASS', len(checks), 'native guard checks, including original fault reproduction and valid-call ABI preservation.')
