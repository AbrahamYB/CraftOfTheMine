# TotK ride: study of the two recordings

Both are the Ascend travel (times are video timestamps from the real frame times; 1 tick = 50 ms). Characters, UI and rooms ignored.

## up.mov (to the plot), 17.7 s
| t (s) | ticks | beat |
|---|---|---|
| 0 - 2.0 | | nothing |
| 2.0 - 2.9 | 0-18 | scene dims; green glyph sparks wind round the arm |
| 2.9 - 4.0 | 18-40 | dim scene, green target glyph (ring + 5x5 grid) shown above |
| 4.12 - 4.65 | 44-54 | hard cut: two cyan ribbons cross round the rider, cyan glow disc on the ground, ribbons turn green |
| 4.65 | 54 | hard cut to the void: black with bright green flame spikes, bright haze at the middle; teal within ~0.3 s |
| 4.65 - 8.43 | 54-130 | dark teal void, stars drifting down, double helix (green, white-green core, thin strands), spikes slide off the bottom in ~1 s |
| 8.43 | 130 | hard cut to the arrival: pale green haze (mean rgb 72,91,63) thinning in ~0.3 s over a green fog tint lasting ~1 s |
| 9 - 13 | | cyan ring + hex bubble round the rider (waiting) |
| 13.0 - 14.5 | | bubble pops, cyan-green glow burst on the ground, green sparks swirl, gone in 1.5 s |

## down.mov (Return), 8.6 s
| t (s) | beat |
|---|---|
| 0 - 2.85 | cyan ring on the ground round the rider (exit marker); it pulls in, the rider turns to a cyan glow |
| 2.97 | hard cut to the void burst (same as up), helix for only 0.92 s |
| 3.88 | hard cut to the room: bright cyan-white glow disc on the ground, grows 0.4 s, green ribbon sparks swirl round the feet 4.4 - 5.2 s, fade by 5.6 s; no haze |

## Colours and sizes
| thing | rgb | size |
|---|---|---|
| void background | (0,70,60) | full screen, black at the cut |
| ribbons early | (70,235,235) cyan, to (80,255,90) green | ~0.25 w wide helix, period ~0.9 h |
| helix core | (190,255,215), halo (70,255,150) | main strands ~1 % w |
| spikes | (90,255,190) | 0.3-0.7 h tall, 1.5-4.5 % w |
| stars | (170,255,200) | 1-2 px, ~90 |
| ground glow | (120,255,230) | ~1 block radius |

## What the mod does with it (TotkRide.java)
Phase 1: glyph sparks + glow + ceiling glyph (Return: ring contracts, bubble, glow). Phase 2 first 10 ticks: world ribbons + ground disc, dim, FOV +12 %, blur. Then the void (HUD, covers world, world change and loading screen; the rider's model drawn small in it) until the rider is 4.5 blocks above the ground in phase 4; hard cut, haze/tint (up) or cyan flash + ground glow and swirl (Return); up: bubble then pop in phase 5.

## Where fixed server phases prevent an exact match
- Phase 2 up is about 25 ticks (60 blocks at up to 2.6 b/t), the footage void is 76 ticks (3.8 s). Ours: phase 2 remainder + hold + most of the descent, about 45-55 ticks.
- Return: footage void is 18 ticks; ours is about 40 (sink + hold + descent from 60 blocks).
- The 2 s of waiting in the bubble (9-13 s) is gameplay, not ride; the release (18 ticks) holds the bubble ~8 ticks and pops it in ~8 more (footage pop+sparks ~30 ticks).
- No sky/sun replacement is needed since the void is a HUD cover.
- Not visually verified: the user's game was open, the harness could not run.

## Requests
- TerminalRide: make phase 2 (kind 0) about 70 ticks (lower MAX_SPEED or taller RISE_HEIGHT) and the Return sink visible longer, to match the 3.8 s / 0.9 s void lengths.
- MudHandClient: the rider's entity id must stay the same across the world change (assumed).
