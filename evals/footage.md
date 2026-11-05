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

## Results (third real run, 11 cases with footage)

5/11. All labels except the Hurulu pair are predictions made before the run, not independent labels.
- Passing: Hurulu critical and high, Pinnawala at the boundary, both no-elephant clips.
- The three calm-elephant clips all came out `medium` (predicted `low`).
- The Assam herd came out `critical` (predicted `high`).
- Both flipped-village-side cases came out `critical` (predicted `medium`). Reversing the village side did not lower the risk at all, which may mean the side is ignored once elephants are at the line. Investigate before trusting the geometry.

These are real findings to look at, not to fix by changing labels.
