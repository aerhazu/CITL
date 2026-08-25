package aerhazu.client.cit.ledger;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

import aerhazu.client.cit.strap.CITLScreen;

public class CITLedgerClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        CITIndexerReloadListener.register();

        String category = "CITL";

        KeyBinding openCitLedger = KeyBindingHelper.registerKeyBinding(
                new KeyBinding(
                        "Open CIT Ledger",
                        InputUtil.Type.KEYSYM,
                        71,
                        category
                )
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openCitLedger.wasPressed()) {
                if (client.player != null) {
                    client.setScreen(new CITLScreen(client.currentScreen));
                }
            }
        });

        System.out.println("[CITLedger] Initialized for 1.21.1");
    }
}