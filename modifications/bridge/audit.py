#!/usr/bin/env python3
"""Check the signed Bridge artifact, native/backend identity and English assets."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import struct
import zipfile
from loguru import logger
logger.remove()
from androguard.core.apk import APK
from elftools.elf.elffile import ELFFile

PACKAGE=Path(__file__).resolve().parent
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--apk',type=Path,required=True)
parser.add_argument('--input-apk',type=Path,required=True)
parser.add_argument('--source-dir',type=Path,required=True)
parser.add_argument('--report',type=Path,required=True)
args=parser.parse_args()
spec=json.loads((PACKAGE/'inputs.json').read_text())
checks=[]
def sha(data):return hashlib.sha256(data).hexdigest()
def check(value,label):
    if not value:raise AssertionError(label)
    checks.append(label)
check(sha(args.input_apk.read_bytes())==spec['input_apk_sha256'],'Exact companion input')
app=APK(str(args.apk))
check(app.get_package()==spec['application_id'] and app.get_app_name()=='XHYN V Bridge','Bridge package and visible label')
check(app.get_androidversion_name()=='0.1.0' and app.get_androidversion_code()=='1','Bridge version')
check('com.kevin.astral.MainActivity' in app.get_activities(),'Original Android activity namespace remains valid')
receivers=set(app.get_receivers())
keys=(args.source_dir/'lib/core/constants/home_widget_keys.dart').read_text()
names=re.findall(r"widget(?:Small|Medium|Large)\s*=\s*'([^']+)'",keys)
check(len(names)==3 and set(names)<=receivers,'Qualified Dart widget names match packaged receivers')
check('com.plugin.vpn_service_plugin.TauriVpnService' in app.get_services(),'VPN service packaged')
android='{http://schemas.android.com/apk/res/android}'
manifest=app.get_android_manifest_xml().find('application')
service=next(x for x in manifest.findall('service') if x.get(android+'name')=='com.plugin.vpn_service_plugin.TauriVpnService')
check(service.get(android+'permission')=='android.permission.BIND_VPN_SERVICE','VPN permission contract preserved')
check(not any('FirebaseInitProvider' in x.get(android+'name','') for x in manifest.findall('provider')),'Separate build does not initialize the upstream Firebase app')
certificates=app.get_certificates_der_v2()
check(len(certificates)==1 and sha(certificates[0])=='3119f160d42bb34070bed377e124b7f55b725f5273489dc4b1e9532239c82c1a','XHYN release certificate')
libraries={}
with zipfile.ZipFile(args.input_apk) as original,zipfile.ZipFile(args.apk) as built,args.apk.open('rb') as stream:
    native=[name for name in built.namelist() if name.startswith('lib/') and name.endswith('.so')]
    check({name.split('/')[1] for name in native}=={'arm64-v8a'},'No incomplete foreign-ABI installation paths')
    for name in native:
        data=built.read(name);elf=ELFFile(io.BytesIO(data))
        check(elf.header.e_machine=='EM_AARCH64','ARM64 library '+name)
        check(all(x.header.p_align>=16384 for x in elf.iter_segments() if x.header.p_type=='PT_LOAD'),'ELF segment alignment '+name)
        item=built.getinfo(name);check(item.compress_type==zipfile.ZIP_STORED,'Uncompressed native library '+name)
        stream.seek(item.header_offset);header=stream.read(30);n,e=struct.unpack_from('<HH',header,26)
        check((item.header_offset+30+n+e)%16384==0,'APK native-entry alignment '+name)
        libraries[name]=sha(data)
    backend='lib/arm64-v8a/librust_lib_astral.so'
    check(built.read(backend)==original.read(backend) and libraries[backend]==spec['native_backend_sha256'],'Networking backend unchanged byte-for-byte')
    check(not any(name.endswith('/zh.json') for name in built.namelist()),'English localization edition')
    prefix='assets/flutter_assets/'
    english=built.read(prefix+'assets/translations/en.json')
    check(english==(args.source_dir/'assets/translations/en.json').read_bytes(),'English assets match tested source')
    check(all(not re.search('[\u3400-\u9fff]',str(v)) for v in json.loads(english).values()),'English localization values')
    check(built.read(prefix+'assets/logo.png')==(PACKAGE.parents[1]/'assets/xhyn-v.png').read_bytes(),'Repository logo packaged')
    for name in ['ASTRAL-LICENSE.txt','UPSTREAM-NOTICES.txt']:
        check(built.read(prefix+'assets/'+name)==(PACKAGE/name).read_bytes(),'Original license/notice retained '+name)
resources=app.get_android_resources()
for name,value in [('widget_brand','XHYN V Bridge'),('widget_status_default','Disconnected'),('widget_room_default','Not selected')]:
    result=resources.get_string(app.get_package(),name)
    check(result is not None and result[1]==value,'English widget resource '+name)
result={'result':'PASS','checks':len(checks),'file':args.apk.name,'sha256':sha(args.apk.read_bytes()),
        'bytes':args.apk.stat().st_size,'package':app.get_package(),'version':'0.1.0',
        'native_library_sha256':libraries,'physical_vpn_tested':False,'two_device_gameplay_tested':False,
        'scope':'Signed artifact identity, native/backend preservation, receiver links and packaged English assets; not a live connectivity test'}
args.report.write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result,indent=2))
