import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile


ROOT = Path(__file__).resolve().parents[1]


class GradleWrapperTest(unittest.TestCase):
    def test_rejects_distribution_with_mismatched_checksum(self):
        with tempfile.TemporaryDirectory(dir=ROOT) as directory:
            fixture = Path(directory)
            wrapper = fixture / "gradle/wrapper"
            shutil.copytree(ROOT / "gradle/wrapper", wrapper)
            shutil.copy2(ROOT / "gradlew", fixture)

            archive = fixture / "substitute-bin.zip"
            with ZipFile(archive, "w") as distribution:
                distribution.writestr("substitute/README", "Checksum test fixture")

            properties = wrapper / "gradle-wrapper.properties"
            lines = properties.read_text(encoding="utf-8").splitlines()
            properties.write_text(
                "\n".join(f"distributionUrl={archive.as_uri()}" if line.startswith("distributionUrl=")
                          else line for line in lines) + "\n",
                encoding="utf-8",
            )
            result = subprocess.run(
                ["./gradlew", "--gradle-user-home", str(fixture / "cache"), "--version"],
                cwd=fixture, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=30,
            )
            self.assertNotEqual(result.returncode, 0, result.stdout)
            self.assertIn("Verification of Gradle distribution failed", result.stdout)


if __name__ == "__main__":
    unittest.main()
