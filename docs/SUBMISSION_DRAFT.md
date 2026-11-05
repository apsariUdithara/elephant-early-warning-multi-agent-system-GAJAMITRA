# Submission form answers (draft)

Every claim below was checked against the repository and real runs on the date of the last edit. If anything changes before submitting (eval numbers, native-speaker review, a real SMS test), update the matching sentence here and in `docs/DEMO_SCRIPT.md`. Judges check the repo and the video against this text.

## Short fields
- **Team Name:** GajaMitra
- **Track:** Indie Developer
- **Problem statement:** Build for Your World
- **Most Viral X post link:** the link to your demo post (post it first, then paste; suggested text is at the end of `docs/DEMO_SCRIPT.md`)
- **Public GitHub Repository:** https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra
- **3-Minute Demo Video:** YouTube (unlisted is fine). Open the link in a private window first.
- **Partner technologies feedback (150 chars max):** `Did not use Fireworks, Modal or Baseten; all inference ran through the Meta Model API (SAM 3.1 and Muse Spark 1.3).`

## Project Description (limit 3,000)
```
Every year in Sri Lanka, hundreds of elephants and more than a hundred people die in human-elephant conflict, mostly at night on the edge of villages. Warnings come too late: existing Sri Lankan systems are detectors trained for one site, and they stop at "elephant detected". Nobody tells the village how long it has, or what to do.

GajaMitra is an early-warning system that goes from video to a villager's phone. A boundary camera clip goes in. Out come a tracked herd, a prediction of when it reaches the village, and a short alert in Sinhala, Tamil and English.

How it works
1. SAM 3.1 tracks every elephant in the clip from the text prompt "elephant". Object IDs stay the same across frames, so no separate tracker is needed.
2. Deterministic code turns those tracks into numbers: where each elephant touches the ground, its velocity, its distance to the village boundary, and an arrival time. It assigns a risk level: low, medium, high or critical. A herd standing still at the fence is never rated low.
3. A Muse Spark responder agent reads keyframes and the tracker data, judges behaviour (calm, alert, warning, aggressive, conflict-risk, calves present) and proposes alerts.
4. A second Muse Spark agent, the verifier, checks the proposal against the same evidence and can send it back for revision.
5. A deterministic policy guard caps the alert's severity to what the tracker measured. The AI proposes; the code decides.
6. Villagers get the alert by SMS in their language. An escalation to "danger" waits for a wildlife officer's approval, but a warning is never delayed, and a timeout sends the escalation anyway.

Why it is safe to act on
Every alert and SMS carries an idempotency key, so a crash or retry can never send twice. The Spring Boot backend re-checks the guard independently and refuses any alert it has no tracker risk for. Python never sends anything itself; every side effect is a tool call on the Spring backend with an audit log and a shadow mode.

What was built during the hackathon
All of it: the tracking maths, the LangGraph pipeline, the Spring Boot backend (alerts, SMS, approvals, audit log, key-protected MCP server), the RabbitMQ worker, a React dashboard in English, Sinhala and Tamil (analyses list, live analysis page, officer approval queue with the frames the agents saw, new-analysis page with a boundary picker, admin pages), and the CI.

What runs end to end today: a clip is uploaded, tracked by SAM 3.1, rated, judged by the two Spark agents, limited by the guard, and the alerts are stored and shown on the dashboard, with escalations waiting for officer approval and sent automatically on timeout. We ran this against the real Meta API on a critical-risk case and a high-risk case with approval. The demo site runs in shadow (test) mode: alerts are stored and no SMS goes to real phones. Twilio sending is built and tested against a mocked service, but not yet against the real one. Not yet done: night footage, live camera streams, portrait phone video, and native-speaker review of the Sinhala and Tamil text.

How we checked it: 182 Python tests, 113 backend tests (Postgres and RabbitMQ in containers) and 81 frontend tests (the first two run in CI), plus a labelled evaluation. Our first run on 13 labelled clips passed 6 of 13. It found two real problems we are fixing: calm herds near water were rated medium, and reversing the village side did not lower the risk. Apart from the two Hurulu cases, the labels were our predictions made before the run, so this is a first honest measurement, not a benchmark.

Impact
The same pipeline works in India and Africa, with a different language list. It needs no site-specific training data, so a new site needs only a camera and a boundary line. The next step is a shadow-mode pilot with Department of Wildlife Conservation rangers: log what GajaMitra would have sent, and compare with real events.
```

