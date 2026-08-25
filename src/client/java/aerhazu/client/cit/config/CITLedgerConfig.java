package aerhazu.client.cit.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CITLedgerConfig {
    private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("cit-ledger.json");

    public boolean scanOnResourceReload = true;

    // Debug Settings
    public boolean hideBroken = false;
    public boolean hideDuplicates = false;

    // Audio Settings
    public boolean playGiveSound = true;
    public boolean playCopySound = true;
    public boolean playErrorSound = true;
    public boolean playClearSound = true;

    // Search Settings
    public boolean persistentSearch = true;
    public boolean persistentSearchMode = true;
    public boolean rightClickClearSearch = true;
    public String savedSearchText = "";
    public String savedSearchMode = "GENERAL";

    // UI Settings
    public boolean enableAnimations = true;

    public List<String> favoriteKeys = new ArrayList<>();
    private static CITLedgerConfig instance;

    public CITLedgerConfig() {
    }

    public static CITLedgerConfig get() {
        if (instance == null) {
            instance = load();
        }

        return instance;
    }

    private static CITLedgerConfig load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                CITLedgerConfig loaded = GSON.fromJson(Files.readString(CONFIG_PATH), CITLedgerConfig.class);
                if (loaded.favoriteKeys == null) {
                    loaded.favoriteKeys = new ArrayList<>();
                }

                return loaded;
            }
        } catch (Exception var1) {
            throw new RuntimeException(var1);
        }

        return new CITLedgerConfig();
    }

    public static void save() {
        try {
            Files.writeString(CONFIG_PATH, GSON.toJson(get()));
        } catch (IOException var1) {
            throw new RuntimeException(var1);
        }

    }

    public boolean isFavorite(String itemName, String newName, String packPath) {
        return favoriteKeys.contains(itemName + "||" + newName + "||" + packPath);
    }

    public void toggleFavorite(String itemName, String newName, String packPath) {
        String key = itemName + "||" + newName + "||" + packPath;
        if (favoriteKeys.contains(key)) {
            favoriteKeys.remove(key);
        } else {
            favoriteKeys.add(key);
        }
    }
}