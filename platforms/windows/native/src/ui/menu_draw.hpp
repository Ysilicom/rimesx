#pragma once

#include <Windows.h>

#include <string>
#include <vector>

#include "icons.hpp"
#include "theme.hpp"

namespace rimes::windows::ui {

struct OwnerMenuItem {
  UINT id = 0;
  std::wstring text;
  bool checked = false;
  bool separator = false;
  bool enabled = true;
};

struct OwnerMenuTheme {
  ThemeId theme = ThemeId::kNight;
  HFONT font = nullptr;
};

inline void ApplyOwnerDrawMenu(HMENU menu, const std::vector<OwnerMenuItem>& items) {
  for (const auto& item : items) {
    if (item.separator) {
      AppendMenuW(menu, MF_SEPARATOR | MF_OWNERDRAW, 0, nullptr);
      continue;
    }
    UINT flags = MF_OWNERDRAW | (item.enabled ? MF_ENABLED : MF_GRAYED);
    if (item.checked) flags |= MF_CHECKED;
    // Store pointer to text via heap copy owned by caller through static storage
    // is unsafe; embed via dwItemData later. Use AppendMenu with MF_STRING first
    // then modify — simpler: MF_OWNERDRAW with item ID; text looked up by ID.
    AppendMenuW(menu, flags, item.id, MAKEINTRESOURCEW(item.id));
  }
}

inline LRESULT MeasureOwnerMenu(MEASUREITEMSTRUCT* measure, unsigned dpi,
                                const std::wstring& text, bool separator) {
  if (!measure) return FALSE;
  if (separator) {
    measure->itemHeight = MulDiv(8, static_cast<int>(dpi), 96);
    measure->itemWidth = MulDiv(120, static_cast<int>(dpi), 96);
    return TRUE;
  }
  measure->itemHeight = MulDiv(28, static_cast<int>(dpi), 96);
  measure->itemWidth =
      MulDiv(24, static_cast<int>(dpi), 96) +
      static_cast<UINT>(text.size()) * MulDiv(8, static_cast<int>(dpi), 96) +
      MulDiv(32, static_cast<int>(dpi), 96);
  if (measure->itemWidth < static_cast<UINT>(MulDiv(120, static_cast<int>(dpi), 96)))
    measure->itemWidth = MulDiv(120, static_cast<int>(dpi), 96);
  return TRUE;
}

inline LRESULT DrawOwnerMenu(const DRAWITEMSTRUCT* draw, ThemeId theme,
                             const std::wstring& text, bool checked,
                             bool separator, HFONT font) {
  if (!draw) return FALSE;
  const ThemePalette& p = Palette(theme);
  if (separator) {
    FillRectColor(draw->hDC, draw->rcItem, ToColorRef(p.surface));
    RECT line = draw->rcItem;
    line.top += (line.bottom - line.top) / 2;
    line.bottom = line.top + 1;
    line.left += 8;
    line.right -= 8;
    FillRectColor(draw->hDC, line, ToColorRef(p.border));
    return TRUE;
  }
  const bool selected = (draw->itemState & ODS_SELECTED) != 0;
  const bool disabled = (draw->itemState & ODS_DISABLED) != 0;
  FillRectColor(draw->hDC, draw->rcItem,
                ToColorRef(selected ? Blend(p.surface, p.accent, 0.22f)
                                    : p.surface));
  HGDIOBJ old = font ? SelectObject(draw->hDC, font) : nullptr;
  SetBkMode(draw->hDC, TRANSPARENT);
  SetTextColor(draw->hDC,
               ToColorRef(disabled ? p.text_muted
                                   : (selected ? p.text_primary : p.text_primary)));
  RECT text_rc = draw->rcItem;
  text_rc.left += 28;
  text_rc.right -= 12;
  if (checked) {
    RECT mark = draw->rcItem;
    mark.left += 8;
    mark.right = mark.left + 14;
    mark.top += 7;
    mark.bottom -= 7;
    DrawIconGlyph(draw->hDC, IconId::kCheck, mark, ToColorRef(p.accent));
  }
  DrawTextW(draw->hDC, text.c_str(), -1, &text_rc,
            DT_LEFT | DT_VCENTER | DT_SINGLELINE | DT_NOPREFIX);
  if (old) SelectObject(draw->hDC, old);
  return TRUE;
}

}  // namespace rimes::windows::ui
