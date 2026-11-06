"""Retain raw-path artwork that Apktool omits from its resource-table decode.

The original Application and touch icons open these entries by exact ZIP path,
without a resource ID. Copy the original encrypted bytes before APK signing;
keep the original authentication and rendering code unchanged.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
from zipfile import ZipFile, ZIP_STORED

ARTWORK = re.compile(r'res/drawable/titanium_[a-z0-9_]+\.bin')


def restore(original, unsigned):
    records = []
    with ZipFile(original) as source, ZipFile(unsigned, 'a') as output:
        names = output.namelist()
        if len(names) != len(set(names)):
            raise ValueError('Duplicate APK entries')
        entries = [entry for entry in source.infolist() if ARTWORK.fullmatch(entry.filename)]
        if len(entries) != 146:
            raise ValueError('Unexpected artwork inventory in the supported base')
        for entry in entries:
            data = source.read(entry)
            if entry.compress_type != ZIP_STORED:
                raise ValueError('Base artwork must support openNonAssetFd')
            if entry.filename in names:
                if output.read(entry.filename) != data or output.getinfo(entry.filename).compress_type != ZIP_STORED:
                    raise ValueError('Rebuilt artwork differs from the original: ' + entry.filename)
            else:
                output.writestr(entry.filename, data, compress_type=ZIP_STORED)
            records.append({'path': entry.filename, 'sha256': hashlib.sha256(data).hexdigest(), 'bytes': len(data)})
    return {'entries': records, 'count': len(records), 'authentication_unchanged': True}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input-apk', required=True, type=Path)
    parser.add_argument('--unsigned-apk', required=True, type=Path)
    parser.add_argument('--report', required=True, type=Path)
    args = parser.parse_args()
    result = restore(args.input_apk, args.unsigned_apk)
    args.report.write_text(json.dumps(result, indent=2) + '\n')
    print('Preserved %d original raw-path artwork files before signing' % result['count'])
