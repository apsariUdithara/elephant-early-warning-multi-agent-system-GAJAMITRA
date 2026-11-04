# Eval footage

| File (local only, git-ignored) | Source | Licence |
|---|---|---|
| `scratch/footage/hurulu_15s.mp4` | Hurulu elephants, Wikimedia Commons, by Cherubino | CC BY-SA 3.0 |

Cases with `"clip": null` are waiting for footage. Record each new clip's source and licence here before using it.
Expected risks for the two Hurulu cases are the observed results, not independent labels; relabel them by hand.
| `scratch/footage/kaziranga_1.mp4`, `kaziranga_2.mp4` | Indian elephant in Kaziranga National Park, March 2025, files 01 and 02, Wikimedia Commons, by Tisha Mukherjee | CC BY-SA 4.0 |
| `scratch/footage/pinnawala_15s.mp4` | Elephants in Sri Lanka, Wikimedia Commons, by McKay Savage (first 15 s) | CC BY 2.0 |

All clips were re-encoded to small mp4 files and trimmed to 15 s or less.

## Results (first real run, 5 cases with footage)

3/5. The two Kaziranga cases were labelled `low` before the run (one calm elephant far from the boundary) but the system said `medium` and sent a warning. That is a real finding to look at (the NEAR_BODIES floor or the tracker's distance maths), not something to fix by changing the label.
