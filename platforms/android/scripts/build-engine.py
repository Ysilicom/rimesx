#!/usr/bin/env python3
"""Pinned Android-only librime build. Never modifies iOS resources or its build cache."""
import argparse, hashlib, json, os, pathlib, platform, shutil, subprocess, tarfile, urllib.request
ROOT = pathlib.Path(__file__).resolve().parents[3]
ANDROID = ROOT / 'platforms/android'
WORK = ANDROID / '.native'
LOCK = json.loads((ROOT / 'platforms/ios/dependencies.lock.json').read_text())
NDK = '29.0.14206865'
CMAKE = '3.22.1'
SCHEMAS = ('rimes_pinyin', 'rimes_pinyin9', 'rimes_ziranma', 'rimes_flypy', 'rimes_wubi')
# Full pinyin, Xiaohe, and Natural Code. The value is the mixed-word table for that scheme.
MIXED = (('rimes_pinyin', 'cn_en'), ('rimes_flypy', 'cn_en_flypy'), ('rimes_ziranma', 'cn_en_ziranma'))
TABLES = (('melt_eng', 'melt_eng'), ('cn_en', 'cn_en'), ('cn_en_flypy', 'cn_en_flypy'), ('cn_en_ziranma', 'cn_en_ziranma'))
ENGLISH_SOURCES = (
    ROOT/'rime-data/melt_eng.dict.yaml',
    ROOT/'rime-data/en_dicts/en.dict.yaml',
    ROOT/'rime-data/en_dicts/en_ext.dict.yaml',
    ROOT/'rime-data/en_dicts/cn_en.txt',
    ROOT/'rime-data/en_dicts/cn_en_flypy.txt',
    ROOT/'rime-data/en_dicts/cn_en_double_pinyin.txt',
    ROOT/'rime-data/licenses/GPL-3.0.txt',
    ROOT/'rime-data/licenses/rime-ice-SOURCE.md',
)
def schema_source(name):
    root = ANDROID/'resources' if name in ('rimes_pinyin', 'rimes_pinyin9', 'rimes_flypy') else ROOT/'platforms/ios/Resources/EngineData'
    return root/(name+'.schema.yaml')
def mixed_schema(text, schema, dictionary):
    translators = '  translators: [punct_translator, script_translator]\n'
    if text.count(translators) != 1 or text.count('  enable_user_dict: true\n') != 1 or text.count('  prism: '+schema+'\n') != 1:
        raise RuntimeError('Cannot derive a mixed schema from '+schema)
    text = text.replace(translators, '  translators: [punct_translator, script_translator, table_translator@melt_eng, table_translator@cn_en]\n', 1)
    text = text.replace('schema_id: '+schema, 'schema_id: '+schema+'_mix', 1)
    text = text.replace('  prism: '+schema+'\n', '  prism: '+schema+'_mix\n', 1)
    text = text.replace('  enable_user_dict: true\n', '  initial_quality: 1.2\n  enable_user_dict: true\n', 1)
    text = '# English tables mounted below are GPL-3.0-only Rime Ice data.\n'+text
    text += (
        '\n# English words and mixed tokens. Short-word demotion is desktop Lua and is not applied here.\n'
        'melt_eng:\n'
        '  dictionary: melt_eng\n'
        '  enable_sentence: false\n'
        '  enable_user_dict: false\n'
        '  enable_completion: true\n'
        '  initial_quality: 1.1\n'
        '  comment_format:\n'
        '    - "xform/.*//"\n'
        'cn_en:\n'
        '  dictionary: '+dictionary+'\n'
        '  enable_sentence: false\n'
        '  enable_user_dict: false\n'
        '  enable_completion: true\n'
        '  initial_quality: 0.5\n'
        '  comment_format:\n'
        '    - "xform/^.+$//"\n'
    )
    return text
def english_schema():
    # Same identity speller as the melt_eng table build, so the prism matches typed letters.
    return (
        '# English candidates for the 中/英 key. GPL-3.0-only Rime Ice melt_eng table.\n'
        'schema:\n'
        '  schema_id: rimes_english\n'
        '  name: RIMES English\n'
        '  version: "1"\n'
        'engine:\n'
        '  processors: [ascii_composer, speller, punctuator, selector, navigator, express_editor]\n'
        '  segmentors: [ascii_segmentor, abc_segmentor, punct_segmentor, fallback_segmentor]\n'
        '  translators: [punct_translator, table_translator]\n'
        'speller:\n'
        '  alphabet: zyxwvutsrqponmlkjihgfedcbaZYXWVUTSRQPONMLKJIHGFEDCBA\n'
        '  delimiter: " \'"\n'
        'translator:\n'
        '  dictionary: melt_eng\n'
        '  prism: rimes_english\n'
        '  enable_sentence: false\n'
        '  enable_user_dict: false\n'
        '  enable_completion: true\n'
        '  spelling_hints: 0\n'
        '  comment_format:\n'
        '    - "xform/.*//"\n'
        'punctuator:\n'
        '  half_shape:\n'
        '    ",": ","\n'
        '    ".": "."\n'
        '    "?": "?"\n'
        '    "!": "!"\n'
        '    ":": ":"\n'
        '    ";": ";"\n'
        "    \"'\": \"'\"\n"
    )
