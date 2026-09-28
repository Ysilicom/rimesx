#pragma once

#include <string>

namespace rimes::windows::broker {

bool InstallBrokerAutostart(const std::wstring& broker_exe,
                            std::wstring* error = nullptr) noexcept;
bool RemoveBrokerAutostart(std::wstring* error = nullptr) noexcept;
bool QueryBrokerAutostart(std::wstring* command,
                          bool* installed,
                          std::wstring* error = nullptr) noexcept;

}  // namespace rimes::windows::broker
