package aerhazu.client.cit.strap;

import aerhazu.client.cit.config.CITLedgerConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class CITLedgerConfigScreen extends Screen {
    private final Screen parent;

    public CITLedgerConfigScreen(Screen parent) {
        super(Text.literal("CITLedger Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int buttonWidth = 150;
        int buttonHeight = 20;
        int gapY = 22; // Slightly smaller gap to fit 5 items in the left column

        int leftColX = centerX - buttonWidth - 10;
        int rightColX = centerX + 10;

        // --- SEARCH & UI SECTION (Left Column) ---
        int startY_UI = 60;

        this.addDrawableChild(ButtonWidget.builder(this.getPersistentSearchText(), (button) -> {
                    CITLedgerConfig config = CITLedgerConfig.get();
                    config.persistentSearch = !config.persistentSearch;
                    CITLedgerConfig.save();
                    button.setMessage(this.getPersistentSearchText());
                })
                .dimensions(leftColX, startY_UI, buttonWidth, buttonHeight)
                .tooltip(Tooltip.of(Text.literal("Saves your typed search query when you close and reopen the menu.")))
                .build());

        this.addDrawableChild(ButtonWidget.builder(this.getPersistentModeText(), (button) -> {
                    CITLedgerConfig config = CITLedgerConfig.get();
                    config.persistentSearchMode = !config.persistentSearchMode;
                    CITLedgerConfig.save();
                    button.setMessage(this.getPersistentModeText());
                })
                .dimensions(leftColX, startY_UI + gapY, buttonWidth, buttonHeight)
                .tooltip(Tooltip.of(Text.literal("Saves the selected search mode (General, Item, etc.) across sessions.")))
                .build());

        this.addDrawableChild(ButtonWidget.builder(this.getRightClickClearText(), (button) -> {
                    CITLedgerConfig config = CITLedgerConfig.get();
                    config.rightClickClearSearch = !config.rightClickClearSearch;
                    CITLedgerConfig.save();
                    button.setMessage(this.getRightClickClearText());
                })
                .dimensions(leftColX, startY_UI + (gapY * 2), buttonWidth, buttonHeight)
                .tooltip(Tooltip.of(Text.literal("Allows right-clicking the search bar to instantly clear the text.")))
                .build());

        this.addDrawableChild(ButtonWidget.builder(this.getAnimationsText(), (button) -> {
                    CITLedgerConfig config = CITLedgerConfig.get();
                    config.enableAnimations = !config.enableAnimations;
                    CITLedgerConfig.save();
                    button.setMessage(this.getAnimationsText());
                })
                .dimensions(leftColX, startY_UI + (gapY * 3), buttonWidth, buttonHeight)
                .tooltip(Tooltip.of(Text.literal("Toggles item bounce, hover zoom, and text scroll animations.")))
                .build());

        this.addDrawableChild(ButtonWidget.builder(this.getAutoReloadText(), (button) -> {
                    CITLedgerConfig config = CITLedgerConfig.get();
                    config.scanOnResourceReload = !config.scanOnResourceReload;
                    CITLedgerConfig.save();
                    button.setMessage(this.getAutoReloadText());
                })
                .dimensions(leftColX, startY_UI + (gapY * 4), buttonWidth, buttonHeight)
                .tooltip(Tooltip.of(Text.literal("Automatically rescans CIT items when reloading resource packs (F3+T).")))
                .build());


        // --- AUDIO SECTION (Right Column Top) ---
        int startY_Audio = 60;

        this.addDrawableChild(ButtonWidget.builder(this.getSoundGiveText(), (button) -> {
                    CITLedgerConfig config = CITLedgerConfig.get();
                    config.playGiveSound = !config.playGiveSound;
                    CITLedgerConfig.save();
                    button.setMessage(this.getSoundGiveText());
                })
                .dimensions(rightColX, startY_Audio, buttonWidth, buttonHeight)
                .tooltip(Tooltip.of(Text.literal("Play a sound when successfully giving yourself an item.")))
                .build());

        this.addDrawableChild(ButtonWidget.builder(this.getSoundCopyText(), (button) -> {
                    CITLedgerConfig config = CITLedgerConfig.get();
                    config.playCopySound = !config.playCopySound;
                    CITLedgerConfig.save();
                    button.setMessage(this.getSoundCopyText());
                })
                .dimensions(rightColX, startY_Audio + gapY, buttonWidth, buttonHeight)
                .tooltip(Tooltip.of(Text.literal("Play a sound when successfully copying an item name.")))
                .build());

        this.addDrawableChild(ButtonWidget.builder(this.getSoundErrorText(), (button) -> {
                    CITLedgerConfig config = CITLedgerConfig.get();
                    config.playErrorSound = !config.playErrorSound;
                    CITLedgerConfig.save();
                    button.setMessage(this.getSoundErrorText());
                })
                .dimensions(rightColX, startY_Audio + (gapY * 2), buttonWidth, buttonHeight)
                .tooltip(Tooltip.of(Text.literal("Play an error sound when an action fails (e.g., no permissions).")))
                .build());

        this.addDrawableChild(ButtonWidget.builder(this.getSoundClearText(), (button) -> {
                    CITLedgerConfig config = CITLedgerConfig.get();
                    config.playClearSound = !config.playClearSound;
                    CITLedgerConfig.save();
                    button.setMessage(this.getSoundClearText());
                })
                .dimensions(rightColX, startY_Audio + (gapY * 3), buttonWidth, buttonHeight)
                .tooltip(Tooltip.of(Text.literal("Play a click sound when clearing the search bar.")))
                .build());


        // --- DEBUG SECTION (Right Column Bottom) ---
        int startY_Debug = 175;

        this.addDrawableChild(ButtonWidget.builder(this.getHideBrokenText(), (button) -> {
                    CITLedgerConfig config = CITLedgerConfig.get();
                    config.hideBroken = !config.hideBroken;
                    CITLedgerConfig.save();
                    button.setMessage(this.getHideBrokenText());
                })
                .dimensions(rightColX, startY_Debug, buttonWidth, buttonHeight)
                .tooltip(Tooltip.of(Text.literal("Hides invalid or unresolvable CIT properties from the list.")))
                .build());

        this.addDrawableChild(ButtonWidget.builder(this.getHideDuplicatesText(), (button) -> {
                    CITLedgerConfig config = CITLedgerConfig.get();
                    config.hideDuplicates = !config.hideDuplicates;
                    CITLedgerConfig.save();
                    button.setMessage(this.getHideDuplicatesText());
                })
                .dimensions(rightColX, startY_Debug + gapY, buttonWidth, buttonHeight)
                .tooltip(Tooltip.of(Text.literal("Hides items that conflict with another pack's custom name.")))
                .build());


        // --- DONE BUTTON ---
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), (button) -> this.close())
                .dimensions(centerX - 100, this.height - 30, 200, 20).build());
    }

    // Label Helpers
    private Text getPersistentSearchText() { return Text.literal("Save Search Text: " + (CITLedgerConfig.get().persistentSearch ? "§aON" : "§cOFF")); }
    private Text getPersistentModeText() { return Text.literal("Save Search Mode: " + (CITLedgerConfig.get().persistentSearchMode ? "§aON" : "§cOFF")); }
    private Text getRightClickClearText() { return Text.literal("Right-Click Clear: " + (CITLedgerConfig.get().rightClickClearSearch ? "§aON" : "§cOFF")); }
    private Text getAnimationsText() { return Text.literal("Animations: " + (CITLedgerConfig.get().enableAnimations ? "§aON" : "§cOFF")); }
    private Text getAutoReloadText() { return Text.literal("Auto Reload: " + (CITLedgerConfig.get().scanOnResourceReload ? "§aON" : "§cOFF")); }

    private Text getSoundGiveText() { return Text.literal("Give Sound: " + (CITLedgerConfig.get().playGiveSound ? "§aON" : "§cOFF")); }
    private Text getSoundCopyText() { return Text.literal("Copy Sound: " + (CITLedgerConfig.get().playCopySound ? "§aON" : "§cOFF")); }
    private Text getSoundErrorText() { return Text.literal("Error Sound: " + (CITLedgerConfig.get().playErrorSound ? "§aON" : "§cOFF")); }
    private Text getSoundClearText() { return Text.literal("Clear Sound: " + (CITLedgerConfig.get().playClearSound ? "§aON" : "§cOFF")); }

    private Text getHideBrokenText() { return Text.literal("Broken Entries: " + (CITLedgerConfig.get().hideBroken ? "§cHidden" : "§aShown")); }
    private Text getHideDuplicatesText() { return Text.literal("Conflicts: " + (CITLedgerConfig.get().hideDuplicates ? "§cHidden" : "§aShown")); }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);

        int leftColCenter = (this.width / 2) - 85;
        int rightColCenter = (this.width / 2) + 85;

        // Render Section Headers
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Search & UI"), leftColCenter, 45, 0xAAAAAA);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Audio"), rightColCenter, 45, 0xAAAAAA);

        // Pushed debug header down to fit the 4th audio button
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Debug"), rightColCenter, 160, 0xAAAAAA);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }
}