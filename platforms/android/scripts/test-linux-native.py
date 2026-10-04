#!/usr/bin/env python3
"""Load the Android x86_64 .so with a pinned AOSP Bionic runtime on x86_64 Linux.
Validation only; this runtime is never packaged in the APK. Requires debugfs (e2fsprogs).
"""
import base64,hashlib,os,pathlib,subprocess,tempfile,urllib.request,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
WORK=ROOT/'.native'
RUNTIME=WORK/'bionic-x86_64'
RUNTIME.mkdir(parents=True,exist_ok=True)
REVISION='070571b455076f77a01c7b07154a15e545d2b428'
URL=f'https://android.googlesource.com/platform/prebuilts/runtime/+/{REVISION}/mainline/runtime/apex/com.android.runtime-x86_64.apex?format=TEXT'
SHA='11c91be1e1f1fe19af790a0c3a6f9db6e8e36d0ab74e403aabb3b81bbe31bdd9'
apex=RUNTIME/'runtime.apex'
if not apex.exists() or hashlib.sha256(apex.read_bytes()).hexdigest()!=SHA:
    apex.write_bytes(base64.b64decode(urllib.request.urlopen(URL,timeout=120).read()))
assert hashlib.sha256(apex.read_bytes()).hexdigest()==SHA
with zipfile.ZipFile(apex) as archive: archive.extract('apex_payload.img',RUNTIME)
for name in ('libc.so','libm.so','libdl.so','linker64'):
    source='bin/linker64' if name=='linker64' else 'lib64/bionic/'+name
    target=RUNTIME/name
    # debugfs will not overwrite an existing file.
    target.unlink(missing_ok=True)
    subprocess.run(['debugfs','-R',f'dump {source} {target}',str(RUNTIME/'apex_payload.img')],check=True)
    assert target.stat().st_size>0
    target.chmod(0o755)
assets=ROOT/'app/build/generated/rime/assets/rime'
with tempfile.TemporaryDirectory(prefix='contract-',dir=WORK) as temp:
    temp=pathlib.Path(temp)
    engine=WORK/'x86_64/bridge/engine_contract'
    def call(*args,**kw): subprocess.run([str(x) for x in args],check=True,**kw)
    call(engine,assets,temp/'learn','learn')
    call(engine,assets,temp/'learn','export')
    before={name:sorted(line for line in (temp/'learn'/f'{name}.export.txt').read_text().splitlines() if not line.startswith('#')) for name in ('pinyin_simp','wubi86')}
    call(engine,assets,temp/'learn','private-existing')
    for name,lines in before.items():
        assert lines==sorted(line for line in (temp/'learn'/f'{name}.export.txt').read_text().splitlines() if not line.startswith('#')),name
    call(engine,assets,temp/'private','private')
    env=dict(os.environ,LD_LIBRARY_PATH=str(RUNTIME))
    call(RUNTIME/'linker64',WORK/'x86_64/bridge/library_contract',ROOT/'app/build/generated/rime/jniLibs/x86_64/librimes_jni.so',assets,temp/'load',env=env)
print('PASS x86_64 Android shared-library loading, real engine, persistent learning, and private/off preservation')
