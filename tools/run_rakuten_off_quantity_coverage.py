#!/usr/bin/env python3
"""Stable CLI entry point for the Rakuten × Open Food Facts coverage tool.

Running a Python file inside ``tools/`` directly makes that directory
``sys.path[0]``. The implementation intentionally imports sibling modules via
the repository-level ``tools`` namespace so it can also be imported cleanly by
tests. This tiny launcher adds only the repository root to ``sys.path`` and
then delegates to the real implementation.

No provider data is read or logged by this launcher itself.
"""

from __future__ import annotations

import sys
from pathlib import Path

REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from tools.measure_rakuten_off_quantity_coverage import main


if __name__ == "__main__":
    main()
