import json
import re
import subprocess
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


class ReleaseBadgeTest(unittest.TestCase):
    def test_readme_badge_matches_latest_release(self):
        readme = Path(__file__).resolve().parents[1] / "README.md"
        badge = re.search(r"^\[!\[Version\]\(([^)]+)\)\]\(([^)]+)\)$",
                          readme.read_text(encoding="utf-8"), re.MULTILINE)
        self.assertIsNotNone(badge, "README must contain a linked version badge")
        image_url, link_url = badge.groups()
        self.assertEqual(link_url, "https://github.com/circleci-tools/Norimaki/releases/latest")
        release = json.loads(fetch("https://api.github.com/repos/circleci-tools/Norimaki/releases/latest"))
        self.assertFalse(release["prerelease"])
        self.assertFalse(release["draft"])
        self.assertEqual(ET.fromstring(fetch(image_url)).get("aria-label"),
                         f"version: {release['tag_name']}")
        self.assertEqual(fetch(link_url, "-o", "/dev/null", "-w", "%{url_effective}"),
                         release["html_url"])


def fetch(url, *options):
    return subprocess.check_output(
        ["curl", "--fail", "--silent", "--show-error", "--location", "--max-time", "20",
         *options, url],
        text=True,
    )


if __name__ == "__main__":
    unittest.main()
