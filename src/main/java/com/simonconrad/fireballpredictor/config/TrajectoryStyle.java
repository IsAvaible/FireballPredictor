package com.simonconrad.fireballpredictor.config;

import com.google.gson.annotations.SerializedName;
import dev.isxander.yacl3.api.NameableEnum;
import net.minecraft.network.chat.Component;

public enum TrajectoryStyle implements NameableEnum {
    @SerializedName("solid")
    SOLID("solid"),
    @SerializedName("dashed")
    DASHED("dashed"),
    @SerializedName("core_only")
    CORE_ONLY("core_only");

    private final String key;

    TrajectoryStyle(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("yacl3.config.fireballpredictor:config.trajectoryStyle." + key);
    }
}
