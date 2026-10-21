# Pokemon Battle Game Using Java

OOP Course Project

# To compile the file 

to compile the file just click on the compile.bat file

No need compile if only the web files are changed

Only compile when you make changes in the java files

# To run the game

To run the game just double click on the run.bat file

## Adding a new Pokémon
 
1. **`Main.java` → `Constants.NUM_CREATURE`** — increase by 1.
2. **`Main.java` → `CreatureData.TEMPLATES`** — add a new `Template(...)` with name, type, stats, and **exactly 8 moves**.
3. **`game.js` → `POKEMON_EMOJI`** — add `Name: '🐉'` (name must match the Template exactly).
No changes needed in `Server.java`.
 
### Only if adding a brand-new type (e.g. Ice)
 
- **`Main.java` → `Constants`** — add `T_ICE`, bump `NUM_TYPES`, add to `TYPE_NAME`.
- **`Main.java` → `Constants.CHART`** — add a new row + column for the type.
- **`game.js` → `TYPE_COLOR`** — add a color for the badge.

