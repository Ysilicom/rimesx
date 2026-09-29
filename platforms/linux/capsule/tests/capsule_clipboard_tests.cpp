#include "capsule_clipboard.hpp"
#include "capsule_model.hpp"
#include "capsule_protocol.hpp"

#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <string>
#include <unistd.h>

namespace {

int g_failures = 0;

void Expect(bool cond, const char* message) {
    if (!cond) {
        std::cerr << "FAIL: " << message << '\n';
        ++g_failures;
    }
}

std::string ReadFile(const std::filesystem::path& path) {
    std::ifstream in(path);
    return std::string((std::istreambuf_iterator<char>(in)), std::istreambuf_iterator<char>());
}

int CountNonEmptyLines(const std::filesystem::path& path) {
    std::ifstream in(path);
    if (!in) {
        return 0;
    }
    int lines = 0;
    std::string line;
    while (std::getline(in, line)) {
        if (!line.empty()) {
            ++lines;
        }
    }
    return lines;
}

class PathGuard {
public:
    PathGuard() {
        const char* current = std::getenv("PATH");
        saved_ = current != nullptr ? current : "";
        had_ = current != nullptr;
    }
    ~PathGuard() {
        if (had_) {
            setenv("PATH", saved_.c_str(), 1);
        } else {
            unsetenv("PATH");
        }
    }
    void Prepend(const std::filesystem::path& dir) {
        const std::string next = dir.string() + (saved_.empty() ? "" : (":" + saved_));
        setenv("PATH", next.c_str(), 1);
    }
    void SetOnly(const std::filesystem::path& dir) { setenv("PATH", dir.string().c_str(), 1); }

private:
    std::string saved_;
    bool had_ = false;
};

std::filesystem::path MakeFakeWlCopy(const std::filesystem::path& root) {
    const auto bin = root / "bin";
    std::filesystem::create_directories(bin);
    const auto log = root / "wl-copy.log";
    const auto script = bin / "wl-copy";
    std::ofstream out(script);
    out << "#!/bin/sh\n"
        << "cat >> '" << log.string() << "'\n"
        << "printf '\\n' >> '" << log.string() << "'\n";
    out.close();
    std::filesystem::permissions(script, std::filesystem::perms::owner_all);
    return bin;
}

}  // namespace