def table_schema(dictionary):
    return (
        '# Build-only table. Not a selectable input scheme.\n'
        'schema:\n'
        '  schema_id: '+dictionary+'\n'
        '  name: '+dictionary+'\n'
        '  version: "1"\n'
        'engine:\n'
        '  processors: [speller]\n'
        '  segmentors: [abc_segmentor]\n'
        '  translators: [table_translator]\n'
        'speller:\n'
        '  alphabet: zyxwvutsrqponmlkjihgfedcbaZYXWVUTSRQPONMLKJIHGFEDCBA\n'
        "  delimiter: \" '\"\n"
        'translator:\n'
        '  dictionary: '+dictionary+'\n'
    )
def write_mixed_table(source, dest, name):
    rows = []
    for line in source.read_text(encoding='utf-8').splitlines():
        if not line.strip() or line.startswith('#'): continue
        text, code = line.split('\t', 1)
        if text and code: rows.append(text+'\t'+code)
    if not rows: raise RuntimeError('Empty mixed-word table '+str(source))
    dest.write_text(
        '# Rime dictionary\n# encoding: utf-8\n# GPL-3.0-only. Generated from '+source.name+'.\n'
        '---\nname: '+name+'\nversion: "1"\nsort: original\ncolumns:\n  - text\n  - code\n...\n'
        +'\n'.join(rows)+'\n', encoding='utf-8')
def stage_mixed_tables(stage):
    english = stage/'en_dicts'
    english.mkdir()
    shutil.copy2(ROOT/'rime-data/melt_eng.dict.yaml', stage/'melt_eng.dict.yaml')
    shutil.copy2(ROOT/'rime-data/en_dicts/en.dict.yaml', english/'en.dict.yaml')
    shutil.copy2(ROOT/'rime-data/en_dicts/en_ext.dict.yaml', english/'en_ext.dict.yaml')
    write_mixed_table(ROOT/'rime-data/en_dicts/cn_en.txt', stage/'cn_en.dict.yaml', 'cn_en')
    write_mixed_table(ROOT/'rime-data/en_dicts/cn_en_flypy.txt', stage/'cn_en_flypy.dict.yaml', 'cn_en_flypy')
    write_mixed_table(ROOT/'rime-data/en_dicts/cn_en_double_pinyin.txt', stage/'cn_en_ziranma.dict.yaml', 'cn_en_ziranma')
    for _schema_id, dictionary in TABLES:
        (stage/(dictionary+'.schema.yaml')).write_text(table_schema(dictionary))
def run(*args, **kw):
    subprocess.run([str(a) for a in args], check=True, **kw)
def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def download(url, path, digest):
    if not path.exists() or sha(path) != digest:
        path.parent.mkdir(parents=True, exist_ok=True)
        temp = path.with_suffix('.download')
        urllib.request.urlretrieve(url, temp)
        if sha(temp) != digest: raise RuntimeError('Checksum mismatch: ' + url)
        temp.replace(path)
def export_git(source, destination, revision):
    actual = subprocess.check_output(['git', '-C', str(source), 'rev-parse', 'HEAD'], text=True).strip()
    if actual != revision: raise RuntimeError('Wrong cached revision: ' + str(source))
    destination.mkdir(parents=True, exist_ok=True)
    archive = WORK / 'source.tar'
    with archive.open('wb') as out: run('git', '-C', source, 'archive', revision, stdout=out)
    with tarfile.open(archive) as tar: tar.extractall(destination, filter='data')
    archive.unlink()
