#include <Windows.h>

#include "ModuleState.h"

BOOL WINAPI DllMain(HINSTANCE instance, DWORD reason, LPVOID) {
  if (reason == DLL_PROCESS_ATTACH) {
    rimes::windows::tsf::module::SetInstance(instance);
    DisableThreadLibraryCalls(instance);
  }
  return TRUE;
}
