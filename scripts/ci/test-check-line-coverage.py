from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


class LineCoverageCheckTest(unittest.TestCase):
    def test_accepts_coverage_at_the_minimum(self):
        result = self.check('<counter type="LINE" covered="3" missed="1"/>')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("75.00% (3/4), minimum 75%", result.stdout)

    def test_rejects_coverage_below_the_minimum_even_when_display_rounds_up(self):
        result = self.check('<counter type="LINE" covered="149999" missed="50001"/>')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("75.00%", result.stdout)
        self.assertIn("below the minimum", result.stderr)

    def test_requires_a_report_level_line_counter(self):
        result = self.check('<package name="example"><counter type="LINE" covered="4" missed="0"/></package>')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("missing report-level LINE counter", result.stderr)

    def check(self, contents):
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "report.xml"
            report.write_text(f'<report name="app">{contents}</report>', encoding="utf-8")
            script = Path(__file__).with_name("check-line-coverage.py")
            return subprocess.run(
                [sys.executable, str(script), str(report), "75"],
                capture_output=True,
                text=True,
            )


if __name__ == "__main__":
    unittest.main()
