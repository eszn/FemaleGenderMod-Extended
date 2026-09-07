# Compatibility boundaries

Target: Minecraft 1.21.1, Java 21, NeoForge 21.1.248 used for development and verification.

The fork keeps `wildfire_gender`, existing public packages, armor APIs, player configuration paths, base packet names and base protocol version. Install it **instead of** the original Female Gender Mod, never alongside another jar with the same mod ID. Back up `config/WildfireGender` before moving between versions.

## Supported paths

- Standard and slim player rigs keep their original part identities, pivot positions, animation transforms, UV layouts and hitboxes.
- Female body proportions affect torso, jacket, upper legs and trousers. Rounded breast choices remain available to genders which already support breasts upstream.
- Vanilla humanoid armor uses its normal texture, dye, trim and glint passes for body deformation. Rigid armor suppresses secondary motion; soft armor is damped.
- Equipment on armor stands retains the upstream breast-size transfer behavior, including the expanded bust range. Body proportions and the rounded player layer are player features, not armor-stand customization.
- First-person arms, capes, elytra, held items, player height, reach and gameplay collision boxes are unchanged.
- The vanilla multiplayer connection and legacy upstream profile messages remain optional. The new profile is sent only to peers that negotiate its channel.

## Custom renderers

No mod can guarantee compatibility with every replacement player renderer or arbitrary armor mesh. This fork recognizes the normal humanoid body/leg cubes and deforms those; additional or differently sized pieces are left to their owning renderer. Entirely custom non-humanoid armor and replacement rigs are not claimed to conform automatically. Test them in the intended pack before deployment. Setting the body sliders to zero and selecting Classic provides an immediate fallback using the upstream model path.

The rounded breast layer uses the standard chest texture region. Armor packs that relocate that region or depend on a custom breast texture layout should use Classic until an adapter is supplied. As with upstream, a Minecraft skin is stretched over added geometry; this does not generate a new skin or clothing texture.

## Mixed versions

| Client | Server | Result |
| --- | --- | --- |
| Fork on all players | Fork | Full extended profile synchronization |
| Fork | Upstream or unmodded | Local customization; extension is not sent |
| Upstream | Fork | Original supported profile, bust clamped to its original limit |
| Unmodded | Fork | Normal vanilla appearance and connection |

The last three rows describe the negotiated protocol design. See the validation report for which combinations were actually exercised.
