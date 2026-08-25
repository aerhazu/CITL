package aerhazu.client.cit.ledger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

public final class CITIndexer {

    private static volatile ResultCache CACHE =
            new ResultCache(new String[0][3], false);

    private static final AtomicBoolean SCANNING =
            new AtomicBoolean(false);

    private CITIndexer() {
    }

    public static String[][] getCachedResults() {
        return CACHE.results();
    }

    public static boolean isLoaded() {
        return CACHE.loaded();
    }

    public static void refreshCache() {
        if (SCANNING.compareAndSet(false, true)) {
            CACHE = new ResultCache(CACHE.results(), false);

            Thread thread = new Thread(() -> {
                try {
                    CACHE = new ResultCache(scanAll(), true);

                    System.out.println(
                            "[CIT Ledger] Loaded "
                                    + CACHE.results().length
                                    + "CIT entries"
                    );
                } finally {
                    SCANNING.set(false);
                }
            }, "cit-ledger-indexer");

            thread.setDaemon(true);
            thread.start();
        }
    }

    private static String[][] scanAll() {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client == null) {
            return new String[0][3];
        }

        ResourceManager resourceManager = client.getResourceManager();

        List<String[]> results = new ArrayList<>();

        scanProperties(resourceManager, "optifine/cit", results);
        scanProperties(resourceManager, "citresewn/cit", results);
        scanJson(resourceManager, results);

        results.sort(
                Comparator.comparing((String[] row) -> row[0])
                        .thenComparing(row -> row[1])
                        .thenComparing(row -> row[2])
        );

