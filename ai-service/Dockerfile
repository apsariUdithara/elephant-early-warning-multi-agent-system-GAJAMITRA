# AI service worker (plan Phase 7): consumes analysis.requests / analysis.resume and runs the LangGraph pipeline.
# Built from the repo root, so the shared policy table is included:
#   docker build -f ai-service/Dockerfile -t gajamitra-worker .
FROM python:3.11-slim
WORKDIR /app
RUN useradd --system --uid 10001 gajamitra
COPY ai-service/pyproject.toml ai-service/
COPY ai-service/gajamitra/ ai-service/gajamitra/
COPY policy/ policy/
# Editable install: the code stays in /app/ai-service, so policy.py finds /app/policy/policy_table.json.
RUN pip install --no-cache-dir -e ./ai-service
ENV GAJAMITRA_SAM_CACHE=/tmp/sam-cache
USER gajamitra
WORKDIR /app/ai-service
CMD ["python", "-m", "gajamitra.worker"]
