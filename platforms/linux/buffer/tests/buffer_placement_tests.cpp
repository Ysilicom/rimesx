#include <cstdlib>
#include <iostream>

#include "buffer_placement.hpp"

namespace {

int failures = 0;

void Expect(bool condition, const char* message) {
    if (!condition) {
        std::cerr << "FAIL: " << message << '\n';
        ++failures;
    }
}

}  // namespace

int main() {
    using rimes::buffer::CaretRect;
    using rimes::buffer::PanelSide;
    using rimes::buffer::PlaceX11Panel;
    using rimes::buffer::Workarea;

    constexpr int kWidth = 760;
    constexpr int kHeight = 78;
    constexpr int kGap = 10;
    Workarea work;
    work.x = 0;
    work.y = 0;
    work.width = 1280;
    work.height = 800;

    CaretRect mid;
    mid.x = 80;
    mid.y = 240;
    mid.width = 8;
    mid.height = 20;
    mid.valid = true;
    const auto above = PlaceX11Panel(mid, work, kWidth, kHeight, kGap);
    Expect(above.side == PanelSide::AboveCaret, "mid-screen caret prefers above");
    Expect(above.y + kHeight + kGap <= mid.y, "panel bottom stays above the caret");

    CaretRect top;
    top.x = 80;
    top.y = 40;
    top.width = 8;
    top.height = 18;
    top.valid = true;
    const auto dock = PlaceX11Panel(top, work, kWidth, kHeight, kGap);
    Expect(dock.side == PanelSide::DockBottom, "top-of-screen caret docks to the bottom");
    Expect(dock.y + kHeight <= work.height, "docked panel stays in the workarea");
    Expect(dock.y > 400, "docked panel is far from a top caret / candidate list");

    CaretRect firefox_low;
    firefox_low.x = 80;
    firefox_low.y = 546;
    firefox_low.width = 8;
    firefox_low.height = 20;
    firefox_low.valid = true;
    const auto below = PlaceX11Panel(firefox_low, work, kWidth, kHeight, kGap);
    Expect(below.side == PanelSide::BelowCaret,
           "low caret whose popup flips up sits below the caret");
    Expect(below.y >= firefox_low.y + firefox_low.height,
           "below-caret panel starts under the caret");
    Expect(below.y + kHeight <= work.height, "below-caret panel stays in the workarea");

    CaretRect bottom;
    bottom.x = 80;
    bottom.y = 715;
    bottom.width = 8;
    bottom.height = 20;
    bottom.valid = true;
    const auto dock_top = PlaceX11Panel(bottom, work, kWidth, kHeight, kGap);
    Expect(dock_top.side == PanelSide::DockTop,
           "caret too low for a below-panel docks to the top");
    Expect(dock_top.y <= 16, "dock-top panel sits at the top of the workarea");
    Expect(dock_top.y + kHeight < bottom.y, "dock-top panel is not under the upward popup");

    if (failures != 0) {
        std::cerr << failures << " placement checks failed\n";
        return EXIT_FAILURE;
    }
    std::cout << "ok: buffer X11 placement\n";
    return EXIT_SUCCESS;
}
