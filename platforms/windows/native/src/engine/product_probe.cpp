#include <filesystem>
#include <iostream>
#include <vector>

#include "rime_engine.hpp"
using namespace rimes::windows::engine;
int wmain(int argc, wchar_t** argv) {
  if (argc != 5) return 2;
  RimeEngineOptions options;
  options.dll_path = argv[1];
  options.shared_data_dir = argv[2];
  options.user_data_dir = argv[3];
  options.log_dir = argv[4];
  options.full_maintenance_check = true;
  std::filesystem::create_directories(options.user_data_dir);
  std::filesystem::create_directories(options.log_dir);
  RimeEngine engine;
  std::string error;
  if (!engine.Start(options, &error)) {
    std::cerr << error;
    return 1;
  }
  auto session = engine.CreateSession(&error);
  if (!session) return 1;
  struct Case {
    const char* schema;
    const char* keys;
    bool traditional;
    const char* expected;
  };
  const std::vector<Case> cases = {{"rime_ice", "nihao", false, "你好"},
                                   {"double_pinyin", "ni", false, "你"},
                                   {"double_pinyin_flypy", "ni", false, "你"},
                                   {"wubi86", "wq", false, "你"},
                                   {"english", "hello", false, "hello"},
                                   {"rime_ice", "han", true, "漢"},
                                   {"wubi86", "ic", true, "漢"}};
  for (const auto& item : cases) {
    if (!engine.Configure(session, item.schema, false, item.traditional, false,
                          &error)) {
      std::cerr << "Configure failed " << item.schema;
      return 1;
    }
    EngineSnapshot snapshot;
    std::string committed;
    for (const char* key = item.keys; *key; ++key) {
      if (!engine.ProcessKey(session, *key, 0, &snapshot, &error)) return 1;
      committed += snapshot.commit_text;
    }
    bool found = committed.find(item.expected) != std::string::npos;
    for (const auto& candidate : snapshot.candidates)
      if (candidate.text == item.expected) found = true;
    if (!found) {
      std::cerr << "Product candidate assertion failed: " << item.schema
                << " traditional=" << item.traditional
                << " candidate_count=" << snapshot.candidates.size() << '\n';
      return 1;
    }
    std::cout << "Product scheme passed: " << item.schema
              << " traditional=" << item.traditional << '\n';
  }
  engine.DestroySession(session);
  return 0;
}
