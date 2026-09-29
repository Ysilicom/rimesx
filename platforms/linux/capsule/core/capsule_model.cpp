#include "capsule_model.hpp"

#include <algorithm>
#include <cctype>

namespace rimes::capsule {
namespace {

bool MatchesQuery(const Card& card, std::string_view query) {
    if (query.empty()) {
        return true;
    }
    std::string haystack = card.title + "\n" + card.payload;
    std::string needle(query);
    for (char& ch : haystack) {
        ch = static_cast<char>(std::tolower(static_cast<unsigned char>(ch)));
    }
    for (char& ch : needle) {
        ch = static_cast<char>(std::tolower(static_cast<unsigned char>(ch)));
    }
    std::size_t start = 0;
    while (start < needle.size()) {
        while (start < needle.size() &&
               std::isspace(static_cast<unsigned char>(needle[start])) != 0) {
            ++start;
        }
        if (start >= needle.size()) {
            break;
        }
        std::size_t end = start;
        while (end < needle.size() &&
               std::isspace(static_cast<unsigned char>(needle[end])) == 0) {
            ++end;
        }
        if (haystack.find(needle.substr(start, end - start)) == std::string::npos) {
            return false;
        }
        start = end;
    }
    return true;
}

}  // namespace

void CapsuleModel::set_visible(bool value) {
    if (visible_ == value) {
        return;
    }
    visible_ = value;
    Touch();
}

void CapsuleModel::set_armed(bool value) {
    if (armed_ == value) {
        return;
    }
    armed_ = value;
    if (!armed_) {
        token_.clear();
    }
    Touch();
}

void CapsuleModel::set_token(std::string token) {
    if (token_ == token) {
        return;
    }
    token_ = std::move(token);
    Touch();
}

bool CapsuleModel::arms(std::string_view token) const {
    return armed_ && !token_.empty() && token_ == token;
}

void CapsuleModel::set_password_field(bool value) {
    if (password_field_ == value) {
        return;
    }
    password_field_ = value;
    if (password_field_) {
        Disarm("password");
        set_visible(false);
    }
    Touch();
}

void CapsuleModel::set_tab(Tab tab) {
    if (tab_ == tab) {
        return;
    }
    tab_ = tab;
    selected_ = 0;
    ApplyFilter();
    Touch();
}

void CapsuleModel::set_query(std::string query) {
    if (query_ == query) {
        return;
    }
    query_ = std::move(query);
    selected_ = 0;
    ApplyFilter();
    Touch();
}

void CapsuleModel::append_query(char ch) {
    if (ch < 0x20 || ch > 0x7e) {
        return;
    }
    query_.push_back(ch);
    selected_ = 0;
    ApplyFilter();
    Touch();
}

void CapsuleModel::backspace_query() {
    if (query_.empty()) {
        return;
    }
    query_.pop_back();
    selected_ = 0;
    ApplyFilter();
    Touch();
}

void CapsuleModel::LoadCards(const std::vector<Record>& records) {
    all_.clear();
    all_.reserve(records.size());
    for (const auto& record : records) {
        Card card;
        card.id = record.id;
        card.kind = record.kind;
        card.title = record.title;
        card.payload = record.content;
        card.preview = PreviewOf(record.content);
        all_.push_back(std::move(card));
    }
    ApplyFilter();
    Touch();
}

const Card* CapsuleModel::Selected() const {
    if (selected_ < 0 || selected_ >= static_cast<int>(cards_.size())) {
        return nullptr;
    }
    return &cards_[static_cast<std::size_t>(selected_)];
}

void CapsuleModel::Select(int index) {
    if (cards_.empty()) {
        selected_ = 0;
        Touch();
        return;
    }
    selected_ = std::clamp(index, 0, static_cast<int>(cards_.size()) - 1);
    Touch();
}

void CapsuleModel::Move(int delta) {
    Select(selected_ + delta);
}

void CapsuleModel::set_last_copied(std::string text) {
    last_copied_ = std::move(text);
    Touch();
}

void CapsuleModel::set_hint(std::string hint) {
    hint_ = std::move(hint);
    Touch();
}

void CapsuleModel::Touch() {
    ++generation_;
    RefreshHint();
}

void CapsuleModel::Disarm(std::string_view /*reason*/) {
    if (!armed_ && token_.empty()) {
        return;
    }
    armed_ = false;
    token_.clear();
    Touch();
}

void CapsuleModel::Hide() {
    visible_ = false;
    armed_ = false;
    token_.clear();
    query_.clear();
    ApplyFilter();
    Touch();
}

void CapsuleModel::ApplyFilter() {
    cards_.clear();
    if (!TabIsPorted(tab_)) {
        selected_ = 0;
        return;
    }
    for (const auto& card : all_) {
        if (card.kind == KindForTab(tab_) && MatchesQuery(card, query_)) {
            cards_.push_back(card);
        }
    }
    if (selected_ >= static_cast<int>(cards_.size())) {
        selected_ = cards_.empty() ? 0 : static_cast<int>(cards_.size()) - 1;
    }
}

void CapsuleModel::RefreshHint() {
    if (password_field_) {
        hint_ = "Protected — Capsule will not insert here";
        return;
    }
    if (!TabIsPorted(tab_)) {
        hint_ = "This tab is not ported on Linux yet";
        return;
    }
    if (!armed_) {
        hint_ = "Disarmed — Ctrl+Shift+V on this field re-arms; press again to close";
        return;
    }
    if (cards_.empty()) {
        hint_ = query_.empty() ? "No notes" : "No matching notes";
        return;
    }
    hint_ = "Return inserts the selected note · Esc closes";
}

}  // namespace rimes::capsule
