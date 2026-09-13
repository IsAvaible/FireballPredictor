package com.simonconrad.fireballpredictor.config;

import com.google.gson.annotations.SerializedName;
import dev.isxander.yacl3.api.NameableEnum;
import net.minecraft.network.chat.Component;

public enum FrozenHeartOverlayStyle implements NameableEnum {
    @SerializedName("thermal_scorch")
    THERMAL_SCORCH("thermal_scorch"),
    @SerializedName("frostbite_shatter")
    FROSTBITE_SHATTER("frostbite_shatter");

    private final String key;

    FrozenHeartOverlayStyle(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("yacl3.config.fireballpredictor:config.frozenHeartOverlayStyle." + key);
    }
}
