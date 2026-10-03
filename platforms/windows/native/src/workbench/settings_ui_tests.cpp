#include "settings_ui.hpp"

#include <cstdlib>
#include <iostream>

namespace {
using namespace rimes::windows;
int failures = 0;
void Check(bool value, const char* message) {
  if (!value) { std::cerr << "FAIL: " << message << '\n'; ++failures; }
}

LPARAM Point(HWND window, float x, float y) {
  const float dpi = static_cast<float>(GetDpiForWindow(window));
  return MAKELPARAM(static_cast<WORD>(x * dpi / 96.0f + 0.5f),
                    static_cast<WORD>(y * dpi / 96.0f + 0.5f));
}
void Click(HWND window, const ui::DipRect& box) {
  const auto point = Point(window, (box.left + box.right) / 2,
                          (box.top + box.bottom) / 2);
  SendMessageW(window, WM_LBUTTONDOWN, MK_LBUTTON, point);
  SendMessageW(window, WM_LBUTTONUP, 0, point);
}
ui::SettingsLayout Layout(HWND window, const ui::SettingsDraft& draft) {
  RECT client{};
  GetClientRect(window, &client);
  const float scale = 96.0f / static_cast<float>(GetDpiForWindow(window));
  return ui::LayoutSettings(static_cast<float>(client.right) * scale,
                             static_cast<float>(client.bottom) * scale, draft);
}

void TestNestedHitTargets() {
  ui::SettingsDraft draft;
  draft.page = ui::SettingsPage::kAppearance;
  for (const float width : {860.0f, 980.0f, 1300.0f}) {
    for (int index = 0; index < 4; ++index) {
      draft.theme_detail = index;
      const auto layout = ui::LayoutSettings(width, 600, draft);
      const auto& detail = layout.theme_details[static_cast<std::size_t>(index)];
      Check(ui::HitTestSettings(layout, detail.left + 1, detail.top + 1) == 400 + index,
            "details own their hit region before the parent card");
      const auto& popup = layout.theme_popover;
      Check(popup.left > layout.content.left && popup.right < width &&
                popup.bottom < layout.save.top,
            "theme details remain inside content and clear Save");
      Check(ui::HitTestSettings(layout, popup.left + 3, popup.top + 3) == 600,
            "popover shields underlying card clicks");
    }
  }
  draft.subpage = 1;
  const auto hidden = ui::LayoutSettings(980, 680, draft);
  Check(hidden.theme_details.empty() && hidden.theme_popover.width() == 0,
        "size subpage has no stale theme hit targets");
}

void TestOwnedClicksAndTransientDetails() {
  int saves = 0;
  int previews = 0;
  ui::ThemeId preview = ui::ThemeId::kNight;
  workbench::SettingsUiCallbacks callbacks;
  callbacks.load = [] { return workbench::Settings{}; };
  callbacks.load_theme = [] { return ui::ThemeId::kNight; };
  callbacks.save = [&](workbench::Settings, const std::wstring&, bool, std::string*) {
    ++saves;
    return true;
  };
  callbacks.on_theme_preview = [&](ui::ThemeId value) { preview = value; ++previews; };
  // Own, disposable windows only. No Runtime, preferences, credentials, input
  // registration or installed Broker. CTest runs in the isolated build session.
  workbench::SettingsUiHost host(std::move(callbacks));
  host.Open(nullptr);
  HWND window = host.hwnd();
  Check(window != nullptr, "isolated settings host opens");
  if (!window) return;
  ui::SettingsDraft draft;
  auto layout = Layout(window, draft);
  SendMessageW(window, WM_LBUTTONUP, 0,
               Point(window, layout.save.left + 2, layout.save.top + 2));
  Check(host.IsOpen() && saves == 0, "unowned mouse release cannot Save");
  if (!host.IsOpen()) return;
  Click(window, layout.nav[1]);
  draft.page = ui::SettingsPage::kAppearance;
  layout = Layout(window, draft);
  Click(window, layout.theme_details[1]);
  Check(previews == 0, "theme details do not select or preview a theme");
  SendMessageW(window, WM_KEYDOWN, VK_ESCAPE, 0);
  Check(host.IsOpen(), "Escape dismisses details before the settings window");
  if (!host.IsOpen()) return;

  const auto a = Point(window, layout.theme_cards[0].left + 4,
                       layout.theme_cards[0].top + 4);
  const auto b = Point(window, layout.theme_cards[1].left + 4,
                       layout.theme_cards[1].top + 4);
  SendMessageW(window, WM_LBUTTONDOWN, MK_LBUTTON, a);
  SendMessageW(window, WM_LBUTTONUP, 0, b);
  Check(previews == 0, "dragging between cards never selects the release card");
  SendMessageW(window, WM_LBUTTONDOWN, MK_LBUTTON, b);
  SendMessageW(window, WM_CANCELMODE, 0, 0);
  SendMessageW(window, WM_LBUTTONUP, 0, b);
  Check(previews == 0, "cancelled capture revokes a late release");

  Click(window, layout.theme_details[0]);
  Click(window, layout.theme_cards[3]);
  Check(previews == 0, "outside click dismisses details without choosing another theme");
  Click(window, layout.theme_cards[3]);
  Check(preview == ui::ThemeId::kRasta && previews == 1,
        "normal owned card click still previews exactly once");
  host.Close(false);
  Check(preview == ui::ThemeId::kNight && saves == 0,
        "closing unsaved settings restores the original theme");
}
}  // namespace

int main() {
  TestNestedHitTargets();
  TestOwnedClicksAndTransientDetails();
  if (failures) return EXIT_FAILURE;
  std::cout << "Settings transient-details and mouse-ownership tests passed\n";
  return EXIT_SUCCESS;
}
