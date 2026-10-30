from pathlib import Path
import os
import subprocess
import sys

PACKAGE = Path(__file__).resolve().parents[1]
WORK = Path(os.environ['XHYN_WORK_DIR'])
OUT = WORK / 'config-host-tests'
OUT.mkdir(exist_ok=True)
sources = PACKAGE / 'java/com/gtavsource/android'
subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(OUT),
                str(sources / 'XhynV02Config.java'), str(sources / 'XhynV02DriverPolicy.java'),
                str(sources / 'XhynV02DxvkOptions.java'), str(sources / 'XhynV02SettingHelp.java'),
                str(PACKAGE / 'tests/ConfigHostChecks.java')], check=True)
result = subprocess.run(['java', '-cp', str(OUT), 'ConfigHostChecks', str(OUT)], check=True, capture_output=True, text=True)
print(result.stdout, end='')
reference = PACKAGE / 'tests/reference/gplall-headers/sources/gplall'
command = ['g++', '-std=c++17', '-O1', '-DDXVK_NATIVE', '-ffunction-sections', '-fdata-sections', '-include', 'cmath', '-include', 'algorithm']
for include in ['src/util/config', 'include/native/windows', 'include/native/directx']:
    command += ['-I', str(reference / include)]
command += [str(PACKAGE / 'tests/native_config_test.cpp'), str(PACKAGE / 'tests/reference/config268.cpp'), '-Wl,--gc-sections', '-o', str(OUT / 'native-parser')]
subprocess.run(command, check=True)
native = subprocess.run([str(OUT / 'native-parser'), str(OUT / 'all-options-effective.conf'), str(OUT / 'ui-global.conf'), str(OUT / 'ui-scoped.conf'), str(OUT / 'tuning-controls.conf')], check=True, capture_output=True, text=True)
print(native.stdout, end='')
(WORK / 'reports/config-host-checks.txt').write_text(result.stdout + native.stdout + native.stderr)
