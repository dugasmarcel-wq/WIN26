#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
base=https://github.com/dugasmarcel-wq/WIN26/releases/download/win26-update-100141/Dayboox-14.apk
bt=$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
mkdir -p dist
curl -fsSL --retry 4 "$base" -o "$work/base.apk"
echo 'd42f3f48800ac75edfdd433dce8f06b778cb971782cf564fe820b82b7cd9faf0  '"$work/base.apk" | sha256sum -c
"$bt/apksigner" verify --print-certs "$work/base.apk" | tee "$work/base-certs"
grep -F '78f942aa7b29f5cc057401bfa61029b0e7affb5ac50c06136b2edd0ae09e0510' "$work/base-certs"
"$bt/aapt2" dump badging "$work/base.apk" | grep -F "package: name='com.marcel.dayboox' versionCode='14'"
unzip -l "$work/base.apk"
curl -fsSL --retry 4 https://github.com/iBotPeaches/Apktool/releases/download/v2.11.1/apktool_2.11.1.jar -o "$work/apktool.jar"
java -jar "$work/apktool.jar" d -s -f "$work/base.apk" -o "$work/decoded"
python3 - "$work/decoded" <<'PY'
import pathlib, re, sys, xml.etree.ElementTree as ET
p=pathlib.Path(sys.argv[1])
m=p/'AndroidManifest.xml'
print(m.read_text())
assets=p/'assets'
index=assets/'index.html'
source=index.read_text()
scripts='\n'.join(x.read_text(errors='replace') for x in assets.rglob('*.js'))
for expected in ['function render(', 'function timeEntry(', 'function api(', 'methodLog', 'methodStart', 'methodStop', 'dayGoals', 'investmentStats', 'daily-pad', 'four-grid']:
    assert expected in scripts, 'Unexpected released frontend: '+expected
assert '</body>' in source and '</head>' in source
for x in assets.rglob('*.js'):
    print('Released script:',x.relative_to(assets),x.stat().st_size)
index.write_text(source.replace('</head>','<link rel="stylesheet" href="preview.css"></head>').replace('</body>','<script src="preview.js"></script></body>'))
A='{http://schemas.android.com/apk/res/android}'
ET.register_namespace('android',A[1:-1])
tree=ET.parse(m);root=tree.getroot()
old=root.get('package');assert old=='com.marcel.dayboox'
# Existing DEX class names and resource IDs are retained; only the installation identity changes.
for elem in root.iter():
    if elem.tag in ['activity','activity-alias','service','receiver','provider','application','instrumentation']:
        for key in ['name','targetActivity','backupAgent','appComponentFactory']:
            value=elem.get(A+key)
            if value and value.startswith('.'):elem.set(A+key,old+value)
            elif value and '.' not in value and not value.startswith('@'):elem.set(A+key,old+'.'+value)
    if elem.tag=='provider':
        auth=elem.get(A+'authorities','')
        if auth:elem.set(A+'authorities',auth.replace('com.marcel.dayboox','com.marcel.daypreview').replace('com.marcel.daybook','com.marcel.daypreview'))
root.set('package','com.marcel.daypreview')
root.set(A+'versionCode','1');root.set(A+'versionName','0.1')
app=root.find('application');app.set(A+'label','Daybook Preview')
# Disable the existing update channel completely in the preview.
for elem in list(root):
    if elem.tag=='uses-permission' and elem.get(A+'name') in ['android.permission.INTERNET','android.permission.REQUEST_INSTALL_PACKAGES']:root.remove(elem)
tree.write(m,encoding='utf-8',xml_declaration=True)
y=p/'apktool.yml';v=y.read_text()
v=re.sub(r'(?m)^(\s*versionCode:\s*).+$',r'\g<1>1',v)
v=re.sub(r'(?m)^(\s*versionName:\s*).+$',r"\g<1>'0.1'",v)
v=re.sub(r'(?m)^(\s*renameManifestPackage:\s*).+$',r'\g<1>com.marcel.daypreview',v)
y.write_text(v)
PY
cp preview.js preview.css "$work/decoded/assets/"
node --check preview.js
java -jar "$work/apktool.jar" b "$work/decoded" -o "$work/unsigned.apk"
"$bt/zipalign" -f 4 "$work/unsigned.apk" "$work/aligned.apk"
# Standalone disposable preview signer; no original production key is used or committed.
keytool -genkeypair -keystore "$work/preview.jks" -storepass preview-build-only -keypass preview-build-only -alias preview -keyalg RSA -keysize 2048 -validity 3650 -dname "CN=Daybook Preview"
"$bt/apksigner" sign --ks "$work/preview.jks" --ks-pass pass:preview-build-only --key-pass pass:preview-build-only --out dist/Daybook-Preview.apk "$work/aligned.apk"
"$bt/apksigner" verify --print-certs dist/Daybook-Preview.apk | tee dist/signing.txt
"$bt/aapt2" dump badging dist/Daybook-Preview.apk | tee dist/package.txt
grep -F "package: name='com.marcel.daypreview' versionCode='1'" dist/package.txt
grep -F "application-label:'Daybook Preview'" dist/package.txt
! grep -F '78f942aa7b29f5cc057401bfa61029b0e7affb5ac50c06136b2edd0ae09e0510' dist/signing.txt
! grep -F "uses-permission: name='android.permission.INTERNET'" dist/package.txt
unzip -p "$work/base.apk" classes.dex > "$work/old.dex"
unzip -p dist/Daybook-Preview.apk classes.dex > "$work/new.dex"
cmp "$work/old.dex" "$work/new.dex"
unzip -p dist/Daybook-Preview.apk assets/preview.js | cmp - preview.js
(cd dist && sha256sum Daybook-Preview.apk > SHA256SUMS)
