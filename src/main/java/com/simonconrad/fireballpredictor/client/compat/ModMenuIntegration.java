package com.simonconrad.fireballpredictor.client.compat;

import com.simonconrad.fireballpredictor.config.ModConfig;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parentScreen -> ModConfig.HANDLER.generateGui().generateScreen(parentScreen);
    }
}
