"""The AI service worker (plan Phase 4): runs the agent graph for analysis.requests and analysis.resume messages and
reports progress on analysis.events. Contract: schema_version 1 (plan Phase 3, "Message contract").

Rules (plan Phase 4 and the GM-20 architecture review):
- A message is acked only once the graph has yielded (finished, or waiting at the approval gate) and its event is
  published, so a crash mid-run means redelivery, and the redelivered job continues from its checkpoint (invariant 6).
- Duplicates are expected (Spring republishes lost messages). A request or resume for a thread waiting at the gate
  resumes it; for a thread with pending nodes continues it; for a finished thread re-publishes its outcome. Nothing is
  ever silently ignored, so Spring's status always catches up (invariants 9, 10).
- Any failure of the run is published as a `failed` event with a reason (never mistaken for "no elephants").

Run: python -m gajamitra.worker. Settings (host, user and password are separate, never one URL): RABBITMQ_HOST,
RABBITMQ_DEFAULT_USER, RABBITMQ_DEFAULT_PASS, POSTGRES_HOST, POSTGRES_PORT, POSTGRES_USER, POSTGRES_PASSWORD,
POSTGRES_DB, MCP_URL, MCP_API_KEY, MODEL_API_KEY.
"""
import asyncio
import base64
import json
import logging
import os
import threading
import uuid
from pathlib import Path

from gajamitra.graph import build_graph, config, resume, start

SCHEMA_VERSION = 1
REQUESTS, RESUME, EVENTS = "analysis.requests", "analysis.resume", "analysis.events"
DECISIONS = ("approve", "reject", "timeout")

log = logging.getLogger("gajamitra.worker")


class BadMessage(ValueError):
    """A message that doesn't match the contract. It goes to the dead-letter queue, not back to the queue."""


def _require(message, *fields):
    if not isinstance(message, dict) or message.get("schema_version") != SCHEMA_VERSION:
        raise BadMessage("not a schema_version 1 message")
    for field in fields:
        if not isinstance(message.get(field), str) or not message[field]:
            raise BadMessage(f"missing {field}")
    try:
        uuid.UUID(message["analysis_id"])
    except ValueError as e:
        raise BadMessage("analysis_id is not a UUID") from e


def outcome(state):
    """What Spring stores of a finished run: no images, no model text beyond the decisions."""
    summary = state.get("summary") or {}
    return {"risk": summary.get("risk"), "herd_size": summary.get("herd_size"), "approval": state.get("approval"),
            "dispatched": [{k: d.get(k) for k in ("stage", "key", "severity")} for d in state.get("dispatched", [])],
            "notes": state.get("notes", [])}


class Worker:
    """The message handling, independent of RabbitMQ. publish(event: dict) sends one analysis.events message."""

    def __init__(self, graph, publish, frames_dir=None):
        self.graph, self.publish = graph, publish
        self.frames_dir = frames_dir  # where the frames the agents looked at are kept for the approval screen (or None)
        # ponytail: one lock per analysis, never removed (a few bytes each); prune finished ones if it ever matters.
        self._locks, self._locks_guard = {}, threading.Lock()

    def _event(self, analysis_id, type_, **fields):
        self.publish({"schema_version": SCHEMA_VERSION, "event_id": str(uuid.uuid4()), "analysis_id": analysis_id,
                      "type": type_, **fields})

    def _save_frames(self, analysis_id, state):
        """Keep the keyframes the agents looked at, so an officer can see the evidence (plan Phase 5). Best effort: a
        disk problem is logged and never delays the approval event, the warning or the run."""
        if not self.frames_dir:
            return
        try:
            folder = Path(self.frames_dir) / analysis_id  # analysis_id was checked to be a UUID: no path tricks
            folder.mkdir(parents=True, exist_ok=True)
            for i, url in enumerate(state.get("keyframes") or []):
                head, _, data = url.partition(",")
                if head == "data:image/jpeg;base64":
                    (folder / f"{i}.jpg").write_bytes(base64.b64decode(data))
        except (OSError, ValueError):
            log.warning("could not save the evidence frames for analysis %s", analysis_id, exc_info=True)

    def _report(self, analysis_id, state):
        self._save_frames(analysis_id, state)
        if "__interrupt__" in state:
            self._event(analysis_id, "approval_required", escalation=state["decision"]["escalation"])
        else:
            self._event(analysis_id, "completed", result=outcome(state))

    def _run(self, analysis_id, step):
        # Only the graph's own failures become a failed event. A failure to publish the outcome propagates instead,
        # so the message is redelivered and the finished (or waiting) run reports again: never "failed" after the
        # alerts went out.
        try:
            state = step()
        except Exception as e:  # deliberately broad: every graph failure must become a visible failed event (inv. 10)
            log.exception("analysis %s failed", analysis_id)
            self._event(analysis_id, "failed", reason=f"{type(e).__name__}: {e}"[:300])
            return
        self._report(analysis_id, state)

    def _lock(self, analysis_id):
        # A duplicate request and a resume for one analysis can arrive on the two channels at once: run them one at
        # a time so the escalation isn't dispatched twice (Spring's keys would still stop a second SMS).
        with self._locks_guard:
            return self._locks.setdefault(analysis_id, threading.Lock())

    def handle_request(self, message):
        _require(message, "analysis_id", "site_id", "video_path")
        boundary = message.get("boundary")
        if not (isinstance(boundary, list) and len(boundary) == 6
                and all(isinstance(v, (int, float)) and 0 <= v <= 1 for v in boundary)):
            raise BadMessage("boundary must be six numbers between 0 and 1")
        aid = message["analysis_id"]
        with self._lock(aid):
            if not self.graph.get_state(config(aid)).values:
                self._event(aid, "started")
            # start() continues an existing checkpoint (or returns a finished state) instead of starting again.
            self._run(aid, lambda: start(self.graph, aid, message["site_id"], message["video_path"], boundary))

    def handle_resume(self, message):
        _require(message, "analysis_id", "decision")
        if message["decision"] not in DECISIONS:
            raise BadMessage("decision must be approve, reject or timeout")
        aid = message["analysis_id"]
        with self._lock(aid):
            snapshot = self.graph.get_state(config(aid))
            if not snapshot.values:
                self._event(aid, "failed", reason="resume for an analysis with no checkpoint")
            elif any(task.interrupts for task in snapshot.tasks):
                self._run(aid, lambda: resume(self.graph, aid, message["decision"]))
            elif snapshot.next:
                self._run(aid, lambda: self.graph.invoke(None, config(aid)))  # crashed past the gate: continue
            else:
                self._report(aid, snapshot.values)  # already finished: tell Spring again


