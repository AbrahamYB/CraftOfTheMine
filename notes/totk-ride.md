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

## Frame-by-frame maps (60 fps frames of the two recordings, mapped 2026-11-14)

### down.mov, the rider and the marker
| t (s) | ticks to the cut | what happens |
|---|---|---|
| 0 - 2.70 | | hold: waist deep in the marker, torso and head tipped forward, hands out on its rim, looking out. Marker: dim teal pool, two wavering cyan rings (thick and thin stretches, strands branching off), ripples leaving the outer ring, each spreading to a fixed distance and fading |
| 2.70 - 2.87 | -5.5 to -2 | the pull: one whole turn about the rider's own axis while they dip to the shoulders; the button prompt fades; the rings draw in about 15 % |
| 2.87 - 2.97 | -2 to 0 | upright again, the arm goes up, glowing hand over the head |
| 2.98 | 0 | hard cut to the void: black with drips hanging from the top, helix round the rider |
| 2.98 - 3.88 | 0 - 18 | falling: rider enters at 73 % of the screen height, is at 46 % by 3.45 s and 58 % at the end; turns about their own axis, about one turn; right hand stays up over the head, left arm out to the side with the forearm up, legs hang, one knee bent; drips gone by 3.4 s; stars are dots |
| 3.88 | 18 | hard cut to the room: rider standing, knees a little bent, arms forward, straight by 4.3 s |
| 3.96 - 4.2 | | white-cyan glow on the floor at the feet, grows to about 1 block |
| 4.36 - 5.26 | | cyan slashes swirl up out of the glow, turn green, widest about 5.0 - 5.2 s |
| 5.36 - 5.6 | | slashes gone, glow fades |

### up.mov, the rider and the ripple
| t (s) | ticks to the cut | what happens |
|---|---|---|
| 4.12 - 4.25 | -10.5 to -8 | ribbons arrive; semi-crouch: knees bent, feet apart, torso tipped forward |
| 4.25 - 4.40 | -8 to -5 | arm up; the rider lifts barely off the floor; the right knee comes up in front, the left leg trails |
| 4.40 - 4.65 | -5 to 0 | that pose held, floating just off the floor, glow on the floor under them |
| 4.65 | 0 | hard cut to the void |
| 4.70 - 5.45 | 1 - 16 | the ripple: thin rings round the rider's waist, seen from a little above; each is born at the rider, spreads to about 2.5 body widths and fades as it gets there; about four of them, each fainter; the body is slack and curled in a little, legs still |
| 5.35 - 5.5 | 14 - 17 | ripple gone; the dive begins: the body tips, the legs start to kick |
| 5.6 - 6.0 | 19 - 27 | arms pull back, then fold into the prayer over the head; held to the end |

### What the mod does with the maps
* Server: an ascent floats 0.6 of a block in its first 10 ticks (`TerminalRide.FLOAT_TICKS`, `FLOAT_SPEED`), then climbs as before; the clients cut to the void as the float ends. Before this the rider was about 19 blocks up at the cut.
* Return: the rider's own client draws them down 0.8 of a block over ticks 4 - 14 (`MudHandClient.sink`); `TotkRide.pose` leans them on their hands; in phase 2 `TotkRide.spin` turns the drawn model once and `sunk` dips it; the arms go up over the last 2.5 ticks. The marker is `TotkRide.marker` (its own alpha-blended pass, fixed to the ground). In the void the rider turns 20 degrees a tick; `landed` is the bent-knee landing; the landing glow and slashes are squeezed into the 18 ticks the server holds the rider (footage: 34).
* Ascent: `crouch`, `LEGS_HELD`, `ARMS_SOFT`, `ripples` and `RIPPLE` (16 ticks) follow the table above.
* Still different: the Return's hold is 2.1 s (footage 2.7 s or more), its void about 2 s (footage 0.9 s), the ascent's void about 2.4 s (footage 3.8 s).

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
