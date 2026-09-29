#include "buffer_placement.hpp"

#include <algorithm>

namespace rimes::buffer {
namespace {

int Clamp(int value, int lo, int hi) {
    if (hi < lo) {
        return lo;
    }
    return std::max(lo, std::min(value, hi));
}

}  // namespace

PanelPlacement PlaceX11Panel(const CaretRect& caret, const Workarea& work,
                             int panel_width, int panel_height, int gap,
                             int popup_reserve) {
    PanelPlacement out;
    const int min_x = work.x + 8;
    const int max_x = work.x + work.width - panel_width - 8;
    out.x = caret.x + (caret.width / 2) - (panel_width / 2);
    out.x = Clamp(out.x, min_x, max_x);

    const int min_y = work.y + 8;
    const int max_y = work.y + work.height - panel_height - 8;
    const int caret_bottom = caret.y + caret.height;
    const int work_bottom = work.y + work.height;
    const bool popup_flips_up = caret_bottom + popup_reserve > work_bottom;

    if (popup_flips_up) {
        const int below_y = caret_bottom + gap;
        if (below_y + panel_height <= work_bottom - 8) {
            out.y = below_y;
            out.side = PanelSide::BelowCaret;
            return out;
        }
        out.y = Clamp(min_y, min_y, max_y);
        out.side = PanelSide::DockTop;
        return out;
    }

    const int above_y = caret.y - gap - panel_height;
    if (above_y >= min_y) {
        out.y = above_y;
        out.side = PanelSide::AboveCaret;
        return out;
    }
    out.y = Clamp(max_y, min_y, max_y);
    out.side = PanelSide::DockBottom;
    return out;
}

}  // namespace rimes::buffer
