package aerhazu.client.cit.ledger;

import aerhazu.client.cit.config.CITLedgerConfig;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

public final class CITIndexerReloadListener {

    private CITIndexerReloadListener() {
    }

    public static void register() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
                .registerReloadListener(
                        new SimpleSynchronousResourceReloadListener() {

                            private static final Identifier ID =
                                    Identifier.of(
                                            "cit-ledger",
                                            "cit_scanner_reload"
                                    );

                            @Override
                            public Identifier getFabricId() {
                                return ID;
                            }

                            @Override
                            public void reload(ResourceManager manager) {
                                if (CITLedgerConfig.get().scanOnResourceReload) {
                                    CITIndexer.refreshCache();
                                }
                            }
                        }
                );
    }
}