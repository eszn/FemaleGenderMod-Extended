# Validation — 4.3.0-extended.2

Test environment: Minecraft 1.21.1, NeoForge 21.1.248. Build/unit/GameTests used Java 21.0.6 on Windows. Final scripted clients used Java 21.0.12 under Ubuntu 26.04 in WSL, with Ubuntu's packaged llvmpipe 26.0.3 in Xvfb. Test worlds/configuration were isolated; reports and captures were copied into this fork's `runs` directory.

## Automated results

**The final revision passed the automated build, unit, dedicated-server and two-client checks below.** Two real Minecraft clients exercised the production renderer and recorded 480 physics frames. `build/verification/result.json` records the exact source hash; GIF encoding and packaging require that hash to match the current source. The final client run took about 127 seconds.

Windows client startup still stalls inside native `glfwCreateWindow`, before the model loads; a minimal hidden-context probe reproduces that host graphics problem. The final verification used a separate Linux graphics path. A third-party Windows graphics archive previously flagged by Defender was never extracted or executed, and its downloader was removed. The Linux run used Ubuntu's packaged libraries and reused the compiled Java output; no Windows driver/JDK replacement or desktop input was involved.

| Check | Result |
| --- | --- |
| Gradle unit tests with local Jenny asset | 43 passed, 0 failed |
| NeoForge dedicated GameTests | 5 passed on the current production revision |
| Build, API jar, source jar | Passed |
| Two real Minecraft clients connected to a dedicated localhost server | Passed on the final revision under Ubuntu/Xvfb |
| Optional extension channel negotiation | Passed on both clients |
| Full 1.2 / 0.9 bust values and body settings delivered to the other client | Passed |
| Leaving and returning to tracking range | Passed |
| Actual player and armor rendering in standing, crouching, swimming and elytra poses | Passed, finite vertices and UVs, normalized normals |
| Render context cleanup between players | Passed |
| Natural and Rounded shapes, front/side/rear/three-quarter captures | Inspected with skin and a neutral diagnostic material from the game's own framebuffer |
| Small, medium and maximum body/bust profiles | Three-quarter front and rear framebuffer comparisons |
| Cached geometry after changing and restoring a profile | Exact vertex fingerprint restored |
| Natural shape with leather and diamond armor | Inspected from the game's own framebuffer |
| Desktop control, mouse/keyboard automation | None used |

The Jenny importer reads 822 selected authored quads. Tests compare the retained source vertex positions and surface area with the archive, require the pelvis to remain attached to the torso, and exercise dynamic sizing, existing position controls and feathered physics attachments. The final render uses cleaned soft boundaries when present and replaces the flat trunk/thigh panels with rounded surfaces. Additional checks cover matching hip boundaries, separated resting legs, finite refined normals, outward face winding, exclusion of overlapping original panels, the hip-to-leg blend, pinned rear chest attachments and monotone depth at small sizes. Three tests requiring third-party geometry are skipped in public source-only builds; the other 40 run without it.

The separately labeled `jenny-cpu-*.gif` previews export 160 frames per support mode from the production mesh, deformation, motion input and shared spring-force calculations. Java exports finite positions/normals; a Python CPU rasterizer interpolates normals per pixel. That preview uses a neutral material, vanilla-style scripted joint rotations and a garment inflation envelope. It does not run the Minecraft renderer, texture atlas, armor layers or network. The final `jenny-physics.gif`, `jenny-leather-physics.gif` and `jenny-diamond-physics.gif` are fresh Minecraft framebuffer recordings of the final source revision.

Three automated physics sequences record 160 framebuffer images each, encoded at 20 frames per second into eight-second GIFs. A preview player uses the production player renderer, production spring updates, and a controlled trajectory through rest, walking, turning, jumping/landing and settling. The preview camera follows its position so body motion can be compared in a fixed viewport. Four views show ordinary skin and a neutral diagnostic material. The test asserts a nonzero response without armor, decay after stopping and exactly zero secondary motion under diamond armor. This scripted preview is separate from the real two-client synchronization test; it does not claim both clients display pixel-identical motion.

