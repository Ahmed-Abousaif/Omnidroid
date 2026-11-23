#include "Emu/localized_string_id.h"
#include <string>

namespace omnidroid::ps3 {
// Guest-system wording only. Emulator settings/menu translations are not part
// of this host. The Android UI supplies its own localized labels and actions.
std::string guest_string(localized_string_id id, const char* argument) {
    using enum localized_string_id;
    const std::string detail = argument && *argument ? std::string("\n") + argument : "";
    switch (id) {
    case CELL_OSK_DIALOG_TITLE: return "Enter text";
    case CELL_OSK_DIALOG_BUSY: return "The PS3 keyboard is already open.";
    case CELL_SAVEDATA_SAVE_CONFIRMATION: return "Create new save data?";
    case CELL_SAVEDATA_DELETE_CONFIRMATION: return "Delete this save data?" + detail;
    case CELL_SAVEDATA_DELETE_SUCCESS: return "Save data deleted." + detail;
    case CELL_SAVEDATA_DELETE: return "Delete save data?" + detail;
    case CELL_SAVEDATA_LOAD: return "Load save data?" + detail;
    case CELL_SAVEDATA_SAVE: return "Save game data?" + detail;
    case CELL_SAVEDATA_OVERWRITE: return "Overwrite this save data?" + detail;
    case CELL_SAVEDATA_AUTOSAVE: return "Saving game data…";
    case CELL_SAVEDATA_AUTOLOAD: return "Loading game data…";
    case CELL_SAVEDATA_NEW_SAVED_DATA_TITLE: return "New save data";
    case CELL_SAVEDATA_NEW_SAVED_DATA_SUB_TITLE: return "Create a new game save";
    case CELL_SAVEDATA_NO_DATA:
    case CELL_SAVEDATA_CB_NO_DATA: return "No save data was found.";
    case CELL_SAVEDATA_CB_BROKEN: return "This save data is damaged and cannot be read.";
    case CELL_SAVEDATA_CB_FAILURE: return "The PS3 could not complete the save-data operation.";
    case CELL_SAVEDATA_CB_NO_SPACE: return "There is not enough storage for this save. Additional space required (KB):" + detail;
    case CELL_GAME_ERROR_NOSPACE:
    case CELL_GAME_ERROR_NOSPACE_EXIT:
    case CELL_HDD_GAME_CHECK_NOSPACE:
    case CELL_GAMEDATA_CHECK_NOSPACE: return "There is not enough storage for the game data." + detail;
    case CELL_GAME_ERROR_BROKEN_GAMEDATA:
    case CELL_GAME_ERROR_BROKEN_HDDGAME:
    case CELL_GAME_ERROR_BROKEN_EXIT_GAMEDATA:
    case CELL_GAME_ERROR_BROKEN_EXIT_HDDGAME:
    case CELL_GAME_DATA_EXIT_BROKEN:
    case CELL_HDD_GAME_EXIT_BROKEN:
    case CELL_HDD_GAME_CHECK_BROKEN:
    case CELL_GAMEDATA_CHECK_BROKEN: return "The installed game data is damaged. Reinstall the game data." + detail;
    case CELL_HDD_GAME_CHECK_NODATA:
    case CELL_GAMEDATA_CHECK_NODATA: return "The required game data is missing." + detail;
    case CELL_HDD_GAME_CHECK_INVALID:
    case CELL_GAMEDATA_CHECK_INVALID:
    case CELL_GAME_ERROR_DIR_NAME: return "The game data is invalid." + detail;
    case PROGRESS_DIALOG_PLEASE_WAIT: return "Please wait…";
    case PROGRESS_DIALOG_PROGRESS: return "Progress:";
    case PROGRESS_DIALOG_PROGRESS_ANALYZING: return "Analyzing game files…";
    case PROGRESS_DIALOG_REMAINING: return "remaining";
    case PROGRESS_DIALOG_DONE: return "done";
    case PROGRESS_DIALOG_FILE: return "file";
    case PROGRESS_DIALOG_MODULE: return "module";
    case PROGRESS_DIALOG_OF: return "of";
    case PROGRESS_DIALOG_STOPPING_PLEASE_WAIT: return "Stopping PS3 emulation…";
    case PROGRESS_DIALOG_SAVESTATE_PLEASE_WAIT: return "Saving PS3 state…";
    case PROGRESS_DIALOG_COMPILING_PPU_MODULES: return "Compiling PS3 modules…";
    case PROGRESS_DIALOG_SCANNING_PPU_EXECUTABLE: return "Scanning game executable…";
    case PROGRESS_DIALOG_ANALYZING_PPU_EXECUTABLE: return "Analyzing game executable…";
    case PROGRESS_DIALOG_SCANNING_PPU_MODULES: return "Scanning PS3 modules…";
    case PROGRESS_DIALOG_LOADING_PPU_MODULES: return "Loading PS3 modules…";
    case PROGRESS_DIALOG_LINKING_PPU_MODULES: return "Linking PS3 modules…";
    case PROGRESS_DIALOG_APPLYING_PPU_CODE: return "Preparing PS3 code…";
    case PROGRESS_DIALOG_BUILDING_SPU_CACHE: return "Building PS3 code cache…";
    default: return "PS3 system request" + detail;
    }
}
}
