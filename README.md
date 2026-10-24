# Auto Schematic Builder (Fabric 1.21.11)

Client-side mod. Press **B** to open the GUI, pick a schematic, set the corner position + rotation + speed, hit **Start build**. **N** pauses/resumes.

Put `.nbt` (vanilla structure) or `.schem` (Sponge v2/v3: WorldEdit, FAWE, Schematica exports) files in `.minecraft/schematics/`.

- Singleplayer: blocks are placed directly through the integrated server (fast, no cheats required).
- Multiplayer: uses /setblock and /fill (requires OP, throttled). Only use where building tools are allowed.

Requires Fabric API.
