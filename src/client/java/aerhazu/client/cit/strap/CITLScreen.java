package aerhazu.client.cit.strap;

import java.util.Locale;

import aerhazu.client.cit.config.CITLedgerConfig;
import aerhazu.client.cit.ledger.CITIndexer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

public class CITLScreen extends Screen {
    private final Screen parent;
    private CITListWidget list;
    private TextFieldWidget searchBox;
    private ButtonWidget searchModeButton;
    private ButtonWidget configButton;
    private ButtonWidget doneButton;

    private SearchMode searchMode = SearchMode.GENERAL;
    private String[][] citArray = new String[0][3];
    private String lastQuery = "";

    // Exposed context variables utilized by CITListWidget
    public CITListWidget.CITEntry selectedEntry;
    public CITListWidget.CITEntry contextMenuEntry = null;
    public double contextMenuX = 0;
    public double contextMenuY = 0;

    private String statusMessage = "";
    private int statusColor = 16777215;

    private static final Identifier CONFIG_ICON = Identifier.of("citl", "textures/gui/citl_config.png");

    public CITLScreen(Screen parent) {
        super(Text.literal("CIT Manager"));
        this.parent = parent;
    }

    // Required accessor hooks for independent widgets
    public TextRenderer getTextRenderer() { return this.textRenderer; }
    public MinecraftClient getClient() { return this.client; }