The physics tests exercise 200,000 randomized extreme-input ticks (1.2 million solver substeps), impulse decay, invalid inputs, constant-speed motion, teleport/dimension/tick-gap resets and render-rate independence. Geometry tests check pinned shoulders/knees, torso-leg field continuity, symmetry, a noncrossing inner leg seam at rest, size bounds, inflated garment ordering, rounded profiles and position controls. Regression checks cover pelvic rather than mid-thigh projection, a shallow rear cleft, curved lower attachments, a gentle natural upper breast slope, a joined chest bridge under opposing spring phases, width/height/depth growth, matching rounded-corner tangents, lower distance-detail sample counts and a positive sampled Jacobian for the maximum chest deformation.

Profile tests cover all shapes and maximum values, exact 42-byte round trips, every packet truncation, unknown enum values, non-finite data and out-of-range inputs. In-server tests exercise spoofed sender rejection, final-value coalescing, legacy size encoding, disk persistence, old configuration migration and armor-stand item data.

The client test calls Minecraft's actual player renderer with a recording vertex consumer. It requires the shaped path to produce additional mesh vertices, checks finite coordinates/UVs and unit normals, and exercises standing, crouching, swimming and elytra poses. It also checks that changing a profile invalidates the geometry cache and restoring it produces the original vertex fingerprint. This confirms that the installed mixins and caches actually apply. Close geometry has more samples to support Minecraft's interpolated vertex lighting; more distant players use reduced detail. These checks are not a many-player performance benchmark.

Captures come from Minecraft's framebuffer API. The optional neutral diagnostic material replaces textures only in the development preview, allowing contours to be assessed independently of skin artwork. An additional base-surface diagnostic hides outer skin layers and arms to isolate lighting and attachment defects. Neither diagnostic changes the release mod's material or visibility behavior. Visual defects found during this revision included duplicate breast surfaces, insufficient lighting samples, Classic normals after scaling, and the 0.1-pixel difference between the nominal half-leg spacing and Minecraft's actual leg pivots.

## Reproduce

Set `JAVA_HOME` to a Java 21 installation, then run:

```powershell
./gradlew.bat test build runGameTestServer --no-daemon
python tools/verify_runtime.py
python tools/assemble_physics_gifs.py
```

For the separate CPU preview, after importing and smoothing the local asset:

```powershell
python tools/export_cpu_preview.py
python tools/render_cpu_preview.py
```

For real client verification through WSL, first compile `gameTest` and prepare the three verification runs with Gradle on Windows. Ubuntu needs Java 21, Xvfb, xauth and functioning software OpenGL packages. From Ubuntu, run `xvfb-run -a python3 /path/to/checkout/tools/verify_linux_runtime.py`. The launcher copies compiled output and cached libraries into a fresh `/tmp/fge-verification-*` directory, starts only its own server/clients, checks their reports, and copies successful captures back. Its logs are in `build/verification-linux`. No browser or desktop automation is used.

The runtime script uses port **25589 on localhost**, creates its own test worlds and offline test profiles, launches two scripted clients, hides their windows after initialization, writes `build/verification/result.json`, and shuts down its own processes. A loading window can appear briefly while Minecraft starts. Python's standard library is sufficient for launching and capturing; GIF encoding additionally uses Pillow. Minecraft requires a working graphics driver for the client portion. Do not use the isolated offline test server as a public server.

Runtime logs: `build/verification/{server,a,b}.log`. Client reports and images: `runs/verification-{a,b}/verification`. Unit report: `build/reports/tests/test/index.html`. The GitHub build workflow also runs unit tests and dedicated GameTests.

## Limits of these results

The two-client test used this fork on both clients and the dedicated server. Mixed upstream/unmodded connections have a compatible optional protocol design and legacy encoding coverage, but were not separately run end to end. Vanilla humanoid skin and armor paths were exercised; this does not certify every third-party renderer, shader pack, custom armor model, or modpack. Dye, trim and glint continue through the original body armor passes; their full combinatorial visual matrix was not manually inspected.

The solver is a restrained real-time approximation, with bounded cosmetic motion and pinned geometry. It is not a volumetric soft-body/contact simulation, does not change hitboxes, and does not simulate material fracture or physical clothing collision. The natural coefficients are design choices informed by the sources in [PHYSICS-RESEARCH.md](PHYSICS-RESEARCH.md), not a universal measured human-body calibration.
