#!/usr/bin/env python3
"""CLI entry point for the bounded Argentina daily release operation."""

from __future__ import annotations

try:
    from tools.argentina_daily_release import main
except ModuleNotFoundError:  # direct invocation from tools/
    from argentina_daily_release import main


if __name__ == "__main__":
    raise SystemExit(main())
