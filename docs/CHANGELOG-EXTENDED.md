# 4.3.0-extended.1

- Fork the upstream NeoForge 1.21.1 sync branch with retained LGPL notices and original public API packages.
- Increase the bust range by 50%, carrying the range through configuration, UI, presets, armor-stand data and the extended network profile. Classic also grows beyond its old geometry limit.
- Add rounded and natural breast surfaces with curved attachment footprints, a shared medial transition, smooth normals and existing position controls.
- Add conservative hip, thigh, buttock and waist shaping using the original player rig and skin UVs; apply the same field to compatible humanoid armor and overlays.
- Locate paired buttock lobes across the lower torso and pelvis, separating their contour from thigh thickness. Pin the shared torso/leg edge during motion.
- Replace the upstream heuristic/random secondary motion with deterministic fixed-step damped springs; include footfall and acceleration response, bounded motion, reset conditions and armor support.
- Add optional, validated 42-byte multiplayer profiles, latest-value coalescing, tracking synchronization and disconnect cleanup while retaining the original packet layout.
- Add configuration controls and a Natural preset, 27 numerical/geometry/profile tests, 5 in-server tests and a reproducible two-client rendering/network harness with four-angle skin and neutral-material views.
