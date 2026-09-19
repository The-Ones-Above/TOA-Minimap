# TOAMinimapAuth 1.3.3

## Oraxen City Map integration

`/toaminimap give map [player]` now gives the Oraxen item:

`city_map_scroll`

The plugin preserves the Oraxen custom item/model and adds:

`toaminimapauth:item_type = city_map`

so the Fabric minimap client recognises it exactly as before.

No Oraxen Maven dependency is required because the plugin accesses Oraxen
reflectively.

If Oraxen is unavailable or the item ID cannot be found, the plugin falls back
to a plain PAPER item and logs a warning.

The older right-click MAP protection remains only for legacy map items.
