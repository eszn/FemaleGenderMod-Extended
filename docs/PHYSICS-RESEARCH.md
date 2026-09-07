# Secondary motion and model design

This fork targets a restrained Minecraft silhouette. It is a lightweight character animation model, not a medical simulation or a claim that all bodies have the same stiffness, mass, or motion. Parameters are tuned in Minecraft model pixels. One model pixel is 1/16 of a block.

## Sources and resulting decisions

1. [Glenn Fiedler, Spring Physics](https://gafferongames.com/post/spring_physics/) derives the spring and damper force `F = -kx - bv`. Damping removes energy after a disturbance. The fork applies this principle to secondary motion driven by the animated character, rather than looping a free-running bounce animation.

2. [Glenn Fiedler, Integration Basics](https://gafferongames.com/post/integration_basics/) demonstrates the instability of explicit Euler for a spring and the improvement from semi-implicit integration. [Fix Your Timestep](https://gafferongames.com/post/fix_your_timestep/) explains why simulation steps should not depend on render frame duration. Here every 20 Hz entity tick performs six 1/120-second semi-implicit substeps. Rendering only interpolates the two tick states. The solver adds nonlinear stiffness near its motion boundary and projects extreme impulses back into a finite envelope.

3. [Haake and Scurr, A dynamic model of the breast during exercise (2010)](https://shura.shu.ac.uk/2367/) models motion relative to the torso with a forced damped oscillator. Their study found changes in fitted damping and stiffness with support. This motivates lower motion and stronger damping under armor, plus a size-dependent response frequency. The study's fitted coefficients are not transplanted into block-scale geometry.

4. [Cai et al., A piecewise mass-spring-damper model of the human breast (2018)](https://pubmed.ncbi.nlm.nih.gov/29276070/) reports that response above and below equilibrium can differ. The fork uses an asymmetric natural rest shape and an increasing constraint stiffness; it does not reproduce the paper's complete piecewise biomechanical model. The rounded option provides a more symmetric alternative.

5. [Murai et al., Dynamic skin deformation simulation using musculoskeletal model and soft tissue dynamics](https://studios.disneyresearch.com/wp-content/uploads/2019/04/Dynamic-Skin-Deformation-SimulationUsing-Musculoskeletal-Model-and-Soft-Tissue-Dynamics-Paper.pdf) describes combining skeletal motion with secondary tissue deformation. The architectural lesson used here is to retain the animated skeleton and apply a localized displacement field with pinned attachment boundaries. The implementation is much simpler than the paper's musculoskeletal system.

6. [Soft-tissue vibration and damping response to footwear changes across a wide range of anthropometrics in running (2021)](https://pubmed.ncbi.nlm.nih.gov/34403445/) reinforces that lower-body tissue vibration and damping vary with the person and conditions. It is not a universal buttock animation calibration. The fork therefore uses a conservative, more strongly damped lower-body response and explicitly configurable motion strength.

7. [Müller et al., A Survey on Position Based Dynamics](https://matthias-research.github.io/pages/publications/PBDTutorial2017-CourseNotes.pdf) and [Ten Minute Physics](https://matthias-research.github.io/pages/tenMinutePhysics/index.html) were considered for a fuller soft-body system. A tetrahedral/XPBD flesh simulation would add substantial geometry, collision, and performance complexity for this model. The selected bounded spring/displacement approach is easier to test, preserve across armor passes, and run for many players.

8. [Defining female gluteal geometry for preoperative planning in body contouring (2026)](https://pmc.ncbi.nlm.nih.gov/articles/PMC13251492/) describes the gluteal region in relation to pelvic landmarks and the gluteal fold, including paired ovoid contours and a sacral depression. This informed the visual correction from a low thigh bulge to two volumes spanning the lower torso and pelvis. Their peak is now at body coordinate Y=11.8, close to the vanilla hip joint at Y=12, and their lower attachment ends before Y=15. These Minecraft coordinates are design choices, not measured anatomical ratios.

9. [Mallucci and Branford, Concepts in aesthetic breast dimensions (2012)](https://pubmed.ncbi.nlm.nih.gov/21868295/) distinguishes a gradual upper-pole slope from a convex lower pole. The natural profile now uses separate upper and lower curves, a rounded apex, and a shallow shared medial transition. Rounded provides a fuller upper-pole alternative. This selected-model observational study supplies contour vocabulary; its aesthetic preferences are not treated as a universal body standard.

## Implementation choices

- Linear acceleration comes from tick-to-tick positions, so remote players and moving vehicles do not depend on a local-input velocity estimate. Body yaw rotates acceleration into the player's coordinates. Wrapped yaw changes avoid a spike at -180/180 degrees.
- Footfall excitation follows the existing walk animation and is gated on grounded movement. Stationary characters receive no continuous gait force. Jump/landing impulses come from vertical acceleration.
- Teleports, dimension changes, skipped entity ticks, sleeping, disabled physics, and rigid coverings reset relevant spring state. Swimming reduces motion. Seated lower-body motion is suppressed.
- Bust support uses the upstream armor resource data. Rigid breast armor has zero secondary motion. Leather leggings allow a small damped lower-body response; rigid and unknown leg coverings hold their shape.
- Hip widening is at most 14% at the pelvis. The waist contracts by at most 18% at its narrowest point. Thigh shaping is localized above the knees and separated from the higher gluteal volume. The rear projection adds at most 1.65 model pixels before the small motion offset. Its paired lobes span the lower torso and uppermost legs, with a pinned shared edge during motion. The controls expose the full normalized range; they do not change collision boxes or reach.
- Breast surfaces combine two smooth lateral lobes with separate upper/lower pole curves and a shared chest bridge. Natural has a gentler upper slope and rounded lower contour; Rounded has a fuller upper profile. Outer rim points remain attached to the torso, and opposing physics phases fade to zero at the shared center. Textures come from the existing skin chest area. Classic retains its explicit upstream armor-physics override; the rounded profiles always respect rigid support.
- Body geometry is subdivided from existing cube faces, retaining their UV orientation and animated `ModelPart` transforms. Surface derivatives supply smooth normals, avoiding hard stripes across the gluteal contour. The same displacement is used in skin, jacket, trousers, standard armor, trim and glint passes. Unknown extra model pieces retain their own geometry.

## Multiplayer contract

See [NeoForge 1.21.1 payload documentation](https://docs.neoforged.net/docs/1.21.1/networking/payload/) for payload registration, direction and main-thread handling.

The upstream v1 profile wire layout is retained, with the original 0.8 bust maximum used when encoding that packet. A separate optional 42-byte extension carries the full bust value and new body settings. The server verifies the sender UUID and decoders reject non-finite, out-of-range, truncated and unknown-enum inputs. Rapid changes are coalesced to the latest profile before forwarding. Tracking entry resends both profiles; no per-frame motion packets are sent.

The clients simulate cosmetic motion from their local view of the player's movement. Network interpolation can cause small phase differences between observers; this is not deterministic lockstep across machines. Everyone needs the fork, including the server, to see all extended settings. Older peers can connect and receive their supported settings; they cannot display new geometry they do not contain. Extended cloud synchronization is not promised.
