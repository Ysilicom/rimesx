#include "default_paths.hpp"

#include <Windows.h>

#include <string_view>

namespace rimes::windows::broker {
namespace {

void SetError(std::wstring* error, std::wstring_view message) noexcept {
  if (error == nullptr) {
    return;
  }
  try {
    error->assign(message);
  } catch (...) {
  }
}

std::filesystem::path KnownFolder(const wchar_t* name) {
  wchar_t buffer[MAX_PATH]{};
  const DWORD length = GetEnvironmentVariableW(name, buffer, MAX_PATH);
  if (length == 0 || length >= MAX_PATH) {
    return {};
  }
  return std::filesystem::path(std::wstring(buffer, length));
}

std::filesystem::path CurrentImageDirectory() {
  wchar_t buffer[MAX_PATH]{};
  const DWORD length = GetModuleFileNameW(nullptr, buffer, MAX_PATH);
  if (length == 0 || length >= MAX_PATH) {
    return {};
  }
  return std::filesystem::path(std::wstring(buffer, length)).parent_path();
}

bool PathExists(const std::filesystem::path& path) noexcept {
  try {
    std::error_code error;
    return !path.empty() && std::filesystem::exists(path, error);
  } catch (...) {
    return false;
  }
}

}  // namespace

bool ResolveDefaultBrokerPaths(DefaultBrokerPaths* paths,
                               std::wstring* error) noexcept {
  if (paths == nullptr) {
    SetError(error, L"default path output is null");
    return false;
  }
  try {
    *paths = {};
    const std::filesystem::path exe_dir = CurrentImageDirectory();
    if (exe_dir.empty() || !exe_dir.is_absolute()) {
      SetError(error, L"could not resolve the broker executable directory");
      return false;
    }

    const std::filesystem::path local = KnownFolder(L"LOCALAPPDATA");
    const std::filesystem::path roaming = KnownFolder(L"APPDATA");
    if (local.empty() || roaming.empty()) {
      SetError(error, L"APPDATA or LOCALAPPDATA is unavailable");
      return false;
    }

    DefaultBrokerPaths resolved;
    resolved.broker_exe = exe_dir / L"RimesBroker.exe";
    const std::filesystem::path adjacent_dll = exe_dir / L"rime.dll";
    const std::filesystem::path roaming_dll =
        local / L"RIMES" / L"runtime" / L"rime.dll";
    resolved.dll_path = PathExists(adjacent_dll) ? adjacent_dll : roaming_dll;

    const std::filesystem::path adjacent_shared = exe_dir / L"shared";
    const std::filesystem::path roaming_shared = local / L"RIMES" / L"shared";
    resolved.shared_data_dir =
        PathExists(adjacent_shared) ? adjacent_shared : roaming_shared;
    resolved.user_data_dir = roaming / L"RIMES";
    resolved.log_dir = local / L"RIMES" / L"logs";

    if (!resolved.dll_path.is_absolute() ||
        !resolved.shared_data_dir.is_absolute() ||
        !resolved.user_data_dir.is_absolute() ||
        !resolved.log_dir.is_absolute()) {
      SetError(error, L"inferred broker paths were not absolute");
      return false;
    }
    *paths = std::move(resolved);
    return true;
  } catch (...) {
    SetError(error, L"exception while resolving default broker paths");
    return false;
  }
}

bool EnsureBrokerDataDirectories(const DefaultBrokerPaths& paths,
                                 std::wstring* error) noexcept {
  try {
    std::error_code filesystem_error;
    std::filesystem::create_directories(paths.user_data_dir, filesystem_error);
    if (filesystem_error) {
      SetError(error, L"could not create the user data directory");
      return false;
    }
    std::filesystem::create_directories(paths.log_dir, filesystem_error);
    if (filesystem_error) {
      SetError(error, L"could not create the log directory");
      return false;
    }
    std::filesystem::create_directories(paths.shared_data_dir, filesystem_error);
    if (filesystem_error) {
      SetError(error, L"could not create the shared data directory");
      return false;
    }
    return true;
  } catch (...) {
    SetError(error, L"exception while creating broker data directories");
    return false;
  }
}

}  // namespace rimes::windows::broker
