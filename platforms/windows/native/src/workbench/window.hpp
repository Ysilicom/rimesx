#pragma once
#include <functional>

#include "runtime.hpp"
namespace rimes::windows::workbench {
void RunWindow(Runtime& runtime, const std::function<void()>& stop,
               const std::function<void()>& deploy);
}
