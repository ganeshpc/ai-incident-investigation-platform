"""Build the synthetic invoice-ingest Git repository with pinned commits.

Git stores each commit as the file tree plus the author, committer, message,
and timestamps. The commit id is a hash of those bytes. The same files and the
same timestamps always produce the same id. This module rebuilds that history
in a temporary directory. The monorepo does not contain the resulting ``.git``
directory.
"""

from __future__ import annotations

import os
import shutil
import subprocess
from pathlib import Path

AUTHOR_NAME = "Demo Engineer"
AUTHOR_EMAIL = "demo-engineer@example.invalid"
PARSER_PATH = "src/main/java/com/demo/invoice/AmountParser.java"

COMMITS: tuple[dict[str, str], ...] = (
    {
        "key": "known_good",
        "message": "Add decimal amount parser",
        "date": "2026-03-18T10:00:00+00:00",
        "revision": "01-known-good",
    },
    {
        "key": "unrelated_readme",
        "message": "Document invoice file location",
        "date": "2026-03-20T09:00:00+00:00",
        "revision": "02-unrelated-readme",
    },
    {
        "key": "regression",
        "message": "Reject decimal amounts in the parser",
        "date": "2026-03-20T09:10:00+00:00",
        "revision": "03-regression",
    },
)


class DemoRepoError(RuntimeError):
    """The synthetic repository could not be built."""


def repo_root() -> Path:
    """Find the monorepo root by walking up until ``fixtures`` exists.

    ``Path.parents`` is the sequence of folders that contain this file.
    The root is the first folder that holds the fixture tree.
    """

    for candidate in Path(__file__).resolve().parents:
        if (candidate / "fixtures" / "github" / "invoice-ingest" / "revisions").is_dir():
            return candidate
    raise DemoRepoError("fixture revisions directory was not found")


def revision_root() -> Path:
    """Return the directory that holds the three snapshot trees."""

    return repo_root() / "fixtures" / "github" / "invoice-ingest" / "revisions"


def build_demo_repo(destination: Path) -> dict[str, str]:
    """Create a new repository at ``destination`` and return the commit ids.

    ``destination`` must already exist and must be empty. The function copies
    each snapshot over the working tree and commits it with a fixed identity
    and a fixed timestamp. A later call with the same snapshots returns the
    same ids.

    Returns:
        A mapping with ``known_good``, ``unrelated_readme``, and ``regression``
        commit ids, plus ``regression_diff`` for the parser change.
    """

    if any(destination.iterdir()):
        raise DemoRepoError(f"{destination} is not empty")
    _git(destination, "init", "-b", "main")
    _git(destination, "config", "user.name", AUTHOR_NAME)
    _git(destination, "config", "user.email", AUTHOR_EMAIL)
    _git(destination, "config", "commit.gpgsign", "false")
    _git(destination, "config", "core.autocrlf", "false")
    snapshots = revision_root()
    identities: dict[str, str] = {}
    for commit in COMMITS:
        _copy_revision(snapshots / commit["revision"], destination)
        _git(destination, "add", "-A")
        _git(
            destination,
            "commit",
            "-m",
            commit["message"],
            env_extra={
                "GIT_AUTHOR_NAME": AUTHOR_NAME,
                "GIT_AUTHOR_EMAIL": AUTHOR_EMAIL,
                "GIT_AUTHOR_DATE": commit["date"],
                "GIT_COMMITTER_NAME": AUTHOR_NAME,
                "GIT_COMMITTER_EMAIL": AUTHOR_EMAIL,
                "GIT_COMMITTER_DATE": commit["date"],
            },
        )
        identities[commit["key"]] = _git(destination, "rev-parse", "HEAD")
    identities["regression_diff"] = _git(
        destination,
        "diff",
        identities["known_good"],
        identities["regression"],
        "--",
        PARSER_PATH,
    )
    return identities


def _copy_revision(source: Path, destination: Path) -> None:
    """Copy one snapshot onto the working tree.

    Files that are not in this snapshot stay as they were. That is how the
    unrelated README commit leaves the known-good parser in place, and how the
    regression commit leaves the updated README in place.
    """

    if not source.is_dir():
        raise DemoRepoError(f"missing revision snapshot {source}")
    for path in source.rglob("*"):
        if not path.is_file():
            continue
        relative = path.relative_to(source)
        target = destination / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(path, target)


def _git(
    destination: Path,
    *args: str,
    env_extra: dict[str, str] | None = None,
) -> str:
    """Run one git command in the synthetic repository and return its output.

    The command list is passed directly to the operating system. No shell
    interprets it, so a commit message cannot become an extra command.
    """

    environment = os.environ.copy()
    environment.update(env_extra or {})
    completed = subprocess.run(
        ["git", "-C", str(destination), *args],
        check=False,
        capture_output=True,
        text=True,
        env=environment,
    )
    if completed.returncode != 0:
        detail = completed.stderr.strip() or completed.stdout.strip()
        raise DemoRepoError(f"git {' '.join(args)} failed: {detail}")
    return completed.stdout.strip()
