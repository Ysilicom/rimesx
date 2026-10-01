#include <jni.h>
#include <rime_api.h>
#include <algorithm>
#include <codecvt>
#include <locale>
#include <string>
#include <vector>

// JNI's modified UTF-8 is not standard UTF-8. Use explicit UTF-16 conversions,
// including surrogate pairs; Rime's caret offsets count UTF-8 bytes.
static std::u16string utf16(const std::string& s) {
    std::wstring_convert<std::codecvt_utf8_utf16<char16_t>, char16_t> converter;
    return converter.from_bytes(s);
}
static std::string utf8(JNIEnv* env, jstring value) {
    if (!value) return {};
    const jchar* chars = env->GetStringChars(value, nullptr);
    std::u16string text(reinterpret_cast<const char16_t*>(chars), env->GetStringLength(value));
    env->ReleaseStringChars(value, chars);
    std::wstring_convert<std::codecvt_utf8_utf16<char16_t>, char16_t> converter;
    return converter.to_bytes(text);
}
static jstring string(JNIEnv* env, const std::string& s) {
    auto value = utf16(s);
    return env->NewString(reinterpret_cast<const jchar*>(value.data()), static_cast<jsize>(value.size()));
}
static RimeApi* api() { return rime_get_api(); }
static bool initialized = false;
static std::string shared_path, user_path, build_path;
#define JNI(name) Java_org_scholay_rimes_android_NativeRimeEngine_##name
extern "C" JNIEXPORT void JNICALL JNI(initializeNative)(JNIEnv* env, jclass, jstring shared, jstring user) {
    if (initialized) return;
    shared_path = utf8(env, shared); user_path = utf8(env, user); build_path = shared_path + "/build";
    RIME_STRUCT(RimeTraits, traits);
    traits.shared_data_dir = shared_path.c_str(); traits.user_data_dir = user_path.c_str();
    traits.prebuilt_data_dir = build_path.c_str(); traits.staging_dir = build_path.c_str();
    traits.app_name = "rime.rimes_android";
    api()->setup(&traits); api()->initialize(&traits); initialized = true;
    // No maintenance/deployment here: all schemas/dictionaries were compiled on the host.
}
extern "C" JNIEXPORT jlong JNICALL JNI(createNative)(JNIEnv*, jclass) { return api()->create_session(); }
extern "C" JNIEXPORT void JNICALL JNI(destroyNative)(JNIEnv*, jclass, jlong session) { api()->destroy_session(session); }
extern "C" JNIEXPORT jboolean JNICALL JNI(schemaNative)(JNIEnv* env, jclass, jlong session, jstring schema) {
    return api()->select_schema(session, utf8(env, schema).c_str());
}
extern "C" JNIEXPORT jboolean JNICALL JNI(keyNative)(JNIEnv*, jclass, jlong session, jint key) { return api()->process_key(session,key,0); }
extern "C" JNIEXPORT jboolean JNICALL JNI(selectNative)(JNIEnv*, jclass, jlong session, jint index) {
    return index >= 0 && api()->select_candidate(session,static_cast<size_t>(index));
}
extern "C" JNIEXPORT void JNICALL JNI(clearNative)(JNIEnv*, jclass, jlong session) { api()->clear_composition(session); }
extern "C" JNIEXPORT jstring JNICALL JNI(roundTripNative)(JNIEnv* env, jclass, jstring text) { return string(env,utf8(env,text)); }
extern "C" JNIEXPORT jobject JNICALL JNI(snapshotNative)(JNIEnv* env, jclass, jlong session, jboolean handled) {
    std::string raw = api()->get_input(session) ? api()->get_input(session) : "";
    std::string commit, preedit;
    RIME_STRUCT(RimeCommit, committed);
    if (api()->get_commit(session,&committed)) { if (committed.text) commit=committed.text; api()->free_commit(&committed); }
    int cursor=0, start=0, highlighted=0; bool last=true;
    std::vector<std::string> candidates, comments;
    RIME_STRUCT(RimeContext, context);
    if (api()->get_context(session,&context)) {
        if (context.composition.preedit) preedit=context.composition.preedit;
        size_t bytes=std::min(preedit.size(),static_cast<size_t>(std::max(0,context.composition.cursor_pos)));
        cursor=static_cast<int>(utf16(preedit.substr(0,bytes)).size());
        start=context.menu.page_no*context.menu.page_size;
        highlighted=start+context.menu.highlighted_candidate_index;
        last=context.menu.is_last_page;
        for (int i=0; i<context.menu.num_candidates; ++i) {
            candidates.emplace_back(context.menu.candidates[i].text ? context.menu.candidates[i].text : "");
            comments.emplace_back(context.menu.candidates[i].comment ? context.menu.candidates[i].comment : "");
        }
        api()->free_context(&context);
    }
    jclass strings=env->FindClass("java/lang/String");
    auto texts=env->NewObjectArray(candidates.size(),strings,nullptr);
    auto notes=env->NewObjectArray(comments.size(),strings,nullptr);
    for (size_t i=0;i<candidates.size();++i) {
        auto t=string(env,candidates[i]); auto n=string(env,comments[i]);
        env->SetObjectArrayElement(texts,i,t); env->SetObjectArrayElement(notes,i,n);
        env->DeleteLocalRef(t); env->DeleteLocalRef(n);
    }
    auto cls=env->FindClass("org/scholay/rimes/core/RimeEngine$Snapshot");
    auto constructor=env->GetMethodID(cls,"<init>","(ZLjava/lang/String;Ljava/lang/String;ILjava/lang/String;[Ljava/lang/String;[Ljava/lang/String;IIZ)V");
    return env->NewObject(cls,constructor,handled,string(env,raw),string(env,preedit),cursor,string(env,commit),texts,notes,start,highlighted,static_cast<jboolean>(last));
}