    @Override
    protected void init() {
        super.init();

        if (CITLedgerConfig.get().persistentSearchMode) {
            try {
                this.searchMode = SearchMode.valueOf(CITLedgerConfig.get().savedSearchMode);
            } catch (Exception e) {
                this.searchMode = SearchMode.GENERAL;
            }
        }

        // Layout Geometry
        int inspectorWidth = Math.max(170, (int) (this.width * 0.38));
        int listX = inspectorWidth;
        int listWidth = this.width - inspectorWidth - 8;
        int listTopMargin = 51;
        int bottomBarY = this.height - 36;
        int listBottom = bottomBarY - 10;

        this.list = new CITListWidget(this, this.client, listX, listWidth, this.width, this.height, listTopMargin, listBottom, 24);
        this.addDrawableChild(this.list);

        this.doneButton = ButtonWidget.builder(Text.literal("Done"), (button) -> this.close())
                .dimensions(8, bottomBarY, 80, 20).build();
        this.addDrawableChild(this.doneButton);

        int searchX = inspectorWidth - 0;
        int configX = this.width - 28;
        int searchModeX = configX - 120;
        int searchWidth = searchModeX - 4 - searchX;

        this.searchBox = new TextFieldWidget(this.textRenderer, searchX, bottomBarY, searchWidth, 20, Text.literal("Search"));
        this.searchBox.setPlaceholder(Text.literal("Search..."));

        if (CITLedgerConfig.get().persistentSearch) {
            this.searchBox.setText(CITLedgerConfig.get().savedSearchText);
        }

        this.searchBox.setChangedListener((value) -> {
            if (CITLedgerConfig.get().persistentSearch) {
                CITLedgerConfig.get().savedSearchText = value;
                CITLedgerConfig.save();
            }
            this.rebuildList();
        });
        this.addDrawableChild(this.searchBox);

        this.searchModeButton = ButtonWidget.builder(Text.literal(this.searchMode.getDisplayName()), (button) -> {
                    this.searchMode = this.searchMode.next();
                    if (CITLedgerConfig.get().persistentSearchMode) {
                        CITLedgerConfig.get().savedSearchMode = this.searchMode.name();
                        CITLedgerConfig.save();
                    }
                    button.setMessage(Text.literal(this.searchMode.getDisplayName()));
                    button.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(this.searchMode.getTooltip()));
                    this.rebuildList();
                })
                .dimensions(searchModeX, bottomBarY, 116, 20)
                .tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(this.searchMode.getTooltip()))
                .build();
        this.addDrawableChild(this.searchModeButton);

        this.configButton = ButtonWidget.builder(Text.empty(), (button) -> {
            if (this.client != null) {
                this.client.setScreen(new CITLedgerConfigScreen(this));
            }
        }).dimensions(configX, bottomBarY, 20, 20).build();
        this.addDrawableChild(this.configButton);

        if (!CITIndexer.isLoaded()) {
            CITIndexer.refreshCache();
        }

        this.rebuildList();
        this.setInitialFocus(this.searchBox);
    }

    public void setStatus(String message, int color) {
        this.statusMessage = message;
        this.statusColor = color;
    }

    public void giveSelectedItem() {
        if (this.selectedEntry == null || this.client == null || this.client.player == null) return;
        boolean hasPermission = this.client.player.hasPermissionLevel(2);

        if (hasPermission && this.client.getNetworkHandler() != null) {
            this.client.getNetworkHandler().sendCommand(this.buildGiveCommand(this.selectedEntry.itemNames.get(0), this.selectedEntry.newName));
            this.setStatus("Item given!", 0x55FF55);

            if (CITLedgerConfig.get().playGiveSound) {
                this.client.player.playSound(SoundEvents.BLOCK_AMETHYST_CLUSTER_HIT, 0.25F, 1.00F);
            }
        } else {
            this.setStatus("Error: No Permission", 0xFF5555);

            if (CITLedgerConfig.get().playErrorSound) {
                this.client.player.playSound(SoundEvents.ENTITY_ENDER_EYE_DEATH, 0.5F, 1.00F);
            }
        }
    }

    private void rebuildList() {
        this.citArray = CITIndexer.getCachedResults();
        String query = this.searchBox == null ? "" : this.searchBox.getText().trim().toLowerCase(Locale.ROOT);
        this.list.rebuild(this.citArray, query, this.searchMode);

        if (!query.equals(this.lastQuery)) {
            if (this.list != null) {
                this.list.setScrollAmount(0.0);
            }
            this.lastQuery = query;
        }
    }

    private String buildGiveCommand(String itemName, String newName) {
        return "give @p minecraft:" + itemName + "[minecraft:custom_name='" + "{\"text\":\"" + newName.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}" + "'] 1";
    }

    @Override
    public void tick() {
        super.tick();
        if (!CITIndexer.isLoaded() && this.list != null && this.list.children().isEmpty()) {
            this.rebuildList();
        }
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(this.parent);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {

        // Right-Click to clear search logic
        if (button == 1 && CITLedgerConfig.get().rightClickClearSearch && this.searchBox != null && this.searchBox.isMouseOver(mouseX, mouseY)) {
            this.searchBox.setText("");
            if (CITLedgerConfig.get().persistentSearch) {
                CITLedgerConfig.get().savedSearchText = "";
                CITLedgerConfig.save();
            }

            if (this.client != null && this.client.player != null && CITLedgerConfig.get().playClearSound) {
                this.client.player.playSound(SoundEvents.ENTITY_ENDER_EYE_DEATH, 0.15F, 1.00F);
            }
            return true;
        }

        // Context Menu Click Logic
        if (this.contextMenuEntry != null) {
            int menuWidth = 100;
            int menuHeight = 40;
            if (mouseX >= this.contextMenuX && mouseX <= this.contextMenuX + menuWidth && mouseY >= this.contextMenuY && mouseY <= this.contextMenuY + menuHeight) {
                if (mouseY < this.contextMenuY + 20) {
                    if (this.client != null) {
                        this.client.keyboard.setClipboard(this.contextMenuEntry.newName);
                        this.setStatus("Copied to clipboard!", 0x55FF55);

                        if (this.client.player != null && CITLedgerConfig.get().playCopySound) {
                            this.client.player.playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, 0.25F, 2.0F);
                        }
                    }
                } else {
                    this.selectedEntry = this.contextMenuEntry;
                    this.giveSelectedItem();
                }
                this.contextMenuEntry = null;
                return true;
            } else {
                this.contextMenuEntry = null;
            }
        }

        if (button == 1 && this.list != null && this.list.isMouseOver(mouseX, mouseY)) {
            CITListWidget.CITEntry entry = this.list.getEntryAt(mouseX, mouseY);
            if (entry != null) {
                this.selectedEntry = entry;
                this.contextMenuEntry = entry;
                this.contextMenuX = mouseX;
                this.contextMenuY = mouseY;
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button) || (this.list != null && this.list.mouseClicked(mouseX, mouseY, button));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.list != null && this.list.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        int inspectorWidth = Math.max(170, (int) (this.width * 0.38));
        int listX = inspectorWidth;
        int listWidth = this.width - inspectorWidth - 8;

        context.fill(listX - 1, 0, listX, this.height, 0x44FFFFFF);
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("CITLedger"), this.width / 2, 12, 0xFFFFFF);

        int headerY = 36;
        context.fill(listX, headerY - 6, this.width - 8, headerY + 14, 0x55000000);

        context.drawTextWithShadow(this.textRenderer, Text.literal("CIT & Item"), listX + 32, headerY, 16777215);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Custom Name"), listX + (int) (listWidth * 0.40), headerY, 16777215);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Pack"), listX + (int) (listWidth * 0.70), headerY, 16777215);

        if (!CITIndexer.isLoaded()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Loading..."), listX + (listWidth / 2), this.height / 2, 16777045);
        } else if (this.list != null && this.list.children().isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("No entries found"), listX + (listWidth / 2), this.height / 2, 11184810);
        }

        int centerX = inspectorWidth / 2;
        int previewTop = 35;
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Preview"), centerX, previewTop, 0xFFFFFF);

        // Sidebar Item Data Rendering
        if (this.selectedEntry != null) {
            int infoX = 16;
            int infoY = this.height - 125;
            int textY = infoY - 60;

            if (!this.selectedEntry.previewStacks.isEmpty()) {
                ItemStack displayItem = this.selectedEntry.previewStacks.get(0);
                float bounce = CITLedgerConfig.get().enableAnimations ? (float) Math.sin(Util.getMeasuringTimeMs() / 600.0) * 1.5f : 0.0f;
                int availableHeight = textY - previewTop;
                float scale = Math.min(10.0f, Math.min(inspectorWidth - 40, availableHeight - 40) / 16.0f);
                scale = Math.max(3.0f, scale);
                float itemCenterY = previewTop + (availableHeight / 2.0f);

                context.getMatrices().push();
                context.getMatrices().translate(centerX - (8 * scale), itemCenterY - (8 * scale) + bounce, 0);
                context.getMatrices().scale(scale, scale, 1.0f);
                context.drawItem(displayItem, 0, 0);
                context.getMatrices().pop();
            }

            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(this.selectedEntry.newName), centerX, textY, 0xFFFFAA);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(this.statusMessage), centerX, textY + 16, this.statusColor);

            String baseItemStr = "Unknown";
            String baseId = "unknown";
            if (!this.selectedEntry.itemNames.isEmpty()) {
                baseId = "minecraft:" + this.selectedEntry.itemNames.get(0);
                if (!this.selectedEntry.displayStacks.isEmpty() && !this.selectedEntry.displayStacks.get(0).isEmpty()) {
                    baseItemStr = this.selectedEntry.displayStacks.get(0).getName().getString();
                }
            }

            context.drawTextWithShadow(this.textRenderer, Text.literal("Item:"), infoX, infoY, 0xAAAAAA);
            context.drawTextWithShadow(this.textRenderer, Text.literal(baseItemStr + " (" + baseId + ")"), infoX, infoY + 12, 0xCCCCCC);
            context.drawTextWithShadow(this.textRenderer, Text.literal("Resource Pack:"), infoX, infoY + 30, 0xAAAAAA);
            context.drawTextWithShadow(this.textRenderer, Text.literal(this.selectedEntry.packName), infoX, infoY + 42, 0xCCCCCC);
        } else {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Select an item to inspect..."), centerX, this.height / 2, 0xFFFFFF);
        }

        context.drawTexture(CONFIG_ICON, this.configButton.getX() + 2, this.configButton.getY() + 2, 0, 0, 16, 16, 16, 16);

        // Context Menu Overlay Rendering
        if (this.contextMenuEntry != null) {
            context.getMatrices().push();
            context.getMatrices().translate(0.0, 0.0, 400.0);

            int menuWidth = 100;
            int menuHeight = 40;
            int itemHeight = 20;
            int mX = (int) this.contextMenuX;
            int mY = (int) this.contextMenuY;

            if (mX + menuWidth > this.width) mX = this.width - menuWidth;
            if (mY + menuHeight > this.height) mY = this.height - menuHeight;

            context.fill(mX, mY, mX + menuWidth, mY + menuHeight, 0xFF222831);
            context.drawBorder(mX, mY, menuWidth, menuHeight, 0x55FFFFFF);

            boolean hasPermission = this.client != null && this.client.player != null && this.client.player.hasPermissionLevel(2);
            boolean isHoveringMenu = mouseX >= mX && mouseX <= mX + menuWidth && mouseY >= mY && mouseY <= mY + menuHeight;
            boolean hoverCopy = isHoveringMenu && mouseY < mY + itemHeight;
            boolean hoverGive = isHoveringMenu && mouseY >= mY + itemHeight;

            context.fill(mX + 1, mY + 1, mX + menuWidth - 1, mY + itemHeight, hoverCopy ? 0x33FFFFFF : 0);
            context.drawTextWithShadow(this.textRenderer, Text.literal("Copy Name"), mX + 8, mY + 6, hoverCopy ? 0xFFFFAA : 16777215);

            context.fill(mX + 1, mY + itemHeight, mX + menuWidth - 1, mY + menuHeight - 1, hoverGive && hasPermission ? 0x33FFFFFF : 0);
            context.drawTextWithShadow(this.textRenderer, Text.literal("Give Item"), mX + 8, mY + 26, !hasPermission ? 7829367 : (hoverGive ? 0xFFFFAA : 16777215));

            context.getMatrices().pop();
        }
    }
}