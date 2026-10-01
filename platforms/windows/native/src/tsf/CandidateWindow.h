#pragma once

#include <Windows.h>

#include <cstdint>
#include <functional>
#include <string>
#include <vector>

namespace rimes::windows::tsf {

struct CandidateItem {
  std::wstring label;
  std::wstring text;
  std::wstring comment;
};

struct CandidateSnapshot {
  bool visible = false;
  std::uint16_t highlighted = 0xffff;
  std::uint16_t page_start = 0;
  std::uint16_t page_size = 0;
  RECT window_rect{};
  RECT caret_rect{};
  std::wstring composition;
  std::vector<CandidateItem> items;
};

class CandidateWindow {
 public:
  CandidateWindow() noexcept = default;
  ~CandidateWindow();

  CandidateWindow(const CandidateWindow&) = delete;
  CandidateWindow& operator=(const CandidateWindow&) = delete;

  void Update(const CandidateSnapshot& snapshot) noexcept;
  void Hide() noexcept;
  void SetSelect(std::function<void(std::size_t)> select) {
    select_ = std::move(select);
  }
  void SetFont(unsigned size) { font_size_ = size; }
  [[nodiscard]] CandidateSnapshot snapshot() const noexcept;

  static bool GetLastSnapshot(CandidateSnapshot* snapshot) noexcept;

 private:
  bool EnsureWindow() noexcept;
  void LayoutAndShow(const CandidateSnapshot& snapshot) noexcept;
  void Paint(HDC device) const noexcept;

  static LRESULT CALLBACK WindowProcedure(HWND window, UINT message,
                                          WPARAM wparam, LPARAM lparam);
  static void PublishSnapshot(const CandidateSnapshot& snapshot) noexcept;

  std::function<void(std::size_t)> select_;
  unsigned font_size_ = 16;
  HWND window_ = nullptr;
  CandidateSnapshot snapshot_{};
};

}  // namespace rimes::windows::tsf