## Describe your use of Meta products (limit 3,000)
```
GajaMitra uses two Meta models through the Meta Model API, each for what it is best at.

SAM 3.1 (video segmentation and tracking)
We upload the camera clip and prompt SAM 3.1 with one noun phrase, "elephant". It streams per-frame boxes with object IDs that persist across frames. We use those persistent IDs directly: they give each elephant a track, and from the track we compute velocity, distance to the village boundary and arrival time. The IDs are what make the prediction possible; a per-frame detector could not give us a speed. We cache the raw SAM output by video hash so evaluation reruns don't pay for SAM again.

Muse Spark 1.3 (vision, structured output, tool use, multilingual writing)
- Vision: the responder reads keyframes with SAM's boxes and IDs drawn on, and judges herd behaviour and whether calves are present, which a tracker cannot do.
- Two agents: a responder proposes alerts, and a separate verifier agent with its own prompt critiques the proposal against the same evidence. We count how often the verifier changes the outcome.
- Structured output: both agents return schema-checked objects. Anything that does not match is rejected.
- Multilingual: the alerts are written in English, Sinhala and Tamil. The Sinhala and Tamil text has not yet been reviewed by a native speaker; we say so on the screen and in the video.
- Tool calling: side effects are tools on our backend, exposed over MCP.

Design choice: models propose, code decides. SAM and Spark supply perception and judgement; deterministic, tested code sets risk levels and limits alert severity, so a model mistake cannot send a village a "danger" alert the tracker does not support.

Evaluation: we run the whole pipeline against labelled clips with the real models, and the eval runner has an AI-judge for alert quality (location, herd count, instruction, brevity) written for Muse Wonder. The judge is built and unit-tested but not yet connected to the runner, so we report no judge scores.
```

## Why are Meta's models important to your solution? (limit 3,000)
```
Without these models, GajaMitra would need a trained detector for every site, and that is exactly the gap in Sri Lankan elephant-detection work. Published systems from Moratuwa, SLIIT, Peradeniya and SEUSL train detectors on local images. Each works for one camera, in one place, at the times of day it was trained on, and each stops at "elephant detected". Collecting and labelling night footage for each new village is slow, and the villages with the worst conflict are the least likely to have the data.

SAM 3.1 removes the training step. A text prompt, "elephant", is enough, and the persistent object IDs give us tracks, which is what lets us estimate speed and arrival time instead of just detecting. Muse Spark adds what a detector cannot: reading behaviour (is this herd feeding, or charging?) and writing a clear alert in Sinhala and Tamil, languages where small, local-first models are weak. Both come from one API, so one developer could build the whole pipeline.

Research angle: a 2026 study (Varma et al.) classifies elephant behaviour with a trained YOLOv8 + LSTM model, and notes that few studies address behaviour recognition for conflict prevention. We found no work using foundation models for elephant behaviour or early warning. GajaMitra tests whether zero-shot models can match trained ones on tracking, behaviour and warning time. Our first measurement is small and honest: 6 of 13 labelled clips matched our expected outcome, no-elephant clips gave no alert, and the misses show where to work next.

Why it matters beyond Sri Lanka: the same approach works wherever people and elephants share land, with only a new camera, a boundary line and a language list.
```

## Survey questions (end of the form)
Answer these from what actually happened tonight. Don't invent a story; Meta reads them as product feedback. Useful honest candidates:
- **Biggest obstacle:** the SAM 3.1 video request format (the docs showed images, not video upload; we followed a cookbook script). [[Replace with the real error you hit in the first live run, and what fixed it.]]
- **How Meta's models performed:** compare with models you have used before, and say what was better and what was worse. Mention tool-calling reliability and Sinhala/Tamil quality only if you tested them.
- **What matters most when choosing a model:** for this project, cost per call, structured-output reliability and non-English quality.
- **What makes switching hard:** SAM 3.1's output format is special-token text, not JSON, so we wrote a parser around it.
- **What would stop production use:** latency of video segmentation, per-clip cost at many cameras, data residency for a government deployment, and no on-premise option for SAM.
- **Ratings (likelihood, opinion):** your honest answer.