def bootstrap(cache):
    WORK.mkdir(parents=True, exist_ok=True)
    src = WORK / 'librime'
    stamp = src / '.android-revision'
    fingerprint = json.dumps([LOCK['librime'], LOCK['submodules']], sort_keys=True)
    if not stamp.exists() or stamp.read_text() != fingerprint:
        if src.exists(): shutil.rmtree(src)
        if cache:
            export_git(cache/'librime', src, LOCK['librime']['commit'])
            for dep, revision in LOCK['submodules'].items():
                export_git(cache/'librime/deps'/dep, src/'deps'/dep, revision)
        else:
            run('git', 'clone', '--branch', LOCK['librime']['tag'], '--depth', '1', LOCK['librime']['url'], src)
            actual = subprocess.check_output(['git', '-C', str(src), 'rev-parse', 'HEAD'], text=True).strip()
            if actual != LOCK['librime']['commit']: raise RuntimeError('librime revision mismatch')
            run('git', '-C', src, 'submodule', 'update', '--init', '--depth', '1')
            for dep, revision in LOCK['submodules'].items():
                actual = subprocess.check_output(['git','-C',str(src/'deps'/dep),'rev-parse','HEAD'],text=True).strip()
                if actual != revision: raise RuntimeError('Submodule revision mismatch: '+dep)
        stamp.write_text(fingerprint)
    archive = WORK/'boost.tar.bz2'
    if cache and not archive.exists(): shutil.copy2(cache/'boost.tar.bz2', archive)
    download(LOCK['boost']['url'], archive, LOCK['boost']['sha256'])
    if not (WORK/'boost_1_86_0/boost/version.hpp').exists():
        with tarfile.open(archive) as tar: tar.extractall(WORK, filter='data')
    return src

def build(src, abi, sdk):
    cmake = sdk/'cmake'/CMAKE/'bin/cmake'
    prefix = WORK/abi/'install'
    base = ['-G','Ninja',f'-DCMAKE_MAKE_PROGRAM={sdk/"cmake"/CMAKE/"bin/ninja"}', '-DCMAKE_BUILD_TYPE=Release',
            f'-DCMAKE_INSTALL_PREFIX={prefix}', '-DCMAKE_POSITION_INDEPENDENT_CODE=ON', '-DBUILD_SHARED_LIBS=OFF', '-DBUILD_TESTING=OFF']
    if abi != 'host':
        base += [f'-DCMAKE_TOOLCHAIN_FILE={sdk/"ndk"/NDK/"build/cmake/android.toolchain.cmake"}',
                 f'-DANDROID_ABI={abi}', '-DANDROID_PLATFORM=android-26', '-DANDROID_STL=c++_static', '-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON']
    deps = [('leveldb',['-DLEVELDB_BUILD_TESTS=OFF','-DLEVELDB_BUILD_BENCHMARKS=OFF','-DHAVE_SNAPPY=OFF','-DHAVE_CRC32C=OFF','-DHAVE_TCMALLOC=OFF']),
            ('marisa-trie',['-DENABLE_TOOLS=OFF']), ('yaml-cpp',['-DYAML_CPP_BUILD_TESTS=OFF','-DYAML_CPP_BUILD_TOOLS=OFF','-DYAML_CPP_BUILD_CONTRIB=OFF']),
            ('opencc',['-DENABLE_GTEST=OFF','-DENABLE_DARTS=OFF','-DBUILD_DOCUMENTATION=OFF'])]
    for dep, flags in deps:
        folder = WORK/abi/dep
        stamp = folder/'.done'
        fingerprint = hashlib.sha256(json.dumps([base+flags,LOCK['submodules']]).encode()).hexdigest()
        if stamp.exists() and stamp.read_text() == fingerprint: continue
        run(cmake, '-S', src/'deps'/dep, '-B', folder, *base, *flags)
        if dep == 'opencc' and abi != 'host':
            run(cmake,'--build',folder,'--target','libopencc','--parallel','2')
            (prefix/'lib').mkdir(parents=True,exist_ok=True)
            for lib in folder.rglob('*.a'): shutil.copy2(lib,prefix/'lib'/lib.name)
            shutil.copytree(src/'deps/opencc/src',prefix/'include/opencc',dirs_exist_ok=True)
            shutil.copy2(folder/'src/opencc_config.h',prefix/'include/opencc/opencc_config.h')
        else: run(cmake,'--build',folder,'--target','install','--parallel','2')
        stamp.write_text(fingerprint)
    flags = [f'-DBoost_INCLUDE_DIR={WORK/"boost_1_86_0"}', f'-DBOOST_ROOT={WORK/"boost_1_86_0"}', '-DBoost_NO_BOOST_CMAKE=ON',
             f'-DYamlCpp_NEW_API={prefix/"include"}',f'-DCMAKE_PREFIX_PATH={prefix}', '-DBUILD_STATIC=ON', '-DBUILD_TEST=OFF',
             '-DENABLE_LOGGING=OFF','-DENABLE_EXTERNAL_PLUGINS=OFF','-DENABLE_TIMESTAMP=OFF']
    for package, library in [('YamlCpp','yaml-cpp'),('LevelDb','leveldb'),('Marisa','marisa'),('Opencc','opencc')]:
        flags += [f'-D{package}_INCLUDE_PATH={prefix/"include"}',f'-D{package}_LIBRARY={prefix/"lib"/f"lib{library}.a"}']
    folder = WORK/abi/'rime'
    run(cmake,'-S',src,'-B',folder,*base,*flags, '-DBUILD_SHARED_LIBS='+('ON' if abi=='host' else 'OFF'))
    run(cmake,'--build',folder,'--target','rime_deployer' if abi=='host' else 'rime-static','--parallel','2')
    if abi != 'host':
        run(cmake,'-S',ANDROID/'native','-B',WORK/abi/'bridge',*base,f'-DRIME_SOURCE={src}',f'-DRIME_BUILD={folder}',f'-DRIME_DEPS={prefix}')
        run(cmake,'--build',WORK/abi/'bridge','--parallel','2')
        destination = ANDROID/'app/build/generated/rime/jniLibs'/abi
        destination.mkdir(parents=True,exist_ok=True)
        shutil.copy2(WORK/abi/'bridge/librimes_jni.so', destination/'librimes_jni.so')

