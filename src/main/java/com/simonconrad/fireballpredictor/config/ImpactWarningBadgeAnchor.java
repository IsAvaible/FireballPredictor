package com.simonconrad.fireballpredictor.config;

import com.google.gson.annotations.SerializedName;
import dev.isxander.yacl3.api.NameableEnum;
import net.minecraft.network.chat.Component;

public enum ImpactWarningBadgeAnchor implements NameableEnum {
    @SerializedName("topleft")
    TOP_LEFT("topleft"),
    @SerializedName("topcenter")
    TOP_CENTER("topcenter"),
    @SerializedName("topright")
    TOP_RIGHT("topright"),
    @SerializedName("bottomleft")
    BOTTOM_LEFT("bottomleft"),
    @SerializedName("bottomcenter")
    BOTTOM_CENTER("bottomcenter"),
    @SerializedName("bottomright")
    BOTTOM_RIGHT("bottomright");

    private final String key;

    ImpactWarningBadgeAnchor(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("yacl3.config.fireballpredictor:config.impactWarningBadgeAnchor." + key);
    }
}