RETRY_DELAY_S = 5.0  # before redelivering after an infrastructure failure, so an outage isn't a hot loop


async def _consume(channel, queue_name, handle):
    queue = await channel.declare_queue(queue_name, passive=True)  # Spring declares the queues and their DLQ

    async def on_message(message):
        try:
            payload = json.loads(message.body)
            await asyncio.to_thread(handle, payload)  # the graph is synchronous; keep the loop free
        except (BadMessage, json.JSONDecodeError) as e:
            log.error("%s message dead-lettered: %s", queue_name, e)
            await message.reject(requeue=False)
            return
        except Exception:  # e.g. RabbitMQ or Postgres down while publishing or checkpointing: redeliver later
            log.exception("%s message not handled; it will be redelivered", queue_name)
            await asyncio.sleep(RETRY_DELAY_S)
            await message.nack(requeue=True)
            return
        await message.ack()

    await queue.consume(on_message)


def main():
    import aio_pika
    import psycopg
    from langgraph.checkpoint.postgres import PostgresSaver
    from psycopg.conninfo import make_conninfo

    from gajamitra.agent import meta_chat
    from gajamitra.mcp_client import McpToolClient

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
    if not os.environ.get("MODEL_API_KEY"):
        # Every analysis will fail (visibly, as FAILED in Spring); say so at start instead of on the first clip.
        log.error("MODEL_API_KEY is not set: every analysis will fail at the SAM 3.1 step")
    # Host, user and password come as separate settings (never inside a URL), so special characters in a password
    # can't break the connection and no URL carrying a password is ever built or logged.
    env = os.environ
    db = {"host": env.get("POSTGRES_HOST", "localhost"), "port": env.get("POSTGRES_PORT", "5432"),
          "user": env["POSTGRES_USER"], "password": env["POSTGRES_PASSWORD"], "dbname": env["POSTGRES_DB"]}
    with psycopg.connect(make_conninfo(**db), autocommit=True) as conn:
        conn.execute("create schema if not exists langgraph")  # own schema: never mixes with Flyway's tables
    checkpoint_conninfo = make_conninfo(**db, options="-csearch_path=langgraph")  # its tables stay in that schema

    async def run(checkpointer):
        loop = asyncio.get_running_loop()
        connection = await aio_pika.connect_robust(
            host=env.get("RABBITMQ_HOST", "localhost"), login=env["RABBITMQ_DEFAULT_USER"],
            password=env["RABBITMQ_DEFAULT_PASS"])
        async with connection:
            events_channel = await connection.channel()

            def publish(event):  # called from the graph's thread
                body = aio_pika.Message(json.dumps(event).encode(), content_type="application/json",
                                        delivery_mode=aio_pika.DeliveryMode.PERSISTENT)
                asyncio.run_coroutine_threadsafe(
                    events_channel.default_exchange.publish(body, routing_key=EVENTS), loop).result(timeout=30)

            worker = Worker(build_graph(McpToolClient(), meta_chat, checkpointer), publish,
                            frames_dir=env.get("EVIDENCE_DIR", "/data/evidence"))
            # Separate channels, prefetch 1 each: a long analysis never blocks an officer's resume.
            for queue_name, handle in ((REQUESTS, worker.handle_request), (RESUME, worker.handle_resume)):
                channel = await connection.channel()
                await channel.set_qos(prefetch_count=1)
                await _consume(channel, queue_name, handle)
            log.info("worker ready")
            await asyncio.Future()  # run until stopped

    with PostgresSaver.from_conn_string(checkpoint_conninfo) as checkpointer:
        checkpointer.setup()
        asyncio.run(run(checkpointer))


if __name__ == "__main__":
    main()