def prepare_data():
    stage = WORK/'data-stage'
    if stage.exists(): shutil.rmtree(stage)
    stage.mkdir()
    resources = ROOT/'platforms/ios/Resources/EngineData'
    default = (resources/'default.yaml').read_text()
    if 'schema: rimes_pinyin9' not in default:
        default = default.replace('  - schema: rimes_pinyin\n', '  - schema: rimes_pinyin\n  - schema: rimes_pinyin9\n')
    if 'schema: rimes_flypy' not in default:
        default = default.replace('  - schema: rimes_ziranma\n', '  - schema: rimes_ziranma\n  - schema: rimes_flypy\n')
    (stage/'default.yaml').write_text(default)
    for schema in SCHEMAS:
        data = schema_source(schema).read_text()
        (stage/(schema+'.schema.yaml')).write_text(data)
        private = data.replace('schema_id: '+schema,'schema_id: '+schema+'_private').replace('enable_user_dict: true','enable_user_dict: false')
        (stage/(schema+'_private.schema.yaml')).write_text(private)
    stage_mixed_tables(stage)
    for schema, dictionary in MIXED:
        mixed = mixed_schema(schema_source(schema).read_text(), schema, dictionary)
        (stage/(schema+'_mix.schema.yaml')).write_text(mixed)
        private = mixed.replace('schema_id: '+schema+'_mix','schema_id: '+schema+'_mix_private').replace('enable_user_dict: true','enable_user_dict: false')
        (stage/(schema+'_mix_private.schema.yaml')).write_text(private)
    for item in LOCK['data']:
        if item['path'].endswith('.dict.yaml'):
            download(item['url'],WORK/'downloads'/pathlib.Path(item['path']).name,item['sha256'])
            shutil.copy2(WORK/'downloads'/pathlib.Path(item['path']).name,stage)
    base_dict = ROOT / 'rime-data/cn_dicts/base.dict.yaml'
    pinyin_simp_stage = stage / 'pinyin_simp.dict.yaml'
    if base_dict.exists() and pinyin_simp_stage.exists():
        existing_words = set()
        for line in pinyin_simp_stage.read_text(encoding='utf-8').splitlines():
            parts = line.strip().split('\t')
            if parts: existing_words.add(parts[0])
        additions = []
        for line in base_dict.read_text(encoding='utf-8').splitlines():
            line_str = line.strip()
            if not line_str or line_str.startswith('#'): continue
            parts = line_str.split('\t')
            if len(parts) >= 3 and parts[2].isdigit():
                freq = int(parts[2])
                word = parts[0]
                if (freq >= 500 or '帧' in word) and word not in existing_words:
                    existing_words.add(word)
                    additions.append(line_str)
        if additions:
            with pinyin_simp_stage.open('a', encoding='utf-8') as out:
                out.write('\n# --- Modern Vocabulary Supplement (rime-ice) ---\n')
                out.write('\n'.join(additions) + '\n')
            print(f'AUGMENTED pinyin_simp with {len(additions)} modern phrases including 帧数', flush=True)
    deployer = WORK/'host/rime/bin/rime_deployer'
    run(deployer,'--build',stage,stage,stage/'build')
    for schema in SCHEMAS:
        run(deployer,'--compile',stage/(schema+'.schema.yaml'),stage,stage,stage/'build')
        run(deployer,'--compile',stage/(schema+'_private.schema.yaml'),stage,stage,stage/'build')
    for _schema_id, dictionary in TABLES:
        run(deployer,'--compile',stage/(dictionary+'.schema.yaml'),stage,stage,stage/'build')
    for schema, _dictionary in MIXED:
        run(deployer,'--compile',stage/(schema+'_mix.schema.yaml'),stage,stage,stage/'build')
        run(deployer,'--compile',stage/(schema+'_mix_private.schema.yaml'),stage,stage,stage/'build')
    (stage/'rimes_english.schema.yaml').write_text(english_schema())
    run(deployer,'--compile',stage/'rimes_english.schema.yaml',stage,stage,stage/'build')
    dest = ANDROID/'app/build/generated/rime/assets/rime'
    if dest.exists(): shutil.rmtree(dest)
    (dest/'build').mkdir(parents=True)
    for path in (stage/'build').iterdir():
        if path.suffix in ('.bin','.yaml'): shutil.copy2(path,dest/'build'/path.name)
    shutil.copy2(stage/'default.yaml',dest/'default.yaml')
    shutil.copy2(ANDROID/'resources/nine-key-syllables.json',dest/'nine-key-syllables.json')
    shutil.copy2(ANDROID/'resources/chord-profile.json',dest/'chord-profile.json')
    # Runtime only needs compiled data. Source dictionaries remain pinned build inputs.
    licenses = ANDROID/'app/build/generated/rime/assets/licenses'
    shutil.copytree(ROOT/'platforms/ios/Licenses', licenses, dirs_exist_ok=True)
    shutil.copy2(ROOT/'LICENSE',licenses/'RIMES-Apache-2.0.txt')
    shutil.copy2(ROOT/'NOTICE',licenses/'RIMES-NOTICE.txt')
    shutil.copy2(ROOT/'OfficialPlugins/NOTICE',licenses/'RIMES-Plugins-NOTICE.txt')
    shutil.copy2(ROOT/'rime-data/licenses/GPL-3.0.txt',licenses/'rime-ice-GPL-3.0.txt')
    shutil.copy2(ROOT/'rime-data/licenses/rime-ice-SOURCE.md',licenses/'rime-ice-SOURCE.md')
    entries = {str(p.relative_to(dest)):sha(p) for p in sorted(dest.rglob('*')) if p.is_file()}
    manifest = {'format':1,'librime':LOCK['librime']['commit'],'files':entries}
    (dest/'manifest.json').write_text(json.dumps(manifest,sort_keys=True,indent=2)+'\n')
    print('PACKAGED',len(entries),'precompiled resources',flush=True)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--source-cache',type=pathlib.Path)
    parser.add_argument('--abis',nargs='+',default=['arm64-v8a','x86_64'])
    args = parser.parse_args()
    sdk = pathlib.Path(os.environ.get('ANDROID_HOME',os.environ.get('ANDROID_SDK_ROOT','')))
    if not (sdk/'ndk'/NDK).exists(): raise SystemExit('Install NDK '+NDK+' and CMake '+CMAKE+'; set ANDROID_HOME')
    source = bootstrap(args.source_cache)
    build(source,'host',sdk)
    prepare_data()
    for abi in args.abis: build(source,abi,sdk)
    # Keep Gradle from accidentally packaging libraries built from older native inputs.
    inputs = [pathlib.Path(__file__).resolve(), ROOT/'LICENSE', ROOT/'NOTICE', ROOT/'OfficialPlugins/NOTICE', ROOT/'platforms/ios/dependencies.lock.json']
    inputs += list((ANDROID/'native').glob('*'))
    inputs += [schema_source(name) for name in SCHEMAS]
    inputs += [ANDROID/'resources/nine-key-syllables.json',ANDROID/'resources/chord-profile.json',
               ANDROID/'core/src/main/java/org/scholay/rimes/core/ChordData.java']
    inputs += [ROOT/'platforms/ios/Resources/EngineData/default.yaml']
    if (ROOT/'rime-data/cn_dicts/base.dict.yaml').exists(): inputs.append(ROOT/'rime-data/cn_dicts/base.dict.yaml')
    inputs += list(ENGLISH_SOURCES)
    inputs += list((ROOT/'platforms/ios/Licenses').glob('*'))
    receipt = {'inputs':{str(p.relative_to(ROOT)):sha(p) for p in sorted(inputs) if p.is_file()},
               'libraries':{abi:sha(ANDROID/'app/build/generated/rime/jniLibs'/abi/'librimes_jni.so') for abi in args.abis},
               'ndk':NDK,'cmake':CMAKE}
    (ANDROID/'app/build/generated/rime/build-receipt.json').write_text(json.dumps(receipt,sort_keys=True,indent=2)+'\n')
