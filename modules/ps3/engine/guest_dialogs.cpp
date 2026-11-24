// Omnidroid's implementations of PS3 guest-system UI contracts. Rendering belongs
// to the Android host; these classes contain no emulator menu or overlay widgets.
#include "guest_dialogs.h"
#include "Emu/Cell/Modules/cellMsgDialog.h"
#include "Emu/Cell/Modules/cellOskDialog.h"
#include "Emu/Cell/Modules/cellSaveData.h"
#include "Utilities/StrUtil.h"
#include <algorithm>
#include <condition_variable>
#include <sstream>

namespace omnidroid::ps3 {
namespace {
std::string quoted(std::string_view input) {
    static const char hex[] = "0123456789abcdef";
    std::string result = "\"";
    for (unsigned char c : input) {
        if (c == '"' || c == '\\') { result += '\\'; result += c; }
        else if (c < 32) { result += "\\u00"; result += hex[c >> 4]; result += hex[c & 15]; }
        else result += c;
    }
    return result + '"';
}

class message final : public MsgDialogBase, public std::enable_shared_from_this<message> {
    std::shared_ptr<host_requests> host_;
    uint64_t token_ = 0;
public:
    explicit message(std::shared_ptr<host_requests> host) : host_(std::move(host)) {}
    ~message() override { host_->dismiss(token_); }
    void Create(const std::string& text, const std::string& title) override {
        state = MsgDialogState::Open;
        const auto weak = weak_from_this();
        token_ = host_->open(OMNI_PS3_MESSAGE,
            "{\"title\":" + quoted(title) + ",\"message\":" + quoted(text) +
            ",\"buttons\":" + std::to_string(type.button_type) +
            ",\"cancelable\":" + std::string(type.disable_cancel ? "false" : "true") +
            ",\"progressBars\":" + std::to_string(type.progress_bar_count) + "}",
            [weak](int32_t result, const std::string&) {
                if (auto self = weak.lock()) {
                    // cellMsgDialog's completion owns its Open -> Close CAS and
                    // must see Open to deliver the guest callback.
                    if (self->on_close) self->on_close(result == INT32_MIN ? CELL_MSGDIALOG_BUTTON_ESCAPE : result);
                    self->state = MsgDialogState::Close;
                }
            });
        if (!token_) {
            if (on_close) on_close(CELL_MSGDIALOG_BUTTON_ESCAPE);
            state = MsgDialogState::Close;
        }
    }
    void Close(bool) override { state = MsgDialogState::Close; host_->dismiss(token_); }
    void SetMsg(const std::string& text) override { host_->update(token_, "{\"message\":" + quoted(text) + "}"); }
    void ProgressBarSetMsg(u32 index, const std::string& text) override { progress(index, "message", quoted(text)); }
    void ProgressBarReset(u32 index) override { progress(index, "value", "0"); }
    void ProgressBarInc(u32 index, u32 delta) override { progress(index, "increment", std::to_string(delta)); }
    void ProgressBarSetValue(u32 index, u32 value) override { progress(index, "value", std::to_string(value)); }
    void ProgressBarSetLimit(u32 index, u32 limit) override { progress(index, "maximum", std::to_string(limit)); }
private:
    void progress(u32 index, const std::string& field, const std::string& value) {
        host_->update(token_, "{\"bar\":" + std::to_string(index) + "," + quoted(field) + ":" + value + "}");
    }
};

class keyboard final : public OskDialogBase, public std::enable_shared_from_this<keyboard> {
    std::shared_ptr<host_requests> host_;
    uint64_t token_ = 0;
    size_t limit_ = CELL_OSKDIALOG_STRING_SIZE - 1;
    std::mutex text_mutex_;
public:
    explicit keyboard(std::shared_ptr<host_requests> host) : host_(std::move(host)) {}
    ~keyboard() override { host_->dismiss(token_); }
    void Create(const osk_params& params) override {
        state = OskDialogState::Open;
        limit_ = std::min<size_t>(params.charlimit, osk_text.size() - 1);
        std::u16string initial;
        if (params.init_text) {
            const auto end = std::find(params.init_text, params.init_text + CELL_OSKDIALOG_STRING_SIZE, char16_t{});
            initial.assign(params.init_text, end);
        }
        store(initial);
        const auto weak = weak_from_this();
        token_ = host_->open(OMNI_PS3_KEYBOARD,
            "{\"title\":" + quoted(params.title) + ",\"message\":" + quoted(utf16_to_utf8(params.message)) +
            ",\"initial\":" + quoted(utf16_to_utf8(initial)) + ",\"maxLength\":" + std::to_string(limit_) +
            ",\"prohibitFlags\":" + std::to_string(params.prohibit_flags) +
            ",\"password\":" + std::string(params.panel_flag & CELL_OSKDIALOG_PANELMODE_PASSWORD ? "true" : "false") + "}",
            [weak](int32_t result, const std::string& text) {
                if (auto self = weak.lock()) {
                    if (result == CELL_OSKDIALOG_CLOSE_CONFIRM) self->store(utf8_to_utf16(text));
                    self->finish(result);
                }
            });
        if (!token_) finish(CELL_OSKDIALOG_CLOSE_CANCEL);
    }
    void Clear(bool all) override { if (all) SetText(u""); }
    void Insert(const std::u16string& text) override {
        std::u16string current;
        { std::lock_guard lock(text_mutex_); current = osk_text.data(); }
        SetText(current + text);
    }
    void SetText(const std::u16string& text) override {
        store(text);
        host_->update(token_, "{\"initial\":" + quoted(utf16_to_utf8(text)) + "}");
    }
    void Close(s32 status) override { host_->cancel(token_, status); }
private:
    void store(const std::u16string& text) {
        std::lock_guard lock(text_mutex_);
        osk_text.fill(0);
        auto length = std::min(limit_, text.size());
        // Do not split a UTF-16 surrogate pair at the guest's length limit.
        if (length && length < text.size() && text[length - 1] >= 0xd800 && text[length - 1] <= 0xdbff) --length;
        std::copy_n(text.begin(), length, osk_text.begin());
    }
    void finish(int32_t result) {
        if (result == INT32_MIN) result = CELL_OSKDIALOG_CLOSE_CANCEL;
        state = OskDialogState::Closed;
        osk_input_result = result == CELL_OSKDIALOG_CLOSE_CONFIRM ? CELL_OSKDIALOG_INPUT_FIELD_RESULT_OK : CELL_OSKDIALOG_INPUT_FIELD_RESULT_CANCELED;
        if (on_osk_close) on_osk_close(result);
    }
};

class savedata final : public SaveDialogBase {
    std::shared_ptr<host_requests> host_;
public:
    explicit savedata(std::shared_ptr<host_requests> host) : host_(std::move(host)) {}
    s32 ShowSaveDataList(const std::string&, std::vector<SaveDataEntry>& entries, s32 focused,
        u32 operation, vm::ptr<CellSaveDataListSet> list, bool) override {
        struct answer { std::mutex mutex; std::condition_variable changed; bool done = false; int32_t choice = -2; };
        auto result = std::make_shared<answer>();
        std::string json = "{\"operation\":" + std::to_string(operation) + ",\"focused\":" + std::to_string(focused) +
            ",\"allowNew\":" + std::string(list && list->newData ? "true" : "false") + ",\"entries\":[";
        for (size_t i = 0; i < entries.size(); ++i) {
            if (i) json += ',';
            json += "{\"title\":" + quoted(entries[i].title) + ",\"subtitle\":" + quoted(entries[i].subtitle) +
                ",\"details\":" + quoted(entries[i].details) + "}";
        }
        json += "]}";
        auto token = host_->open(OMNI_PS3_SAVEDATA, json, [result](int32_t choice, const std::string&) {
            { std::lock_guard lock(result->mutex); result->choice = choice; result->done = true; }
            result->changed.notify_one();
        });
        if (!token) return -2;
        std::unique_lock lock(result->mutex);
        result->changed.wait(lock, [&] { return result->done; });
        const auto choice = result->choice;
        if (choice >= 0 && static_cast<size_t>(choice) < entries.size()) return choice;
        if (choice == -1 && list && list->newData) return -1;
        return -2;
    }
};
}
std::shared_ptr<MsgDialogBase> make_message_dialog(std::shared_ptr<host_requests> host) { return std::make_shared<message>(std::move(host)); }
std::shared_ptr<OskDialogBase> make_keyboard_dialog(std::shared_ptr<host_requests> host) { return std::make_shared<keyboard>(std::move(host)); }
std::unique_ptr<SaveDialogBase> make_savedata_dialog(std::shared_ptr<host_requests> host) { return std::make_unique<savedata>(std::move(host)); }
}
