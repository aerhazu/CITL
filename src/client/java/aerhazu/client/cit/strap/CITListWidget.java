package aerhazu.client.cit.strap;

import aerhazu.client.cit.config.CITLedgerConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.util.*;

public class CITListWidget extends AlwaysSelectedEntryListWidget<CITListWidget.CITEntry> {
    private final CITLScreen screen;
    private final int listX;
    private final int listWidth;

    private static final Identifier STAR_F = Identifier.of("citl", "textures/gui/citl_fave_f.png");
    private static final Identifier STAR_T = Identifier.of("citl", "textures/gui/citl_fave_t.png");

    public CITListWidget(CITLScreen screen, MinecraftClient client, int listX, int listWidth, int screenWidth, int screenHeight, int top, int bottom, int itemHeight) {
        super(client, listWidth, bottom - top, top, itemHeight);
        this.screen = screen;
        this.listX = listX;
        this.listWidth = listWidth;
        this.setX(listX);
    }

    @Override
    public int getRowWidth() {
        return this.listWidth - 52;
    }

    @Override
    public int getRowLeft() {
        return this.listX + 26;
    }

    @Override
    public int getScrollbarX() {
        return this.listX + this.listWidth - 6;
    }

    public CITEntry getEntryAt(double mouseX, double mouseY) {
        if (this.isMouseOver(mouseX, mouseY)) {
            for (int i = 0; i < this.getEntryCount(); i++) {
                if (mouseY >= this.getRowTop(i) && mouseY <= this.getRowBottom(i)) {
                    return this.getEntry(i);
                }
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        if (this.isMouseOver(mouseX, mouseY)) {
            for (int i = 0; i < this.getEntryCount(); i++) {
                if (mouseY >= this.getRowTop(i) && mouseY <= this.getRowBottom(i)) {
                    CITEntry entry = this.getEntry(i);
                    if (entry.mouseClicked(mouseX, mouseY, button)) {
                        this.setFocused(entry);
                        this.setDragging(true);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static ItemStack createBaseStack(String itemName) {
        Identifier id = Identifier.of("minecraft", itemName);
        Item item = id == null ? null : Registries.ITEM.get(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static ItemStack createPreviewStack(String itemName, String newName) {
        Identifier id = Identifier.of("minecraft", itemName);
        Item item = id == null ? null : Registries.ITEM.get(id);
        ItemStack stack = item == null ? ItemStack.EMPTY : new ItemStack(item);
        if (!stack.isEmpty()) stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(newName));
        return stack;
    }

    public void rebuild(String[][] citArray, String query, SearchMode searchMode) {
        this.clearEntries();
        Map<String, List<String>> duplicateSources = new HashMap<>();

        for (String[] row : citArray) {
            if (row.length >= 3) {
                String key = row[0] + "||" + row[1];
                duplicateSources.computeIfAbsent(key, (k) -> new ArrayList<>());
                if (!duplicateSources.get(key).contains(row[2])) duplicateSources.get(key).add(row[2]);
            }
        }

        Map<String, GroupedRowData> grouped = new LinkedHashMap<>();

        for (String[] row : citArray) {
            if (row.length >= 3) {
                String packDisplayName = PackIconManager.cleanPackName(row[2]);

                String target = switch (searchMode) {
                    case GENERAL -> row[0] + " " + row[1] + " " + packDisplayName;
                    case ITEM -> row[0];
                    case NAME -> row[1];
                    case PACK -> packDisplayName;
                };

                boolean matches = true;
                if (!query.isEmpty()) {
                    String[] tokens = query.toLowerCase(Locale.ROOT).split("\\s+");
                    String lowerTarget = target.toLowerCase(Locale.ROOT);
                    for (String token : tokens) {
                        if (!lowerTarget.contains(token)) {
                            matches = false;
                            break;
                        }
                    }
                }

                if (matches) {
                    String groupKey = row[0] + "||" + row[1] + "||" + row[2];
                    GroupedRowData group = grouped.computeIfAbsent(groupKey, (k) -> new GroupedRowData(row[1], row[2]));
                    ItemStack baseStack = createBaseStack(row[0]);
                    ItemStack previewStack = createPreviewStack(row[0], row[1]);
                    boolean scannerBroken = row.length > 3 && Boolean.parseBoolean(row[3]);
                    boolean duplicate = duplicateSources.getOrDefault(row[0] + "||" + row[1], List.of()).size() > 1;

                    group.itemNames.add(row[0]);
                    group.displayStacks.add(baseStack);
                    group.previewStacks.add(previewStack);

                    if (!baseStack.isEmpty() && !scannerBroken) group.hasAnyValidStack = true;
                    group.duplicate = group.duplicate || duplicate;
                }
            }
        }

        List<GroupedRowData> rows = new ArrayList<>();
        for (GroupedRowData row : grouped.values()) {
            if ((!CITLedgerConfig.get().hideBroken || !row.isBroken()) && (!CITLedgerConfig.get().hideDuplicates || !row.duplicate)) {
                rows.add(row);
            }
        }

        rows.sort((a, b) -> {
            boolean aFav = CITLedgerConfig.get().isFavorite(a.itemNames.get(0), a.newName, a.rawPackPath);
            boolean bFav = CITLedgerConfig.get().isFavorite(b.itemNames.get(0), b.newName, b.rawPackPath);
            if (aFav != bFav) return aFav ? -1 : 1;
            else if (a.duplicate != b.duplicate) return a.duplicate ? -1 : 1;
            else if (a.isBroken() != b.isBroken()) return a.isBroken() ? 1 : -1;
            else return Comparator.comparing((GroupedRowData r) -> r.newName).thenComparing((r) -> r.rawPackPath).compare(a, b);
        });

        for (GroupedRowData row : rows) this.addEntry(new CITEntry(row));
    }

    public class CITEntry extends AlwaysSelectedEntryListWidget.Entry<CITEntry> {
        public final String newName;
        public final String packName;
        public final String rawPackPath;
        public final boolean broken;
        public final boolean duplicate;
        public final List<String> itemNames;
        public final List<ItemStack> displayStacks;
        public final List<ItemStack> previewStacks;

        private long lastClickTime = 0;
        private float hoverProgress = 0.0f;

        CITEntry(GroupedRowData row) {
            this.newName = row.newName;
            this.packName = PackIconManager.cleanPackName(row.rawPackPath);
            this.rawPackPath = row.rawPackPath;
            this.broken = row.isBroken();
            this.duplicate = row.duplicate;
            this.itemNames = row.itemNames;
            this.displayStacks = row.displayStacks;
            this.previewStacks = row.previewStacks;
        }

        @Override
        public Text getNarration() {
            return Text.literal(this.newName + ", " + this.packName);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                int starX = CITListWidget.this.listX + 8;

                if (mouseX >= starX && mouseX <= starX + 10) {
                    CITLedgerConfig.get().toggleFavorite(this.itemNames.get(0), this.newName, this.rawPackPath);
                    CITLedgerConfig.save();
                    return true;
                }

                screen.selectedEntry = this;
                screen.contextMenuEntry = null;
                screen.setStatus("", 16777215);

                long time = Util.getMeasuringTimeMs();
                if (time - this.lastClickTime < 300) {
                    screen.giveSelectedItem();
                }
                this.lastClickTime = time;
                return true;
            }
            return false;
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            boolean selected = screen.selectedEntry == this;
            boolean favorite = CITLedgerConfig.get().isFavorite(this.itemNames.get(0), this.newName, this.rawPackPath);

            if (selected || hovered) {
                int boxHeight = 26;
                int yOffset = (entryHeight - boxHeight) / 2;
                int renderY = y + yOffset;

                context.fill(x, renderY, x + entryWidth, renderY + boxHeight, 0xFF222831);
                context.fill(x, renderY, x + entryWidth, renderY + boxHeight, selected ? (this.duplicate ? 0x55FF5555 : 0x66FFFFFF) : 0x33FFFFFF);
                context.drawBorder(x, renderY, entryWidth, boxHeight, selected ? 0xCCFFFFFF : 0x55FFFFFF);
            }

            int color = this.broken ? 16742263 : (this.duplicate ? 16775065 : (favorite ? 16766720 : 16777215));
            context.drawTexture(favorite ? STAR_T : STAR_F, CITListWidget.this.listX + 8, y + 5, 0, 0, 10, 10, 10, 10);

            int iconX = x + 4;
            if (!this.previewStacks.isEmpty()) {
                ItemStack currentDisplay = this.previewStacks.get(0);
                boolean active = selected || hovered;
                float scale = 1.0f;

                if (CITLedgerConfig.get().enableAnimations) {
                    float targetProgress = active ? 1.0f : 0.0f;
                    this.hoverProgress += (targetProgress - this.hoverProgress) * 0.3f;
                    scale = 1.0f + (0.5f * this.hoverProgress);
                } else {
                    scale = active ? 1.5f : 1.0f;
                }

                context.getMatrices().push();
                float centerX = iconX + 8;
                float centerY = y + 12;
                context.getMatrices().translate(centerX, centerY, 0.0f);
                context.getMatrices().scale(scale, scale, 1.0f);
                context.getMatrices().translate(-centerX, -centerY, 0.0f);
                context.drawItem(currentDisplay, iconX, y + 2);
                context.getMatrices().pop();
            }

            String baseItemName = (!this.displayStacks.isEmpty() && !this.displayStacks.get(0).isEmpty())
                    ? this.displayStacks.get(0).getName().getString() : "Unknown";

            context.drawTextWithShadow(screen.getTextRenderer(), Text.literal(baseItemName), iconX + 28, y + 6, 0xFFFFFFFF);

            int nameColumnX = CITListWidget.this.listX + (int)(CITListWidget.this.listWidth * 0.40);
            context.drawTextWithShadow(screen.getTextRenderer(), Text.literal(this.newName), nameColumnX, y + 6, color);

            if (this.duplicate) {
                context.drawTextWithShadow(screen.getTextRenderer(), Text.literal("CONFLICT"), nameColumnX + 160, y + 6, 16733525);
            }

            int packColumnX = CITListWidget.this.listX + (int)(CITListWidget.this.listWidth * 0.70);
            Identifier packIcon = PackIconManager.getIcon(screen.getClient(), this.rawPackPath);

            if (packIcon != null) {
                context.drawTexture(packIcon, packColumnX, y + 2, 0.0F, 0.0F, 16, 16, 16, 16);
            }

            int packTextX = packColumnX + 20;
            int maxTextWidth = (CITListWidget.this.listX + CITListWidget.this.listWidth) - packTextX - 33;
            int textWidth = screen.getTextRenderer().getWidth(this.packName);

            if (textWidth > maxTextWidth) {
                context.enableScissor(packTextX, y, packTextX + maxTextWidth, y + entryHeight);
                int scroll = 0;
                if (CITLedgerConfig.get().enableAnimations) {
                    double cycle = (Util.getMeasuringTimeMs() / 2000.0) % (Math.PI * 2);
                    scroll = (int) ((Math.sin(cycle - Math.PI/2) + 1.0) / 2.0 * (textWidth - maxTextWidth));
                }
                context.drawTextWithShadow(screen.getTextRenderer(), Text.literal(this.packName), packTextX - scroll, y + 6, 13421772);
                context.disableScissor();
            } else {
                context.drawTextWithShadow(screen.getTextRenderer(), Text.literal(this.packName), packTextX, y + 6, 13421772);
            }
        }
    }

    private static class GroupedRowData {
        final String newName;
        final String rawPackPath;
        final List<String> itemNames = new ArrayList<>();
        final List<ItemStack> displayStacks = new ArrayList<>();
        final List<ItemStack> previewStacks = new ArrayList<>();
        boolean hasAnyValidStack = false;
        boolean duplicate;

        GroupedRowData(String newName, String rawPackPath) {
            this.newName = newName;
            this.rawPackPath = rawPackPath;
        }

        boolean isBroken() { return !this.hasAnyValidStack; }
    }
}