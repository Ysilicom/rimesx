#include <Windows.h>

#include <algorithm>
namespace {
#if defined(_WIN64)
constexpr wchar_t kTitle[] = L"RIMES TSF Test Host · x64";
#else
constexpr wchar_t kTitle[] = L"RIMES TSF Test Host · x86";
#endif
LRESULT CALLBACK WindowProcedure(HWND window, UINT message, WPARAM wparam,
                                 LPARAM lparam) {
  switch (message) {
    case WM_CREATE:
      for (int index = 0; index < 3; ++index) {
        const auto id = 1001 + index;
        const auto styles = WS_CHILD | WS_VISIBLE | WS_TABSTOP | ES_LEFT |
                            (index == 2 ? ES_PASSWORD | ES_AUTOHSCROLL
                                        : ES_MULTILINE | ES_AUTOVSCROLL |
                                              ES_WANTRETURN | WS_VSCROLL);
        auto control = CreateWindowExW(
            WS_EX_CLIENTEDGE, L"EDIT", L"", styles, 0, 0, 0, 0, window,
            reinterpret_cast<HMENU>(static_cast<INT_PTR>(id)),
            GetModuleHandleW(nullptr), nullptr);
        if (!control) return -1;
        SendMessageW(control, WM_SETFONT,
                     reinterpret_cast<WPARAM>(GetStockObject(DEFAULT_GUI_FONT)),
                     TRUE);
        CreateWindowExW(
            0, L"STATIC",
            index == 0   ? L"Input A"
            : index == 1 ? L"Input B - change target"
                         : L"Password - Buffer must be disabled",
            WS_CHILD | WS_VISIBLE, 0, 0, 0, 0, window,
            reinterpret_cast<HMENU>(static_cast<INT_PTR>(2001 + index)),
            GetModuleHandleW(nullptr), nullptr);
      }
      SetFocus(GetDlgItem(window, 1001));
      return 0;
    case WM_SIZE: {
      const int width = (std::max)(100, static_cast<int>(LOWORD(lparam)) - 24),
                height = static_cast<int>(HIWORD(lparam));
      const int row = (std::max)(80, (height - 124) / 2);
      for (int index = 0; index < 3; ++index) {
        const int y = 12 + index * (row + 30);
        MoveWindow(GetDlgItem(window, 2001 + index), 12, y, width, 20, TRUE);
        MoveWindow(GetDlgItem(window, 1001 + index), 12, y + 22, width,
                   index == 2 ? 28 : row, TRUE);
      }
      return 0;
    }
    case WM_SETFOCUS:
      SetFocus(GetDlgItem(window, 1001));
      return 0;
    case WM_DESTROY:
      PostQuitMessage(0);
      return 0;
    default:
      return DefWindowProcW(window, message, wparam, lparam);
  }
}
}  // namespace
int WINAPI wWinMain(HINSTANCE instance, HINSTANCE, PWSTR, int show) {
  WNDCLASSW wc{};
  wc.hInstance = instance;
  wc.lpfnWndProc = WindowProcedure;
  wc.lpszClassName = L"RimesTsfTestHostWindow";
  wc.hCursor = LoadCursorW(nullptr, IDC_IBEAM);
  wc.hbrBackground = reinterpret_cast<HBRUSH>(COLOR_WINDOW + 1);
  if (!RegisterClassW(&wc)) return 1;
  auto window = CreateWindowExW(
      0, wc.lpszClassName, kTitle, WS_OVERLAPPEDWINDOW, CW_USEDEFAULT,
      CW_USEDEFAULT, 760, 580, nullptr, nullptr, instance, nullptr);
  if (!window) return 2;
  ShowWindow(window, show);
  UpdateWindow(window);
  MSG message{};
  while (GetMessageW(&message, nullptr, 0, 0) > 0) {
    if (IsDialogMessageW(window, &message)) continue;
    TranslateMessage(&message);
    DispatchMessageW(&message);
  }
  return static_cast<int>(message.wParam);
}
