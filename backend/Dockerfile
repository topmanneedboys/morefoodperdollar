FROM python:3.12.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 PORT=8080
WORKDIR /app
COPY backend/requirements.txt /app/backend/requirements.txt
RUN pip install --no-cache-dir -r /app/backend/requirements.txt
COPY backend /app/backend
COPY tools /app/tools
COPY ARGENTINA_MICRO_PARTITION_MOBILE_QUALIFICATION.json /app/ARGENTINA_MICRO_PARTITION_MOBILE_QUALIFICATION.json
RUN useradd --create-home --uid 10001 valuepilot
USER 10001
EXPOSE 8080
STOPSIGNAL SIGTERM
CMD ["python", "-m", "backend"]
