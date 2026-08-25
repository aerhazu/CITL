package aerhazu.client.cit.config;

import aerhazu.client.cit.strap.CITLScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class CITLedgerIntegrationAPI implements ModMenuApi {
    public CITLedgerIntegrationAPI() {
    }

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // Point this to the new config screen!
        return CITLScreen::new;
    }
}