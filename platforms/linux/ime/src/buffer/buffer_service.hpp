#pragma once

#include <atomic>
#include <functional>
#include <memory>
#include <mutex>
#include <string>
#include <string_view>
#include <thread>
#include <vector>

#include <fcitx-utils/event.h>
#include <fcitx/event.h>
#include <fcitx/inputcontext.h>
#include <fcitx/instance.h>

#include "buffer_model.hpp"
#include "buffer_protocol.hpp"
#include "buffer_return.hpp"

namespace fcitx {

// Process-wide Default Buffer. The model lives in the Fcitx5 addon; the GTK
// workbench is a renderer that speaks length-prefixed JSON over a Unix socket.
class BufferService {
public:
    using PostFn = std::function<void(std::function<void()>)>;

    BufferService(Instance* instance, PostFn post);
    ~BufferService();

    BufferService(const BufferService&) = delete;
    BufferService& operator=(const BufferService&) = delete;

    rimes::buffer::BufferModel& model() { return model_; }
    const rimes::buffer::BufferModel& model() const { return model_; }

    bool OnCommit(InputContext* ic, std::string_view text);
    bool HandleEarlyKey(KeyEvent& event, bool composing);
    void AfterRime(InputContext* ic, KeyEvent& event, bool rime_handled,
                   bool composing, std::string_view preedit);
    void OnActivate(InputContext* ic);
    void OnDeactivate(InputContext* ic, bool switching_im);
    void OnPasswordField(InputContext* ic, bool password);

    void Toggle(InputContext* ic);
    void ShowAndCapture(InputContext* ic);
    void CloseAndPause();
    bool SendNext();
    bool SendAll();

    std::string SocketPath() const { return socket_path_; }
    void Publish();

private:
    struct Client {
        int fd = -1;
        std::string incoming;
    };

    static std::string TokenFor(InputContext* ic);
    static bool IsToggleHotkey(const KeyEvent& event);
    static bool IsReturnKey(const Key& key);
    static bool IsBackspaceKey(const Key& key);
    static bool IsPrintableAscii(const KeyEvent& event, char* out);

    InputContext* LiveTarget() const;
    void RefreshCaret(InputContext* ic);
    void ClearClientPreedit(InputContext* ic);
    void ApplyReturnAction(rimes::buffer::ReturnGesture::Action action, InputContext* ic,
                           bool composing);
    bool Deliver(bool all);
    void HandleCommand(const rimes::buffer::Command& command);
    void EnsureUi();
    void ReapUi();
    void StartSocket();
    void StopSocket();
    void SocketLoop();
    void AcceptClient();
    void ReadClient(Client* client);
    void CloseClient(int fd);
    void WriteAll(const std::string& payload);
    void ArmHoldTimer();
    void CancelHoldTimer();

    Instance* instance_;
    PostFn post_;
    rimes::buffer::BufferModel model_;
    rimes::buffer::ReturnGesture gesture_;
    std::string socket_path_;
    std::string dump_path_;
    int listen_fd_ = -1;
    std::vector<Client> clients_;
    std::recursive_mutex clients_mu_;
    std::thread socket_thread_;
    std::atomic<bool> running_{false};
    std::unique_ptr<EventSourceTime> hold_timer_;
    pid_t ui_pid_ = 0;
    bool auto_capture_ = false;
    bool headless_ = false;
    bool eat_return_until_release_ = false;
};

}  // namespace fcitx
