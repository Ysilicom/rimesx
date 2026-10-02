#include <Windows.h>

#include <cstdlib>
#include <iostream>

#include "CandidateWindow.h"
#include "Guids.h"

namespace {
void Check(bool value, const char* description) {
  if (!value) {
    std::cerr << "FAIL: " << description << '\n';
    std::exit(1);
  }
}
}  // namespace

int main() {
  using namespace rimes::windows::tsf;
  SetThreadDpiAwarenessContext(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2);
  const HWND foreground = GetForegroundWindow();
  CandidateWindow candidate;
  int selected = 0;
  candidate.SetSelect([&](std::size_t index) {
    Check(index == 0, "mouse selects the visible logical item");
    ++selected;
  });
  CandidateSnapshot snapshot;
  snapshot.visible = true;
  snapshot.composition = L"nihao";
  snapshot.caret_rect = {100, 100, 100, 124};
  snapshot.items = {{L"1", L"你好", L""}};
  candidate.Update(snapshot);
  HWND popup = FindWindowW(kCandidateWindowClass, nullptr);
  DWORD process = 0;
  if (popup) GetWindowThreadProcessId(popup, &process);
  Check(popup && process == GetCurrentProcessId(), "test owns its isolated popup");
  Check(GetForegroundWindow() == foreground, "candidate does not activate the host");
  const unsigned dpi = GetDpiForWindow(popup);
  const auto px = [dpi](int dip) { return MulDiv(dip, static_cast<int>(dpi), 96); };
  HRGN region = CreateRectRgn(0, 0, 0, 0);
  Check(GetWindowRgn(popup, region) != ERROR, "rounded popup has an explicit region");
  Check(!PtInRegion(region, px(10), px(22)), "gap between preedit and strip is transparent");
  Check(PtInRegion(region, px(10), px(40)), "painted candidate remains in the window region");
  DeleteObject(region);
  const LPARAM click = MAKELPARAM(px(10), px(40));
  SendMessageW(popup, WM_LBUTTONUP, 0, click);
  Check(selected == 0, "late mouse release cannot select a candidate");
  SendMessageW(popup, WM_LBUTTONDOWN, MK_LBUTTON, click);
  SendMessageW(popup, WM_LBUTTONUP, 0, click);
  Check(selected == 1, "owned down/up selects once");
  SendMessageW(popup, WM_LBUTTONDOWN, MK_LBUTTON, click);
  snapshot.composition = L"ni";
  candidate.Update(snapshot);
  SendMessageW(popup, WM_LBUTTONUP, 0, click);
  Check(selected == 1, "composition change invalidates the old mouse press");
  SendMessageW(popup, WM_LBUTTONDOWN, MK_LBUTTON, click);
  candidate.Hide();
  SendMessageW(popup, WM_LBUTTONUP, 0, click);
  Check(selected == 1, "hidden popup invalidates the old mouse press");
  Check(GetCapture() != popup, "hiding releases mouse capture");
  Check(GetForegroundWindow() == foreground, "click ownership preserves no-activation");
  std::cout << "Candidate region, click ownership and no-activation passed\n";
}
