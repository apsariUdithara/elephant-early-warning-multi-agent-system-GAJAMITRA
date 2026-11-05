# GajaMitra: 3-minute demo script

**Goal of the video:** in three minutes a judge should believe three things: (1) the problem is real and the gap is real, (2) a clip really becomes a warning on a villager's phone through Meta's models, (3) the system is safe enough to trust, and the team is honest about its limits.

**One-sentence pitch (say it twice: opening and close):** *"GajaMitra turns a camera clip into a warning in a villager's own language, before the herd reaches the fence, and no AI mistake can send a false alarm or hold back a real one."*

**Rules for this video**
- Show only what runs. Every screen in the video is the real app, on a real run. No mock-ups, no slides of features that do not exist.
- Say "test mode" out loud when it matters: the demo site is in shadow mode, so alerts are stored and no SMS goes to a real phone. Judges trust a team that says this first.
- Credit the clip on screen (Hurulu elephants, Cherubino, CC BY-SA 3.0, Wikimedia Commons).
- Voice-over about 400 words. Speak slowly. Captions in English throughout.

---

## Timeline (3:00)

| Time | On screen | Voice-over (say this) |
|---|---|---|
| **0:00 – 0:15** Hook | Cut-in of the real Hurulu clip (herd walking toward a line), then fade to the title "GajaMitra". Big text: *"Hundreds of elephants and over a hundred people die every year in Sri Lanka."* | "In Sri Lanka, human–elephant conflict kills hundreds of elephants and more than a hundred people every year, mostly at night, at the edge of a village. The warning almost always comes too late." |
| **0:15 – 0:30** Gap | Simple slide, two columns: *"Today: a detector for one site says 'elephant detected'"* / *"GajaMitra: how long you have, and what to do, in your language."* | "Existing systems are detectors trained for one camera. They stop at 'elephant detected'. Nobody tells the village how long it has, or what to do. GajaMitra goes from video to the villager's phone." |
| **0:30 – 0:55** Upload | Screen recording: **New analysis**. Pick site *Galgamuwa* (badge: test mode). Choose the clip. Click two points along the boundary, one point on the village side. Click **Start the analysis**. | "An officer uploads a boundary-camera clip and draws the village boundary: two points on the line, one on the village side. That is all the setup a new site needs. No training data." |
| **0:55 – 1:25** Perception and risk | **Analysis page**, status moving *Analysing → …*. Then show the risk badge and the notes line ("Herd of N within X body heights of the boundary"). Optional: one annotated keyframe with SAM boxes and IDs (the evidence frame). | "Meta's SAM 3.1 tracks every elephant from one text prompt, 'elephant'. Object IDs stay the same across frames, so we get real tracks: speed, distance to the boundary, and an arrival time. Plain tested code, not an AI, turns that into a risk level. A herd standing still at the fence is never rated low." |
| **1:25 – 1:50** Agents and guard | Same page: officer report line, then the alert card **Warning · Sent immediately** with the English, Sinhala and Tamil texts and the *"Test mode: stored, not sent to phones"* tag. | "Two Muse Spark agents read the frames and the tracker data. One proposes the alert; a second one challenges it. Then a deterministic guard caps the severity to what the tracker actually measured. The AI proposes, the code decides. The warning goes out at once, in Sinhala, Tamil and English." |
| **1:50 – 2:25** Approval | Switch to **Approvals**. Show the notice (*"The warning … has already been sent"*), the escalation text, the **evidence frames**, the countdown. Click **Approve and send now**, show the confirm step, confirm, show "Decision saved." | "A bigger escalation, 'danger', waits for a wildlife officer. The officer sees the exact text and the frames the agents saw. But a warning is never delayed by approval, and if nobody answers, the escalation is sent automatically when the timer runs out. The officer can never be a single point of failure." |
| **2:25 – 2:45** Safety proof | Split screen or quick cuts: terminal showing `pytest 182 passed`, backend `113 tests, BUILD SUCCESS`, frontend `81 passed`; then the eval report line. | "Every alert carries an idempotency key, so a retry never sends twice. The Spring Boot backend re-checks the guard on its own and refuses any alert with no stored tracker risk. Python never sends anything: every side effect is an audited tool call. And we measure it: 13 labelled clips, run against the real models." |
| **2:45 – 3:00** Honest close | The eval summary on screen, then the title card with repo URL. | "Our first real eval passed 6 of 13. It found things we are now fixing: calm herds scored medium, and flipping the village side did not lower the risk. We'd rather show you that than hide it. GajaMitra: a warning in time, in the villager's language, that you can trust." |

