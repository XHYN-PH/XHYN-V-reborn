from pathlib import Path
import hashlib
import json
import os
import struct
import subprocess

PACKAGE = Path(__file__).resolve().parent
WORK = Path(os.environ['XHYN_WORK_DIR'])
NDK = Path(os.environ['XHYN_NDK_ROOT'])
assert '27.2.12479018' in (NDK / 'source.properties').read_text(), 'Use the pinned NDK r27c'
TOOLS = NDK / 'toolchains/llvm/prebuilt/linux-x86_64/bin'
SHADERS = NDK / 'shader-tools/linux-x86_64'
OUT = WORK / 'native-build'
OUT.mkdir(exist_ok=True)
arrays = ['#pragma once\n#include <stdint.h>\n']
reports = {}
for extension, name in [('vert', 'vertex'), ('frag', 'fragment')]:
    source = PACKAGE / 'native' / ('probe.' + extension)
    spv = OUT / ('probe.' + extension + '.spv')
    subprocess.run([str(SHADERS / 'glslc'), '--target-env=vulkan1.1', '-O', str(source), '-o', str(spv)], check=True)
    subprocess.run([str(SHADERS / 'spirv-val'), '--target-env', 'vulkan1.1', str(spv)], check=True)
    data = spv.read_bytes()
    words = struct.unpack('<' + 'I' * (len(data) // 4), data)
    arrays.append('static const uint32_t probe_' + name + '[] = {\n' + ','.join('0x%08x' % value for value in words) + '\n};\n')
    reports['probe.' + extension] = {'source_sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
                                    'spirv_sha256': hashlib.sha256(data).hexdigest(), 'validated': True}
(OUT / 'probe_spv.h').write_text('\n'.join(arrays))
library = WORK / 'decoded/lib/arm64-v8a/libxhyn_v02.so'
command = [str(TOOLS / 'aarch64-linux-android30-clang'), '-std=c11', '-O2', '-shared', '-fPIC',
           '-Wall', '-Wextra', '-Werror', '-funwind-tables', '-fasynchronous-unwind-tables',
           '-fno-omit-frame-pointer', '-I', str(OUT), str(PACKAGE / 'native/xhyn_v02.c'),
           '-Wl,-soname,libxhyn_v02.so', '-Wl,--no-undefined', '-Wl,--build-id=sha1',
           '-Wl,-z,max-page-size=16384', '-Wl,-z,common-page-size=16384', '-Wl,-z,relro,-z,now',
           '-landroid', '-llog', '-ldl', '-lunwind', '-o', str(library)]
subprocess.run(command, check=True)
reports.update({'library': {'sha256': hashlib.sha256(library.read_bytes()).hexdigest(), 'bytes': library.stat().st_size},
                'ndk_revision': '27.2.12479018', 'minimum_android_api': 30,
                'validation': 'Native compilation and SPIR-V validation; physical driver execution remains untested'})
(WORK / 'reports/native-component-build.json').write_text(json.dumps(reports, indent=2) + '\n')
print('Built', library.name, library.stat().st_size, 'bytes; probe shaders validated.')
