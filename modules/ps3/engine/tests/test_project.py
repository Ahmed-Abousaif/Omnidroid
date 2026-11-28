import importlib.util
from pathlib import Path
import unittest
import os
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("stage_project", ROOT / "stage_project.py")
project = importlib.util.module_from_spec(spec)
spec.loader.exec_module(project)


class ProjectTests(unittest.TestCase):
    @unittest.skipIf(os.name == 'nt' or not shutil.which('git'), 'Linux source builder uses symbolic links')
    def test_staging_keeps_buildfiles_and_git_metadata_without_application_target(self):
        with tempfile.TemporaryDirectory() as temporary:
            source, destination = Path(temporary) / 'source', Path(temporary) / 'staged'
            source.mkdir()
            subprocess.run(['git', 'init', '-q', str(source)], check=True)
            original = 'project(engine)\nif(ANDROID)\n    add_subdirectory(android)\nendif()\n'
            (source / 'CMakeLists.txt').write_text(original)
            (source / 'buildfiles/cmake').mkdir(parents=True)
            (source / 'buildfiles/cmake/host.cmake').write_text('# platform tooling')
            (source / 'build-android').mkdir()
            project.stage(source, destination, Path('/omnidroid/host'))
            self.assertTrue((destination / 'buildfiles/cmake/host.cmake').is_file())
            self.assertFalse((destination / 'build-android').exists())
            self.assertTrue((destination / '.git').is_dir())
            self.assertNotIn('add_subdirectory(android)', (destination / 'CMakeLists.txt').read_text())
            self.assertEqual(original, (source / 'CMakeLists.txt').read_text())

    def test_platform_host_replaces_application_target(self):
        original = 'project(engine)\nif(ANDROID)\n    add_subdirectory(android)\nendif()\n'
        adapted = project.project_cmake(original, Path('/omnidroid/host'))
        self.assertNotIn('add_subdirectory(android)', adapted)
        self.assertIn('add_subdirectory("/omnidroid/host"', adapted)

    def test_upstream_cmake_drift_does_not_silently_restore_frontend(self):
        with self.assertRaises(ValueError):
            project.project_cmake('project(changed)\n', Path('/omnidroid/host'))
