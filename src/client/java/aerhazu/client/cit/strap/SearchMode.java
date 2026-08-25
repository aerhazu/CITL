package aerhazu.client.cit.strap;

import net.minecraft.text.Text;

public enum SearchMode {
    GENERAL, ITEM, NAME, PACK;

    public SearchMode next() {
        return switch (this) {
            case GENERAL -> ITEM;
            case ITEM -> NAME;
            case NAME -> PACK;
            case PACK -> GENERAL;
        };
    }

    public String getDisplayName() {
        return switch (this) {
            case GENERAL -> "Search: General";
            case ITEM -> "Search: Item";
            case NAME -> "Search: Name";
            case PACK -> "Search: Pack";
        };
    }

    public Text getTooltip() {
        return switch (this) {
            case GENERAL -> Text.literal("Searches across items, custom names, and pack names.");
            case ITEM -> Text.literal("Searches exclusively by base Minecraft item ID.");
            case NAME -> Text.literal("Searches exclusively by CIT custom display name.");
            case PACK -> Text.literal("Searches exclusively by source Resource Pack name.");
        };
    }
}