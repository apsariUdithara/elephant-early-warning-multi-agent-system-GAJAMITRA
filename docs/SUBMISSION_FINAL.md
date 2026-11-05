# Submission form: final answers (paste-ready)

Each block is paste-ready text. Limits: description, Meta products and "why Meta" are 3,000 characters each; partner feedback is 150.

## Team Name
```
GajaMitra
```

## Track and problem statement
Pick: **Indie Developer** (track) and **Build for Your World** (problem statement), if those are the dropdown options. Confirm against the dropdown wording.

## Project Description
```
Every year in Sri Lanka, hundreds of elephants and more than a hundred people die in human-elephant conflict, mostly at night on the edge of villages. Warnings come too late. Existing systems are detectors trained for one camera, and they stop at "elephant detected": nobody tells the village how long it has, or what to do.

GajaMitra goes from video to a villager's phone. An officer uploads a boundary-camera clip and marks the village boundary (two points on the line, one on the village side). Out come a tracked herd, an estimated arrival time, a risk level, and a short alert in Sinhala, Tamil and English.

How it works
1. SAM 3.1 tracks every elephant from one text prompt, "elephant". Object IDs persist across frames, so we get real tracks without training a detector.
2. Deterministic, tested code turns the tracks into speed, distance to the boundary and arrival time, and sets the risk level (low, medium, high, critical). A herd standing at the fence is never rated low.
3. A Muse Spark responder agent reads annotated keyframes and the tracker data, judges behaviour and proposes alerts. A second Muse Spark verifier agent checks the proposal and can send it back.
4. A deterministic guard caps the alert's severity to what the tracker measured. The AI proposes; the code decides.
5. The warning goes out at once. An escalation to "danger" waits for a wildlife officer, who sees the exact text and the frames the agents saw. Approval never delays a warning, and if nobody answers, the escalation is sent automatically when the timer ends.

Why it can be trusted
Python never sends anything. Every side effect is an audited tool call on the Spring Boot backend, which re-checks the same policy table and refuses any alert it has no tracker risk for. Every alert carries an idempotency key, so retries never double-send, and jobs resume from a checkpoint after a crash.

What we built
The tracking maths, the LangGraph pipeline, the Spring Boot backend (alerts, SMS, approvals, audit log, key-protected MCP server), a RabbitMQ worker, and a React dashboard in three languages with an officer approval screen showing the evidence frames. We ran it end to end against the real Meta API on a critical-risk case and a high-risk case with approval.

Honest limits
The demo site runs in test mode: alerts are stored and no SMS reaches real phones; Twilio sending is tested only against a mock. Sinhala and Tamil text awaits native-speaker review. No night footage or live streams yet. Our first evaluation on 13 labelled clips passed 6 of 13 and found two real problems (calm herds rated medium; flipping the village side did not lower the risk). Apart from two cases, the labels were our own predictions, so this is a first measurement, not a benchmark.

Impact
A new site needs only a camera, a boundary line and a language list, so the same pipeline can serve India and Africa. Next: a shadow-mode pilot with wildlife rangers, comparing what GajaMitra would have sent with real events.
```

## Public GitHub Repository
```
https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra
```

## 3-Minute Demo Video
Your YouTube link (unlisted is fine). Open it in a private window before pasting.

## Describe your use of Meta products to build the submitted project
```
GajaMitra uses two Meta models through the Meta Model API, each for what it does best.

SAM 3.1: video segmentation and tracking
We send the camera clip and prompt SAM 3.1 with one noun phrase, "elephant". It streams per-frame boxes with object IDs that persist across frames. Those IDs are the foundation of the system: they give each elephant a track, and from the track we compute velocity, distance to the village boundary and arrival time. A per-frame detector could not give us speed. SAM's output is special-token text, not JSON, so we wrote and tested a parser for it, and we cache the raw output by video hash so evaluation reruns do not pay for SAM again.

Muse Spark 1.3: vision, structured output, tool use, multilingual writing
- Vision: the responder reads keyframes with SAM's boxes and IDs drawn on, and judges herd behaviour and whether calves are present, which a tracker cannot do.
- Two agents: a responder proposes alerts, and a separate verifier with its own prompt checks the proposal against the same evidence and can send it back for revision.
- Structured output: both agents return schema-checked objects. Anything that does not match is rejected and retried; in our runs a reply occasionally missed a required field, and the check caught it.
- Multilingual: alerts are written in English, Sinhala and Tamil. A native speaker has not yet reviewed the Sinhala and Tamil text, and we say so in the product and the video.
- Tool use: every side effect (recording a risk, sending an alert, storing an officer report) is a tool on our Spring Boot backend, exposed over MCP and protected by an API key.

Design choice: models propose, code decides
SAM and Spark supply perception and judgement. Deterministic, tested code sets risk levels and limits alert severity, and the backend re-checks the limit on its own, so a model mistake cannot send a village a "danger" alert the tracker does not support. Model output is treated as untrusted input throughout.

Evaluation
We run the whole pipeline against labelled clips with the real models and report counts such as "6/13". The runner includes an AI-judge for alert quality (location, herd size, instruction, brevity) written for Muse Wonder. It is built and unit-tested but not yet connected to the runner, so we report no judge scores. The first run is honest, not flattering: 6 of 13, with two real findings that we are fixing.
```