---

## Before you record (checklist)

1. **Stack up:** `docker compose up -d`, backend, worker and frontend running; sign in as an admin you created through the environment variables. Do not show `.env` or any key on screen, in the terminal or in the browser URL.
2. **Pre-run the analysis once** so SAM's result is cached by video hash (`data/sam-cache`). Record the second run: it finishes fast and you control the pace. Say nothing about speed that is not true; if a run takes a minute, cut the wait in editing and keep the status text visible.
3. **Choose the run for each segment:**
   - Perception, agents and warning (0:55–1:50): the **critical-risk** run (boundary `0,0.62,1,0.62,0.5,0.95`): fast, clear warning.
   - Approval (1:50–2:25): the **high-risk** run with approval (boundary `0,0.8,1,0.8,0.5,0.98`), so there is an escalation to approve.
4. **Site in test mode.** Show the mode badge once. If you switch a site to live for any reason, never put a real phone number on screen. Phone numbers in the villager list must be masked.
5. **Browser:** 1280×720, light theme, English first. Zoom 110% so text is readable in a compressed video. Close other tabs and notifications.
6. **Record the screen at 1080p, voice separately** (clean mic, no echo), then align. Add captions. Keep the cursor slow and deliberate.
7. **Rehearse the whole path once** with a stopwatch. Anything over 3:00 gets cut from the Gap segment, not from the safety segment.
8. **Verify the text you read matches the screen:** risk labels, counts and the 6/13 figure come from the last real run. If the eval is re-run before recording, update the numbers in the script and in the submission.

## Fallbacks (decide before you hit record)
- **Live run fails or Meta API is slow:** use the cached run; show the saved analysis page from the list. The video can say "this run is from earlier today", which is true.
- **SSE shows "Live updates stopped":** reload the page; it is a real state the product handles, but do not leave it on screen.
- **The approval timer expires before you click:** that is a feature. Show the automatic send, and say so.

## What NOT to claim
- Do **not** say SMS reached real phones. Twilio sending is built and unit-tested against a mocked HTTP client, but was not tested against the real service. Say "test mode: stored, not sent".
- Do **not** say the Sinhala and Tamil were reviewed by a native speaker unless that happens before you record. Say "drafted by Muse Spark; native review in progress".
- Do **not** claim accuracy numbers beyond the eval you actually ran. Eval labels, apart from the two Hurulu cases, are predictions made before the run.
- Do **not** claim it works at night, on phone-rotated portrait video, or with live camera streams. It does not yet.
- Do **not** show or mention API keys, the `.env` file or credentials.

## Likely judge questions (have one-line answers ready)
| Question | Answer |
|---|---|
| Why not just fine-tune a detector? | A detector needs labelled night footage per site; the worst-hit villages have none. SAM 3.1 needs a boundary line and a text prompt. |
| What stops the AI sending a false "danger"? | The guard caps severity to the tracker's measured risk, and Spring re-checks it independently. Both read the same policy table. |
| What if the officer is asleep? | Warnings are never held. The escalation is sent automatically on timeout. |
| What if the service crashes mid-run? | Jobs resume from the LangGraph checkpoint; every send has an idempotency key, so a retry never double-sends. |
| What did the eval show? | 6 of 13 on first run, with two real findings we are fixing. Predicted labels, not independent ones. |
| What is next? | A shadow-mode pilot with Department of Wildlife Conservation rangers: log what GajaMitra would have sent, compare with real events. |

## X (Twitter) post (for the "Most Viral X post" field)
> 🐘 In Sri Lanka, elephant–human conflict kills 100+ people a year. Warnings come too late. We built GajaMitra in the Meta Global AI Developer Hackathon: SAM 3.1 tracks the herd, Muse Spark agents propose the alert, plain code limits it, an officer approves escalations, and villagers get an SMS in Sinhala, Tamil and English. 3-min demo: [link] #MetaAI #BuildForYourWorld
