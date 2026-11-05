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

## Results (fourth real run, 13 cases with footage)

6/13. Passing: Hurulu critical and high, Pinnawala at the boundary, three no-elephant clips (paddy, grass, empty road).
- Calm elephants (3 clips): `medium`, predicted `low`.
- Both approach clips (Assam crossing, river approach): `critical`, predicted `high`. My labels may simply be too low for elephants that fill the frame near the line; a person should decide.
- Both flipped-village-side cases: `critical`, predicted `medium`: reversing the side did not lower the risk.
Only the Hurulu labels are observed results; every other label is my prediction before the run.
