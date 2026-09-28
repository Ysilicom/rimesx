#include "autostart.hpp"

#include <Windows.h>

#include <string_view>
#include <vector>

namespace rimes::windows::broker {
namespace {

constexpr wchar_t kRunKey[] =
    L"Software\\Microsoft\\Windows\\CurrentVersion\\Run";
constexpr wchar_t kValueName[] = L"RimesBroker";

void SetError(std::wstring* error, std::wstring_view message) noexcept {
  if (error == nullptr) {
    return;
  }
  try {
    error->assign(message);
  } catch (...) {
  }
}

}  // namespace

bool InstallBrokerAutostart(const std::wstring& broker_exe,
                            std::wstring* error) noexcept {
  if (broker_exe.empty() || broker_exe.front() == L'"') {
    SetError(error, L"autostart requires an absolute broker path");
    return false;
  }
  HKEY key = nullptr;
  const LSTATUS opened =
      RegCreateKeyExW(HKEY_CURRENT_USER, kRunKey, 0, nullptr,
                      REG_OPTION_NON_VOLATILE, KEY_SET_VALUE, nullptr, &key,
                      nullptr);
  if (opened != ERROR_SUCCESS || key == nullptr) {
    SetError(error, L"could not open the current-user Run key");
    return false;
  }
  const std::wstring command = L"\"" + broker_exe + L"\"";
  const DWORD bytes =
      static_cast<DWORD>((command.size() + 1) * sizeof(wchar_t));
  const LSTATUS written =
      RegSetValueExW(key, kValueName, 0, REG_SZ,
                     reinterpret_cast<const BYTE*>(command.c_str()), bytes);
  RegCloseKey(key);
  if (written != ERROR_SUCCESS) {
    SetError(error, L"could not write the broker autostart command");
    return false;
  }
  return true;
}

bool RemoveBrokerAutostart(std::wstring* error) noexcept {
  HKEY key = nullptr;
  const LSTATUS opened =
      RegOpenKeyExW(HKEY_CURRENT_USER, kRunKey, 0, KEY_SET_VALUE, &key);
  if (opened == ERROR_FILE_NOT_FOUND) {
    return true;
  }
  if (opened != ERROR_SUCCESS || key == nullptr) {
    SetError(error, L"could not open the current-user Run key");
    return false;
  }
  const LSTATUS removed = RegDeleteValueW(key, kValueName);
  RegCloseKey(key);
  if (removed != ERROR_SUCCESS && removed != ERROR_FILE_NOT_FOUND) {
    SetError(error, L"could not remove the broker autostart command");
    return false;
  }
  return true;
}

bool QueryBrokerAutostart(std::wstring* command,
                          bool* installed,
                          std::wstring* error) noexcept {
  if (installed == nullptr) {
    SetError(error, L"autostart query output is null");
    return false;
  }
  *installed = false;
  if (command != nullptr) {
    command->clear();
  }
  HKEY key = nullptr;
  const LSTATUS opened =
      RegOpenKeyExW(HKEY_CURRENT_USER, kRunKey, 0, KEY_QUERY_VALUE, &key);
  if (opened == ERROR_FILE_NOT_FOUND) {
    return true;
  }
  if (opened != ERROR_SUCCESS || key == nullptr) {
    SetError(error, L"could not open the current-user Run key");
    return false;
  }
  DWORD type = 0;
  DWORD bytes = 0;
  LSTATUS result =
      RegQueryValueExW(key, kValueName, nullptr, &type, nullptr, &bytes);
  if (result == ERROR_FILE_NOT_FOUND) {
    RegCloseKey(key);
    return true;
  }
  if (result != ERROR_SUCCESS || (type != REG_SZ && type != REG_EXPAND_SZ) ||
      bytes < sizeof(wchar_t)) {
    RegCloseKey(key);
    SetError(error, L"broker autostart value is malformed");
    return false;
  }
  std::vector<wchar_t> buffer(bytes / sizeof(wchar_t) + 1, L'\0');
  result = RegQueryValueExW(key, kValueName, nullptr, &type,
                            reinterpret_cast<BYTE*>(buffer.data()), &bytes);
  RegCloseKey(key);
  if (result != ERROR_SUCCESS) {
    SetError(error, L"could not read the broker autostart command");
    return false;
  }
  if (command != nullptr) {
    *command = buffer.data();
  }
  *installed = true;
  return true;
}

}  // namespace rimes::windows::broker
