"""Exact-build guards for the observed invalid-input and optional-command paths.

The audio object guard rejects the observed invalid vtable path. It does not
establish that the cause of the object's invalid lifetime has been repaired.
"""
from pathlib import Path
import hashlib
import io
import json
import os
import struct
from elftools.elf.elffile import ELFFile

ROOT = Path(os.environ['XHYN_WORK_DIR'])
REPORTS = ROOT / 'reports'


def branch(pc, target, link=False):
    delta = target - pc
    assert delta % 4 == 0 and -(1 << 27) <= delta < (1 << 27)
    return struct.pack('<I', (0x94000000 if link else 0x14000000) | ((delta >> 2) & 0x3ffffff))


def cbz(pc, target, reg):
    delta = target - pc
    assert delta % 4 == 0 and -(1 << 20) <= delta < (1 << 20)
    return struct.pack('<I', 0xb4000000 | (((delta >> 2) & 0x7ffff) << 5) | reg)


def cond(pc, target, condition):
    delta = target - pc
    assert delta % 4 == 0 and -(1 << 20) <= delta < (1 << 20)
    return struct.pack('<I', 0x54000000 | (((delta >> 2) & 0x7ffff) << 5) | condition)


def pointer(pc, address, reg):
    delta = ((address & ~4095) - (pc & ~4095)) >> 12
    assert -(1 << 20) <= delta < (1 << 20)
    bits = delta & 0x1fffff
    return struct.pack('<II', 0x90000000 | ((bits & 3) << 29) | ((bits >> 2) << 5) | reg,
                       0x91000000 | ((address & 4095) << 10) | (reg << 5) | reg)


class Image:
    def __init__(self, name, sha):
        self.path = ROOT / 'decoded/lib/arm64-v8a' / name
        self.source = self.path.read_bytes()
        assert hashlib.sha256(self.source).hexdigest() == sha, name + ': wrong input'
        self.elf = ELFFile(io.BytesIO(self.source))
        self.output = bytearray(self.source)
        self.edits = []

    def offset(self, address, size):
        for segment in self.elf.iter_segments():
            if segment['p_type'] == 'PT_LOAD' and segment['p_vaddr'] <= address and address + size <= segment['p_vaddr'] + segment['p_filesz']:
                return segment['p_offset'] + address - segment['p_vaddr']
        raise ValueError(hex(address))

    def read(self, address, size):
        offset = self.offset(address, size)
        return self.source[offset:offset + size]

    def patch(self, label, address, before, after):
        assert len(before) == len(after) and self.read(address, len(before)) == before, label
        offset = self.offset(address, len(before))
        self.output[offset:offset + len(after)] = after
        self.edits.append({'label': label, 'address': hex(address), 'offset': offset,
                           'before': before.hex(), 'after': after.hex()})

    def finish(self):
        current = ELFFile(io.BytesIO(self.output))
        for name in ['.dynamic', '.rela.dyn', '.rela.plt', '.dynsym', '.dynstr', '.eh_frame_hdr']:
            assert self.elf.get_section_by_name(name).data() == current.get_section_by_name(name).data(), name
        assert len(self.source) == len(self.output)
        return {'input_sha256': hashlib.sha256(self.source).hexdigest(),
                'output_sha256': hashlib.sha256(self.output).hexdigest(), 'edits': self.edits,
                'size_dynamic_symbols_relocations_and_unwind_index_preserved': True}


engine = Image('libgtav.so', '1462d59dd75e2b87f08a5d7528442a628b153f385214436e5de8b5cf595f98e1')
gate = 0xfceca4
packing, factory, audio = gate + 0x180, gate + 0x1d0, gate + 0x200
RET = struct.pack('<I', 0xd65f03c0)

# The English patch removes both original callers of this locale function.
language = json.loads((REPORTS / 'native-language-patch.json').read_text())
assert language['locale_gate'] == hex(gate)
assert language['removed_locale_callers'] == ['0xfcec7c', '0xfcf474']
for caller in [0xfcec7c, 0xfcf474]:
    assert engine.read(caller, 4) == bytes.fromhex('20008052')

# Preserve all valid packing values; invalid headers take the caller's existing
# load-failure cleanup branch rather than being interpreted as valid audio.
body = cbz(packing, packing + 16, 0)
body += struct.pack('<I', 0xf9400009)  # ldr x9,[x0]
body += cbz(packing + 8, packing + 16, 9)
body += branch(packing + 12, 0x2980428)
body += struct.pack('<I', 0x12800000) + RET  # mov w0,#-1; ret
engine.patch('Audio header validation thunk', packing, engine.read(packing, len(body)), body)
engine.patch('Check header before audio packing accessor', 0x2980424,
             bytes.fromhex('090040f9'), branch(0x2980424, packing))
assert engine.read(0x29d0280, 8) == bytes.fromhex('1f040071e10a0054')

# This factory already returns null and skips initialization on allocation
# failure. Move the check ahead of construction, preserving that contract.
body = cbz(factory, factory + 8, 0) + branch(factory + 4, 0x2c5a1a0) + RET
engine.patch('Null allocation constructor thunk', factory, engine.read(factory, len(body)), body)
engine.patch('Check allocation before constructing startup object', 0x2c5a41c,
             branch(0x2c5a41c, 0x2c5a1a0, True), branch(0x2c5a41c, factory, True))
assert engine.read(0x2c5a420, 8) == bytes.fromhex('e00313aad30100b4')

