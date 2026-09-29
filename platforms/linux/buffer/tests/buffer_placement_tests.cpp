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

    if (failures != 0) {
        std::cerr << failures << " placement checks failed\n";
        return EXIT_FAILURE;
    }
    std::cout << "ok: buffer X11 placement\n";
    return EXIT_SUCCESS;
}
