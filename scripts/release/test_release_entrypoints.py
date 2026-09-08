#!/usr/bin/env python3

from __future__ import annotations

import json
import os
import shutil
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


SOURCE_ROOT = Path(__file__).resolve().parents[2]
COMMIT = "a" * 40
BUILD_TIME = "2026-07-28T12:00:00Z"


def write_executable(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(textwrap.dedent(content).lstrip(), encoding="utf-8")
    path.chmod(0o755)


class ReleaseEntrypointTest(unittest.TestCase):
    def test_preflight_is_atomic_and_gates_both_compose_files_by_default(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            repo = Path(temporary) / "repo"
            release_dir = repo / "scripts/release"
            docker_dir = repo / "docker"
            release_dir.mkdir(parents=True)
            docker_dir.mkdir()
            shutil.copy2(
                SOURCE_ROOT / "scripts/release/preflight-release.sh",
                release_dir / "preflight-release.sh",
            )
            write_executable(
                release_dir / "release_tool.py",
                """
                #!/usr/bin/env python3
                import json
                import os
                import sys
                from pathlib import Path

                Path(os.environ["FAKE_TOOL_LOG"]).write_text(
                    "\\n".join(sys.argv[1:]) + "\\n",
                    encoding="utf-8",
                )
                compose_files = [
                    sys.argv[index + 1]
                    for index, value in enumerate(sys.argv)
                    if value == "--compose-file"
                ]
                print(json.dumps({"status": "passed", "composes": compose_files}))
                """,
            )
            env_file = docker_dir / ".env"
            env_file.write_text("RELEASE_ID=test\n", encoding="utf-8")
            for name in ("docker-compose.yml", "docker-compose.ecs-host.yml"):
                (docker_dir / name).write_text("services: {}\n", encoding="utf-8")
            archive = repo / "candidate.tar.gz"
            archive.write_bytes(b"fixture")
            evidence = repo / "evidence/preflight.json"
            evidence.parent.mkdir()
            fake_bin = repo / "fake-bin"
            docker_log = repo / "docker.log"
            tool_log = repo / "tool.log"
            write_executable(
                fake_bin / "docker",
                """
                #!/usr/bin/env bash
                printf '%s\\n' "$*" >>"$FAKE_DOCKER_LOG"
                if [[ -n "${FAKE_DOCKER_FAIL_ON:-}" && "$*" == *"$FAKE_DOCKER_FAIL_ON"* ]]; then
                  exit 42
                fi
                """,
            )
            environment = {
                **os.environ,
                "PATH": f"{fake_bin}:{os.environ['PATH']}",
                "FAKE_DOCKER_LOG": str(docker_log),
                "FAKE_TOOL_LOG": str(tool_log),
            }
            command = [
                str(release_dir / "preflight-release.sh"),
                "--archive",
                str(archive),
                "--evidence",
                str(evidence),
                "--env-file",
                str(env_file),
            ]

            evidence.write_text('{"status":"passed"}\n', encoding="utf-8")
            failed = subprocess.run(
                command,
                cwd=repo,
                env={
                    **environment,
                    "FAKE_DOCKER_FAIL_ON": "docker-compose.ecs-host.yml",
                },
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                check=False,
            )
            self.assertNotEqual(failed.returncode, 0)
            self.assertFalse(evidence.exists())
            self.assertFalse(tool_log.exists())
            self.assertFalse(list(evidence.parent.glob(".release-preflight.*")))

            docker_log.unlink()
            passed = subprocess.run(
                command,
                cwd=repo,
                env=environment,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                check=False,
            )
            self.assertEqual(passed.returncode, 0, passed.stderr)
            value = json.loads(evidence.read_text(encoding="utf-8"))
            self.assertEqual(value["status"], "passed")
            self.assertEqual(len(value["composes"]), 2)
            self.assertEqual(
                sum(
                    1
                    for line in tool_log.read_text(encoding="utf-8").splitlines()
                    if line == "--compose-file"
                ),
                2,
            )
            self.assertEqual(
                len(docker_log.read_text(encoding="utf-8").splitlines()), 2
            )
            self.assertFalse(list(evidence.parent.glob(".release-preflight.*")))

    def test_build_gate_checks_the_java_runtime_used_by_maven(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            repo = Path(temporary) / "repo"
            release_dir = repo / "scripts/release"
            docker_dir = repo / "docker"
            ui_dir = repo / "erp-ui"
            release_dir.mkdir(parents=True)
            docker_dir.mkdir()
            ui_dir.mkdir()
            shutil.copy2(
                SOURCE_ROOT / "scripts/release/build-release.sh",
                release_dir / "build-release.sh",
            )
            (docker_dir / ".env.example").write_text(
                "TEMPLATE=true\n", encoding="utf-8"
            )
            write_executable(
                release_dir / "release_tool.py",
                """
                #!/usr/bin/env python3
                import os
                import sys

                with open(os.environ["FAKE_RELEASE_LOG"], "a", encoding="utf-8") as output:
                    output.write(" ".join(sys.argv[1:]) + "\\n")
                raise SystemExit(0)
                """,
            )
            maven_log = repo / "maven.log"
            npm_log = repo / "npm.log"
            release_log = repo / "release.log"
            write_executable(
                repo / "mvnw",
                """
                #!/usr/bin/env bash
                if [[ "${1:-}" == "--version" ]]; then
                  echo "Apache Maven 3.9.16"
                  echo "Java version: ${FAKE_MAVEN_JAVA}, vendor: Test"
                  exit 0
                fi
                printf '%s\\n' "$*" >>"$FAKE_MAVEN_LOG"
                exit "${FAKE_MAVEN_EXIT:-0}"
                """,
            )
            fake_bin = repo / "fake-bin"
            write_executable(
                fake_bin / "git",
                f"""
                #!/usr/bin/env bash
                if [[ "${{1:-}}" == "rev-parse" ]]; then
                  echo "{COMMIT}"
                elif [[ "${{1:-}}" == "status" ]]; then
                  exit 0
                else
                  exit 1
                fi
                """,
            )
            write_executable(
                fake_bin / "java",
                """
                #!/usr/bin/env bash
                echo 'openjdk version "17.0.18"' >&2
                """,
            )
            write_executable(
                fake_bin / "node",
                """
                #!/usr/bin/env bash
                [[ "${1:-}" == "-p" ]] || exit 64
                [[ "${2:-}" == 'process.versions.node.split(".")[0]' ]] || exit 65
                echo 22
                """,
            )
            write_executable(
                fake_bin / "npm",
                """
                #!/usr/bin/env bash
                printf '%s\\n' "$*" >>"$FAKE_NPM_LOG"
                """,
            )
            environment = {
                **os.environ,
                "PATH": f"{fake_bin}:{os.environ['PATH']}",
                "FAKE_MAVEN_LOG": str(maven_log),
                "FAKE_NPM_LOG": str(npm_log),
                "FAKE_RELEASE_LOG": str(release_log),
            }
            for variable in (
                "MAVEN_ARGS",
                "MAVEN_OPTS",
                "JAVA_TOOL_OPTIONS",
                "JDK_JAVA_OPTIONS",
                "_JAVA_OPTIONS",
            ):
                environment.pop(variable, None)
            command = [
                str(release_dir / "build-release.sh"),
                "--release-id",
                "erp-release-test",
                "--git-commit",
                COMMIT,
                "--build-time",
                BUILD_TIME,
                "--output-dir",
                str(repo / "output"),
            ]

            skip_build = subprocess.run(
                [*command, "--skip-build"],
                cwd=repo,
                env={**environment, "FAKE_MAVEN_JAVA": "17.0.18"},
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                check=False,
            )
            self.assertNotEqual(skip_build.returncode, 0)
            self.assertIn("Usage:", skip_build.stderr)
            self.assertFalse(maven_log.exists())

            for variable in (
                "MAVEN_ARGS",
                "MAVEN_OPTS",
                "JAVA_TOOL_OPTIONS",
                "JDK_JAVA_OPTIONS",
                "_JAVA_OPTIONS",
            ):
                with self.subTest(injected_build_environment=variable):
                    injected = subprocess.run(
                        command,
                        cwd=repo,
                        env={
                            **environment,
                            "FAKE_MAVEN_JAVA": "17.0.18",
                            variable: (
                                "-Dtest=NoSuchReleaseGateTest "
                                "-Dsurefire.failIfNoSpecifiedTests=false"
                            ),
                        },
                        text=True,
                        stdout=subprocess.PIPE,
                        stderr=subprocess.PIPE,
                        check=False,
                    )
                    self.assertNotEqual(injected.returncode, 0)
                    self.assertIn(
                        f"{variable} must be unset for a production release build",
                        injected.stderr,
                    )
                    self.assertFalse(maven_log.exists())

            rejected = subprocess.run(
                command,
                cwd=repo,
                env={**environment, "FAKE_MAVEN_JAVA": "25.0.2"},
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                check=False,
            )
            self.assertNotEqual(rejected.returncode, 0)
            self.assertIn("Maven must run on Java 17", rejected.stderr)
            self.assertFalse(maven_log.exists())

            accepted = subprocess.run(
                command,
                cwd=repo,
                env={**environment, "FAKE_MAVEN_JAVA": "17.0.18"},
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                check=False,
            )
            self.assertEqual(accepted.returncode, 0, accepted.stderr)
            self.assertEqual(
                maven_log.read_text(encoding="utf-8").splitlines(),
                [
                    f"clean verify -Dbuild.commit={COMMIT} "
                    "-DskipTests=false -Dmaven.test.skip=false"
                ],
            )
            self.assertEqual(
                npm_log.read_text(encoding="utf-8").splitlines(),
                ["ci", "run build:prod"],
            )
            self.assertTrue(
                any(
                    line.startswith("package ")
                    for line in release_log.read_text(encoding="utf-8").splitlines()
                )
            )

            migration_manifest = repo / "scripts/test-migration-release.json"
            migration_manifest.write_text("{}\n", encoding="utf-8")
            release_log.unlink()
            with_migration = subprocess.run(
                [
                    *command,
                    "--migration-manifest",
                    str(migration_manifest),
                ],
                cwd=repo,
                env={**environment, "FAKE_MAVEN_JAVA": "17.0.18"},
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                check=False,
            )
            self.assertEqual(0, with_migration.returncode, with_migration.stderr)
            package_lines = [
                line
                for line in release_log.read_text(encoding="utf-8").splitlines()
                if line.startswith("package ")
            ]
            self.assertEqual(1, len(package_lines))
            self.assertIn(
                f"--migration-manifest {migration_manifest}", package_lines[0]
            )

            maven_log.unlink()
            npm_log.unlink()
            release_log.unlink()
            failed_verify = subprocess.run(
                command,
                cwd=repo,
                env={
                    **environment,
                    "FAKE_MAVEN_JAVA": "17.0.18",
                    "FAKE_MAVEN_EXIT": "9",
                },
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                check=False,
            )
            self.assertNotEqual(failed_verify.returncode, 0)
            self.assertFalse(npm_log.exists())
            self.assertFalse(
                any(
                    line.startswith("package ")
                    for line in release_log.read_text(encoding="utf-8").splitlines()
                )
            )


if __name__ == "__main__":
    unittest.main()
