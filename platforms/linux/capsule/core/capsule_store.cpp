#include "capsule_store.hpp"

#include <algorithm>
#include <cctype>
#include <cerrno>
#include <cstdlib>
#include <cstring>
#include <fcntl.h>
#include <fstream>
#include <sstream>
#include <sys/file.h>
#include <sys/stat.h>
#include <unistd.h>

#include "buffer_protocol.hpp"
#include "capsule_hash.hpp"

namespace rimes::capsule {
namespace {

bool IsUnsafePath(const std::filesystem::path& path) {
    std::error_code error;
    if (std::filesystem::is_symlink(path, error)) {
        return true;
    }
    return false;
}

StoreStatus IoFail(std::string* error, const std::string& message) {
    if (error != nullptr) {
        *error = message;
    }
    return StoreStatus::Io;
}

StoreStatus InvalidFail(std::string* error, const std::string& message) {
    if (error != nullptr) {
        *error = message;
    }
    return StoreStatus::Invalid;
}

bool IsRetiredRaw(std::string_view raw) {
    return raw == "prompt" || raw == "memory" || raw == "url";
}

std::string Trim(std::string_view text) {
    std::size_t begin = 0;
    while (begin < text.size() &&
           std::isspace(static_cast<unsigned char>(text[begin])) != 0) {
        ++begin;
    }
    std::size_t end = text.size();
    while (end > begin && std::isspace(static_cast<unsigned char>(text[end - 1])) != 0) {
        --end;
    }
    return std::string(text.substr(begin, end - begin));
}

}  // namespace

std::string JsonQuote(std::string_view text) {
    return rimes::buffer::JsonEscape(text);
}

bool JsonUnquote(std::string_view text, std::string* out) {
    const auto trimmed = Trim(text);
    if (trimmed.size() < 2 || trimmed.front() != '"' || trimmed.back() != '"') {
        return false;
    }
    return rimes::buffer::JsonUnescape(trimmed.substr(1, trimmed.size() - 2), out);
}

std::string RenderMarkdown(const Record& record) {
    std::ostringstream out;
    out << "---\n"
        << "capsule: " << KindRaw(record.kind) << "\n"
        << "version: 1\n"
        << "id: " << JsonQuote(record.id) << "\n"
        << "title: " << JsonQuote(record.title) << "\n"
        << "updated_at: " << JsonQuote(record.updated_at) << "\n"
        << "---\n\n"
        << record.content;
    return out.str();
}

bool ParseMarkdown(std::string_view text, Record* out, std::string* error) {
    if (out == nullptr) {
        return false;
    }
    const auto start = text.find("---");
    if (start != 0) {
        if (error != nullptr) {
            *error = "missing front matter";
        }
        return false;
    }
    const auto end = text.find("\n---", 3);
    if (end == std::string_view::npos) {
        if (error != nullptr) {
            *error = "unterminated front matter";
        }
        return false;
    }
    const auto header = text.substr(3, end - 3);
    std::string kind_raw;
    std::string version;
    std::string id;
    std::string title;
    std::string updated;
    std::istringstream lines{std::string(header)};
    std::string line;
    while (std::getline(lines, line)) {
        if (!line.empty() && line.back() == '\r') {
            line.pop_back();
        }
        const auto colon = line.find(':');
        if (colon == std::string::npos) {
            continue;
        }
        const auto key = Trim(line.substr(0, colon));
        const auto value = Trim(line.substr(colon + 1));
        if (key == "capsule") {
            kind_raw = value;
        } else if (key == "version") {
            version = value;
        } else if (key == "id") {
            if (!JsonUnquote(value, &id)) {
                id = value;
            }
        } else if (key == "title") {
            if (!JsonUnquote(value, &title)) {
                title = value;
            }
        } else if (key == "updated_at") {
            if (!JsonUnquote(value, &updated)) {
                updated = value;
            }
        }
    }
    if (IsRetiredRaw(kind_raw)) {
        if (error != nullptr) {
            *error = "retired";
        }
        return false;
    }
    const auto kind = KindFromRaw(kind_raw);
    if (version != "1" || kind == Kind::Retired || kind == Kind::Password ||
        !LooksLikeUuid(id) || title.empty() ||
        static_cast<int>(title.size()) > kMaxTitleChars) {
        if (error != nullptr) {
            *error = "malformed front matter";
        }
        return false;
    }
    auto body = text.substr(end + 4);
    // After "\n---", consume the rest of that line, then one blank line
    // the way CapsuleContentStore skips an empty line after the closer.
    if (body.size() >= 2 && body[0] == '\r' && body[1] == '\n') {
        body.remove_prefix(2);
    } else if (!body.empty() && body.front() == '\n') {
        body.remove_prefix(1);
    }
    if (body.size() >= 2 && body[0] == '\r' && body[1] == '\n') {
        body.remove_prefix(2);
    } else if (!body.empty() && body.front() == '\n') {
        body.remove_prefix(1);
    }
    if (body.size() > static_cast<std::size_t>(kMaxContentChars) ||
        body.find('\0') != std::string_view::npos || body.empty()) {
        if (error != nullptr) {
            *error = "invalid body";
        }
        return false;
    }
    out->id = id;
    out->kind = kind;
    out->title = title;
    out->content = std::string(body);
    out->updated_at = updated;
    return true;
}

std::filesystem::path ContentStore::DefaultRoot() {
    if (const char* override_path = std::getenv("RIMES_CAPSULE_ROOT")) {
        if (override_path[0] == '/') {
            return override_path;
        }
    }
    if (const char* user = std::getenv("RIMES_USER_DIR")) {
        if (user[0] == '/') {
            return std::filesystem::path(user) / "capsule";
        }
    }
    if (const char* xdg = std::getenv("XDG_DATA_HOME")) {
        if (xdg[0] == '/') {
            return std::filesystem::path(xdg) / "rimes" / "capsule";
        }
    }
    if (const char* home = std::getenv("HOME")) {
        if (home[0] == '/') {
            return std::filesystem::path(home) / ".local" / "share" / "rimes" / "capsule";
        }
    }
    return {};
}

ContentStore::ContentStore(std::filesystem::path root)
    : root_(std::move(root)),
      entries_(root_ / "entries"),
      seed_(root_ / "content-seed-v1"),
      lock_(root_ / ".lock") {}

StoreStatus ContentStore::WithLock(const std::function<StoreStatus()>& body,
                                   std::string* error) const {
    const auto prepared = PrepareDirs(error);
    if (prepared != StoreStatus::Ok) {
        return prepared;
    }
    const int fd = open(lock_.c_str(), O_CREAT | O_RDWR | O_CLOEXEC, 0600);
    if (fd < 0) {
        return IoFail(error, "cannot open store lock");
    }
    if (fchmod(fd, 0600) != 0) {
        close(fd);
        return IoFail(error, "cannot chmod store lock");
    }
    if (flock(fd, LOCK_EX) != 0) {
        close(fd);
        return IoFail(error, "cannot lock capsule store");
    }
    const auto status = body();
    flock(fd, LOCK_UN);
    close(fd);
    return status;
}

StoreStatus ContentStore::PrepareDirs(std::string* error) const {
    if (root_.empty() || !root_.is_absolute()) {
        return InvalidFail(error, "capsule root must be absolute");
    }
    std::error_code fs_error;
    std::filesystem::create_directories(root_, fs_error);
    if (fs_error) {
        return IoFail(error, fs_error.message());
    }
    std::filesystem::create_directories(entries_, fs_error);
    if (fs_error) {
        return IoFail(error, fs_error.message());
    }
    if (IsUnsafePath(root_) || IsUnsafePath(entries_)) {
        return IoFail(error, "capsule path is a symlink");
    }
    chmod(root_.c_str(), 0700);
    chmod(entries_.c_str(), 0700);
    return StoreStatus::Ok;
}

StoreStatus ContentStore::SeedIfNeeded(std::string* error) {
    return WithLock(
        [&]() {
            if (std::filesystem::exists(seed_)) {
                return StoreStatus::Ok;
            }
            std::vector<Record> records;
            const auto listed = ReadAll(&records, error);
            if (listed != StoreStatus::Ok) {
                return listed;
            }
            bool present = false;
            for (const auto& record : records) {
                if (record.id == kDefaultEntryId || record.title == kDefaultEntryTitle) {
                    present = true;
                    break;
                }
            }
            if (!present) {
                Record seed;
                seed.id = kDefaultEntryId;
                seed.kind = Kind::Note;
                seed.title = kDefaultEntryTitle;
                seed.content = kDefaultEntryContent;
                seed.updated_at = Iso8601UtcNow();
                const auto written = WriteRecord(seed, error);
                if (written != StoreStatus::Ok) {
                    return written;
                }
            }
            std::ofstream marker(seed_, std::ios::binary | std::ios::trunc);
            if (!marker) {
                return IoFail(error, "cannot write seed marker");
            }
            marker << "seeded\n";
            marker.close();
            chmod(seed_.c_str(), 0600);
            return StoreStatus::Ok;
        },
        error);
}

StoreStatus ContentStore::List(Kind kind, std::vector<Record>* out, std::string* error) const {
    if (out == nullptr) {
        return InvalidFail(error, "list output is null");
    }
    return WithLock(
        [&]() {
            std::vector<Record> records;
            const auto listed = ReadAll(&records, error);
            if (listed != StoreStatus::Ok) {
                return listed;
            }
            out->clear();
            for (auto& record : records) {
                if (record.kind == kind) {
                    out->push_back(std::move(record));
                }
            }
            return StoreStatus::Ok;
        },
        error);
}

StoreStatus ContentStore::Get(std::string_view id, Record* out, std::string* error) const {
    if (out == nullptr || !LooksLikeUuid(id)) {
        return InvalidFail(error, "invalid id");
    }
    return WithLock(
        [&]() {
            const auto path = EntryPath(id);
            if (!std::filesystem::exists(path)) {
                if (error != nullptr) {
                    *error = "not found";
                }
                return StoreStatus::NotFound;
            }
            return ParseFile(path, out, error);
        },
        error);
}

StoreStatus ContentStore::Put(const Record& request, std::string expected_revision, Record* out,
                              std::string* error) {
    if (request.kind != Kind::Note) {
        return InvalidFail(error, "linux store only writes notes");
    }
    if (request.title.empty() || static_cast<int>(request.title.size()) > kMaxTitleChars ||
        request.content.empty() ||
        static_cast<int>(request.content.size()) > kMaxContentChars ||
        request.title.find('\0') != std::string::npos ||
        request.content.find('\0') != std::string::npos) {
        return InvalidFail(error, "invalid title or content");
    }
    return WithLock(
        [&]() {
            Record record = request;
            if (record.id.empty()) {
                record.id = NewUuidV4();
            } else if (!LooksLikeUuid(record.id)) {
                return InvalidFail(error, "invalid id");
            }
            record.kind = Kind::Note;
            record.title = Trim(record.title);
            record.updated_at = Iso8601UtcNow();
            const auto path = EntryPath(record.id);
            if (std::filesystem::exists(path)) {
                Record existing;
                const auto parsed = ParseFile(path, &existing, error);
                if (parsed != StoreStatus::Ok) {
                    return parsed;
                }
                if (!expected_revision.empty() && expected_revision != existing.revision) {
                    if (error != nullptr) {
                        *error = "revision conflict";
                    }
                    return StoreStatus::RevisionConflict;
                }
            } else if (!expected_revision.empty()) {
                if (error != nullptr) {
                    *error = "revision conflict";
                }
                return StoreStatus::RevisionConflict;
            }
            const auto written = WriteRecord(record, error);
            if (written != StoreStatus::Ok) {
                return written;
            }
            if (out != nullptr) {
                return ParseFile(path, out, error);
            }
            return StoreStatus::Ok;
        },
        error);
}

StoreStatus ContentStore::Remove(std::string_view id, std::string_view expected_revision,
                                 std::string* error) {
    if (!LooksLikeUuid(id)) {
        return InvalidFail(error, "invalid id");
    }
    return WithLock(
        [&]() {
            const auto path = EntryPath(id);
            if (!std::filesystem::exists(path)) {
                return StoreStatus::NotFound;
            }
            Record existing;
            const auto parsed = ParseFile(path, &existing, error);
            if (parsed != StoreStatus::Ok) {
                return parsed;
            }
            if (!expected_revision.empty() && expected_revision != existing.revision) {
                if (error != nullptr) {
                    *error = "revision conflict";
                }
                return StoreStatus::RevisionConflict;
            }
            std::error_code fs_error;
            std::filesystem::remove(path, fs_error);
            if (fs_error) {
                return IoFail(error, fs_error.message());
            }
            return StoreStatus::Ok;
        },
        error);
}

StoreStatus ContentStore::ReadAll(std::vector<Record>* out, std::string* error) const {
    out->clear();
    std::error_code fs_error;
    if (!std::filesystem::exists(entries_, fs_error)) {
        return StoreStatus::Ok;
    }
    for (const auto& entry : std::filesystem::directory_iterator(entries_, fs_error)) {
        if (fs_error) {
            return IoFail(error, fs_error.message());
        }
        if (!entry.is_regular_file() || entry.path().extension() != ".md") {
            continue;
        }
        if (IsUnsafePath(entry.path())) {
            continue;
        }
        Record record;
        std::string parse_error;
        if (ParseFile(entry.path(), &record, &parse_error) != StoreStatus::Ok) {
            continue;
        }
        out->push_back(std::move(record));
    }
    std::sort(out->begin(), out->end(), [](const Record& left, const Record& right) {
        if (left.updated_at != right.updated_at) {
            return left.updated_at > right.updated_at;
        }
        return left.id < right.id;
    });
    return StoreStatus::Ok;
}

StoreStatus ContentStore::ParseFile(const std::filesystem::path& path, Record* out,
                                    std::string* error) const {
    if (IsUnsafePath(path)) {
        return IoFail(error, "entry is a symlink");
    }
    std::ifstream in(path, std::ios::binary);
    if (!in) {
        return IoFail(error, "cannot read entry");
    }
    std::ostringstream raw;
    raw << in.rdbuf();
    const auto bytes = raw.str();
    if (bytes.size() > static_cast<std::size_t>(kMaxDocumentBytes)) {
        return InvalidFail(error, "document too large");
    }
    if (!ParseMarkdown(bytes, out, error)) {
        return StoreStatus::Invalid;
    }
    out->path = path;
    out->revision = Sha256Hex(bytes);
    return StoreStatus::Ok;
}

StoreStatus ContentStore::WriteRecord(const Record& record, std::string* error) const {
    const auto path = EntryPath(record.id);
    const auto markdown = RenderMarkdown(record);
    const auto tmp = path.string() + ".tmp";
    std::ofstream out(tmp, std::ios::binary | std::ios::trunc);
    if (!out) {
        return IoFail(error, "cannot write temp entry");
    }
    out << markdown;
    out.close();
    if (!out) {
        unlink(tmp.c_str());
        return IoFail(error, "temp entry write failed");
    }
    chmod(tmp.c_str(), 0600);
    if (rename(tmp.c_str(), path.c_str()) != 0) {
        unlink(tmp.c_str());
        return IoFail(error, std::strerror(errno));
    }
    chmod(path.c_str(), 0600);
    return StoreStatus::Ok;
}

std::filesystem::path ContentStore::EntryPath(std::string_view id) const {
    std::string lower(id);
    for (char& ch : lower) {
        ch = static_cast<char>(std::tolower(static_cast<unsigned char>(ch)));
    }
    return entries_ / (lower + ".md");
}

}  // namespace rimes::capsule
