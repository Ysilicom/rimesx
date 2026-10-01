#include <dlfcn.h>
#include <rime_api.h>
#include <cstdio>
#include <string>
#include <sys/stat.h>
int main(int argc,char** argv) {
    if(argc!=4) return 2;
    void* library=dlopen(argv[1],RTLD_NOW|RTLD_LOCAL);
    if(!library) { std::fprintf(stderr,"%s\n",dlerror()); return 1; }
    auto get_api=reinterpret_cast<RimeApi*(*)()>(dlsym(library,"rime_get_api"));
    if(!get_api || !dlsym(library,"Java_org_scholay_rimes_android_NativeRimeEngine_snapshotNative")) return 1;
    auto api=get_api(); std::string compiled=std::string(argv[2])+"/build"; mkdir(argv[3],0700);
    RIME_STRUCT(RimeTraits,traits); traits.shared_data_dir=argv[2]; traits.user_data_dir=argv[3];
    traits.prebuilt_data_dir=compiled.c_str(); traits.staging_dir=compiled.c_str(); traits.app_name="rime.android_library_contract";
    api->setup(&traits); api->initialize(&traits); auto session=api->create_session();
    struct Case {const char* schema; const char* code; const char* expected;};
    for(const auto item:{Case{"rimes_pinyin_private","nihao","你好"},Case{"rimes_ziranma_private","nihk","你好"},Case{"rimes_wubi_private","wq","你"}}) {
        if(!api->select_schema(session,item.schema)) return 1;
        for(const char* c=item.code;*c;++c) api->process_key(session,*c,0);
        if(!api->select_candidate(session,0)) return 1;
        RIME_STRUCT(RimeCommit,commit);
        if(!api->get_commit(session,&commit) || std::string(commit.text)!=item.expected) return 1;
        api->free_commit(&commit); std::printf("PASS dlopen %s -> %s\n",item.code,item.expected);
    }
    api->destroy_session(session); api->finalize(); dlclose(library); return 0;
}
