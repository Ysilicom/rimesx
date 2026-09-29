#pragma once

#include <cstdint>
#include <string>
#include <string_view>
#include <vector>

#include "capsule_kinds.hpp"
#include "capsule_store.hpp"

namespace rimes::capsule {

struct Card {
    std::string id;
    Kind kind = Kind::Note;
    std::string title;
    std::string preview;
    std::string payload;
};

class CapsuleModel {
public:
    void set_visible(bool value);
    bool visible() const { return visible_; }

    void set_armed(bool value);
    bool armed() const { return armed_; }

    void set_token(std::string token);
    const std::string& token() const { return token_; }
    bool arms(std::string_view token) const;

    void set_password_field(bool value);
    bool password_field() const { return password_field_; }

    void set_tab(Tab tab);
    Tab tab() const { return tab_; }

    void set_query(std::string query);
    void append_query(char ch);
    void backspace_query();
    const std::string& query() const { return query_; }

    void LoadCards(const std::vector<Record>& records);
    const std::vector<Card>& cards() const { return cards_; }
    const Card* Selected() const;
    void Select(int index);
    void Move(int delta);
    int selected() const { return selected_; }

    void set_last_copied(std::string text);
    const std::string& last_copied() const { return last_copied_; }

    void set_hint(std::string hint);
    const std::string& hint() const { return hint_; }

    std::uint64_t generation() const { return generation_; }
    void Touch();

    void Disarm(std::string_view reason);
    void Hide();

private:
    void ApplyFilter();
    void RefreshHint();

    bool visible_ = false;
    bool armed_ = false;
    bool password_field_ = false;
    std::string token_;
    Tab tab_ = Tab::Note;
    std::string query_;
    std::vector<Card> all_;
    std::vector<Card> cards_;
    int selected_ = 0;
    std::string last_copied_;
    std::string hint_;
    std::uint64_t generation_ = 0;
};

}  // namespace rimes::capsule
