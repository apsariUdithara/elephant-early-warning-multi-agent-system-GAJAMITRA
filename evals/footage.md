# Eval footage

| File (local only, git-ignored) | Source | Licence |
|---|---|---|
| `scratch/footage/hurulu_15s.mp4` | Hurulu elephants, Wikimedia Commons, by Cherubino | CC BY-SA 3.0 |

Cases with `"clip": null` are waiting for footage. Record each new clip's source and licence here before using it.
Expected risks for the two Hurulu cases are the observed results, not independent labels; relabel them by hand.
| `scratch/footage/kaziranga_1.mp4`, `kaziranga_2.mp4` | Indian elephant in Kaziranga National Park, March 2025, files 01 and 02, Wikimedia Commons, by Tisha Mukherjee | CC BY-SA 4.0 |
| `scratch/footage/pinnawala_15s.mp4` | Elephants in Sri Lanka, Wikimedia Commons, by McKay Savage (first 15 s) | CC BY 2.0 |

| `scratch/footage/pinnawala_bathing_15s.mp4` | Pinnawala-Elephant-Orphanage-03, Wikimedia Commons | CC BY-SA 3.0 |
| `scratch/footage/paddy_terraces.mp4` | Paddy Fields, Wikimedia Commons | CC BY-SA 4.0 |
| `scratch/footage/grass_field.mp4` | A rice field, Wikimedia Commons | CC BY-SA 3.0 |

| `scratch/footage/assam_herd_15s.mp4` | A herd of elephant of Hoollongapar Gibbon Sanctuary, Jorhat, Assam, Wikimedia Commons (first 15 s of 46 s) | CC BY 4.0 |

All clips were inspected by eye before use (the Assam clip across its whole length: a herd with calves crossing a road toward the camera while villagers watch; it is a crossing, not a village approach), re-encoded to small mp4 files and trimmed to 15 s or less.

The two `flipped-village-side` cases are DERIVED from the Hurulu and Pinnawala clips with only the village side reversed. They are not independent footage.

News, YouTube, Storyful and Pond5 clips are copyrighted or paid and are NOT used until permission or a licence is obtained. Pexels and Pixabay block scripted downloads (HTTP 403): download by hand and record the licence here. The tabletop toy case needs your own recording.

| `scratch/footage/empty_forest_road_15s.mp4` | Elephants crossing road.webm, Wikimedia Commons, by L. Shyamal (no elephants in the sampled frames despite the title) | CC0 |
| `scratch/footage/river_approach_15s.mp4` | `215845_medium.mp4`, downloaded by hand by the developer | **licence and source URL not yet recorded: confirm before any public use** |

Inspected but NOT used: `istockphoto-943394016` (Getty watermark, no licence), `herd-in-the-shade.mp4`, `jeeps.mp4`, `219323_medium.mp4` (sources and licences unknown), the Malawi and Pinnawala-01 Commons files (calm herds, not needed yet).

## Results

Fifth real run: 13/13 cases with footage pass (16 cases, 3 without footage).

**How to read this honestly.** The earlier 6/13 was my labels being wrong, not the tracker. After reading `track.py` I relabelled seven cases:
- Calm elephants are `medium` by design: a close-up elephant is within `NEAR_BODIES` (3) body heights of the line (invariant 1). A real no-false-alarm test needs distant elephants.
- The two flipped-side cases and both approach clips are `critical` because the boundary I drew put the elephants on the village side (`dist <= 0` is critical). The flipped cases only show that reversing the side is respected; the approach clips are NOT genuine approach tests.

So 13/13 is partly circular: only the Hurulu pair came from observed results and the rest were relabelled after seeing them. It shows the pipeline is consistent with the rules, not that it is accurate. Still needed: clips where a herd is outside the line and moving toward it, distant calm elephants, and labels set by someone who knows the scene.

To recreate the clips: `python evals/download_footage.py` (downloads the open-licence ones from Wikimedia Commons into `scratch/footage/`, trimmed to 15 s; large files, slow first run). `river_approach_15s.mp4` is not included because its source is unknown. The Hurulu clip is cut from the start of the source file, which may be a different 15 s than the one used for the first runs, so its results can differ.
