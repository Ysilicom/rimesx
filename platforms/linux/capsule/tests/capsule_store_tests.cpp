#include "capsule_hash.hpp"
#include "capsule_store.hpp"

#include <filesystem>
#include <fstream>
#include <iostream>
#include <string>
#include <unistd.h>
#include <vector>
#include <sys/stat.h>

namespace {

int g_failures = 0;

void Expect(bool cond, const char* message) {
    if (!cond) {
        std::cerr << "FAIL: " << message << '\n';
        ++g_failures;
    }
}

std::filesystem::path TempRoot() {
    return std::filesystem::temp_directory_path() /
           ("rimes-capsule-store-" + std::to_string(getpid()));
}

}  // namespace

int main() {
    const auto root = TempRoot();
    std::error_code error;
    std::filesystem::remove_all(root, error);
    rimes::capsule::ContentStore store(root);

    std::string store_error;
    Expect(store.SeedIfNeeded(&store_error) == rimes::capsule::StoreStatus::Ok, "seed");
    Expect(std::filesystem::exists(root / "content-seed-v1"), "seed marker");

    std::vector<rimes::capsule::Record> notes;
    Expect(store.List(rimes::capsule::Kind::Note, &notes, &store_error) ==
               rimes::capsule::StoreStatus::Ok,
           "list");
    Expect(notes.size() == 1, "one seed note");
    Expect(notes[0].id == rimes::capsule::kDefaultEntryId, "seed id");
    Expect(notes[0].title == rimes::capsule::kDefaultEntryTitle, "seed title");
    Expect(notes[0].content == rimes::capsule::kDefaultEntryContent, "seed body");

    struct stat st {};
    Expect(stat(root.c_str(), &st) == 0 && (st.st_mode & 0777) == 0700, "root 0700");
    Expect(stat(notes[0].path.c_str(), &st) == 0 && (st.st_mode & 0777) == 0600, "entry 0600");

    const auto first_revision = notes[0].revision;
    Expect(!first_revision.empty(), "revision");
    Expect(store.SeedIfNeeded(&store_error) == rimes::capsule::StoreStatus::Ok, "seed idempotent");
    notes.clear();
    store.List(rimes::capsule::Kind::Note, &notes, &store_error);
    Expect(notes.size() == 1, "seed does not duplicate");

    rimes::capsule::Record extra;
    extra.kind = rimes::capsule::Kind::Note;
    extra.title = "Second";
    extra.content = "hello capsule";
    rimes::capsule::Record written;
    Expect(store.Put(extra, "", &written, &store_error) == rimes::capsule::StoreStatus::Ok, "put");
    Expect(rimes::capsule::LooksLikeUuid(written.id), "generated uuid");

    rimes::capsule::Record stale = written;
    stale.content = "changed";
    Expect(store.Put(stale, "deadbeef", &written, &store_error) ==
               rimes::capsule::StoreStatus::RevisionConflict,
           "stale revision");

    const auto markdown = rimes::capsule::RenderMarkdown(notes[0]);
    Expect(markdown.find("capsule: note") != std::string::npos, "front matter kind");
    rimes::capsule::Record parsed;
    Expect(rimes::capsule::ParseMarkdown(markdown, &parsed, &store_error), "parse seed markdown");
    Expect(parsed.title == rimes::capsule::kDefaultEntryTitle, "roundtrip title");

    Expect(store.Remove(written.id, written.revision, &store_error) ==
               rimes::capsule::StoreStatus::Ok,
           "remove");
    notes.clear();
    store.List(rimes::capsule::Kind::Note, &notes, &store_error);
    Expect(notes.size() == 1, "remove left the seed");

    std::filesystem::remove_all(root, error);
    if (g_failures != 0) {
        std::cerr << g_failures << " capsule store failures\n";
        return 1;
    }
    std::cout << "ok: capsule store\n";
    return 0;
}
