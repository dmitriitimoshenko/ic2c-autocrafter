# Changelog

## 1.2.2
* Recipe highlighting reworked so the icon always stays readable: the active recipe gets a thin
  green frame and a faint glow instead of a fill, a switched-off one a light dim plus a red mark in
  the corner.
* New casing textures, logo and GUI icons. All art in the mod is now original, so the MIT license
  covers everything.

## 1.2.1
* Fixed: recipes were not cleared when the Memory Stick was taken out. IC2's `SlotBase.remove()`
  hands back the very same stack object, already emptied, so comparing the old stack with the new
  one never fired. The machine now keeps a copy of the stick and checks against it, on every tick
  as well.
* Fixed: clicking a recipe icon drew a black square, because the translucent fill was rendered
  without blending. A switched-off recipe is now dimmed properly, and the active one is framed
  rather than filled.
* The GUI is 10 pixels taller: IC2's "I" and "C" buttons no longer overlap the recipe panel.

## 1.2.0
* The machine moved to **MV** (128 EU/t input): 8 EU/t, 100 ticks, 800 EU and 5 seconds per craft,
  4000 EU buffer. Machine recipe: Advanced Machine Block + 2x Advanced Circuit + Electronic Circuit
  + Crafting Table.
* Custom sound: a seamless 2 second working loop, plus start and interrupt cues.
* Animation: an assembly wave across the 3x3 grid on the top face, running lights on the sides,
  sparks and smoke above the working face.
* The tooltip names the exact missing ingredient ("Missing: 3x Copper Plate").
* Settings through CarbonConfig: `config/ic2c/ic2c_autocrafter.cfg` (EU per tick, craft time,
  buffer, sound).
* Fixed: Overclockers did nothing. IC2C files them under RECIPE_MOD, a type the machine did not
  accept; it now accepts every upgrade type, like the stock IC2 machines.
* Languages: added be_by, sr_sp, el_gr, tr_tr (16 in total).

## 1.1.0
* Three output slots instead of one: different recipes no longer block each other.
* Craft leftovers (buckets and the like) go back to the buffer only if they are ingredients
  themselves, otherwise to the output, so the buffer can no longer jam for good.
* Progress drains smoothly instead of resetting at once.
* The machine notices items that the Import Upgrade and tubes write straight into its inventory.
* Any of the 9 recipes can be switched off with a click; the active one is highlighted, disabled
  ones are dimmed.
* The tooltip on the arrow explains why the machine is idle (8 states).
* GUI rearranged: recipes framed on the left, stick/charge/battery in the middle, outputs and
  upgrades in columns on the right, buffer at the bottom.
* Own textures for the top and side faces; 12 languages.

## 1.0.0
* First version: LV machine, reads up to 9 recipes from a Memory Stick, 9 buffer slots, 1 output,
  IC2 upgrades, IC2-style GUI, en_us + ru_ru.