# The recorded mixer failure loads an invalid vtable's slot. Its receiver is an
# in-engine audio object. Use the same stack-canary/return path as a missing
# receiver, and log once this path is reached. No attempt is made to free a
# corrupt object or call through an untrusted function pointer.
relro = engine.elf.get_section_by_name('.data.rel.ro')
text = engine.elf.get_section_by_name('.text')
table_low, table_high = relro['sh_addr'], relro['sh_addr'] + relro['sh_size'] - 0x40
code_low, code_high = text['sh_addr'], text['sh_addr'] + text['sh_size']
relocations = list(engine.elf.get_section_by_name('.rela.plt').iter_relocations())
symbols = engine.elf.get_section_by_name('.dynsym')
names = [symbols.get_symbol(rel['r_info_sym']).name for rel in relocations]
logger = '__android_log_write' if '__android_log_write' in names else '__android_log_print'
plt = engine.elf.get_section_by_name('.plt')
assert plt['sh_size'] == 32 + 16 * len(relocations)
logger_address = plt['sh_addr'] + 32 + 16 * names.index(logger)
tag, message = gate + 0x340, gate + 0x350
body = bytearray()
fail_branches = []

def emit(value):
    body.extend(value)

def bounds(low, high):
    emit(pointer(audio + len(body), low, 16))
    emit(struct.pack('<I', 0xeb10013f))  # cmp x9,x16
    fail_branches.append((len(body), 3))  # lo
    emit(b'\0' * 4)
    emit(pointer(audio + len(body), high, 16))
    emit(struct.pack('<I', 0xeb10013f))
    fail_branches.append((len(body), 2))  # hs
    emit(b'\0' * 4)

bounds(table_low, table_high)
emit(engine.read(0x29ecdd8, 4))  # ldr x9,[x9,#0x38]
bounds(code_low, code_high)
emit(branch(audio + len(body), 0x29ecddc))
failure = audio + len(body)
emit(struct.pack('<I', 0x528000a0))  # Android WARN
emit(pointer(audio + len(body), tag, 1))
emit(pointer(audio + len(body), message, 2))
emit(branch(audio + len(body), logger_address, True))
emit(branch(audio + len(body), 0x29eced4))
for offset, condition in fail_branches:
    body[offset:offset + 4] = cond(audio + offset, failure, condition)
assert audio + len(body) <= gate + 0x300
engine.patch('Reject invalid audio object vtables and methods', audio, engine.read(audio, len(body)), bytes(body))
engine.patch('Validate mixer receiver before virtual dispatch', 0x29ecdd8,
             bytes.fromhex('291d40f9'), branch(0x29ecdd8, audio))
for address, value in [(tag, b'XHYN V\0'),
                       (message, b'Audio guard skipped an invalid voice; inspect the session report.\0')]:
    assert address + len(value) < gate + 0x400
    engine.patch('Audio guard diagnostic', address, engine.read(address, len(value)), value)

# Give the repurposed region accurate unwind state. Called leaf thunks use
# SP+0; the interior mixer guard uses its original function's FP+32 state.
eh = engine.elf.get_section_by_name('.eh_frame')
fde_offset = 0x328
fde = eh.data()[fde_offset:fde_offset + 56]
assert fde.hex() == '340000002c030000fc3257000004000000440eb001500c1d3093029404950696089e0a9d0c0a03b4030c1fb001500e00d3d4d5d6dedd440b'
leaf = bytes.fromhex('0c1f0008130814081d081e')
mixer_state = bytes.fromhex('0c1d20930294049d089e06')
instructions = leaf + bytes.fromhex('030002') + mixer_state + bytes.fromhex('030001') + leaf
assert len(instructions) == 39
engine.patch('Unwind rules for called and interior guard thunks', eh['sh_addr'] + fde_offset + 17,
             fde[17:], instructions)
engine_report = engine.finish()
engine_report.update({'packing_thunk': hex(packing), 'factory_thunk': hex(factory),
                      'audio_thunk': hex(audio), 'audio_failure': hex(failure),
                      'audio_logger': hex(logger_address), 'audio_exit': hex(0x29eced4),
                      'vtable_range': [hex(table_low), hex(table_high)],
                      'method_range': [hex(code_low), hex(code_high)],
                      'audio_scope': 'Protect observed invalid header and in-engine receiver paths; underlying lifetime/corruption cause remains unproven.'})

dxvk = Image('libdxvk_d3d11.so', '6e081a67ce0bcbf3838fcd68964c8c2601a0fe044735cee7e3f3a57ed00037cd')
shader_text = dxvk.elf.get_section_by_name('.text')
shader_plt = dxvk.elf.get_section_by_name('.plt')
shim = shader_text['sh_addr'] + shader_text['sh_size']
assert shim == 0x352314 and shader_plt['sh_addr'] - shim == 12
assert dxvk.read(shim, 12) == b'\0' * 12
# A leaf call adapter: supported commands receive their original arguments and
# return address; a missing optional command is not called or advertised.
payload = cbz(shim, shim + 8, 8) + struct.pack('<I', 0xd61f0100) + RET
dxvk.patch('Missing optional sample-location command adapter', shim, b'\0' * 12, payload)
dxvk.patch('Route optional sample-location enable through null guard', 0x27d468,
           bytes.fromhex('00013fd6'), branch(0x27d468, shim, True))
dxvk_report = dxvk.finish()
dxvk_report['shim'] = hex(shim)
dxvk_report['scope'] = 'Skip a missing optional dynamic command; preserve supported dispatch and the separate sample-location setup call. No feature-support flags are falsified.'
report = {'engine': engine_report, 'dxvk': dxvk_report,
          'runtime_validation': 'Pending Android driver/gameplay testing; CPU and unwind checks are separate.'}
for image in [engine, dxvk]:
    temporary = image.path.with_suffix('.so.xhyn-stage')
    temporary.write_bytes(image.output)
    temporary.replace(image.path)
(REPORTS / 'native-guards.json').write_text(json.dumps(report, indent=2) + '\n')
print('Applied exact-build header, object, allocation, and optional Vulkan-command guards.')
