#include "candidate_layout.hpp"

#include <cstdlib>
#include <iostream>

namespace rimes::windows::tsf::tests {
namespace {

int g_failures = 0;

void Check(bool condition, const char* message) {
  if (!condition) {
    std::cerr << "FAIL: " << message << '\n';
    ++g_failures;
  }
}

void TestPrefersBelowCaret() {
  const ScreenRect caret{100, 200, 140, 224};
  const ScreenRect work{0, 0, 1920, 1080};
  const ScreenPoint origin = PlaceCandidateWindow(caret, 320, 180, work);
  Check(origin.x == 100, "window should align with the caret left edge");
  Check(origin.y == 228, "window should sit just below the caret");
}

void TestFlipsAboveWhenNeeded() {
  const ScreenRect caret{100, 1000, 140, 1024};
  const ScreenRect work{0, 0, 1920, 1080};
  const ScreenPoint origin = PlaceCandidateWindow(caret, 320, 180, work);
  Check(origin.y == 816, "window should flip above the caret near the bottom");
}

void TestClampsToWorkArea() {
  const ScreenRect caret{1800, 100, 1900, 124};
  const ScreenRect work{0, 0, 1920, 1080};
  const ScreenPoint origin = PlaceCandidateWindow(caret, 400, 180, work);
  Check(origin.x == 1520, "window should stay inside the right work-area edge");
}

void TestDpiScale() {
  Check(ScaleForDpi(16, 96) == 16, "96 DPI should be identity");
  Check(ScaleForDpi(16, 144) == 24, "150% DPI should scale by 1.5");
}

}  // namespace

int RunCandidateLayoutTests() {
  TestPrefersBelowCaret();
  TestFlipsAboveWhenNeeded();
  TestClampsToWorkArea();
  TestDpiScale();
  return g_failures == 0 ? EXIT_SUCCESS : EXIT_FAILURE;
}

}  // namespace rimes::windows::tsf::tests

int main() {
  return rimes::windows::tsf::tests::RunCandidateLayoutTests();
}
