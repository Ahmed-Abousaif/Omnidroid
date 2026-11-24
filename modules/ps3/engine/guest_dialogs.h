#pragma once
#include "host_requests.h"
#include <memory>

class MsgDialogBase;
class OskDialogBase;
class SaveDialogBase;

namespace omnidroid::ps3 {
std::shared_ptr<MsgDialogBase> make_message_dialog(std::shared_ptr<host_requests> host);
std::shared_ptr<OskDialogBase> make_keyboard_dialog(std::shared_ptr<host_requests> host);
std::unique_ptr<SaveDialogBase> make_savedata_dialog(std::shared_ptr<host_requests> host);
}
