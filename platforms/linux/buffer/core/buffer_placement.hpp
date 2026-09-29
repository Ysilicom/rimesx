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
    DockBottom,
};

struct PanelPlacement {
    int x = 0;
    int y = 0;
    PanelSide side = PanelSide::AboveCaret;
};

// Stock Fcitx5 candidates grow downward from the caret. Prefer sitting
// above the caret so a 9-row popup cannot cover the chips. If that does
// not fit the workarea, dock to the bottom of the monitor — never a small
// gap immediately below the caret (that overlaps the popup).
PanelPlacement PlaceX11Panel(const CaretRect& caret, const Workarea& work,
                             int panel_width, int panel_height, int gap);

}  // namespace rimes::buffer
