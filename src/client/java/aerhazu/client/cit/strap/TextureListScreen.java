package aerhazu.client.cit.strap;

import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import aerhazu.client.cit.config.CITLedgerConfig;
import aerhazu.client.cit.ledger.CITIndexer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

public class TextureListScreen extends Screen {
    private final Screen parent;
    private CITEntryListWidget list;
    private TextFieldWidget searchBox;
    private ButtonWidget searchModeButton;
    private ButtonWidget configButton;

    // Bottom Bar Buttons
    private ButtonWidget doneButton;
    private ButtonWidget reloadToggleButton;
    private ButtonWidget hideBrokenButton;
    private ButtonWidget hideDuplicatesButton;

    private static SearchMode searchMode = SearchMode.GENERAL;

    private String[][] citArray;
    private CITEntryListWidget.CITEntry selectedEntry;
    private String statusMessage = "";
    private int statusColor = 16777215;

    private CITEntryListWidget.CITEntry contextMenuEntry = null;
    private double contextMenuX = 0;
    private double contextMenuY = 0;

    private final Map<String, Identifier> packIconCache = new HashMap<>();

    private static final Identifier STAR_F = Identifier.of("citl", "textures/gui/citl_fave_f.png");
    private static final Identifier STAR_T = Identifier.of("citl", "textures/gui/citl_fave_t.png");
    private static final Identifier CONFIG_ICON = Identifier.of("citl", "textures/gui/citl_config.png");

    public TextureListScreen(Screen parent) {
        super(Text.literal("CIT Manager"));
        this.citArray = new String[0][3];
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int inspectorWidth = (int) (this.width * 0.38);
        int listX = inspectorWidth;
        int listWidth = this.width - inspectorWidth;

        int listTopMargin = 51;
        int bottomBarY = this.height - 24;
        int listBottom = bottomBarY - 10;

        this.list = new CITEntryListWidget(this.client, listX, listWidth, this.width, this.height, listTopMargin, listBottom, 24);
        this.addDrawableChild(this.list);

        // BOTTOM LEFT
        this.doneButton = ButtonWidget.builder(Text.literal("Done"), (button) -> this.close())
                .dimensions(8, bottomBarY, 60, 20).build();
        this.addDrawableChild(this.doneButton);

        this.reloadToggleButton = ButtonWidget.builder(this.autoReloadText(), (button) -> {
            CITLedgerConfig config = CITLedgerConfig.get();
            config.scanOnResourceReload = !config.scanOnResourceReload;
            CITLedgerConfig.save();
            button.setMessage(this.autoReloadText());
        }).dimensions(72, bottomBarY, 90, 20).build();
        this.addDrawableChild(this.reloadToggleButton);

        this.hideBrokenButton = ButtonWidget.builder(this.hideBrokenText(), (button) -> {
            CITLedgerConfig config = CITLedgerConfig.get();
            config.hideBroken = !config.hideBroken;
            CITLedgerConfig.save();
            button.setMessage(this.hideBrokenText());
            this.rebuildList();
        }).dimensions(166, bottomBarY, 80, 20).build();
        this.addDrawableChild(this.hideBrokenButton);

        this.hideDuplicatesButton = ButtonWidget.builder(this.hideDuplicatesText(), (button) -> {
            CITLedgerConfig config = CITLedgerConfig.get();
            config.hideDuplicates = !config.hideDuplicates;
            CITLedgerConfig.save();
            button.setMessage(this.hideDuplicatesText());
            this.rebuildList();
        }).dimensions(250, bottomBarY, 90, 20).build();
        this.addDrawableChild(this.hideDuplicatesButton);

        // BOTTOM RIGHT
        // 1. Define where the search box should start on the left
        // (Set to 350 to safely clear your bottom-left buttons, or lower if you have space)
        int searchX = 364;

        // 2. Lock the right-side buttons to the right edge of the screen so they never move
        int configX = this.width - 28; // 8px margin from right edge + 20px button width
        int searchModeX = configX - 120; // 116px button width + 4px gap

        // 3. Calculate search width dynamically so it stretches ONLY to the left
        int searchWidth = searchModeX - 4 - searchX;

        this.searchBox = new TextFieldWidget(this.textRenderer, searchX, bottomBarY, searchWidth, 20, Text.literal("Search"));
        this.searchBox.setPlaceholder(Text.literal("Search..."));
        this.searchBox.setChangedListener((value) -> this.rebuildList());
        this.addDrawableChild(this.searchBox);

        this.searchModeButton = ButtonWidget.builder(Text.literal(searchMode.getDisplayName()), (button) -> {
            searchMode = searchMode.next();
            button.setMessage(Text.literal(searchMode.getDisplayName()));
            this.rebuildList();
        }).dimensions(searchModeX, bottomBarY, 116, 20).build();
        this.addDrawableChild(this.searchModeButton);

        this.configButton = ButtonWidget.builder(Text.empty(), (button) -> {}).dimensions(configX, bottomBarY, 20, 20).build();
        this.addDrawableChild(this.configButton);

        if (!CITIndexer.isLoaded()) {
            CITIndexer.refreshCache();
        }

        this.rebuildList();
        this.setInitialFocus(this.searchBox);
    }

