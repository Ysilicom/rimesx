#pragma once

// In-process fake host; no desktop input, installed IME or user data involved.
namespace {
class FocusProbeBroker final : public rimes::windows::tsf::BrokerClient {
 public:
  std::uint64_t context = 0, connection = 1;
  bool connected = true;
  void BeginConnect() noexcept override {}
  void Disconnect() noexcept override { context = 0; }
  bool SetContext(std::uint64_t value) noexcept override {
    if (!connected) return false;
    context = value;
    return true;
  }
  void SetNotificationWindow(HWND) noexcept override {}
  std::optional<rimes::windows::core::Json> TakeNotification() override {
    return {};
  }
  bool Control(rimes::windows::core::Json) noexcept override { return true; }
  bool Capturing() const noexcept override { return false; }
  std::uint64_t ConnectionGeneration() const noexcept override {
    return connection;
  }
  bool IsConnected() const noexcept override { return connected; }
  rimes::windows::tsf::BrokerKeyResult HandleKey(
      const rimes::windows::tsf::BrokerKeyEvent&,
      rimes::windows::tsf::BrokerInputState*) noexcept override {
    return rimes::windows::tsf::BrokerKeyResult::kPassThrough;
  }
};

void CheckFocusRestoration() {
  using namespace rimes::windows::e2e;
  using rimes::windows::tsf::TextService;
  FakeDocument document;
  auto* context = new FakeContext(&document);
  auto* manager = new FakeDocumentMgr(context);
  auto* thread = new FakeThreadMgr();
  thread->SetFocus(manager);
  auto broker = std::make_unique<FocusProbeBroker>();
  auto* probe = broker.get();
  auto* service = new TextService(std::move(broker));
  Expect(SUCCEEDED(service->Activate(thread, 1)), "focus probe activates");
  service->OnSetFocus(TRUE);
  const auto first = probe->context;
  Expect(first != 0, "foreground entry publishes target before any key");
  service->OnSetFocus(FALSE);
  Expect(probe->context == 0, "foreground loss revokes old target");
  service->OnSetFocus(TRUE);
  Expect(probe->context > first,
         "same document return publishes a fresh target without typing");
  const auto resumed = probe->context;
  service->OnSetFocus(TRUE);
  Expect(probe->context == resumed, "duplicate foreground event is stable");
  service->OnSetFocus(FALSE);
  probe->connected = false;
  service->OnSetFocus(TRUE);
  Expect(probe->context == 0, "disconnected foreground has no broker target");
  probe->connected = true;
  ++probe->connection;
  service->OnSetFocus(TRUE);
  Expect(probe->context > resumed,
         "connection-ready focus refresh binds without replaying a key");
  context->read_only = true;
  service->OnSetFocus(TRUE);
  Expect(probe->context == 0, "read-only field cannot regain capture authority");
  context->read_only = false;
  thread->SetFocus(nullptr);
  service->OnSetFocus(TRUE);
  Expect(probe->context == 0, "empty focus cannot revive saved target");
  service->Deactivate();
  service->Release();

  thread->SetFocus(manager);
  broker = std::make_unique<FocusProbeBroker>();
  probe = broker.get();
  service = new TextService(std::move(broker));
  service->ActivateEx(thread, 1, TF_TMAE_SECUREMODE);
  service->OnSetFocus(TRUE);
  Expect(probe->context == 0, "secure activation never publishes a target");
  service->Deactivate();
  service->Release();
  thread->Release();
  manager->Release();
  context->Release();
}
}  // namespace
