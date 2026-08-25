package aerhazu.client.cit.strap;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class PackIconManager {
    private static final Map<String, Identifier> CACHE = new HashMap<>();
    private static final Set<String> FAILED_LOOKUPS = new HashSet<>();

    public static String cleanPackName(String rawPackPath) {
        String name = rawPackPath;
        if (rawPackPath.startsWith("file/")) name = rawPackPath.substring(5);
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) name = name.substring(slash + 1);
        if (name.endsWith(".zip")) name = name.substring(0, name.length() - 4);
        return name;
    }

    public static Identifier getIcon(MinecraftClient client, String rawPackPath) {
        if (client == null) return null;
        if (CACHE.containsKey(rawPackPath)) return CACHE.get(rawPackPath);
        if (FAILED_LOOKUPS.contains(rawPackPath)) return null;

        CompletableFuture.runAsync(() -> {
            String packFileName = rawPackPath.startsWith("file/") ? rawPackPath.substring(5) : rawPackPath;
            Path resourcepacksDir = FabricLoader.getInstance().getGameDir().resolve("resourcepacks");
            Path packPath = resourcepacksDir.resolve(packFileName);

            try {
                NativeImage image = null;
                if (Files.isDirectory(packPath, LinkOption.NOFOLLOW_LINKS)) {
                    Path iconPath = packPath.resolve("pack.png");
                    if (Files.exists(iconPath, LinkOption.NOFOLLOW_LINKS)) {
                        try (InputStream in = Files.newInputStream(iconPath)) { image = NativeImage.read(in); }
                    }
                } else if (Files.exists(packPath, LinkOption.NOFOLLOW_LINKS)) {
                    try (FileSystem zipFs = FileSystems.newFileSystem(packPath, (ClassLoader) null)) {
                        Path iconPath = zipFs.getPath("pack.png");
                        if (Files.exists(iconPath, LinkOption.NOFOLLOW_LINKS)) {
                            try (InputStream in = Files.newInputStream(iconPath)) { image = NativeImage.read(in); }
                        }
                    }
                }

                if (image != null) {
                    final NativeImage finalImage = image;
                    client.execute(() -> {
                        try {
                            NativeImageBackedTexture texture = new NativeImageBackedTexture(finalImage);
                            Identifier id = client.getTextureManager().registerDynamicTexture(
                                    "cit_manager_pack_" + Math.abs(rawPackPath.hashCode()), texture
                            );
                            CACHE.put(rawPackPath, id);
                        } catch (Exception e) {
                            FAILED_LOOKUPS.add(rawPackPath);
                        }
                    });
                    return;
                }
            } catch (Exception ignored) {}

            synchronized (FAILED_LOOKUPS) {
                FAILED_LOOKUPS.add(rawPackPath);
            }
        });

        return null;
    }
}