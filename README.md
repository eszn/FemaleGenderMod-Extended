# Female Gender Extended — NeoForge 1.21.1

An independent fork of [FemaleGenderMod/FemaleGenderMod](https://github.com/FemaleGenderMod/FemaleGenderMod), based on its `neoforge-1.21.1_sync` branch at `1733351d389d149e11da809e484957668a24ccdb`. Original mod by WildfireRomeo and contributors; fork additions by Female Gender Extended contributors.

## Features

- Bust slider extended from the original 100% maximum to 150% (internal range 0–1.2, previously 0–0.8).
- Optional Jenny body import from a user-supplied archive. Her authored curves provide the breast and buttock contours; a cleanup step closes overlapping panels and removes small ridges. Rounded torso and thigh surfaces replace the flat joint panels; your Minecraft head, arms and skin remain in use. Classic and Rounded remain available, with Natural as the fallback when no imported asset is installed.
- Adjustable hips, upper thighs, buttocks and waist for female player models, with conservative proportions and unchanged gameplay hitboxes.
- The imported pelvis stays on its authored body bone when the legs walk. UV seams do not split it into independently moving pieces.
- Size controls scale the imported curves, including beyond the original torso sides. The Jenny preset follows the source proportions, with smoothed contours and tucked chest attachments: body sliders 50%, bust 100% (internal 0.8).
- Acceleration-driven, damped breast and buttock motion, including walk, jump, landing and turning response. Rigid armor holds still; soft coverings reduce motion.
- Standard skin layers and humanoid armor follow the body shape, including ordinary armor dyes, trims and enchantment effects.
- Optional extended multiplayer profiles, validated by the server and resent when players enter tracking range. Older clients receive the original supported settings.

## Install and customize

Use Minecraft **1.21.1**, **NeoForge 21.1.248 or newer**, and **Java 21**. Put the built `Female-Gender-Extended-neoforge-1.21.1-4.3.0-extended.2.jar` into `mods` on clients and the server. Replace the original Female Gender Mod jar: both share the `wildfire_gender` mod ID and cannot be installed together.

Press **G**, open the appearance customization screen, then choose **Body shape & motion**. Start with **Jenny preset** in the locally imported build, or **Natural preset** in a build without the supplied asset. Zero body sliders and Classic give the original geometry path. Existing settings live in `config/WildfireGender`; back them up when switching forks or downgrading.

All participants and the server need the fork to synchronize the added settings. Client-only installation still supports local customization. Extended cloud synchronization is not supported by this release.

Read [compatibility boundaries](docs/COMPATIBILITY.md) before using custom armor or a replacement player renderer. Arbitrary third-party meshes are not guaranteed to fit automatically.

The automated preview records rest, walking, turning, jumping/landing and settling with skin, leather and diamond armor. `tools/assemble_physics_gifs.py` writes three eight-second GIFs to `dist/previews`, using actual Minecraft framebuffer captures at 1x playback speed.

When native graphics initialization is unavailable, `tools/export_cpu_preview.py` and `tools/render_cpu_preview.py` create separately labeled CPU previews from the production geometry, deformation and spring forces. These use neutral materials and scripted joint motion; they do not replace in-game skin, armor or multiplayer verification. The latest refinement also passed two real Minecraft clients under Ubuntu's packaged software OpenGL in a virtual display, producing fresh game-framebuffer GIFs. See [current validation status](docs/VALIDATION.md).

## Import your local Jenny body

```powershell
python tools/import_character_model.py 'C:\path\to\your-supplied-mod.jar' --model jenny
python tools/smooth_character_surfaces.py
./gradlew.bat test build --no-daemon
```

The converter reads only selected geometry as data. It does not run the supplied mod or import its gameplay, textures, sounds or animations. Generated model data stays in ignored `local-models/resources`; Gradle includes it in the local build. Use the same imported build on every participating client. The public source repository supplies the converter and renderer, not the third-party character asset.

The optional surface cleanup uses NumPy and SciPy. CPU preview encoding also uses Pillow and Numba, plus Java 21 and the existing Gradle dependency cache. Cleanup retains the original quads for provenance, closes each authored side with a convex boundary and smooths details smaller than about 0.3 model pixels. It is a contour refinement, not an exact copy of every original panel.

## Build and automated verification

```powershell
./gradlew.bat test build runGameTestServer --no-daemon
```

The release jar and source jar are written to `build/libs`. Tests cover numerical stability, interpolation, geometry seams, armor envelopes, profile validation, persistence, legacy encoding and server packet handling. A separate development test mod contains two scripted Minecraft clients and a localhost server; it is excluded from release jars. See [validation](docs/VALIDATION.md) for actual outcomes and how to reproduce the runtime checks.

See [physics research and implementation decisions](docs/PHYSICS-RESEARCH.md) for sources, model assumptions and practical limits.

## License

Code: LGPL-3.0-or-later, retaining upstream notices. See [LICENSE](LICENSE) and [LICENSE-GPL](LICENSE-GPL). User-supplied character geometry retains its original authorship and is not relicensed by this fork. The importer records the archive and geometry hashes and credits SchnurriTV and the original model contributors.
