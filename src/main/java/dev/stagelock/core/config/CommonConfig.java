package dev.stagelock.core.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class CommonConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue STAGE_ALIAS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        STAGE_ALIAS = builder
                .comment("Also register /stage as a shorter alias of /stagelock. Disable if another mod owns /stage.")
                .define("registerStageAlias", true);
        SPEC = builder.build();
    }

    private CommonConfig() {
    }
}
