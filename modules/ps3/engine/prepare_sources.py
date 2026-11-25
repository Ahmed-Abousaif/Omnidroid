"""Create the small platform source adaptations for the pinned engine build.

The upstream checkout stays untouched. Adaptations are explicit and fail if the
matching implementation changes, rather than silently compiling frontend code.
"""
from pathlib import Path
import argparse
import re


def write(path: Path, text: str) -> None:
    if not path.exists() or path.read_text(encoding="utf-8") != text:
        path.write_text(text, encoding="utf-8")


def prepare(source: Path, output: Path) -> None:
    original = (source / "rpcs3/Input/pad_thread.cpp").read_text(encoding="utf-8")
    pattern = r"void pad_thread::open_home_menu\(\)\s*\{.*?\n\}\s*(?=void pad_thread::set_target_window)"
    adapted, count = re.subn(pattern, "void pad_thread::open_home_menu() {}\n\n", original, flags=re.DOTALL)
    if count != 1:
        raise ValueError("Pinned pad_thread menu implementation changed")
    adapted = adapted.replace('#include "Emu/RSX/Overlays/HomeMenu/overlay_home_menu.h"\n', "")
    if "home_menu_dialog" in adapted:
        raise ValueError("Frontend menu dependency survived platform adaptation")
    output.mkdir(parents=True, exist_ok=True)
    write(output / "pad_thread.cpp", adapted)
    rsx = (source / "rpcs3/Emu/RSX/RSXThread.cpp").read_text(encoding="utf-8")
    if "g_cfg.misc.use_native_interface" not in rsx:
        raise ValueError("Pinned RSX native UI initialization changed")
    # A loaded legacy snapshot/config must not recreate the native UI manager.
    rsx = rsx.replace("g_cfg.misc.use_native_interface", "false /* Omnidroid owns guest UI */")
    write(output / "RSXThread.cpp", rsx)
    system = (source / "rpcs3/Emu/System.cpp").read_text(encoding="utf-8")
    system = system.replace('#include "Emu/RSX/Overlays/BigPicture/overlay_big_picture.h"\n', "")
    pattern = r"bool Emulator::BootBigPictureMode\(\)\s*\{.*?(?=game_boot_result Emulator::GetElfPathFromDir)"
    system, count = re.subn(pattern,
        "bool Emulator::BootBigPictureMode() { return false; }\n"
        "void Emulator::DeactivateBigPictureMode() const {}\n\n", system, flags=re.DOTALL)
    if count != 1:
        raise ValueError("Pinned shell boot implementation changed")
    system = system.replace("extern atomic_t<bool> g_big_picture_mode_active;", "")
    system = system.replace("!after_kill_callback && g_big_picture_mode_active.exchange(false)", "false")
    if "g_big_picture_mode_active" in system or "open_big_picture_mode" in system:
        raise ValueError("Frontend shell dependency survived engine adaptation")
    # Notify the host when the core rejects a capture without stopping the VM.
    if system.count("replay_held_resume();") != 2:
        raise ValueError("Pinned savestate failure paths changed")
    system = "namespace omnidroid::ps3 { void snapshot_failed(); }\n" + system.replace(
        "replay_held_resume();", "replay_held_resume();\n\t\t\t\tomnidroid::ps3::snapshot_failed();")
    write(output / "System.cpp", system)
    cpu = (source / "rpcs3/Emu/CPU/CPUThread.cpp").read_text(encoding="utf-8")
    marker = "if (!savable_threads)"
    if cpu.count(marker) != 1:
        raise ValueError("Pinned SPU snapshot lock guard changed")
    # Zero SPUs require no SPU locks. Still enter the existing suspend_all
    # critical section, which checks for thread creation/deletion races.
    cpu = cpu.replace(marker, "if (!savable_threads && !spu_list->empty())")
    write(output / "CPUThread.cpp", cpu)
    display = (source / "rpcs3/Emu/RSX/Core/RSXDisplayEngine.cpp").read_text(encoding="utf-8")
    marker = "rpcs3::utils::report_frame_work_ns((wall - idle_delta) * 1000);"
    if display.count(marker) != 1:
        raise ValueError("Pinned Android frame timing hook changed")
    display = '#include "omnidroid_performance.h"\n' + display.replace(marker, marker +
        "\n\t\t\t\t\t\tomnidroid::ps3::report_frame(wall * 1000, (wall - idle_delta) * 1000, s_tid);")
    write(output / "RSXDisplayEngine.cpp", display)
    # ARMSX3 also put app-only capture/VR hooks into the renderer. Remove the
    # capture implementation, rather than linking its application service or
    # supplying fake globals to make that service appear available.
    for backend in ("VK", "GL"):
        present = (source / f"rpcs3/Emu/RSX/{backend}/{backend}Present.cpp").read_text(encoding="utf-8")
        if present.count("g_cardboard_vr.load()") != 1:
            raise ValueError("Pinned presenter VR hook changed")
        present = present.replace("extern atomic_t<u32> g_cardboard_vr;", "")
        present = present.replace("g_cardboard_vr.load()", "0 /* No application VR mode */")
        if backend == "VK":
            marker = "namespace vk\n{\n\t// ARMSX3 remote play:"
            if present.count(marker) != 1:
                raise ValueError("Pinned presenter remote-play implementation changed")
            present = present.split(marker)[0]
            present = re.sub(r"^extern atomic_t<[^>]+> g_remote_play_\w+;\n", "", present, flags=re.MULTILINE)
            present = re.sub(r"^\tconst (?:u32 remote_play_max_size|bool remote_play_capture) = .*?;\n", "", present, flags=re.MULTILINE)
            present = present.replace("!remote_play_max_size && ", "")
            pattern = r"\tif \(image_to_flip && remote_play_capture\).*?(?=\t// WARNING: We have to do this here\.)"
            present, count = re.subn(pattern, "", present, flags=re.DOTALL)
            if count != 1 or "g_remote_play_" in present or "remote_play_max_size" in present:
                raise ValueError("Remote-play capture hook survived presenter adaptation")
            # The retained engine class declaration owns empty capture fields.
            # No capture resources can be created in this build.
            present += "\nVKGSRender::remote_capture_hardware::~remote_capture_hardware() = default;\n"
            present += "void VKGSRender::remote_capture_release(bool) {}\n"
        write(output / f"{backend}Present.cpp", present)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    prepare(args.source, args.output)
