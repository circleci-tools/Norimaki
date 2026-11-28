#!/usr/bin/env python3
"""Check buildscript artifact verification against a local Maven repository."""

import hashlib
from pathlib import Path
import subprocess
from tempfile import TemporaryDirectory
import unittest
from zipfile import ZipFile


ROOT = Path(__file__).resolve().parents[2]
POM = """<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>fixture</groupId><artifactId>build-logic</artifactId><version>1.0</version>
</project>
"""
BUILD = """buildscript {
    repositories { maven { url = uri('repository') } }
    dependencies { classpath 'fixture:build-logic:1.0' }
}
println fixture.BuildLogic.load()
"""
METADATA = """<verification-metadata>
  <configuration>
    <verify-metadata>true</verify-metadata>
    <verify-signatures>false</verify-signatures>
  </configuration>
  <components>
    <component group="fixture" name="build-logic" version="1.0">
      <artifact name="build-logic-1.0.pom"><sha256 value="{pom}"/></artifact>
      <artifact name="build-logic-1.0.jar"><sha256 value="{jar}"/></artifact>
    </component>
  </components>
</verification-metadata>
"""


def create_fixture(project):
    artifact_dir = project / "repository/fixture/build-logic/1.0"
    artifact_dir.mkdir(parents=True)
    pom = artifact_dir / "build-logic-1.0.pom"
    pom.write_text(POM, encoding="utf-8")
    artifact = artifact_dir / "build-logic-1.0.jar"

    source = project / "BuildLogic.java"
    source.write_text(
        'package fixture; public class BuildLogic { '
        'public static String load() { return "Fixture build logic loaded"; } }',
        encoding="utf-8",
    )
    subprocess.run(["javac", "-d", str(project), str(source)], check=True)
    with ZipFile(artifact, "w") as jar:
        jar.write(project / "fixture/BuildLogic.class", "fixture/BuildLogic.class")

    (project / "settings.gradle").write_text("rootProject.name = 'fixture'\n", encoding="utf-8")
    (project / "build.gradle").write_text(BUILD, encoding="utf-8")
    (project / "gradle").mkdir()
    (project / "gradle/verification-metadata.xml").write_text(
        METADATA.format(
            pom=hashlib.sha256(pom.read_bytes()).hexdigest(),
            jar=hashlib.sha256(artifact.read_bytes()).hexdigest(),
        ),
        encoding="utf-8",
    )
    return artifact


def run_gradle(project):
    return subprocess.run(
        [str(ROOT / "gradlew"), "--project-dir", str(project), "--offline",
         "--dependency-verification", "strict", "help"],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        timeout=120,
    )


class GradleDependencyVerificationTest(unittest.TestCase):
    def test_rejects_modified_buildscript_jar_before_loading_it(self):
        with TemporaryDirectory(prefix="gradle-verification-", dir=ROOT) as directory:
            project = Path(directory)
            artifact = create_fixture(project)
            original = run_gradle(project)
            self.assertEqual(original.returncode, 0, original.stdout)
            self.assertIn("Fixture build logic loaded", original.stdout)

            with ZipFile(artifact, "a") as jar:
                jar.writestr("changed.txt", "Modified bytes at the same version")

            modified = run_gradle(project)
            self.assertNotEqual(modified.returncode, 0, modified.stdout)
            self.assertIn("Dependency verification failed", modified.stdout)
            self.assertIn("build-logic-1.0.jar", modified.stdout)
            self.assertIn("checksum", modified.stdout.lower())
            self.assertNotIn("Fixture build logic loaded", modified.stdout)


if __name__ == "__main__":
    unittest.main()