        return results.toArray(new String[0][3]);
    }

    private static void scanProperties(
            ResourceManager resourceManager,
            String root,
            List<String[]> out
    ) {
        Map<Identifier, Resource> resources =
                resourceManager.findResources(
                        root,
                        path -> path.getPath().endsWith(".properties")
                );

        for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
            Properties properties = new Properties();

            try (
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(
                                    entry.getValue().getInputStream(),
                                    StandardCharsets.UTF_8
                            )
                    )
            ) {
                properties.load(reader);
            } catch (IOException ignored) {
                continue;
            }

            Set<String> items = parseItems(properties);

            if (!items.isEmpty()) {
                List<String> names =
                        parseNames(
                                properties,
                                entry.getKey().getPath()
                        );

                if (!names.isEmpty()) {
                    String packName =
                            extractPackName(entry.getValue());

                    for (String item : items) {
                        for (String name : names) {
                            boolean broken =
                                    isBroken(
                                            properties,
                                            resourceManager,
                                            entry.getKey()
                                    );

                            out.add(
                                    new String[]{
                                            item,
                                            name,
                                            packName,
                                            String.valueOf(broken)
                                    }
                            );
                        }
                    }
                }
            }
        }
    }

    private static void scanJson(
            ResourceManager resourceManager,
            List<String[]> out
    ) {
        Map<Identifier, Resource> resources =
                resourceManager.findResources(
                        "models/item",
                        path -> path.getPath().endsWith(".json")
                );

        Iterator<Map.Entry<Identifier, Resource>> iterator =
                resources.entrySet().iterator();

        while (true) {
            Map.Entry<Identifier, Resource> entry;
            String item;
            String content;

            while (true) {
                if (!iterator.hasNext()) {
                    return;
                }

                entry = iterator.next();

                item = itemNameFromModelPath(
                        entry.getKey().getPath()
                );

                if (item != null && !item.isBlank()) {
                    try (
                            BufferedReader reader =
                                    new BufferedReader(
                                            new InputStreamReader(
                                                    entry.getValue().getInputStream(),
                                                    StandardCharsets.UTF_8
                                            )
                                    )
                    ) {
                        content =
                                reader.lines()
                                        .reduce("", (a, b) -> a + "\n" + b);

                        break;
                    } catch (IOException ignored) {
                    }
                }
            }

            if (
                    content.contains("custom_name")
                            || content.contains("minecraft:custom_name")
                            || content.contains("cases")
            ) {
                for (String name : extractQuotedValues(content, "when")) {
                    out.add(
                            new String[]{
                                    item,
                                    name,
                                    extractPackName(entry.getValue()),
                                    "false"
                            }
                    );
                }
            }
        }
    }

    private static Set<String> parseItems(Properties properties) {
        String raw =
                firstNonBlank(
                        properties.getProperty("items"),
                        properties.getProperty("matchItems"),
                        properties.getProperty("matchitems")
                );

        if (raw == null) {
            return Set.of();
        }

        Set<String> items = new LinkedHashSet<>();

        for (String token : raw.split("\\s+")) {
            String cleaned = cleanItemToken(token);

            if (!cleaned.isBlank()) {
                items.add(cleaned);
            }
        }

        return items;
    }

    private static List<String> parseNames(
            Properties properties,
            String fallbackPath
    ) {
        List<String> names = new ArrayList<>();

        for (String key : properties.stringPropertyNames()) {
            String lower = key.toLowerCase(Locale.ROOT);

            if (
                    (lower.contains("name")
                            || lower.contains("custom_name"))
                            && (lower.contains("nbt")
                            || lower.contains("component")
                            || lower.startsWith("name"))
            ) {
                String value =
                        properties.getProperty(key, "").trim();

                if (!value.isBlank()) {
                    names.add(cleanNameValue(value));
                }
            }
        }

        if (names.isEmpty()) {
            String fallback =
                    fallbackPath
                            .substring(
                                    fallbackPath.lastIndexOf('/') + 1
                            )
                            .replace(".properties", "");

            names.add(fallback);
        }

        return names.stream()
                .distinct()
                .toList();
    }

    private static String cleanItemToken(String token) {
        String value = token.trim();

        int namespaceIndex = value.indexOf(':');

        if (namespaceIndex >= 0) {
            value =
                    value.substring(namespaceIndex + 1);
        }

        return value.toLowerCase(Locale.ROOT);
    }

    private static String cleanNameValue(String value) {
        String cleaned = value.trim();

        for (
                String prefix :
                Arrays.asList(
                        "pattern:",
                        "ipattern:",
                        "regex:",
                        "iregex:",
                        "raw:",
                        "literal:"
                )
        ) {
            if (
                    cleaned.regionMatches(
                            true,
                            0,
                            prefix,
                            0,
                            prefix.length()
                    )
            ) {
                cleaned =
                        cleaned.substring(prefix.length()).trim();

                break;
            }
        }

        if (
                (cleaned.startsWith("\"")
                        && cleaned.endsWith("\""))
                        || (cleaned.startsWith("'")
                        && cleaned.endsWith("'"))
        ) {
            cleaned =
                    cleaned.substring(
                            1,
                            cleaned.length() - 1
                    );
        }

        return cleaned;
    }

    private static List<String> extractQuotedValues(
            String content,
            String key
    ) {
        List<String> values = new ArrayList<>();

        String needle = "\"" + key + "\"";
        int index = 0;

        while ((index = content.indexOf(needle, index)) >= 0) {
            int colon =
                    content.indexOf(
                            ':',
                            index + needle.length()
                    );

            if (colon < 0) {
                break;
            }

            int quoteStart =
                    content.indexOf('"', colon + 1);

            if (quoteStart < 0) {
                index = colon + 1;
            } else {
                int quoteEnd =
                        content.indexOf(
                                '"',
                                quoteStart + 1
                        );

                if (quoteEnd < 0) {
                    break;
                }

                String value =
                        content.substring(
                                quoteStart + 1,
                                quoteEnd
                        ).trim();

                if (!value.isBlank()) {
                    values.add(value);
                }

                index = quoteEnd + 1;
            }
        }

        return values;
    }

    private static String itemNameFromModelPath(String path) {
        return path.startsWith("models/item/")
                && path.endsWith(".json")
                ? path.substring(
                "models/item/".length(),
                path.length() - ".json".length()
        ).toLowerCase(Locale.ROOT)
                : null;
    }

    private static String extractPackName(Resource resource) {
        try {
            return resource.getPackId();
        } catch (Throwable ignored) {
            return "Unknown Pack";
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return null;
    }

    private static boolean isBroken(
            Properties properties,
            ResourceManager resourceManager,
            Identifier propertiesId
    ) {
        List<String> assetRefs = new ArrayList<>();

        for (String key : properties.stringPropertyNames()) {
            String lower = key.toLowerCase(Locale.ROOT);

            if (
                    lower.equals("texture")
                            || lower.equals("model")
                            || lower.startsWith("texture.bow")
                            || lower.startsWith("model.bow")
                            || lower.startsWith("texture.crossbow")
                            || lower.startsWith("model.crossbow")
            ) {
                String value =
                        properties.getProperty(key, "").trim();

                if (!value.isBlank()) {
                    assetRefs.add(value);
                }
            }
        }

        if (assetRefs.isEmpty()) {
            return false;
        }

        String namespace =
                propertiesId.getNamespace();

        String propertiesPath =
                propertiesId.getPath();

        String propertiesDir =
                propertiesPath.contains("/")
                        ? propertiesPath.substring(
                        0,
                        propertiesPath.lastIndexOf('/')
                )
                        : "";

        boolean foundAtLeastOneReferencedAsset = false;

        for (String ref : assetRefs) {
            if (
                    assetExists(
                            resourceManager,
                            namespace,
                            propertiesDir,
                            ref
                    )
            ) {
                foundAtLeastOneReferencedAsset = true;
                break;
            }
        }

        return !foundAtLeastOneReferencedAsset;
    }

    private static boolean assetExists(
            ResourceManager resourceManager,
            String defaultNamespace,
            String propertiesDir,
            String rawRef
    ) {
        String ref = rawRef.trim();

        if (ref.isBlank()) {
            return false;
        }

        String namespace = defaultNamespace;
        String path = ref;

        int namespaceIndex = ref.indexOf(':');

        if (namespaceIndex >= 0) {
            namespace =
                    ref.substring(0, namespaceIndex);

            path =
                    ref.substring(namespaceIndex + 1);
        }

        List<String> candidates = new ArrayList<>();

        boolean looksLikeModel =
                path.endsWith(".json")
                        || path.contains("/")
                        || rawRef
                        .toLowerCase(Locale.ROOT)
                        .contains("model");

        if (looksLikeModel) {
            addModelCandidates(
                    candidates,
                    propertiesDir,
                    path
            );
        } else {
            addTextureCandidates(
                    candidates,
                    propertiesDir,
                    path
            );
        }

        for (String candidate : candidates) {
            try {
                Identifier id =
                        Identifier.of(
                                namespace,
                                candidate
                        );

                if (
                        resourceManager
                                .getResource(id)
                                .isPresent()
                ) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }

        return false;
    }

    private static void addTextureCandidates(
            List<String> candidates,
            String propertiesDir,
            String path
    ) {
        String p =
                path.endsWith(".png")
                        ? path
                        : path + ".png";

        candidates.add(p);

        if (!p.startsWith("textures/")) {
            candidates.add("textures/" + p);
        }

        if (!propertiesDir.isBlank()) {
            candidates.add(
                    propertiesDir + "/" + p
            );
        }
    }

    private static void addModelCandidates(
            List<String> candidates,
            String propertiesDir,
            String path
    ) {
        String p = path;

        if (path.startsWith("./")) {
            p = path.substring(2);

            if (!propertiesDir.isBlank()) {
                p = propertiesDir + "/" + p;
            }
        }

        if (!p.endsWith(".json")) {
            p = p + ".json";
        }

        candidates.add(p);

        if (!p.startsWith("models/")) {
            candidates.add("models/" + p);
        }

        if (!propertiesDir.isBlank()) {
            candidates.add(
                    propertiesDir + "/" + p
            );
        }
    }

    private record ResultCache(
            String[][] results,
            boolean loaded
    ) {
    }
}