from __future__ import annotations

import os

import uvicorn

from .app import create_app


if __name__ == "__main__":
    uvicorn.run(create_app(), host=os.environ.get("VALUEPILOT_HOST", "0.0.0.0"), port=int(os.environ.get("PORT", "8080")), log_level=os.environ.get("VALUEPILOT_LOG_LEVEL", "info"), access_log=False)
