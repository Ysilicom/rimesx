#include "window.hpp"

#include <d2d1.h>
#include <dwrite.h>
#include <shellapi.h>
#include <wtsapi32.h>

#include <algorithm>
#include <atomic>
#include <memory>
#include <string>
#include <thread>

#include "../broker/autostart.hpp"
#include "../broker/default_paths.hpp"

namespace rimes::windows::workbench {
namespace {
constexpr UINT kChanged = WM_APP + 80, kTray = WM_APP + 81;
constexpr int kToggle = 100, kSettings = 101, kDeploy = 102, kStartup = 103,
              kAbout = 105, kExit = 104;
const wchar_t* kSchemas[] = {L"rime_ice", L"double_pinyin",
                             L"double_pinyin_flypy", L"wubi86", L"english"};
const wchar_t* kSchemaNames[] = {L"雾凇拼音", L"自然码双拼", L"小鹤双拼",
                                 L"五笔 86", L"英文"};
const wchar_t* kActions[] = {L"绑定 / 暂停", L"粘贴",   L"生成", L"翻译",
                             L"取消",        L"下一块", L"全部", L"复制结果",
                             L"设置",        L"关闭"};
struct Window {
  Runtime& runtime;
  std::function<void()> stop, deploy;
  HWND window = nullptr, settings = nullptr;
  std::atomic<HWND> notification{nullptr};
  ID2D1Factory* factory = nullptr;
  IDWriteFactory* write = nullptr;
  ID2D1HwndRenderTarget* render = nullptr;
  ID2D1SolidColorBrush* brush = nullptr;
  IDWriteTextFormat* format = nullptr;
  NOTIFYICONDATAW tray{};
  float scroll[2] = {0, 0};
  std::jthread maintenance;
  std::atomic_bool deploying = false;
  Window(Runtime& r, std::function<void()> s, std::function<void()> d)
      : runtime(r), stop(std::move(s)), deploy(std::move(d)) {}
  ~Window() {
    if (maintenance.joinable()) maintenance.join();
    if (format) format->Release();
    if (brush) brush->Release();
    if (render) render->Release();
    if (write) write->Release();
    if (factory) factory->Release();
  }
  HWND Item(int id) const { return GetDlgItem(settings, id); }
  std::wstring Text(int id) const {
    const HWND item = Item(id);
    std::wstring text(static_cast<std::size_t>(GetWindowTextLengthW(item)) + 1,
                      L'\0');
    GetWindowTextW(item, text.data(), static_cast<int>(text.size()));
    text.resize(wcslen(text.c_str()));
    return text;
  }
  void Label(const wchar_t* text, int y) {
    CreateWindowExW(0, L"STATIC", text, WS_CHILD | WS_VISIBLE, 20, y, 180, 23,
                    settings, nullptr, GetModuleHandleW(nullptr), nullptr);
  }
  void Edit(int id, const std::wstring& text, int y, bool secret = false) {
    CreateWindowExW(WS_EX_CLIENTEDGE, L"EDIT", text.c_str(),
                    WS_CHILD | WS_VISIBLE | WS_TABSTOP | ES_AUTOHSCROLL |
                        (secret ? ES_PASSWORD : 0),
                    205, y - 3, 425, 26, settings,
                    reinterpret_cast<HMENU>(static_cast<INT_PTR>(id)),
                    GetModuleHandleW(nullptr), nullptr);
  }
  void Check(int id, const wchar_t* text, int y, bool checked) {
    auto control = CreateWindowExW(
        0, L"BUTTON", text,
        WS_CHILD | WS_VISIBLE | WS_TABSTOP | BS_AUTOCHECKBOX, 205, y, 390, 24,
        settings, reinterpret_cast<HMENU>(static_cast<INT_PTR>(id)),
        GetModuleHandleW(nullptr), nullptr);
    SendMessageW(control, BM_SETCHECK, checked ? BST_CHECKED : BST_UNCHECKED,
                 0);
  }
  void OpenSettings();
  void Save();
  void Paint();
  void Update();
  void Action(int index);
  void Copy();
  void Paste();
  void Track(const core::Json& blocks, const std::string& tail, D2D1_RECT_F box,
             int lane);
  void Draw(const std::wstring& text, D2D1_RECT_F box, D2D1_COLOR_F color) {
    brush->SetColor(color);
    render->DrawTextW(text.c_str(), static_cast<UINT32>(text.size()), format,
                      box, brush, D2D1_DRAW_TEXT_OPTIONS_CLIP);
  }
  static LRESULT CALLBACK SettingsProcedure(HWND, UINT, WPARAM, LPARAM);
  static LRESULT CALLBACK Procedure(HWND, UINT, WPARAM, LPARAM);
};
void Window::OpenSettings() {
  runtime.Close();
  if (settings) {
    ShowWindow(settings, SW_SHOWNORMAL);
    SetForegroundWindow(settings);
    return;
  }
  WNDCLASSW wc{};
  wc.lpfnWndProc = SettingsProcedure;
  wc.hInstance = GetModuleHandleW(nullptr);
  wc.lpszClassName = L"Rimes.Settings";
  wc.hCursor = LoadCursorW(nullptr, IDC_ARROW);
  wc.hbrBackground = GetSysColorBrush(COLOR_WINDOW);
  RegisterClassW(&wc);
  settings = CreateWindowExW(0, wc.lpszClassName, L"RIMES 设置",
                             WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU,
                             CW_USEDEFAULT, CW_USEDEFAULT, 680, 585, nullptr,
                             nullptr, wc.hInstance, this);
  auto config = runtime.Configuration();
  Label(L"输入方案", 24);
  auto combo = CreateWindowExW(
      0, L"COMBOBOX", L"",
      WS_CHILD | WS_VISIBLE | WS_TABSTOP | CBS_DROPDOWNLIST, 205, 20, 425, 180,
      settings, reinterpret_cast<HMENU>(205), wc.hInstance, nullptr);
  for (int i = 0; i < 5; ++i) {
    SendMessageW(combo, CB_ADDSTRING, 0,
                 reinterpret_cast<LPARAM>(kSchemaNames[i]));
    if (config.schema == Utf8(kSchemas[i]))
      SendMessageW(combo, CB_SETCURSEL, static_cast<WPARAM>(i), 0);
  }
  Check(208, L"英文直输", 62, config.ascii);
  Check(209, L"繁体转换", 92, config.traditional);
  Check(210, L"英文标点", 122, config.ascii_punctuation);
  Label(L"候选字号（10–40）", 158);
  Edit(206, std::to_wstring(config.font_size), 158);
  Label(L"Buffer：Ctrl+Alt+字母", 194);
  Edit(207, std::wstring(1, static_cast<wchar_t>(config.hotkey_key)), 194);
  Label(L"API 地址", 238);
  Edit(201, Wide(config.base_url), 238);
  Label(L"模型", 276);
  Edit(202, Wide(config.model), 276);
  Label(L"API 密钥（留空保留）", 314);
  Edit(203, L"", 314, true);
  Label(L"翻译目标语言", 352);
  Edit(204, Wide(config.target_language), 352);
  CreateWindowExW(0, L"STATIC",
                  L"密钥保存在 Windows 凭据管理器。正文只在生成或翻译时发送。",
                  WS_CHILD | WS_VISIBLE, 20, 397, 620, 40, settings, nullptr,
                  wc.hInstance, nullptr);
  CreateWindowExW(0, L"BUTTON", L"保存",
                  WS_CHILD | WS_VISIBLE | WS_TABSTOP | BS_DEFPUSHBUTTON, 425,
                  470, 95, 32, settings, reinterpret_cast<HMENU>(1),
                  wc.hInstance, nullptr);
  CreateWindowExW(0, L"BUTTON", L"关闭", WS_CHILD | WS_VISIBLE | WS_TABSTOP,
                  535, 470, 95, 32, settings, reinterpret_cast<HMENU>(2),
                  wc.hInstance, nullptr);
  const auto dpi = GetDpiForWindow(settings);
  if (dpi != 96) {
    EnumChildWindows(
        settings,
        [](HWND child, LPARAM value) -> BOOL {
          RECT rect{};
          GetWindowRect(child, &rect);
          MapWindowPoints(nullptr, GetParent(child),
                          reinterpret_cast<POINT*>(&rect), 2);
          const int d = static_cast<int>(value);
          SetWindowPos(child, nullptr, MulDiv(rect.left, d, 96),
                       MulDiv(rect.top, d, 96),
                       MulDiv(rect.right - rect.left, d, 96),
                       MulDiv(rect.bottom - rect.top, d, 96), SWP_NOZORDER);
          return TRUE;
        },
        static_cast<LPARAM>(dpi));
    SetWindowPos(
        settings, nullptr, 0, 0, MulDiv(680, static_cast<int>(dpi), 96),
        MulDiv(585, static_cast<int>(dpi), 96), SWP_NOMOVE | SWP_NOZORDER);
  }
  ShowWindow(settings, SW_SHOWNORMAL);
  SetForegroundWindow(settings);
}
void Window::Save() {
  try {
    auto old = runtime.Configuration(), config = old;
    const auto selected = SendMessageW(Item(205), CB_GETCURSEL, 0, 0);
    if (selected < 0 || selected > 4) throw std::runtime_error("scheme");
    config.schema = Utf8(kSchemas[selected]);
    config.base_url = Utf8(Text(201));
    config.model = Utf8(Text(202));
    config.target_language = Utf8(Text(204));
    config.font_size = static_cast<unsigned>(std::stoul(Text(206)));
    auto hotkey = Text(207);
    if (config.font_size < 10 || config.font_size > 40 || hotkey.size() != 1)
      throw std::runtime_error("range");
    config.hotkey_key = static_cast<unsigned>(towupper(hotkey[0]));
    config.hotkey_modifiers = MOD_CONTROL | MOD_ALT;
    if (config.hotkey_key < 'A' || config.hotkey_key > 'Z' ||
        config.base_url.size() > 2048 || config.model.size() > 256 ||
        config.target_language.size() > 128)
      throw std::runtime_error("range");
    config.ascii = SendMessageW(Item(208), BM_GETCHECK, 0, 0) == BST_CHECKED;
    config.traditional =
        SendMessageW(Item(209), BM_GETCHECK, 0, 0) == BST_CHECKED;
    config.ascii_punctuation =
        SendMessageW(Item(210), BM_GETCHECK, 0, 0) == BST_CHECKED;
    UnregisterHotKey(window, 1);
    if (!RegisterHotKey(window, 1, config.hotkey_modifiers | MOD_NOREPEAT,
                        config.hotkey_key)) {
      RegisterHotKey(window, 1, old.hotkey_modifiers | MOD_NOREPEAT,
                     old.hotkey_key);
      MessageBoxW(settings, L"快捷键已被占用，请选择其他字母。", L"RIMES",
                  MB_OK);
      return;
    }
    auto key = Text(203);
    std::string error;
    const bool saved = runtime.Configure(config, key, !key.empty(), &error);
    if (!key.empty())
      SecureZeroMemory(key.data(), key.size() * sizeof(wchar_t));
    if (!saved) {
      UnregisterHotKey(window, 1);
      RegisterHotKey(window, 1, old.hotkey_modifiers | MOD_NOREPEAT,
                     old.hotkey_key);
      MessageBoxW(settings, Wide(error).c_str(), L"RIMES", MB_OK);
      return;
    }
    DestroyWindow(settings);
  } catch (...) {
    MessageBoxW(settings, L"请检查输入方案、字号和快捷键。", L"RIMES", MB_OK);
  }
}
LRESULT CALLBACK Window::SettingsProcedure(HWND hwnd, UINT message,
                                           WPARAM wparam, LPARAM lparam) {
  auto* self =
      reinterpret_cast<Window*>(GetWindowLongPtrW(hwnd, GWLP_USERDATA));
  if (message == WM_NCCREATE) {
    self = static_cast<Window*>(
        reinterpret_cast<CREATESTRUCTW*>(lparam)->lpCreateParams);
    SetWindowLongPtrW(hwnd, GWLP_USERDATA, reinterpret_cast<LONG_PTR>(self));
  }
  if (self) {
    if (message == WM_COMMAND) {
      if (LOWORD(wparam) == 1) self->Save();
      if (LOWORD(wparam) == 2) DestroyWindow(hwnd);
      return 0;
    }
    if (message == WM_DESTROY) {
      self->settings = nullptr;
      return 0;
    }
  }
  return DefWindowProcW(hwnd, message, wparam, lparam);
}
void Window::Copy() {
  auto snapshot = runtime.Snapshot();
  auto text = snapshot.value("result", std::string());
  if (text.empty()) text = snapshot.value("source", std::string());
  if (text.empty()) return;
  auto wide = Wide(text);
  if (!OpenClipboard(window)) return;
  HGLOBAL memory =
      GlobalAlloc(GMEM_MOVEABLE, (wide.size() + 1) * sizeof(wchar_t));
  if (memory) {
    auto* data = GlobalLock(memory);
    if (data) {
      memcpy(data, wide.c_str(), (wide.size() + 1) * sizeof(wchar_t));
      GlobalUnlock(memory);
      EmptyClipboard();
      if (!SetClipboardData(CF_UNICODETEXT, memory)) GlobalFree(memory);
    } else
      GlobalFree(memory);
  }
  CloseClipboard();
}
void Window::Paste() {
  if (!OpenClipboard(window)) return;
  auto memory = GetClipboardData(CF_UNICODETEXT);
  std::wstring text;
  if (memory) {
    const auto bytes = GlobalSize(memory);
    if (bytes <= Model::kLimit * 2) {
      auto* data = static_cast<const wchar_t*>(GlobalLock(memory));
      if (data) {
        const auto count = wcsnlen(data, bytes / sizeof(wchar_t));
        if (count < bytes / sizeof(wchar_t)) text.assign(data, count);
        GlobalUnlock(memory);
      }
    }
  }
  CloseClipboard();
  if (!text.empty()) runtime.Paste(Utf8(text));
}
void Window::Action(int index) {
  switch (index) {
    case 0:
      runtime.Toggle();
      break;
    case 1:
      Paste();
      break;
    case 2:
      runtime.Generate(false);
      break;
    case 3:
      runtime.Generate(true);
      break;
    case 4:
      runtime.Cancel();
      break;
    case 5:
      runtime.Send(false);
      break;
    case 6:
      runtime.Send(true);
      break;
    case 7:
      Copy();
      break;
    case 8:
      OpenSettings();
      break;
    case 9:
      runtime.Close();
      break;
    default:
      break;
  }
}
void Window::Update() {
  if (runtime.Stopping()) {
    PostMessageW(window, WM_CLOSE, 0, 0);
    return;
  }
  auto state = runtime.Snapshot();
  ShowWindow(window,
             state.value("visible", false) ? SW_SHOWNOACTIVATE : SW_HIDE);
  InvalidateRect(window, nullptr, FALSE);
}
void Window::Track(const core::Json& blocks, const std::string& tail,
                   D2D1_RECT_F box, int lane) {
  render->PushAxisAlignedClip(box, D2D1_ANTIALIAS_MODE_PER_PRIMITIVE);
  float y = box.top - scroll[lane];
  auto item = [&](std::string value, bool streaming) {
    auto text = Wide(value);
    if (text.size() > 4096) {
      std::size_t end = 4096;
      if (text[end - 1] >= 0xd800 && text[end - 1] <= 0xdbff) --end;
      text = text.substr(0, end) + L"…";
    }
    IDWriteTextLayout* layout = nullptr;
    if (FAILED(write->CreateTextLayout(
            text.c_str(), static_cast<UINT32>(text.size()), format,
            box.right - box.left - 24, 180, &layout)))
      return;
    DWRITE_TEXT_METRICS metrics{};
    layout->GetMetrics(&metrics);
    const float height = (std::clamp)(metrics.height + 20, 42.0f, 200.0f);
    if (y + height >= box.top && y < box.bottom) {
      auto rect = D2D1::RectF(box.left, y, box.right, y + height);
      brush->SetColor(D2D1::ColorF(streaming ? 0.89f : 1.0f, 0.97f, 0.98f));
      render->FillRoundedRectangle(D2D1::RoundedRect(rect, 6, 6), brush);
      brush->SetColor(D2D1::ColorF(lane ? 0.05f : 0.12f, 0.26f, 0.3f));
      render->DrawTextLayout(D2D1::Point2F(box.left + 12, y + 10), layout,
                             brush, D2D1_DRAW_TEXT_OPTIONS_CLIP);
    }
    layout->Release();
    y += height + 8;
  };
  for (const auto& block : blocks)
    item(block.value("text", std::string()), false);
  if (!tail.empty()) item(tail, true);
  if (y < box.bottom && scroll[lane] > 0)
    scroll[lane] = (std::max)(0.0f, scroll[lane] - (box.bottom - y));
  render->PopAxisAlignedClip();
}
void Window::Paint() {
  PAINTSTRUCT paint{};
  BeginPaint(window, &paint);
  RECT rect{};
  GetClientRect(window, &rect);
  if (!render) {
    factory->CreateHwndRenderTarget(
        D2D1::RenderTargetProperties(),
        D2D1::HwndRenderTargetProperties(
            window, D2D1::SizeU(static_cast<UINT32>(rect.right),
                                static_cast<UINT32>(rect.bottom))),
        &render);
    if (render) {
      const auto dpi = static_cast<float>(GetDpiForWindow(window));
      render->SetDpi(dpi, dpi);
    }
    if (render)
      render->CreateSolidColorBrush(D2D1::ColorF(0.12f, 0.17f, 0.2f), &brush);
  }
  if (render && brush && format) {
    const auto dpi = static_cast<float>(GetDpiForWindow(window)) / 96.0f;
    const float width = static_cast<float>(rect.right) / dpi;
    render->BeginDraw();
    render->Clear(D2D1::ColorF(0.96f, 0.97f, 0.98f));
    for (int i = 0; i < 10; ++i) {
      const float x = 8 + static_cast<float>(i) * (width - 16) / 10;
      auto box = D2D1::RectF(x, 9, x + (width - 16) / 10 - 4, 40);
      brush->SetColor(D2D1::ColorF(0.88f, 0.92f, 0.93f));
      render->FillRoundedRectangle(D2D1::RoundedRect(box, 5, 5), brush);
      Draw(kActions[i], D2D1::RectF(x + 5, 14, box.right, 40),
           D2D1::ColorF(0.1f, 0.25f, 0.29f));
    }
    const float height = static_cast<float>(rect.bottom) / dpi;
    auto state = runtime.Snapshot();
    Draw(L"原文", D2D1::RectF(14, 53, width / 2 - 10, 78),
         D2D1::ColorF(0.4f, 0.47f, 0.5f));
    Draw(state.value("busy", false) ? L"结果 · 正在接收" : L"结果",
         D2D1::RectF(width / 2 + 8, 53, width - 14, 78),
         D2D1::ColorF(0.4f, 0.47f, 0.5f));
    Track(state["source_blocks"], state.value("preedit", std::string()),
          D2D1::RectF(14, 83, width / 2 - 10, height - 46), 0);
    Track(state["result_blocks"], state.value("preview", std::string()),
          D2D1::RectF(width / 2 + 8, 83, width - 14, height - 46), 1);
    Draw(Wide(state.value("status", std::string())),
         D2D1::RectF(14, height - 34, width - 14, height - 4),
         D2D1::ColorF(0.36f, 0.41f, 0.46f));
    if (render->EndDraw() == D2DERR_RECREATE_TARGET) {
      brush->Release();
      brush = nullptr;
      render->Release();
      render = nullptr;
    }
  }
  EndPaint(window, &paint);
}
LRESULT CALLBACK Window::Procedure(HWND hwnd, UINT message, WPARAM wparam,
                                   LPARAM lparam) {
  auto* self =
      reinterpret_cast<Window*>(GetWindowLongPtrW(hwnd, GWLP_USERDATA));
  if (message == WM_NCCREATE) {
    self = static_cast<Window*>(
        reinterpret_cast<CREATESTRUCTW*>(lparam)->lpCreateParams);
    SetWindowLongPtrW(hwnd, GWLP_USERDATA, reinterpret_cast<LONG_PTR>(self));
    self->window = hwnd;
  }
  if (!self) return DefWindowProcW(hwnd, message, wparam, lparam);
  try {
    switch (message) {
      case WM_MOUSEACTIVATE:
        return MA_NOACTIVATE;
      case WM_PAINT:
        self->Paint();
        return 0;
      case WM_ERASEBKGND:
        return 1;
      case WM_HOTKEY:
        if (wparam == 1) self->runtime.Toggle();
        return 0;
      case kChanged:
        self->Update();
        return 0;
      case WM_TIMER:
        self->runtime.Tick();
        return 0;
      case WM_DPICHANGED: {
        if (self->render)
          self->render->SetDpi(static_cast<float>(HIWORD(wparam)),
                               static_cast<float>(HIWORD(wparam)));
        auto* rect = reinterpret_cast<RECT*>(lparam);
        SetWindowPos(hwnd, nullptr, rect->left, rect->top,
                     rect->right - rect->left, rect->bottom - rect->top,
                     SWP_NOZORDER | SWP_NOACTIVATE);
        return 0;
      }
      case WM_MOUSEWHEEL: {
        POINT point{static_cast<short>(LOWORD(lparam)),
                    static_cast<short>(HIWORD(lparam))};
        ScreenToClient(hwnd, &point);
        RECT rect{};
        GetClientRect(hwnd, &rect);
        const int lane = point.x < rect.right / 2 ? 0 : 1;
        self->scroll[lane] =
            (std::max)(0.0f,
                       self->scroll[lane] -
                           static_cast<float>(GET_WHEEL_DELTA_WPARAM(wparam)) /
                               WHEEL_DELTA * 60);
        InvalidateRect(hwnd, nullptr, FALSE);
        return 0;
      }
      case WM_GETMINMAXINFO: {
        auto* limits = reinterpret_cast<MINMAXINFO*>(lparam);
        const int dpi = static_cast<int>(GetDpiForWindow(hwnd));
        limits->ptMinTrackSize = {MulDiv(780, dpi, 96), MulDiv(300, dpi, 96)};
        return 0;
      }
      case WM_SIZE:
        if (self->render)
          self->render->Resize(D2D1::SizeU(LOWORD(lparam), HIWORD(lparam)));
        return 0;
      case WM_LBUTTONUP: {
        RECT rect{};
        GetClientRect(hwnd, &rect);
        const float dpi = static_cast<float>(GetDpiForWindow(hwnd)) / 96.0f;
        const float x = static_cast<float>(static_cast<short>(LOWORD(lparam))) /
                        dpi,
                    y = static_cast<float>(static_cast<short>(HIWORD(lparam))) /
                        dpi;
        const float width = static_cast<float>(rect.right) / dpi;
        if (y >= 9 && y <= 40 && x >= 8)
          self->Action(static_cast<int>((x - 8) / ((width - 16) / 10)));
        return 0;
      }
      case WM_NCHITTEST: {
        auto result = DefWindowProcW(hwnd, message, wparam, lparam);
        POINT point{static_cast<short>(LOWORD(lparam)),
                    static_cast<short>(HIWORD(lparam))};
        ScreenToClient(hwnd, &point);
        RECT client{};
        GetClientRect(hwnd, &client);
        if (result == HTCLIENT &&
            point.y > client.bottom -
                          36 * static_cast<int>(GetDpiForWindow(hwnd)) / 96)
          return HTCAPTION;
        return result;
      }
      case kTray:
        if (lparam == WM_LBUTTONUP) {
          self->runtime.Toggle();
          return 0;
        }
        if (lparam == WM_RBUTTONUP) {
          HMENU menu = CreatePopupMenu();
          AppendMenuW(menu, MF_STRING, kToggle, L"打开 / 绑定 Buffer");
          AppendMenuW(menu, MF_STRING, kSettings, L"设置");
          AppendMenuW(menu, MF_STRING, kDeploy, L"重新部署词库");
          std::wstring startup_command;
          bool startup_enabled = false;
          broker::QueryBrokerAutostart(&startup_command, &startup_enabled);
          AppendMenuW(menu, MF_STRING | (startup_enabled ? MF_CHECKED : 0),
                      kStartup, L"登录时启动");
          AppendMenuW(menu, MF_STRING, kAbout, L"版本与诊断");
          AppendMenuW(menu, MF_SEPARATOR, 0, nullptr);
          AppendMenuW(menu, MF_STRING, kExit, L"退出");
          POINT cursor{};
          GetCursorPos(&cursor);
          SetForegroundWindow(hwnd);
          const auto command =
              TrackPopupMenu(menu, TPM_RETURNCMD | TPM_NONOTIFY, cursor.x,
                             cursor.y, 0, hwnd, nullptr);
          DestroyMenu(menu);
          PostMessageW(hwnd, WM_COMMAND, command, 0);
          return 0;
        }
        break;
      case WM_COMMAND:
        switch (LOWORD(wparam)) {
          case kToggle:
            self->runtime.Toggle();
            break;
          case kSettings:
            self->OpenSettings();
            break;
          case kDeploy:
            if (!self->deploying.exchange(true)) {
              self->runtime.Protect();
              self->maintenance = std::jthread([self] {
                self->deploy();
                self->deploying.store(false);
              });
            }
            break;
          case kStartup: {
            broker::DefaultBrokerPaths paths;
            std::wstring error, command;
            bool enabled = false;
            if (broker::QueryBrokerAutostart(&command, &enabled, &error)) {
              if (enabled)
                broker::RemoveBrokerAutostart(&error);
              else if (broker::ResolveDefaultBrokerPaths(&paths, &error))
                broker::InstallBrokerAutostart(paths.broker_exe.wstring(),
                                               &error);
            }
            if (!error.empty())
              MessageBoxW(hwnd, error.c_str(), L"RIMES", MB_OK);
            break;
          }
          case kAbout: {
            const auto about_text =
                L"RIMES Windows 0.2.0 内部预览\nCommit: " +
                Wide(RIMES_BUILD_COMMIT) +
                L"\n协议 v2 · x64 Broker / x64+x86 "
                L"TSF\n词库：%APPDATA%\\RIMES\n设置与阶段日志：%LOCALAPPDATA%"
                L"\\RIMES\nBuffer 正文不会在重启后恢复。";
            MessageBoxW(hwnd, about_text.c_str(), L"版本与诊断", MB_OK);
            break;
          }
          case kExit:
            DestroyWindow(hwnd);
            break;
          default:
            break;
        }
        return 0;
      case WM_WTSSESSION_CHANGE:
        if (wparam == WTS_SESSION_LOCK || wparam == WTS_SESSION_LOGOFF ||
            wparam == WTS_REMOTE_DISCONNECT || wparam == WTS_CONSOLE_DISCONNECT)
          self->runtime.Protect();
        return 0;
      case WM_POWERBROADCAST:
        if (wparam == PBT_APMSUSPEND) self->runtime.Protect();
        return TRUE;
      case WM_CLOSE:
        DestroyWindow(hwnd);
        return 0;
      case WM_DESTROY:
        self->notification.store(nullptr);
        self->runtime.SetNotify({});
        UnregisterHotKey(hwnd, 1);
        WTSUnRegisterSessionNotification(hwnd);
        Shell_NotifyIconW(NIM_DELETE, &self->tray);
        if (self->settings) DestroyWindow(self->settings);
        self->runtime.Stop();
        self->stop();
        PostQuitMessage(0);
        return 0;
      default:
        break;
    }
  } catch (...) {
    self->runtime.Close();
  }
  return DefWindowProcW(hwnd, message, wparam, lparam);
}
}  // namespace
void RunWindow(Runtime& runtime, const std::function<void()>& stop,
               const std::function<void()>& deploy) {
  CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED);
  struct Apartment {
    ~Apartment() { CoUninitialize(); }
  } apartment;
  SetThreadDpiAwarenessContext(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2);
  Window ui(runtime, stop, deploy);
  if (FAILED(
          D2D1CreateFactory(D2D1_FACTORY_TYPE_SINGLE_THREADED, &ui.factory)) ||
      FAILED(DWriteCreateFactory(DWRITE_FACTORY_TYPE_SHARED,
                                 __uuidof(IDWriteFactory),
                                 reinterpret_cast<IUnknown**>(&ui.write)))) {
    runtime.Stop();
    stop();
    return;
  }
  ui.write->CreateTextFormat(
      L"Microsoft YaHei UI", nullptr, DWRITE_FONT_WEIGHT_NORMAL,
      DWRITE_FONT_STYLE_NORMAL, DWRITE_FONT_STRETCH_NORMAL, 14, L"zh-CN",
      &ui.format);
  WNDCLASSW wc{};
  wc.lpfnWndProc = Window::Procedure;
  wc.hInstance = GetModuleHandleW(nullptr);
  wc.lpszClassName = L"Rimes.Workbench";
  wc.hCursor = LoadCursorW(nullptr, IDC_ARROW);
  RegisterClassW(&wc);
  RECT area{};
  SystemParametersInfoW(SPI_GETWORKAREA, 0, &area, 0);
  HWND window = CreateWindowExW(
      WS_EX_NOACTIVATE | WS_EX_TOOLWINDOW | WS_EX_TOPMOST, wc.lpszClassName,
      L"RIMES Buffer", WS_POPUP | WS_THICKFRAME, area.left + 40,
      area.bottom - 500, 900, 440, nullptr, nullptr, wc.hInstance, &ui);
  if (!window) {
    runtime.Stop();
    stop();
    return;
  }
  ui.notification.store(window);
  runtime.SetNotify([&ui] {
    auto handle = ui.notification.load();
    if (handle) PostMessageW(handle, kChanged, 0, 0);
  });
  auto config = runtime.Configuration();
  RegisterHotKey(window, 1, config.hotkey_modifiers | MOD_NOREPEAT,
                 config.hotkey_key);
  ui.tray.cbSize = sizeof(ui.tray);
  ui.tray.hWnd = window;
  ui.tray.uID = 1;
  ui.tray.uFlags = NIF_ICON | NIF_MESSAGE | NIF_TIP;
  ui.tray.uCallbackMessage = kTray;
  ui.tray.hIcon = LoadIconW(nullptr, IDI_APPLICATION);
  wcscpy_s(ui.tray.szTip, L"RIMES 输入法与 Buffer");
  Shell_NotifyIconW(NIM_ADD, &ui.tray);
  WTSRegisterSessionNotification(window, NOTIFY_FOR_THIS_SESSION);
  SetTimer(window, 1, 100, nullptr);
  MSG message{};
  while (GetMessageW(&message, nullptr, 0, 0) > 0) {
    if (ui.settings && IsDialogMessageW(ui.settings, &message)) continue;
    TranslateMessage(&message);
    DispatchMessageW(&message);
  }
}
}  // namespace rimes::windows::workbench
