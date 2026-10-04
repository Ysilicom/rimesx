"""Static guardrails for one mainline with independent platform channels."""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]


def workflow(name):
    return (ROOT / ".github/workflows" / name).read_text()


def event_section(text, event):
    """Read our two-space top-level event blocks without a YAML dependency."""
    match = re.search(
        rf"^  {re.escape(event)}:\n(.*?)(?=^  [a-z_]+:|^permissions:)",
        text,
        re.MULTILINE | re.DOTALL,
    )
    if match is None:
        raise AssertionError(f"Missing event block: {event}")
    return match.group(1)


class MainlineChannelTests(unittest.TestCase):
    def test_platform_checks_follow_main_not_retired_feature_branches(self):
        for name in ("ios.yml", "linux-ime.yml", "windows-ime.yml"):
            with self.subTest(workflow=name):
                text = workflow(name)
                for event in ("push", "pull_request"):
                    block = event_section(text, event)
                    self.assertRegex(block, r"branches:\s*(?:\[main\]|\n\s+- main\n)")
                    self.assertIn("paths:", block)
                trigger = text.split("permissions:", 1)[0]
                self.assertNotIn("cursor/", trigger)
                self.assertNotIn("codex/ios-", trigger)

    def test_native_checks_only_publish_ephemeral_artifacts(self):
        for name in ("linux-ime.yml", "windows-ime.yml"):
            with self.subTest(workflow=name):
                text = workflow(name)
                self.assertIn("contents: read", text)
                self.assertIn("actions/upload-artifact@", text)
                self.assertIn("${{ github.sha }}", text)
                self.assertNotIn("gh release create", text)
                self.assertNotIn("contents: write", text)
                self.assertNotIn("tags:", text.split("permissions:", 1)[0])

    def test_ios_publication_requires_a_separate_tag_and_mainline_commit(self):
        text = workflow("ios-release.yml")
        self.assertIn("tags: ['ios-v*']", text)
        self.assertIn("fetch-depth: 0", text)
        guard = "git merge-base --is-ancestor HEAD origin/main"
        self.assertIn(guard, text)
        self.assertLess(text.index(guard), text.index("Check required signing credentials"))
        self.assertNotIn("workflow_dispatch:", text.split("permissions:", 1)[0])

    def test_shared_dictionary_changes_get_platform_evidence(self):
        for name in ("ios.yml", "windows-ime.yml", "linux-ime.yml"):
            with self.subTest(workflow=name):
                for event in ("push", "pull_request"):
                    block = event_section(workflow(name), event)
                    self.assertRegex(block, r"['\"]rime-data/\*\*['\"]")
                    if name != "ios.yml":
                        self.assertRegex(block, r"['\"]scripts/platform-preview/\*\*['\"]")


if __name__ == "__main__":
    unittest.main()