## Why are Meta's models important to your solution?
```
Without these models, GajaMitra would need a trained detector for every site. That is exactly the gap in the Sri Lankan elephant-detection systems we found: they train detectors on local images, so each works for one camera, in one place, at the times of day it was trained on, and each stops at "elephant detected". Collecting and labelling night footage for every new village is slow, and the villages with the worst conflict are the least likely to have the data.

SAM 3.1 removes the training step. A text prompt, "elephant", is enough, and its persistent object IDs give us tracks. Tracks are what let us estimate speed and arrival time instead of only detecting. A new site needs a boundary line, not a dataset. In our evaluation clips with no elephants (a paddy field, grass and an empty forest road) produced no alerts.

Muse Spark adds what a detector cannot do. It reads behaviour from keyframes: is this herd feeding or approaching, and are there calves? And it writes a clear, short alert in English, Sinhala and Tamil, where small local models are weak. Because a second Spark agent checks the first agent's proposal, we get a cheap second opinion before any alert is limited by our deterministic guard.

Both models come from one API, so one developer could build the whole pipeline, from video to a structured, validated alert, in a hackathon. That matters for the people this serves. The tools that protect rural communities are often built by very small teams, and a single platform that offers perception, vision, tool use and Sinhala and Tamil writing makes a project like this possible at all.

We are also candid about what the models cannot yet do for us. Night footage, live streams and native-speaker quality for Sinhala and Tamil are untested, and our first evaluation passed 6 of 13 labelled clips. The structure of the system, with models that propose and tested code that decides, is what lets us use them for a safety-critical task while we learn where they are weak.

The same approach works wherever people and elephants share land, with only a new camera, a boundary line and a language list.
```

## Partner technologies feedback (150 characters max)
```
Did not use Fireworks, Modal or Baseten. All inference ran through the Meta Model API (SAM 3.1 and Muse Spark 1.3).
```

## Ratings (your honest answers; suggestions)
- **Likelihood to build with Meta Model API:** pick what is true for you. A defensible high answer: the single API for video tracking plus a vision and tool-use model is what made this project possible.
- **Overall opinion:** same.
- **Recommend Meta Model API / Muse Code:** optional fields; answer only what you used. Leave Muse Code blank if you did not use it.

## Survey questions
**Biggest obstacle**
```
Getting SAM 3.1 to work on video. The docs we found showed image examples, not a video request, so we followed a cookbook script. Its output is special-token text rather than JSON, so we wrote a parser and tests around it. Second: Muse Spark occasionally returned a reply missing a required field, so every model output goes through schema validation with a retry.
```
**How Meta's models performed vs others**
```
SAM 3.1 gave persistent IDs across frames from a one-word prompt, which a per-frame detector cannot do, and it produced no false alerts on three clips with no elephants. Muse Spark followed our structured-output schema most of the time, and a retry handled the rare miss. We have not had Sinhala or Tamil quality reviewed by a native speaker, so we cannot compare it yet.
```
**What matters most when choosing a model or tool**
```
Reliable structured output and tool calling, cost per call (video is expensive at many cameras), non-English quality, and latency, because a warning has to arrive in time.
```
**What makes it hardest to switch**
```
SAM 3.1 has no drop-in equivalent for text-prompted video tracking, and its special-token output needs a custom parser. Our prompts and schemas are also tuned to Spark's behaviour.
```
**What would stop you using Meta's models in production**
```
Per-clip cost and latency at many cameras, no on-premise option for SAM (a government deployment may require data to stay in country), and unproven night-time accuracy.
```
