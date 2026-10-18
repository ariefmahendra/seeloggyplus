import importlib.util
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

spec = importlib.util.spec_from_file_location("fonts", Path(__file__).with_name("setup_ci_fonts.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class FontsTest(unittest.TestCase):
    def test_native_check_loads_font_and_desktop_dependencies(self):
        loaded = []
        module.require_native_libraries(loaded.append)
        self.assertEqual({"libfontconfig.so.1", "libfreetype.so.6", "libpango-1.0.so.0", "libpangoft2-1.0.so.0", "libgtk-3.so.0"}, set(loaded))

    def test_native_check_reports_all_missing_libraries_before_tests_run(self):
        def load(name):
            if name in {"libpango-1.0.so.0", "libgtk-3.so.0"}:
                raise OSError(name + " missing")
        with self.assertRaisesRegex(RuntimeError, "libpango-1.0.so.0.*libgtk-3.so.0.*dnf install"):
            module.require_native_libraries(load)

    def test_font_config_uses_only_the_job_directory(self):
        with tempfile.TemporaryDirectory(prefix="ci fonts ") as directory:
            root = Path(directory)
            fonts, path = module.configure(root)
            config = ET.parse(path).getroot()
            self.assertEqual(str(fonts), config.find("dir").text)
            self.assertEqual(str(root / "cache"), config.find("cachedir").text)
            self.assertEqual({"sans-serif", "serif", "monospace"}, {alias.find("family").text for alias in config.findall("alias")})
            self.assertEqual((fonts, path), module.configure(root))


if __name__ == "__main__":
    unittest.main()
