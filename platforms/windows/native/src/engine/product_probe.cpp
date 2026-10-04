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
  if (!engine.Configure(session, "my_combo", false, false, false, &error)) {
    std::cerr << "Configure failed my_combo: " << error; return 1;
  }
  EngineSnapshot chord;
  constexpr int released = 1 << 30;
  for (const char key : {'d', 'v', 'i'}) {
    if (!engine.ProcessKey(session, key, 0, &chord, &error) || !chord.commit_text.empty()) {
      std::cerr << "Chord key-down committed prematurely"; return 1;
    }
  }
  for (const char key : {'v', 'd', 'i'}) {
    if (!engine.ProcessKey(session, key, released, &chord, &error) || !chord.commit_text.empty()) {
      std::cerr << "Chord release failed or bypassed selection"; return 1;
    }
    if (key != 'i' && !chord.candidates.empty()) {
      std::cerr << "Chord resolved before the final key-up"; return 1;
    }
  }
  if (chord.candidates.empty() || chord.candidates.front().text != "你") {
    std::cerr << "Chord d+v+i did not resolve to ni / 你"; return 1;
  }
  if (!engine.ProcessKey(session, ' ', 0, &chord, &error) || chord.commit_text != "你") {
    std::cerr << "Chord candidate failed to commit exactly once"; return 1;
  }
  if (!engine.ProcessKey(session, ' ', released, &chord, &error) || !chord.commit_text.empty()) {
    std::cerr << "Chord space release duplicated commit"; return 1;
  }
  std::cout << "Product scheme passed: my_combo chord down/up and commit\n";
  engine.DestroySession(session);
  return 0;
}
