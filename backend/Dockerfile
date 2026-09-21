FROM rust:1.85-bookworm AS searchpack-builder

RUN apt-get update \
    && apt-get install --no-install-recommends -y python3 python3-pip python3-dev \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /build
COPY native/searchpack /build/native/searchpack
RUN python3 -m pip install --break-system-packages --no-cache-dir 'maturin>=1.9,<2' \
    && maturin build --manifest-path /build/native/searchpack/Cargo.toml --release --out /build/wheels

FROM python:3.12.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 PORT=8080
WORKDIR /app
COPY backend/requirements.txt /app/backend/requirements.txt
RUN pip install --no-cache-dir -r /app/backend/requirements.txt
COPY --from=searchpack-builder /build/wheels /tmp/searchpack-wheels
RUN pip install --no-cache-dir /tmp/searchpack-wheels/*.whl \
    && rm -rf /tmp/searchpack-wheels
COPY backend /app/backend
COPY tools /app/tools
COPY ARGENTINA_MICRO_PARTITION_MOBILE_QUALIFICATION.json /app/ARGENTINA_MICRO_PARTITION_MOBILE_QUALIFICATION.json
RUN useradd --create-home --uid 10001 valuepilot
USER 10001
EXPOSE 8080
STOPSIGNAL SIGTERM
CMD ["python", "-m", "backend"]
