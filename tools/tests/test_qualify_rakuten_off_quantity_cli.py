from __future__ import annotations

import subprocess
import sys
import unittest
from pathlib import Path


class RakutenOffQuantityCoverageCliTest(unittest.TestCase):
    def test_launcher_can_run_from_outside_repository(self):
        repository_root = Path(__file__).resolve().parents[2]
        launcher = repository_root / "tools" / "run_rakuten_off_quantity_coverage.py"

        completed = subprocess.run(
            [sys.executable, str(launcher), "--help"],
            cwd=repository_root.parent,
            text=True,
            capture_output=True,
            timeout=30,
            check=False,
        )

        self.assertEqual(0, completed.returncode, completed.stderr)
        self.assertIn("--input", completed.stdout)
        self.assertIn("--markdown", completed.stdout)
        self.assertIn("--expected-brand", completed.stdout)


if __name__ == "__main__":
    unittest.main()
