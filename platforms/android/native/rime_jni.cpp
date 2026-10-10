#include <jni.h>
#include <rime_api.h>
#include <rime/candidate.h>
#include <rime/context.h>
#include <rime/service.h>
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
static jclass g_string_class = nullptr;
static jclass g_snapshot_class = nullptr;
static jmethodID g_snapshot_init = nullptr;

static void ensure_classes(JNIEnv* env) {
    if (!g_string_class) {
        jclass local_string = env->FindClass("java/lang/String");
        g_string_class = reinterpret_cast<jclass>(env->NewGlobalRef(local_string));
        env->DeleteLocalRef(local_string);
    }
    if (!g_snapshot_class) {
        jclass local_snapshot = env->FindClass("org/scholay/rimes/core/RimeEngine$Snapshot");
        g_snapshot_class = reinterpret_cast<jclass>(env->NewGlobalRef(local_snapshot));
        g_snapshot_init = env->GetMethodID(g_snapshot_class, "<init>", "(ZLjava/lang/String;Ljava/lang/String;ILjava/lang/String;[Ljava/lang/String;[Ljava/lang/String;IIZ)V");
        env->DeleteLocalRef(local_snapshot);
    }
}

#define JNI(name) Java_org_scholay_rimes_android_NativeRimeEngine_##name
extern "C" JNIEXPORT void JNICALL JNI(initializeNative)(JNIEnv* env, jclass, jstring shared, jstring user) {
    if (initialized) return;
    ensure_classes(env);
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
    // librime 1.17 leaves the iterator at index -1. candidate_list_next is what
    // yields item 0. Reading before next inserts a blank slot, so the word drawn
    // at position i is select_candidate(i + 1).
    RimeCandidateListIterator iter = {};
    if (api()->candidate_list_begin && api()->candidate_list_begin(session, &iter)) {
        std::vector<std::string> listed, listedComments;
        while (listed.size() < 60 && api()->candidate_list_next(&iter)) {
            listed.emplace_back(iter.candidate.text ? iter.candidate.text : "");
            listedComments.emplace_back(iter.candidate.comment ? iter.candidate.comment : "");
        }
        api()->candidate_list_end(&iter);
        if (!listed.empty()) {
            candidates.swap(listed);
            comments.swap(listedComments);
            // This walk is the whole menu from absolute index 0, not the current
            // page. pageStart must stay 0 or a tap commits pageStart + index.
            start = 0;
            last = candidates.size() < 60;
        }
    }
    ensure_classes(env);
    auto texts=env->NewObjectArray(candidates.size(),g_string_class,nullptr);
    auto notes=env->NewObjectArray(comments.size(),g_string_class,nullptr);
    for (size_t i=0;i<candidates.size();++i) {
        auto t=string(env,candidates[i]); auto n=string(env,comments[i]);
        env->SetObjectArrayElement(texts,i,t); env->SetObjectArrayElement(notes,i,n);
        env->DeleteLocalRef(t); env->DeleteLocalRef(n);
    }
    return env->NewObject(g_snapshot_class,g_snapshot_init,handled,string(env,raw),string(env,preedit),cursor,string(env,commit),texts,notes,start,highlighted,static_cast<jboolean>(last));
}
extern "C" JNIEXPORT jdoubleArray JNICALL JNI(qualitiesNative)(JNIEnv* env, jclass, jlong session_id) {
    jdoubleArray empty = env->NewDoubleArray(0);
    if (session_id == 0) return empty;
    auto held = rime::Service::instance().GetSession(static_cast<rime::SessionId>(session_id));
    if (!held || !held->context()) return empty;
    auto& composition = held->context()->composition();
    const rime::Segment* segment = nullptr;
    for (auto it = composition.rbegin(); it != composition.rend(); ++it) {
        if (it->GetCandidateAt(0)) { segment = &(*it); break; }
    }
    if (!segment) return empty;
    std::vector<jdouble> values;
    for (size_t i = 0; i < 60; ++i) {
        auto candidate = segment->GetCandidateAt(i);
        if (!candidate) break;
        values.push_back(candidate->quality());
    }
    if (values.empty()) return empty;
    jdoubleArray array = env->NewDoubleArray(static_cast<jsize>(values.size()));
    env->SetDoubleArrayRegion(array, 0, static_cast<jsize>(values.size()), values.data());
    return array;
}
