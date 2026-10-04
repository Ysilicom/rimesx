#pragma once

#include <Windows.h>

namespace rimes::windows::tsf::module {

void SetInstance(HINSTANCE instance) noexcept;
[[nodiscard]] HINSTANCE Instance() noexcept;

void AddObject() noexcept;
void ReleaseObject() noexcept;
void AddServerLock() noexcept;
void ReleaseServerLock() noexcept;
HRESULT CanUnloadNow() noexcept;

}  // namespace rimes::windows::tsf::module
