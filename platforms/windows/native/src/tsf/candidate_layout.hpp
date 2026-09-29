#pragma once

namespace rimes::windows::tsf {

struct ScreenRect {
  long left = 0;
  long top = 0;
  long right = 0;
  long bottom = 0;

  [[nodiscard]] long width() const noexcept { return right - left; }
  [[nodiscard]] long height() const noexcept { return bottom - top; }
  [[nodiscard]] bool empty() const noexcept {
    return right <= left || bottom <= top;
  }
};

struct ScreenPoint {
  long x = 0;
  long y = 0;
};

// Places a candidate window near the caret rectangle. Prefers immediately
// below the caret, flips above when the work area cannot hold it, and clamps
// the origin so the window stays on the same monitor.
inline ScreenPoint PlaceCandidateWindow(const ScreenRect& caret,
                                        long window_width,
                                        long window_height,
                                        const ScreenRect& work_area) noexcept {
  ScreenPoint origin;
  if (window_width <= 0) {
    window_width = 1;
  }
  if (window_height <= 0) {
    window_height = 1;
  }

  ScreenRect area = work_area;
  if (area.empty()) {
    area = caret.empty() ? ScreenRect{0, 0, 1920, 1080} : caret;
    if (area.width() < window_width) {
      area.right = area.left + window_width;
    }
    if (area.height() < window_height) {
      area.bottom = area.top + window_height;
    }
  }

  origin.x = caret.empty() ? area.left : caret.left;
  const long below = caret.empty() ? area.top : caret.bottom + 4;
  const long above = caret.empty() ? area.top : caret.top - window_height - 4;
  if (below + window_height <= area.bottom || above < area.top) {
    origin.y = below;
  } else {
    origin.y = above;
  }

  if (origin.x + window_width > area.right) {
    origin.x = area.right - window_width;
  }
  if (origin.x < area.left) {
    origin.x = area.left;
  }
  if (origin.y + window_height > area.bottom) {
    origin.y = area.bottom - window_height;
  }
  if (origin.y < area.top) {
    origin.y = area.top;
  }
  return origin;
}

inline int ScaleForDpi(int value, unsigned int dpi) noexcept {
  if (dpi == 0) {
    dpi = 96;
  }
  return static_cast<int>((static_cast<long long>(value) * dpi) / 96);
}

}  // namespace rimes::windows::tsf
