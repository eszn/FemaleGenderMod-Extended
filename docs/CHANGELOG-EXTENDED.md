# 4.3.0-extended.2

- Add an optional Jenny body importer, retaining the player's own head, arms and skin. Derive smooth closed breast/buttock contours from the authored panels and tuck the hidden chest attachment into the torso. Round flat torso/thigh sections and replace overlapping hip panels while following the reference proportions.
- Smooth imported panel normals and wrap the player's side textures across curved surfaces. Keep the pelvis on the torso during walking; use leg texture regions below the waist and standard armor passes.
- Record three eight-second physics GIFs from the actual game framebuffer: no armor, leather and diamond. Each shows rest, walking, turning, jump/landing response and settling, with skin and neutral-material views. Verify excitation, decay and zero motion under rigid armor.
- Replace the separate Natural/Rounded breast patches with a continuous torso deformation, including front and side faces and standard armor. Remove the duplicated attachment surfaces responsible for overlap artifacts.
- Grow breast width, height and projection together. Let large breast and buttock profiles extend beyond the original torso width, with modest relaxed-arm clearance.
- Broaden the pelvic volume, soften its central crease, and keep its lower attachment above mid-thigh. Move waist contraction below the main breast volume.
- Round the modified body's outer corners and calculate matching surface normals. Correct Classic normals after size scaling.
- Add distance-based mesh detail and reuse unchanged geometry across frames and armor passes.
- Increase walking excitation and reduce damping to make secondary motion visible while remaining bounded. Leather damps movement; diamond remains stationary. Add continuous rounded hip boundaries and closed foot surfaces.
- Move the leg blend toward the hip and use continuous profile tangents to remove horizontal shading bands. Add a CPU preview route that shares production mesh and force code without requiring graphics drivers or desktop control. These previews are labeled separately from actual Minecraft captures.
- Pin chest attachments during size changes so large profiles expand outward without appearing through the back. Keep depth ordered at small sizes.
- Add size comparison and close-up framebuffer captures, cache invalidation checks and geometry regressions. Total: 43 unit tests with the locally supplied geometry and 5 dedicated GameTests, plus the scripted multiplayer/rendering harness. Source-only builds skip three asset-dependent checks.

# 4.3.0-extended.1

- Fork the upstream NeoForge 1.21.1 sync branch with retained LGPL notices and original public API packages.
- Increase the bust range by 50%, carrying the range through configuration, UI, presets, armor-stand data and the extended network profile. Classic also grows beyond its old geometry limit.
- Add rounded and natural breast surfaces with curved attachment footprints, a shared medial transition, smooth normals and existing position controls.
- Add conservative hip, thigh, buttock and waist shaping using the original player rig and skin UVs; apply the same field to compatible humanoid armor and overlays.
- Locate paired buttock lobes across the lower torso and pelvis, separating their contour from thigh thickness. Pin the shared torso/leg edge during motion.
- Replace the upstream heuristic/random secondary motion with deterministic fixed-step damped springs; include footfall and acceleration response, bounded motion, reset conditions and armor support.
- Add optional, validated 42-byte multiplayer profiles, latest-value coalescing, tracking synchronization and disconnect cleanup while retaining the original packet layout.
- Add configuration controls and a Natural preset, 27 numerical/geometry/profile tests, 5 in-server tests and a reproducible two-client rendering/network harness with four-angle skin and neutral-material views.
