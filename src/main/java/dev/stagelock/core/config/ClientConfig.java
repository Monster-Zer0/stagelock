package dev.stagelock.core.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue HIDE_NAMES;
    public static final ModConfigSpec.BooleanValue SHOW_REQUIREMENTS;
    public static final ModConfigSpec.BooleanValue SHOW_LOCK_ICON;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        HIDE_NAMES = builder
                .comment("Show restricted items under their hidden name (e.g. \"Unfamiliar Item\") everywhere: tooltips, hotbar, Jade.")
                .define("hideNames", true);
        SHOW_REQUIREMENTS = builder
                .comment("List the missing stages in the tooltip of restricted items.")
                .define("showRequirementsInTooltip", true);
        SHOW_LOCK_ICON = builder
                .comment("Draw a padlock on locked items in inventories and recipe viewers.")
                .define("showLockIcon", true);
        SPEC = builder.build();
    }

    private ClientConfig() {
    }
}
