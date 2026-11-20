from pathlib import Path
from xml.etree import ElementTree


def main():
    results = Path("app/build/test-results/testDebugUnitTest")
    test_classes = (
        "com.unhappychoice.norimaki.extension.Utf8StreamDecoderTest",
        "com.unhappychoice.norimaki.presentation.view.APITokenViewTest",
    )
    passed_classes = {
        case.get("classname")
        for report in results.glob("TEST-*.xml")
        for case in ElementTree.parse(report).iter("testcase")
        if all(case.find(status) is None for status in ("skipped", "failure", "error"))
    }
    missing = sorted(set(test_classes) - passed_classes)
    if missing:
        raise SystemExit("No passing test cases reported for: " + ", ".join(missing))


if __name__ == "__main__":
    main()
