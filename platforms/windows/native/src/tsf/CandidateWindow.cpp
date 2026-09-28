#include "CandidateWindow.h"

#include <algorithm>
#include <mutex>

#include "Guids.h"
#include "ModuleState.h"
#include "candidate_layout.hpp"

namespace rimes::windows::tsf {
namespace {

constexpr int kPaddingDip = 8;
constexpr int kItemHeightDip = 22;
constexpr int kMinWidthDip = 160;
constexpr int kFontDip = 16;

std::mutex g_snapshot_mutex;
CandidateSnapshot g_last_snapshot;

RECT ToRect(const ScreenPoint& origin, long width, long height) noexcept {
  RECT rect{};
  rect.left = origin.x;
  rect.top = origin.y;
  rect.right = origin.x + width;
  rect.bottom = origin.y + height;
  return rect;
}

unsigned int WindowDpi(HWND window) noexcept {
  using GetDpiForWindowFn = UINT(WINAPI*)(HWND);
  const HMODULE user32 = GetModuleHandleW(L"user32.dll");
  if (user32 != nullptr) {
    const auto get_dpi = reinterpret_cast<GetDpiForWindowFn>(
        GetProcAddress(user32, "GetDpiForWindow"));
    if (get_dpi != nullptr && window != nullptr) {
      const UINT dpi = get_dpi(window);
      if (dpi != 0) {
        return dpi;
      }
    }
  }
  const HDC desktop = GetDC(nullptr);
  const int dpi = desktop != nullptr ? GetDeviceCaps(desktop, LOGPIXELSX) : 96;
  if (desktop != nullptr) {
    ReleaseDC(nullptr, desktop);
  }
  return dpi > 0 ? static_cast<unsigned int>(dpi) : 96U;
}

ScreenRect WorkAreaFromCaret(const RECT& caret) noexcept {
  const POINT probe{caret.left, caret.top};
  const HMONITOR monitor =
      MonitorFromPoint(probe, MONITOR_DEFAULTTONEAREST);
  MONITORINFO info{};
  info.cbSize = sizeof(info);
  if (monitor != nullptr && GetMonitorInfoW(monitor, &info)) {
    return {info.rcWork.left, info.rcWork.top, info.rcWork.right,
            info.rcWork.bottom};
  }
  RECT work{};
  SystemParametersInfoW(SPI_GETWORKAREA, 0, &work, 0);
  return {work.left, work.top, work.right, work.bottom};
}

}  // namespace

CandidateWindow::~CandidateWindow() {
  Hide();
  if (window_ != nullptr) {
    DestroyWindow(window_);
    window_ = nullptr;
  }
}

bool CandidateWindow::GetLastSnapshot(CandidateSnapshot* snapshot) noexcept {
  if (snapshot == nullptr) {
    return false;
  }
  try {
    std::lock_guard lock(g_snapshot_mutex);
    *snapshot = g_last_snapshot;
    return true;
  } catch (...) {
    return false;
  }
}

void CandidateWindow::PublishSnapshot(const CandidateSnapshot& snapshot) noexcept {
  try {
    std::lock_guard lock(g_snapshot_mutex);
    g_last_snapshot = snapshot;
  } catch (...) {
  }
}

CandidateSnapshot CandidateWindow::snapshot() const noexcept {
  return snapshot_;
}

void CandidateWindow::Hide() noexcept {
  snapshot_ = {};
  PublishSnapshot(snapshot_);
  if (window_ != nullptr) {
    ShowWindow(window_, SW_HIDE);
  }
}

bool CandidateWindow::EnsureWindow() noexcept {
  if (window_ != nullptr) {
    return true;
  }
  HINSTANCE instance = module::Instance();
  if (instance == nullptr) {
    instance = GetModuleHandleW(nullptr);
  }
  if (instance == nullptr) {
    return false;
  }

  WNDCLASSEXW window_class{};
  window_class.cbSize = sizeof(window_class);
  window_class.lpfnWndProc = WindowProcedure;
  window_class.hInstance = instance;
  window_class.lpszClassName = kCandidateWindowClass;
  window_class.hCursor = LoadCursorW(nullptr, IDC_ARROW);
  window_class.hbrBackground =
      static_cast<HBRUSH>(GetStockObject(WHITE_BRUSH));
  window_class.style = CS_HREDRAW | CS_VREDRAW | CS_DROPSHADOW;
  RegisterClassExW(&window_class);

  window_ = CreateWindowExW(
      WS_EX_TOOLWINDOW | WS_EX_TOPMOST | WS_EX_NOACTIVATE | WS_EX_NOINHERITLAYOUT,
      kCandidateWindowClass, L"", WS_POPUP, 0, 0, 0, 0, nullptr, nullptr,
      instance, this);
  return window_ != nullptr;
}

void CandidateWindow::Update(const CandidateSnapshot& snapshot) noexcept {
  if (!snapshot.visible || snapshot.items.empty()) {
    Hide();
    return;
  }
  try {
    snapshot_ = snapshot;
  } catch (...) {
    Hide();
    return;
  }
  // Always publish before attempting to create a HWND. Hosted CI and
  // in-process Fake TSF hosts can observe candidates even when the desktop
  // session refuses a top-level tool window.
  PublishSnapshot(snapshot_);
  if (EnsureWindow()) {
    LayoutAndShow(snapshot_);
  }
}

void CandidateWindow::LayoutAndShow(const CandidateSnapshot& snapshot) noexcept {
  const unsigned int dpi = WindowDpi(window_);
  const int padding = ScaleForDpi(kPaddingDip, dpi);
  const int item_height = ScaleForDpi(kItemHeightDip, dpi);
  const int min_width = ScaleForDpi(kMinWidthDip, dpi);
  const int font_height = ScaleForDpi(kFontDip, dpi);

  HDC device = GetDC(window_);
  HFONT font = CreateFontW(-font_height, 0, 0, 0, FW_NORMAL, FALSE, FALSE,
                           FALSE, DEFAULT_CHARSET, OUT_DEFAULT_PRECIS,
                           CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY,
                           DEFAULT_PITCH | FF_DONTCARE, L"Segoe UI");
  HFONT previous = nullptr;
  if (device != nullptr && font != nullptr) {
    previous = static_cast<HFONT>(SelectObject(device, font));
  }

  long text_width = 0;
  if (device != nullptr) {
    for (const CandidateItem& item : snapshot.items) {
      std::wstring line = item.label;
      if (!line.empty()) {
        line.append(L" ");
      }
      line.append(item.text);
      if (!item.comment.empty()) {
        line.append(L"  ");
        line.append(item.comment);
      }
      SIZE size{};
      GetTextExtentPoint32W(device, line.c_str(),
                            static_cast<int>(line.size()), &size);
      text_width = (std::max)(text_width, static_cast<long>(size.cx));
    }
  }
  if (device != nullptr) {
    if (previous != nullptr) {
      SelectObject(device, previous);
    }
    ReleaseDC(window_, device);
  }
  if (font != nullptr) {
    DeleteObject(font);
  }

  const long width = (std::max)(static_cast<long>(min_width),
                                text_width + (2 * padding) + 8);
  const long height = (2 * padding) +
                      static_cast<long>(snapshot.items.size()) * item_height;
  const ScreenRect caret{snapshot.caret_rect.left, snapshot.caret_rect.top,
                         snapshot.caret_rect.right, snapshot.caret_rect.bottom};
  const ScreenPoint origin =
      PlaceCandidateWindow(caret, width, height, WorkAreaFromCaret(snapshot.caret_rect));
  snapshot_.window_rect = ToRect(origin, width, height);
  PublishSnapshot(snapshot_);

  SetWindowPos(window_, HWND_TOPMOST, origin.x, origin.y, width, height,
               SWP_NOACTIVATE | SWP_SHOWWINDOW);
  InvalidateRect(window_, nullptr, TRUE);
}

void CandidateWindow::Paint(HDC device) const noexcept {
  RECT client{};
  GetClientRect(window_, &client);
  FillRect(device, &client, static_cast<HBRUSH>(GetStockObject(WHITE_BRUSH)));

  const unsigned int dpi = WindowDpi(window_);
  const int padding = ScaleForDpi(kPaddingDip, dpi);
  const int item_height = ScaleForDpi(kItemHeightDip, dpi);
  const int font_height = ScaleForDpi(kFontDip, dpi);
  HFONT font = CreateFontW(-font_height, 0, 0, 0, FW_NORMAL, FALSE, FALSE,
                           FALSE, DEFAULT_CHARSET, OUT_DEFAULT_PRECIS,
                           CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY,
                           DEFAULT_PITCH | FF_DONTCARE, L"Segoe UI");
  const HFONT previous =
      font != nullptr ? static_cast<HFONT>(SelectObject(device, font))
                      : nullptr;
  SetBkMode(device, TRANSPARENT);

  for (std::size_t index = 0; index < snapshot_.items.size(); ++index) {
    RECT row = client;
    row.top = padding + static_cast<LONG>(index) * item_height;
    row.bottom = row.top + item_height;
    row.left += padding;
    row.right -= padding;
    const bool selected =
        snapshot_.highlighted != 0xffff &&
        index == static_cast<std::size_t>(snapshot_.highlighted);
    if (selected) {
      HBRUSH highlight = CreateSolidBrush(RGB(210, 228, 255));
      FillRect(device, &row, highlight);
      DeleteObject(highlight);
    }
    std::wstring line = snapshot_.items[index].label;
    if (!line.empty()) {
      line.append(L" ");
    }
    line.append(snapshot_.items[index].text);
    if (!snapshot_.items[index].comment.empty()) {
      line.append(L"  ");
      line.append(snapshot_.items[index].comment);
    }
    SetTextColor(device, RGB(20, 20, 20));
    DrawTextW(device, line.c_str(), static_cast<int>(line.size()), &row,
              DT_LEFT | DT_VCENTER | DT_SINGLELINE | DT_NOPREFIX);
  }

  if (previous != nullptr) {
    SelectObject(device, previous);
  }
  if (font != nullptr) {
    DeleteObject(font);
  }
}

LRESULT CALLBACK CandidateWindow::WindowProcedure(HWND window,
                                                  UINT message,
                                                  WPARAM wparam,
                                                  LPARAM lparam) {
  CandidateWindow* self = nullptr;
  if (message == WM_NCCREATE) {
    const auto* create = reinterpret_cast<CREATESTRUCTW*>(lparam);
    self = static_cast<CandidateWindow*>(create->lpCreateParams);
    SetWindowLongPtrW(window, GWLP_USERDATA, reinterpret_cast<LONG_PTR>(self));
    using EnableNonClientDpiScalingFn = BOOL(WINAPI*)(HWND);
    const HMODULE user32 = GetModuleHandleW(L"user32.dll");
    if (user32 != nullptr) {
      const auto enable = reinterpret_cast<EnableNonClientDpiScalingFn>(
          GetProcAddress(user32, "EnableNonClientDpiScaling"));
      if (enable != nullptr) {
        enable(window);
      }
    }
  } else {
    self = reinterpret_cast<CandidateWindow*>(
        GetWindowLongPtrW(window, GWLP_USERDATA));
  }

  switch (message) {
    case WM_PAINT: {
      PAINTSTRUCT paint{};
      const HDC device = BeginPaint(window, &paint);
      if (self != nullptr && device != nullptr) {
        self->Paint(device);
      }
      EndPaint(window, &paint);
      return 0;
    }
    case WM_MOUSEACTIVATE:
      return MA_NOACTIVATE;
    case WM_ERASEBKGND:
      return 1;
    default:
      break;
  }
  return DefWindowProcW(window, message, wparam, lparam);
}

}  // namespace rimes::windows::tsf
