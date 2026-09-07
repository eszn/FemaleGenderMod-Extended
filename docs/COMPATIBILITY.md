# Compatibility boundaries

Target: Minecraft 1.21.1, Java 21, NeoForge 21.1.248 used for development and verification.

The fork keeps `wildfire_gender`, existing public packages, armor APIs, player configuration paths, base packet names and base protocol version. Install it **instead of** the original Female Gender Mod, never alongside another jar with the same mod ID. Back up `config/WildfireGender` before moving between versions.

## Supported paths

- Standard and slim player rigs keep their original part identities, pivot positions and hitboxes. The imported body uses the existing skin atlas with a continuous side wrap; skin artwork stretches to fit its proportions. Empty-handed relaxed arms angle slightly outward with larger proportions; item-use, attack, swimming and flying poses keep their existing animation control.
- Female body proportions affect torso, jacket, upper legs and trousers. Rounded breast choices remain available to genders which already support breasts upstream.
- Vanilla humanoid armor uses its normal texture, dye, trim and glint passes for body deformation. Rigid armor suppresses secondary motion; soft armor is damped.
- Equipment on armor stands retains the upstream breast-size transfer behavior, including the expanded bust range. Body proportions and the rounded torso are player features, not armor-stand customization.
- First-person arms, capes, elytra, held items, player height, reach and gameplay collision boxes are unchanged.
- The vanilla multiplayer connection and legacy upstream profile messages remain optional. The new profile is sent only to peers that negotiate its channel.

## Custom renderers

No mod can guarantee compatibility with every replacement player renderer or arbitrary armor mesh. This fork recognizes the normal humanoid body/leg cubes and deforms those; additional or differently sized pieces are left to their owning renderer. Entirely custom non-humanoid armor and replacement rigs are not claimed to conform automatically. Test them in the intended pack before deployment. Setting the body sliders to zero and selecting Classic provides an immediate fallback using the upstream model path.

When local Jenny geometry is present, Natural selects the imported curves. Optional cleanup closes the authored soft surfaces, removes subpixel ridges and tucks their hidden chest attachment into the player torso. Original panels remain in the resource for provenance but are not drawn over the cleaned surface. Rounded body and leg surfaces replace overlapping flat hip panels. Proximal thigh transforms blend into the torso between model heights 12 and 15, keeping the bend near the hip. The torso keeps the pelvis; standard leg textures cover the lower pelvis while it moves with the body. Chest armor ends at its usual waist boundary, while leggings cover the pelvis and legs. Without the local asset, Natural and Rounded use the procedural torso path. Upstream custom breast-layer texture adapters apply to Classic. None of these paths generates a new skin or clothing texture.

The source character is stylized. Its original shape is the reference preset, not an anatomical calibration. Shoulder and leg animations still follow the Minecraft rig; original Jenny facial, sexual and other gameplay animations are not included. Exact character mesh data is a local installation asset and is not sent in profile packets. Clients must use the same imported build to see the same geometry.

## Mixed versions

| Client | Server | Result |
| --- | --- | --- |
| Fork on all players | Fork | Full extended profile synchronization |
| Fork | Upstream or unmodded | Local customization; extension is not sent |
| Upstream | Fork | Original supported profile, bust clamped to its original limit |
| Unmodded | Fork | Normal vanilla appearance and connection |

The last three rows describe the negotiated protocol design. See the validation report for which combinations were actually exercised.
