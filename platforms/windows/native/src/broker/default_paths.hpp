#pragma once

#include <filesystem>
#include <string>

namespace rimes::windows::broker {

struct DefaultBrokerPaths {
  std::filesystem::path broker_exe;
  std::filesystem::path dll_path;
  std::filesystem::path shared_data_dir;
  std::filesystem::path user_data_dir;
  std::filesystem::path log_dir;
};

// Resolves the daily-use layout:
//   rime.dll          next to RimesBroker.exe, else %LOCALAPPDATA%\RIMES\runtime\rime.dll
//   shared data       <exe>\shared, else %LOCALAPPDATA%\RIMES\shared
//   user data         %APPDATA%\RIMES
//   logs              %LOCALAPPDATA%\RIMES\logs
// Paths are absolute even when the files have not been installed yet.
bool ResolveDefaultBrokerPaths(DefaultBrokerPaths* paths,
                               std::wstring* error = nullptr) noexcept;

bool EnsureBrokerDataDirectories(const DefaultBrokerPaths& paths,
                                 std::wstring* error = nullptr) noexcept;

}  // namespace rimes::windows::broker
