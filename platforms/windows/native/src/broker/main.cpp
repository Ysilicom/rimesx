#include <iostream>
#include <memory>
#include <string>
#include <thread>

#include "../engine/rime_engine.hpp"
#include "../workbench/window.hpp"
#include "autostart.hpp"
#include "broker_connection.hpp"
#include "broker_options.hpp"
#include "default_paths.hpp"
#include "named_pipe_server.hpp"
#include "single_instance.hpp"
#include "win32_security.hpp"

namespace rimes::windows::broker {
namespace {

void PrintUsage() {
  std::wcout
      << L"RIMES Windows Broker 0.2.0-preview\n\n"
      << L"Usage:\n"
      << L"  RimesBroker --print-endpoint\n"
      << L"  RimesBroker --print-paths\n"
      << L"  RimesBroker --deploy-only\n"
      << L"  RimesBroker --install-autostart | --remove-autostart\n"
      << L"  RimesBroker [--once] [--rime-dll <absolute-path>]\n"
      << L"      [--shared-data-dir <absolute-path>]\n"
      << L"      [--user-data-dir <absolute-path>]\n"
      << L"      [--log-dir <absolute-path>] [--full-maintenance-check]\n\n"
      << L"  --once                  Serve one verified client, then exit.\n"
      << L"  --print-endpoint        Print this user's pipe name, then exit.\n"
      << L"  --print-paths           Print resolved engine paths, then exit.\n"
      << L"  --install-autostart     Register a current-user logon Run key.\n"
      << L"  --remove-autostart      Remove the current-user logon Run key.\n"
      << L"  --full-maintenance-check  Ask librime for a full maintenance "
         L"pass.\n"
      << L"  Missing engine paths default to %%LOCALAPPDATA%%\\RIMES and\n"
      << L"  %%APPDATA%%\\RIMES, or files next to this executable.\n";
}

}  // namespace
}  // namespace rimes::windows::broker

int wmain(const int argc, wchar_t** argv) {
  using namespace rimes::windows;
  using namespace rimes::windows::broker;

  BrokerOptions options;
  std::wstring error;
  if (!ParseBrokerOptions(argc, argv, &options, &error)) {
    std::wcerr << L"Invalid broker options: " << error << L'\n';
    PrintUsage();
    return 2;
  }
  if (options.show_help) {
    PrintUsage();
    return 0;
  }

  UserSecurityContext security;
  if (options.print_endpoint) {
    if (!security.Initialize(&error)) {
      std::wcerr << error << L'\n';
      return 3;
    }
    std::wcout << security.pipe_name() << L'\n';
    return 0;
  }
  if (options.remove_autostart) {
    if (!RemoveBrokerAutostart(&error)) {
      std::wcerr << L"Failed to remove broker autostart: " << error << L'\n';
      return 7;
    }
    std::wcout << L"Removed the current-user RIMES broker autostart entry.\n";
    return 0;
  }
  if (options.install_autostart) {
    DefaultBrokerPaths defaults;
    if (!ResolveDefaultBrokerPaths(&defaults, &error)) {
      std::wcerr << L"Failed to resolve the broker path: " << error << L'\n';
      return 7;
    }
    if (!InstallBrokerAutostart(defaults.broker_exe.wstring(), &error)) {
      std::wcerr << L"Failed to install broker autostart: " << error << L'\n';
      return 7;
    }
    std::wcout << L"Installed current-user autostart for "
               << defaults.broker_exe.wstring() << L'\n';
    return 0;
  }
  if (options.print_paths) {
    std::wcout << L"rime-dll=" << options.engine.dll_path.wstring() << L'\n'
               << L"shared-data-dir="
               << options.engine.shared_data_dir.wstring() << L'\n'
               << L"user-data-dir=" << options.engine.user_data_dir.wstring()
               << L'\n' << L"log-dir=" << options.engine.log_dir.wstring()
               << L'\n' << L"used-defaults="
               << (options.used_default_paths ? L"yes" : L"no") << L'\n';
    return 0;
  }

  DefaultBrokerPaths created_dirs;
  created_dirs.shared_data_dir = options.engine.shared_data_dir;
  created_dirs.user_data_dir = options.engine.user_data_dir;
  created_dirs.log_dir = options.engine.log_dir;
  if (!EnsureBrokerDataDirectories(created_dirs, &error)) {
    std::wcerr << L"Failed to create broker data directories: " << error
               << L'\n';
    return 5;
  }

  if (options.deploy_only) {
    options.engine.full_maintenance_check = true;
    engine::RimeEngine deploy;
    std::string failure;
    if (!deploy.Start(options.engine, &failure)) {
      std::cerr << "Deployment failed: " << failure << '\n';
      return 5;
    }
    deploy.Stop();
    std::cout << "Dictionary deployment finished. User data retained.\n";
    return 0;
  }
  if (!security.Initialize(&error)) {
    std::wcerr << error << L'\n';
    return 3;
  }
  SingleInstance instance;
  if (!instance.Acquire(security.mutex_name(), security.attributes(), &error)) {
    std::wcerr << L"Failed to acquire the broker mutex: " << error << L'\n';
    return 4;
  }
  if (instance.already_running()) {
    std::wcerr << L"The per-user RIMES broker is already running.\n";
    return 0;
  }

  engine::RimeEngine engine;
  std::string engine_error;
  if (!engine.Start(options.engine, &engine_error)) {
    std::cerr << "Failed to start the RIME engine: " << engine_error << '\n';
    return 5;
  }

  NamedPipeServer server(&security);
  workbench::Runtime runtime;
  const bool interactive = security.session_id() != 0;
  std::jthread ui;
  if (interactive && !options.serve_once)
    ui = std::jthread([&] {
      workbench::RunWindow(
          runtime, [&] { server.RequestStop(); },
          [&] { engine.RunMaintenance(true, nullptr); });
    });
  error.clear();
  const ServeResult result = server.ServeClients(
      [&security, &engine, &runtime, interactive](DWORD) {
        auto connection = std::make_shared<BrokerConnection>(
            security.session_id(), &engine, interactive ? &runtime : nullptr);
        return
            [connection](const core::Frame& request,
                         const DWORD client_process_id, core::Frame* response) {
              return connection->Handle(request, client_process_id, response);
            };
      },
      options.serve_once, &error);
  runtime.Stop();
  if (ui.joinable()) ui.join();
  if (result == ServeResult::kFatalError) {
    std::wcerr << L"Broker pipe failure: " << error << L'\n';
    return 6;
  }
  if (result == ServeResult::kClientRejected && !error.empty()) {
    std::wcerr << L"Rejected broker client: " << error << L'\n';
  }
  return 0;
}
