import sys
from xml.etree import ElementTree


def main(report_path, minimum):
    try:
        report = ElementTree.parse(report_path).getroot()
        counter = report.find("./counter[@type='LINE']")
        if report.tag != "report" or counter is None:
            raise ValueError("missing report-level LINE counter")
        covered = int(counter.get("covered", ""))
        missed = int(counter.get("missed", ""))
        total = covered + missed
        if covered < 0 or missed < 0 or total == 0:
            raise ValueError("invalid LINE counts")
    except (OSError, ElementTree.ParseError, ValueError) as error:
        raise SystemExit(f"Cannot read JaCoCo line coverage from {report_path}: {error}")

    print(f"JaCoCo line coverage: {covered / total:.2%} ({covered}/{total}), minimum {minimum}%")
    if covered * 100 < minimum * total:
        raise SystemExit("JaCoCo line coverage is below the minimum.")


if __name__ == "__main__":
    main(sys.argv[1], int(sys.argv[2]))
