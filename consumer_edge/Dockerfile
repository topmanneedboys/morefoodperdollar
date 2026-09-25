FROM python:3.12.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 PORT=8080
WORKDIR /app
COPY consumer_edge/requirements.txt /app/consumer_edge/requirements.txt
RUN pip install --no-cache-dir -r /app/consumer_edge/requirements.txt
COPY backend/__init__.py /app/backend/__init__.py
COPY backend/consumer_edge.py /app/backend/consumer_edge.py
COPY consumer_edge /app/consumer_edge
RUN useradd --create-home --uid 10001 valuepilot
USER 10001
EXPOSE 8080
STOPSIGNAL SIGTERM
CMD ["python", "-m", "consumer_edge"]
