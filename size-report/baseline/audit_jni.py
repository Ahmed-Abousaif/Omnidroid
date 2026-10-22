import os
import re

cpp_dir = 'libretrodroid/src/main/cpp'

findclass_pat = re.compile(r'FindClass\s*\(\s*"([^"]+)"\s*\)')
getmethod_pat = re.compile(r'GetMethodID\s*\([^,]+,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*\)')
getstatic_method_pat = re.compile(r'GetStaticMethodID\s*\([^,]+,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*\)')
getfield_pat = re.compile(r'GetFieldID\s*\([^,]+,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*\)')
getstatic_field_pat = re.compile(r'GetStaticFieldID\s*\([^,]+,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*\)')

for root, dirs, files in os.walk(cpp_dir):
    for f in files:
        if f.endswith('.cpp') or f.endswith('.h'):
            p = os.path.join(root, f)
            with open(p, 'r', encoding='utf-8', errors='ignore') as fp:
                content = fp.read()
            fc = findclass_pat.findall(content)
            gm = getmethod_pat.findall(content)
            gsm = getstatic_method_pat.findall(content)
            gf = getfield_pat.findall(content)
            gsf = getstatic_field_pat.findall(content)
            if any([fc, gm, gsm, gf, gsf]):
                print(f"=== {f} ===")
                if fc:
                    print("  FindClass:", fc)
                if gm:
                    print("  GetMethodID:", gm)
                if gsm:
                    print("  GetStaticMethodID:", gsm)
                if gf:
                    print("  GetFieldID:", gf)
                if gsf:
                    print("  GetStaticFieldID:", gsf)
