from pathlib import Path
import os
import shutil
import subprocess

PACKAGE = Path(__file__).resolve().parents[1]
WORK = Path(os.environ['XHYN_WORK_DIR'])
NDK = Path(os.environ['XHYN_NDK_ROOT'])
OUT = WORK / 'native-host-tests'
OUT.mkdir(exist_ok=True)
include = OUT / 'include'
include.mkdir(exist_ok=True)
headers = NDK / 'toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/include'
for name in ['vulkan', 'vk_video']:
    shutil.copytree(headers / name, include / name, dirs_exist_ok=True)
android = include / 'android'
android.mkdir(exist_ok=True)
(android / 'log.h').write_text('#define ANDROID_LOG_ERROR 6\nint __android_log_print(int,const char *,const char *,...);\n')
(android / 'native_window_jni.h').write_text('#include <jni.h>\n#include <stdint.h>\ntypedef struct ANativeWindow ANativeWindow;\nANativeWindow *ANativeWindow_fromSurface(JNIEnv *,jobject);\nvoid ANativeWindow_release(ANativeWindow *);\nint32_t ANativeWindow_getWidth(ANativeWindow *);\nint32_t ANativeWindow_getHeight(ANativeWindow *);\n')
jdk = Path(os.environ['JAVA_HOME'])
command = ['cc', '-std=c11', '-O1', '-g', '-pthread', '-fno-omit-frame-pointer', '-Wall', '-Wextra', '-Werror',
           '-Wno-error=int-to-pointer-cast', '-I', str(include), '-I', str(jdk/'include'), '-I', str(jdk/'include/linux'),
           '-I', str(WORK/'native-build'), str(PACKAGE/'tests/native_component_host.c'), '-ldl', '-o', str(OUT/'native-component')]
subprocess.run(command, check=True)
result = subprocess.run([str(OUT/'native-component'), str(OUT)], capture_output=True, text=True)
(WORK/'reports/native-component-host-checks.txt').write_text(result.stdout + result.stderr)
print(result.stdout, end='')
if result.returncode: raise RuntimeError(result.stderr or 'native component host checks failed')
sanitized=OUT/'native-component-sanitized'
sanitizer=command[:-2]+['-fsanitize=address,undefined','-pthread','-o',str(sanitized)]
subprocess.run(sanitizer,check=True)
environment=os.environ.copy();environment.update(ASAN_OPTIONS='detect_leaks=0:halt_on_error=1',UBSAN_OPTIONS='halt_on_error=1:print_stacktrace=1')
checked=subprocess.run([str(sanitized),str(OUT)],capture_output=True,text=True,env=environment)
(WORK/'reports/native-sanitizer-checks.txt').write_text(checked.stdout+checked.stderr)
if checked.returncode or checked.stderr:raise RuntimeError(checked.stdout+checked.stderr)
print('ASan/UBSan: '+checked.stdout,end='')