int main() {
    using rimes::capsule::ApplyCopiedNote;
    using rimes::capsule::ClipboardApplyState;
    using rimes::capsule::ClipboardWritePath;
    using rimes::capsule::DecideClipboardWrite;
    using rimes::capsule::FindOnPath;
    using rimes::capsule::ShouldWriteClipboard;
    using rimes::capsule::SpawnWlCopy;
    using rimes::capsule::kWaylandCopyFallbackHint;

    Expect(!ShouldWriteClipboard(false, 0, 1, "RIMES"),
           "first snapshot after connect/respawn must not copy");
    Expect(!ShouldWriteClipboard(true, 1, 1, "RIMES"), "same seq must not recopy");
    Expect(!ShouldWriteClipboard(true, 1, 1, "USERCLIP"), "search snapshot must not recopy");
    Expect(ShouldWriteClipboard(true, 0, 1, "RIMES"), "first Ctrl+C writes");
    Expect(ShouldWriteClipboard(true, 1, 2, "RIMES"), "second Ctrl+C on the same card writes");
    Expect(!ShouldWriteClipboard(true, 1, 2, ""), "seq bump with empty last_copied does not write");

    Expect(DecideClipboardWrite(true, false, false).path == ClipboardWritePath::Gtk,
           "X11 uses GTK even without wl-copy");
    Expect(DecideClipboardWrite(true, true, true).path == ClipboardWritePath::WlCopy,
           "Wayland prefers wl-copy");
    const auto fallback = DecideClipboardWrite(true, true, false);
    Expect(fallback.path == ClipboardWritePath::GtkFallback, "Wayland without wl-copy falls back");
    Expect(fallback.status_override != nullptr &&
               std::string(fallback.status_override) == kWaylandCopyFallbackHint,
           "fallback status text must appear without wl-copy");
    Expect(DecideClipboardWrite(false, true, true).path == ClipboardWritePath::Skip,
           "skip when edge trigger says no");

    rimes::capsule::CapsuleModel model;
    rimes::capsule::Record note;
    note.id = rimes::capsule::kDefaultEntryId;
    note.kind = rimes::capsule::Kind::Note;
    note.title = rimes::capsule::kDefaultEntryTitle;
    note.content = rimes::capsule::kDefaultEntryContent;
    model.LoadCards({note});
    Expect(model.copy_seq() == 0, "seq starts at 0");
    model.set_last_copied("RIMES");
    Expect(model.copy_seq() == 1, "copy increments seq");
    Expect(model.last_copied() == "RIMES", "copied body");
    model.set_last_copied("RIMES");
    Expect(model.copy_seq() == 2, "second copy of the same body increments");
    model.append_query('z');
    Expect(model.copy_seq() == 2, "search must not bump copy_seq");
    model.Hide();
    Expect(model.last_copied().empty(), "Hide clears last_copied");
    Expect(model.copy_seq() == 2, "Hide must not bump copy_seq");

    const auto after_hide = rimes::capsule::EncodeSnapshot(rimes::capsule::MakeSnapshot(model));
    Expect(after_hide.find("\"copy_seq\":2") != std::string::npos, "encode copy_seq");
    Expect(after_hide.find("\"last_copied\":\"\"") != std::string::npos, "encode cleared copy");
    rimes::capsule::Snapshot decoded;
    Expect(rimes::capsule::DecodeSnapshot(after_hide, &decoded), "decode after hide");
    Expect(decoded.copy_seq == 2, "decoded seq");
    Expect(decoded.last_copied.empty(), "decoded last_copied empty");

    const auto root =
        std::filesystem::temp_directory_path() / ("rimes-wl-copy-" + std::to_string(getpid()));
    std::error_code error;
    std::filesystem::remove_all(root, error);
    const auto bin = MakeFakeWlCopy(root);
    const auto log = root / "wl-copy.log";
    {
        PathGuard path;
        path.Prepend(bin);
        Expect(FindOnPath("wl-copy").find("wl-copy") != std::string::npos, "PATH finds wl-copy");
        Expect(FindOnPath("wl-copy/../wl-copy").empty(), "names with slash are rejected");

        ClipboardApplyState state;
        auto d0 = ApplyCopiedNote(&state, 1, "RIMES", true, true);
        Expect(d0.path == ClipboardWritePath::Skip, "first snapshot after connect is record-only");
        Expect(CountNonEmptyLines(log) == 0, "connect snapshot must not spawn wl-copy");

        auto d_search = ApplyCopiedNote(&state, 1, "RIMES", true, true);
        Expect(d_search.path == ClipboardWritePath::Skip, "search must not copy");
        auto d_close = ApplyCopiedNote(&state, 1, "", true, true);
        Expect(d_close.path == ClipboardWritePath::Skip, "close must not copy");
        auto d_reopen = ApplyCopiedNote(&state, 1, "", true, true);
        Expect(d_reopen.path == ClipboardWritePath::Skip, "reopen must not copy");
        Expect(CountNonEmptyLines(log) == 0, "search/close/reopen must not spawn wl-copy");

        auto d_copy = ApplyCopiedNote(&state, 2, "RIMES", true, true);
        Expect(d_copy.path == ClipboardWritePath::WlCopy, "Ctrl+C uses wl-copy on Wayland");
        Expect(SpawnWlCopy("RIMES"), "Ctrl+C must spawn wl-copy");
        Expect(CountNonEmptyLines(log) == 1, "Ctrl+C invokes wl-copy exactly once");
        Expect(ReadFile(log) == "RIMES\n", "wl-copy stdin is the note body");

        auto d_search2 = ApplyCopiedNote(&state, 2, "RIMES", true, true);
        Expect(d_search2.path == ClipboardWritePath::Skip, "later search must not copy");
        Expect(CountNonEmptyLines(log) == 1, "search after copy must not spawn wl-copy again");

        auto d_again = ApplyCopiedNote(&state, 3, "RIMES", true, true);
        Expect(d_again.path == ClipboardWritePath::WlCopy, "second Ctrl+C on the same card copies");
        Expect(SpawnWlCopy("RIMES"), "second Ctrl+C must spawn wl-copy");
        Expect(CountNonEmptyLines(log) == 2, "two Ctrl+C presses spawn wl-copy twice");

        ClipboardApplyState respawn;
        auto d_respawn = ApplyCopiedNote(&respawn, 3, "RIMES", true, true);
        Expect(d_respawn.path == ClipboardWritePath::Skip,
               "respawn first snapshot with stale seq does not write");
        Expect(CountNonEmptyLines(log) == 2, "respawn must not invoke wl-copy");
    }

    {
        PathGuard path;
        path.SetOnly(root / "empty-path");
        std::filesystem::create_directories(root / "empty-path");
        Expect(FindOnPath("wl-copy").empty(), "missing wl-copy is detected");
        ClipboardApplyState state;
        ApplyCopiedNote(&state, 0, "", true, false);
        const auto missing = ApplyCopiedNote(&state, 1, "RIMES", true, false);
        Expect(missing.path == ClipboardWritePath::GtkFallback,
               "without wl-copy the UI falls back to GTK");
        Expect(missing.status_override != nullptr &&
                   std::string(missing.status_override).find("wl-clipboard") != std::string::npos,
               "without wl-copy the fallback status text appears");
        Expect(!SpawnWlCopy("RIMES"), "SpawnWlCopy fails when wl-copy is absent");
    }

    std::filesystem::remove_all(root, error);

    if (g_failures != 0) {
        std::cerr << g_failures << " capsule clipboard failures\n";
        return 1;
    }
    std::cout << "ok: capsule clipboard\n";
    return 0;
}
