# Metro Villagers

A lightweight mod that dynamically makes items compostable! Its main goal is to provide automatic out-of-the-box composter compatibility for food and items added by other mods, without needing any configuration.

## Features

- **Automatic Food Support:** Scans the item registry and automatically makes any food item compostable. The compost chance is scaled based on the food's nutrition value.
- **Smart Categorization:** Automatically detects and adds support for items that should be compostable, such as seeds, crops, drinks, and bonemealable plants.
- **Recipe Propagation:** If an item is crafted entirely from compostable ingredients, the mod will automatically make the crafted item compostable as well!
- **Meat Rejection:** Specifically ignores meat, fish, and recipes crafted with them, keeping the composter vegetarian-friendly as intended by vanilla gameplay.
- **Item Remainders:** If a compostable item is crafted with a bowl or a glass bottle (like a soup or a drink), composting it will correctly give you the empty bowl or bottle back instead of consuming it entirely!

## Compatibility

This mod works dynamically by analyzing item properties, tags, and crafting recipes at startup. It provides instant compatibility with almost any modded item, no data packs or configuration required!