#include "capsule_clipboard.hpp"
#include "capsule_model.hpp"
#include "capsule_protocol.hpp"

#include <iostream>

namespace {

int g_failures = 0;

void Expect(bool cond, const char* message) {
    if (!cond) {
        std::cerr << "FAIL: " << message << '\n';
        ++g_failures;
    }
}

}  // namespace

int main() {
    using rimes::capsule::ShouldWriteClipboard;

    Expect(!ShouldWriteClipboard(false, 0, 1, "RIMES"),
           "first snapshot after connect/respawn must not copy");
    Expect(!ShouldWriteClipboard(true, 1, 1, "RIMES"), "same seq must not recopy");
    Expect(!ShouldWriteClipboard(true, 1, 1, "USERCLIP"), "search snapshot must not recopy");
    Expect(ShouldWriteClipboard(true, 0, 1, "RIMES"), "first Ctrl+C writes");
    Expect(ShouldWriteClipboard(true, 1, 2, "RIMES"), "second Ctrl+C on the same card writes");
    Expect(!ShouldWriteClipboard(true, 1, 2, ""), "seq bump with empty last_copied does not write");

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

    bool seen = false;
    std::uint64_t handled = 0;
    auto apply = [&](std::uint64_t seq, std::string_view text) {
        const bool write = ShouldWriteClipboard(seen, handled, seq, text);
        seen = true;
        handled = seq;
        return write;
    };
    Expect(!apply(2, "RIMES"), "respawn first snapshot with stale seq does not write");
    Expect(!apply(2, "RIMES"), "later identical snapshot does not write");
    Expect(apply(3, "RIMES"), "Ctrl+C after respawn writes once");

    if (g_failures != 0) {
        std::cerr << g_failures << " capsule clipboard failures\n";
        return 1;
    }
    std::cout << "ok: capsule clipboard\n";
    return 0;
}
