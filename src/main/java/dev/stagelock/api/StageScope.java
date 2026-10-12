package dev.stagelock.api;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

public enum StageScope implements StringRepresentable {
    PLAYER,
    TEAM,
    GLOBAL;

    public static final StringRepresentable.EnumCodec<StageScope> CODEC = StringRepresentable.fromEnum(StageScope::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
