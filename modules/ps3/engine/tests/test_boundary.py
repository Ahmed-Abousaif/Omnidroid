import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("boundary", ROOT / "verify_boundary.py")
boundary = importlib.util.module_from_spec(spec)
spec.loader.exec_module(boundary)


class BoundaryTests(unittest.TestCase):
    def test_rejects_legacy_frontend_artifacts_even_when_menu_is_hidden(self):
        policy = json.loads((ROOT / "boundary.json").read_text())
        for name, content in [
            ("assets/ps3/Icons/ui/pad.png", b"image"),
            ("classes.dex", b"Lcom/armsx3/core/FirmwareRepository;"),
            ("lib/arm64-v8a/libarmsx3-core.so", b"ELF"),
            ("lib/arm64-v8a/libomnidroid-armsx3.so", b"_armsx3_initialize"),
            ("lib/arm64-v8a/libomnidroid-ps3-engine.so", b"ELF"),
        ]:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as directory:
                package = Path(directory) / "app.apk"
                with zipfile.ZipFile(package, "w") as archive:
                    archive.writestr(name, content)
                with self.assertRaises(ValueError):
                    boundary.verify_package(package, policy)

    def test_rejects_frontend_linked_into_engine(self):
        policy = json.loads((ROOT / "boundary.json").read_text())
        with tempfile.TemporaryDirectory() as directory:
            commands = Path(directory) / "compile_commands.json"
            commands.write_text(json.dumps([{
                "file": "/src/android/src/armsx3-core.cpp",
                "command": "c++ -o CMakeFiles/omnidroid-ps3-engine.dir/host.o"
            }]))
            with self.assertRaisesRegex(ValueError, "Frontend source"):
                boundary.verify_sources(commands, policy)

    def test_rejects_configured_upstream_application_target(self):
        policy = json.loads((ROOT / "boundary.json").read_text())
        with tempfile.TemporaryDirectory() as directory:
            commands = Path(directory) / "compile_commands.json"
            commands.write_text(json.dumps([
                {"file": "/src/android/src/armsx3-core.cpp", "command": "c++ -o CMakeFiles/armsx3-core.dir/app.o"},
                {"file": "/host/runtime.cpp", "command": "c++ -o CMakeFiles/omnidroid-ps3-engine.dir/runtime.o"},
            ]))
            with self.assertRaisesRegex(ValueError, "Frontend source"):
                boundary.verify_sources(commands, policy)


if __name__ == "__main__":
    unittest.main()
