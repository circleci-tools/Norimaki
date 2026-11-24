from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

REQUIRED_CLASSES = (
    "com.unhappychoice.norimaki.extension.Utf8StreamDecoderTest",
    "com.unhappychoice.norimaki.presentation.view.APITokenViewTest",
)


class UnitTestResultsCheckTest(unittest.TestCase):
    def test_passes_when_every_required_class_has_a_passing_case(self):
        cases = {cls: '<testcase classname="{}" name="it works"/>'.format(cls) for cls in REQUIRED_CLASSES}
        result = self.check(cases)
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_fails_when_a_required_class_has_no_report_at_all(self):
        result = self.check({
            REQUIRED_CLASSES[0]: '<testcase classname="{}" name="it works"/>'.format(REQUIRED_CLASSES[0])
        })
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("No passing test cases reported for", result.stderr)
        self.assertIn(REQUIRED_CLASSES[1], result.stderr)

    def test_fails_when_the_only_case_for_a_class_was_skipped(self):
        result = self.check({
            REQUIRED_CLASSES[0]: '<testcase classname="{}" name="it works"/>'.format(REQUIRED_CLASSES[0]),
            REQUIRED_CLASSES[1]: '<testcase classname="{}" name="it works"><skipped/></testcase>'.format(
                REQUIRED_CLASSES[1]
            ),
        })
        self.assertNotEqual(result.returncode, 0)
        self.assertIn(REQUIRED_CLASSES[1], result.stderr)

    def check(self, cases_by_classname):
        with tempfile.TemporaryDirectory() as directory:
            results = Path(directory) / "app" / "build" / "test-results" / "testDebugUnitTest"
            results.mkdir(parents=True)
            for index, testcase in enumerate(cases_by_classname.values()):
                report = results / f"TEST-{index}.xml"
                report.write_text(f"<testsuite>{testcase}</testsuite>", encoding="utf-8")
            script = Path(__file__).with_name("check-unit-test-results.py")
            return subprocess.run(
                [sys.executable, str(script)],
                capture_output=True,
                text=True,
                cwd=directory,
            )


if __name__ == "__main__":
    unittest.main()
