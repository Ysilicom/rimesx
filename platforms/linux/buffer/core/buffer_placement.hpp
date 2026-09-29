#pragma once

#include "buffer_model.hpp"

namespace rimes::buffer {

struct Workarea {
    int x = 0;
    int y = 0;
    int width = 0;
    int height = 0;
};

enum class PanelSide {
    AboveCaret,
    BelowCaret,
    DockBottom,
    DockTop,
};

struct PanelPlacement {
    int x = 0;
    int y = 0;
    PanelSide side = PanelSide::AboveCaret;
};

// Stock Fcitx5 candidates are ~250 px (9 rows). They grow downward from the
// caret unless caret-bottom + this reserve exceeds the workarea, in which
// case the popup flips upward.
inline constexpr int kCandidatePopupReserve = 260;

// Prefer sitting above the caret so a downward popup cannot cover the chips.
// When the popup would flip upward, sit below the caret if that fits;
// otherwise dock to the top of the monitor. Never leave a small gap on the
// same side as the popup.
PanelPlacement PlaceX11Panel(const CaretRect& caret, const Workarea& work,
                             int panel_width, int panel_height, int gap,
                             int popup_reserve = kCandidatePopupReserve);

}  // namespace rimes::buffer