    private Text autoReloadText() { return CITLedgerConfig.get().scanOnResourceReload ? Text.literal("Auto Reload: ON") : Text.literal("Auto Reload: OFF"); }
    private Text hideBrokenText() { return CITLedgerConfig.get().hideBroken ? Text.literal("Hide Broken") : Text.literal("Show Broken"); }
    private Text hideDuplicatesText() { return CITLedgerConfig.get().hideDuplicates ? Text.literal("Hide Duplicates") : Text.literal("Show Duplicates"); }

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
            this.client.player.playSound(SoundEvents.BLOCK_AMETHYST_CLUSTER_HIT, 1.0F, 1.00F);
        } else {
            this.setStatus("Error: No Permission", 0xFF5555);
            this.client.player.playSound(SoundEvents.ENTITY_VILLAGER_NO, 1.0F, 1.00F);
        }
    }

    private void rebuildList() {
        this.citArray = CITIndexer.getCachedResults();
        String query = this.searchBox == null ? "" : this.searchBox.getText().trim().toLowerCase(Locale.ROOT);
        this.list.rebuild(this.citArray, query, searchMode);
    }

    private ItemStack createBaseStack(String itemName) {
        Identifier id = Identifier.of("minecraft", itemName);
        Item item = id == null ? null : Registries.ITEM.get(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private ItemStack createPreviewStack(String itemName, String newName) {
        Identifier id = Identifier.of("minecraft", itemName);
        Item item = id == null ? null : Registries.ITEM.get(id);
        ItemStack stack = item == null ? ItemStack.EMPTY : new ItemStack(item);
        if (!stack.isEmpty()) stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(newName));
        return stack;
    }

    private String buildGiveCommand(String itemName, String newName) {
        return "give @p minecraft:" + itemName + "[minecraft:custom_name='" + "{\"text\":\"" + newName.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}" + "'] 1";
    }

    private String cleanPackName(String rawPackPath) {
        String name = rawPackPath;
        if (rawPackPath.startsWith("file/")) name = rawPackPath.substring(5);
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) name = name.substring(slash + 1);
        if (name.endsWith(".zip")) name = name.substring(0, name.length() - 4);
        return name;
    }

    private Identifier getPackIcon(String rawPackPath) {
        if (this.client == null) return null;
        if (this.packIconCache.containsKey(rawPackPath)) return this.packIconCache.get(rawPackPath);

        String packFileName = rawPackPath.startsWith("file/") ? rawPackPath.substring(5) : rawPackPath;
        Path resourcepacksDir = FabricLoader.getInstance().getGameDir().resolve("resourcepacks");
        Path packPath = resourcepacksDir.resolve(packFileName);

        try {
            NativeImage image = null;
            if (Files.isDirectory(packPath, new LinkOption[0])) {
                Path iconPath = packPath.resolve("pack.png");
                if (Files.exists(iconPath, new LinkOption[0])) try (InputStream in = Files.newInputStream(iconPath)) { image = NativeImage.read(in); }
            } else if (Files.exists(packPath, new LinkOption[0])) {
                try (FileSystem zipFs = FileSystems.newFileSystem(packPath, (ClassLoader)null)) {
                    Path iconPath = zipFs.getPath("pack.png");
                    if (Files.exists(iconPath, new LinkOption[0])) try (InputStream in = Files.newInputStream(iconPath)) { image = NativeImage.read(in); }
                }
            }
            if (image != null) {
                NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
                Identifier id = this.client.getTextureManager().registerDynamicTexture("cit_manager_pack_" + Math.abs(rawPackPath.hashCode()), texture);
                this.packIconCache.put(rawPackPath, id);
                return id;
            }
        } catch (Exception ignored) {}

        this.packIconCache.put(rawPackPath, null);
        return null;
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
        if (this.contextMenuEntry != null) {
            int menuWidth = 100;
            int menuHeight = 40;
            if (mouseX >= this.contextMenuX && mouseX <= this.contextMenuX + menuWidth && mouseY >= this.contextMenuY && mouseY <= this.contextMenuY + menuHeight) {
                if (mouseY < this.contextMenuY + 20) {
                    if (this.client != null) {
                        this.client.keyboard.setClipboard(this.contextMenuEntry.newName);
                        this.setStatus("Copied to clipboard!", 0x55FF55);
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
            CITEntryListWidget.CITEntry entry = this.list.getEntryAt(mouseX, mouseY);
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
        return (this.list != null && this.list.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) || super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        int inspectorWidth = (int) (this.width * 0.38);
        int listX = inspectorWidth;
        int listWidth = this.width - inspectorWidth;

        context.fill(listX - 1, 0, listX, this.height, 0x44FFFFFF);

        super.render(context, mouseX, mouseY, delta);

        // Global Title
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("CITLedger"), this.width / 2, 12, 0xFFFFFF);

        // HEADERS
        int headerY = 36;
        context.fill(listX, headerY - 6, this.width, headerY + 14, 0x55000000);

        // The CIT & Item header shifted to perfectly align with the new indented selection box
        context.drawTextWithShadow(this.textRenderer, Text.literal("CIT & Item"), listX + 32, headerY, 16777215);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Custom Name"), listX + (int)(listWidth * 0.40), headerY, 16777215);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Pack"), listX + (int)(listWidth * 0.70), headerY, 16777215);

        if (!CITIndexer.isLoaded()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Loading..."), listX + (listWidth / 2), this.height / 2, 16777045);
        } else if (this.list != null && this.list.children().isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("No entries found"), listX + (listWidth / 2), this.height / 2, 11184810);
        }

        // INSPECTOR (Left Pane)
        int centerX = inspectorWidth / 2;
        int previewTop = 35;

        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Preview"), centerX, previewTop, 0xFFFFFF);

        if (this.selectedEntry != null) {
            if (!this.selectedEntry.previewStacks.isEmpty()) {
                ItemStack displayItem = this.selectedEntry.previewStacks.get(0);
                float bounce = (float) Math.sin(Util.getMeasuringTimeMs() / 600.0) * 1.5f;

                context.getMatrices().push();
                context.getMatrices().translate(centerX - 80, previewTop + 70 + bounce, 0);
                context.getMatrices().scale(10.0f, 10.0f, 1.0f);
                context.drawItem(displayItem, 0, 0);
                context.getMatrices().pop();
            }

            int infoX = 16;
            int infoY = this.height - 100;

            int textY = infoY - 45;
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

        if (this.contextMenuEntry != null) {
            int mX = (int) this.contextMenuX;
            int mY = (int) this.contextMenuY;
            if (mX + 100 > this.width) mX = this.width - 100;
            if (mY + 40 > this.height) mY = this.height - 40;

            context.fill(mX, mY, mX + 100, mY + 40, 0xF0101010);
            context.drawBorder(mX, mY, 100, 40, 0xFF555555);

            context.drawTextWithShadow(this.textRenderer, Text.literal("Copy Name"), mX + 8, mY + 6, 16777215);
            boolean hasPermission = this.client != null && this.client.player != null && this.client.player.hasPermissionLevel(2);
            context.drawTextWithShadow(this.textRenderer, Text.literal("Give Item"), mX + 8, mY + 22, hasPermission ? 16777215 : 7829367);
        }
    }

    private enum SearchMode {
        GENERAL, ITEM, NAME, PACK;

        private SearchMode next() {
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
    }

    private class CITEntryListWidget extends AlwaysSelectedEntryListWidget<CITEntryListWidget.CITEntry> {
        private final int listX;
        private final int listWidth;

        public CITEntryListWidget(MinecraftClient client, int listX, int listWidth, int screenWidth, int screenHeight, int top, int bottom, int itemHeight) {
            super(client, listWidth, bottom - top, top, itemHeight);
            this.listX = listX;
            this.listWidth = listWidth;
            this.setX(listX);
        }

        @Override
        public int getRowWidth() {
            // Limits the right side of the selection box
            return this.listWidth - 52;
        }

        @Override
        public int getRowLeft() {
            // Shifts the vanilla selection box 24 pixels to the right, leaving an exclusive column for the star
            return this.listX + 26;
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
            return super.mouseClicked(mouseX, mouseY, button);
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
                    String packDisplayName = TextureListScreen.this.cleanPackName(row[2]);

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
                        ItemStack baseStack = TextureListScreen.this.createBaseStack(row[0]);
                        ItemStack previewStack = TextureListScreen.this.createPreviewStack(row[0], row[1]);
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

        class CITEntry extends AlwaysSelectedEntryListWidget.Entry<CITEntry> {
            private final String newName;
            private final String packName;
            private final String rawPackPath;
            private final boolean broken;
            private final boolean duplicate;
            private final List<String> itemNames;
            private final List<ItemStack> displayStacks;
            private final List<ItemStack> previewStacks;
            private long lastClickTime = 0;
            private float hoverProgress = 0.0f;

            CITEntry(GroupedRowData row) {
                this.newName = row.newName;
                this.packName = TextureListScreen.this.cleanPackName(row.rawPackPath);
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
                    int starX = CITEntryListWidget.this.listX + 8; // Exact coordinate where star is drawn

                    if (mouseX >= starX && mouseX <= starX + 10) {
                        CITLedgerConfig.get().toggleFavorite(this.itemNames.get(0), this.newName, this.rawPackPath);
                        CITLedgerConfig.save();
                        return true;
                    }

                    TextureListScreen.this.selectedEntry = this;
                    TextureListScreen.this.contextMenuEntry = null;
                    TextureListScreen.this.setStatus("", 16777215);

                    long time = Util.getMeasuringTimeMs();
                    if (time - this.lastClickTime < 300) {
                        TextureListScreen.this.giveSelectedItem();
                    }
                    this.lastClickTime = time;
                    return true;
                }
                return false;
            }

            @Override
            public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
                boolean selected = TextureListScreen.this.selectedEntry == this;
                boolean favorite = CITLedgerConfig.get().isFavorite(this.itemNames.get(0), this.newName, this.rawPackPath);

                // Custom selection background is now strictly locked INSIDE x and entryWidth
                if (selected || hovered) {
                    int bgColor = selected ? (this.duplicate ? 1722430259 : 1429892773) : 857874978;
                    context.fill(x, y, x + entryWidth, y + entryHeight, bgColor);
                }

                int color = 16777215;
                if (this.broken) color = 16742263;
                else if (this.duplicate) color = 16775065;
                else if (favorite) color = 16766720;

                // Star drawn OUTSIDE the selection box on the left margin we created
                int starX = CITEntryListWidget.this.listX + 8;
                Identifier starTex = favorite ? STAR_T : STAR_F;
                context.drawTexture(starTex, starX, y + 7, 0, 0, 10, 10, 10, 10);

                // Icon drawn securely inside the selection box bounds
                // Icon drawn securely inside the selection box bounds with smooth scale animation
                int iconX = x + 4;
                if (!this.previewStacks.isEmpty()) {
                    ItemStack currentDisplay = this.previewStacks.get(0);

                    // Determine target state (1.0 if hovered or selected, 0.0 otherwise)
                    boolean active = selected || hovered;
                    float targetProgress = active ? 1.0f : 0.0f;

                    // Smoothly interpolate (lerp) toward the target state each frame
                    this.hoverProgress += (targetProgress - this.hoverProgress) * 0.3f;

                    // Calculate scale factor: 1.0 (normal size) up to 1.25 (25% larger when active)
                    float scale = 1.0f + (0.5f * this.hoverProgress);

                    context.getMatrices().push();
                    // Center point of the 16x16 item icon
                    float centerX = iconX + 8;
                    float centerY = y + 4 + 8;

                    context.getMatrices().translate(centerX, centerY, 0.0f);
                    context.getMatrices().scale(scale, scale, 1.0f);
                    context.getMatrices().translate(-centerX, -centerY, 0.0f);

                    context.drawItem(currentDisplay, iconX, y + 4);

                    context.getMatrices().pop();
                }

                String baseItemName = "Unknown";
                if (!this.displayStacks.isEmpty() && !this.displayStacks.get(0).isEmpty()) {
                    baseItemName = this.displayStacks.get(0).getName().getString();
                }

                context.drawTextWithShadow(TextureListScreen.this.textRenderer, Text.literal(baseItemName), iconX + 26, y + 8, 11184810);

                int nameColumnX = CITEntryListWidget.this.listX + (int)(CITEntryListWidget.this.listWidth * 0.40);
                context.drawTextWithShadow(TextureListScreen.this.textRenderer, Text.literal(this.newName), nameColumnX, y + 8, color);

                if (this.duplicate) {
                    context.drawTextWithShadow(TextureListScreen.this.textRenderer, Text.literal("CONFLICT"), nameColumnX + 160, y + 8, 16733525);
                }

                int packColumnX = CITEntryListWidget.this.listX + (int)(CITEntryListWidget.this.listWidth * 0.70);
                Identifier packIcon = TextureListScreen.this.getPackIcon(this.rawPackPath);

                if (packIcon != null) {
                    context.drawTexture(packIcon, packColumnX, y + 4, 0.0F, 0.0F, 16, 16, 16, 16);
                }

                int packTextX = packColumnX + 20;
                int maxTextWidth = (CITEntryListWidget.this.listX + CITEntryListWidget.this.listWidth) - packTextX - 33;
                int textWidth = TextureListScreen.this.textRenderer.getWidth(this.packName);

                if (textWidth > maxTextWidth) {
                    context.enableScissor(packTextX, y, packTextX + maxTextWidth, y + entryHeight);
                    double cycle = (Util.getMeasuringTimeMs() / 2000.0) % (Math.PI * 2);
                    int scroll = (int) ((Math.sin(cycle - Math.PI/2) + 1.0) / 2.0 * (textWidth - maxTextWidth));
                    context.drawTextWithShadow(TextureListScreen.this.textRenderer, Text.literal(this.packName), packTextX - scroll, y + 8, 13421772);
                    context.disableScissor();
                } else {
                    context.drawTextWithShadow(TextureListScreen.this.textRenderer, Text.literal(this.packName), packTextX, y + 8, 13421772);
                }
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