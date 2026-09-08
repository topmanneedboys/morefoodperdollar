"""FastAPI HTTP surface for ValuePilot's read-only backend."""

import os
import uuid
from typing import Any

from .service import BackendService, MAX_REQUEST_BYTES, ResponseLimitError


def create_app(service: BackendService | None = None):
    try:
        from fastapi import FastAPI, Request
        from fastapi.responses import JSONResponse
        from pydantic import BaseModel, ConfigDict, Field, StrictStr
    except ImportError as exc:  # pragma: no cover
        raise RuntimeError("backend dependencies are not installed") from exc

    class Item(BaseModel):
        model_config = ConfigDict(extra="forbid")
        lineId: StrictStr | None = None
        query: StrictStr
        amount: StrictStr
        unit: StrictStr

    class ShopRequest(BaseModel):
        model_config = ConfigDict(extra="forbid")
        latitude: StrictStr
        longitude: StrictStr
        radiusKm: StrictStr
        items: list[Item] = Field(min_length=1, max_length=10)

    class SearchRequest(BaseModel):
        model_config = ConfigDict(extra="forbid")
        latitude: StrictStr
        longitude: StrictStr
        radiusKm: StrictStr
        query: StrictStr = Field(min_length=1, max_length=96)

    class InterpretRequest(BaseModel):
        model_config = ConfigDict(extra="forbid")
        text: StrictStr = Field(min_length=1, max_length=4096)

    class ShopTextRequest(BaseModel):
        model_config = ConfigDict(extra="forbid")
        latitude: StrictStr
        longitude: StrictStr
        radiusKm: StrictStr
        text: StrictStr = Field(min_length=1, max_length=4096)

    app = FastAPI(title="ValuePilot Argentina Backend", version="valuepilot-argentina-backend-v1", docs_url=None, redoc_url=None, openapi_url=None)
    runtime_service = service or BackendService(os.environ.get("VALUEPILOT_RELEASE_ROOT", "local-provider-data/argentina-backend-release"))

    @app.middleware("http")
    async def bounded_body(request: Request, call_next):
        length = request.headers.get("content-length")
        if length is not None:
            try:
                if int(length) > MAX_REQUEST_BYTES:
                    return JSONResponse({"error": "REQUEST_TOO_LARGE"}, status_code=413)
            except ValueError:
                return JSONResponse({"error": "INVALID_CONTENT_LENGTH"}, status_code=400)
        return await call_next(request)

    def correlation(request: Request) -> str:
        value = request.headers.get("x-request-id")
        return value if value and len(value) <= 96 and value.isprintable() else str(uuid.uuid4())

    @app.get("/healthz")
    async def healthz():
        return runtime_service.health()

    @app.get("/readyz")
    async def readyz():
        from fastapi import HTTPException
        ready, value = runtime_service.ready()
        if not ready:
            raise HTTPException(status_code=503, detail=value)
        return value

    @app.get("/v1/status")
    async def status():
        return runtime_service.status()

    @app.post("/v1/search")
    async def search(payload: SearchRequest, request: Request):
        try:
            return runtime_service.search(payload.model_dump(exclude_none=True), correlation_id=correlation(request))
        except ResponseLimitError as exc:
            return JSONResponse({"error": str(exc)}, status_code=413)
        except Exception as exc:  # noqa: BLE001 - public boundary is fail-closed
            from fastapi import HTTPException
            raise HTTPException(status_code=400, detail=str(exc)) from exc

    @app.post("/v1/interpret")
    async def interpret(payload: InterpretRequest, request: Request):
        try:
            return runtime_service.interpret(payload.model_dump(exclude_none=True), correlation_id=correlation(request))
        except ResponseLimitError as exc:
            return JSONResponse({"error": str(exc)}, status_code=413)
        except Exception as exc:  # noqa: BLE001 - public boundary is fail-closed
            from fastapi import HTTPException
            raise HTTPException(status_code=400, detail=str(exc)) from exc

    @app.post("/v1/shop-text")
    async def shop_text(payload: ShopTextRequest, request: Request):
        try:
            return runtime_service.shop_text(payload.model_dump(exclude_none=True), correlation_id=correlation(request))
        except ResponseLimitError as exc:
            return JSONResponse({"error": str(exc)}, status_code=413)
        except Exception as exc:  # noqa: BLE001 - public boundary is fail-closed
            from fastapi import HTTPException
            status_code = 503 if str(exc) == "CURRENT_PRICE_EVIDENCE_UNAVAILABLE" else 400
            raise HTTPException(status_code=status_code, detail=str(exc)) from exc

    @app.post("/v1/shop")
    async def shop(payload: ShopRequest, request: Request):
        try:
            return runtime_service.shop(payload.model_dump(exclude_none=True), correlation_id=correlation(request))
        except ResponseLimitError as exc:
            return JSONResponse({"error": str(exc)}, status_code=413)
        except Exception as exc:  # noqa: BLE001
            from fastapi import HTTPException
            status_code = 503 if str(exc) == "CURRENT_PRICE_EVIDENCE_UNAVAILABLE" else 400
            raise HTTPException(status_code=status_code, detail=str(exc)) from exc

    return app


__all__ = ["create_app"]
