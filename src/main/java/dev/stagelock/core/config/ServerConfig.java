package dev.stagelock.core.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class ServerConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue CREATIVE_BYPASS;
    public static final ModConfigSpec.BooleanValue SPECTATOR_BYPASS;
    public static final ModConfigSpec.IntValue OP_BYPASS_LEVEL;
    public static final ModConfigSpec.EnumValue<TeamProviderMode> TEAM_PROVIDER;
    public static final ModConfigSpec.IntValue MESSAGE_COOLDOWN_TICKS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> FAKE_PLAYERS;
    public static final ModConfigSpec.EnumValue<DependencyMode> DEPENDENCY_MODE;
    public static final ModConfigSpec.BooleanValue STRICT_STAGES;
    public static final ModConfigSpec.EnumValue<ToolMining> RESTRICTED_TOOL_MINING;
    public static final ModConfigSpec.IntValue INVENTORY_RESCAN_TICKS;
    public static final ModConfigSpec.BooleanValue EVICT_ON_LOGIN;
    public static final ModConfigSpec.EnumValue<AutomationPolicy> AUTOMATION_POLICY;
    public static final ModConfigSpec.EnumValue<Policy> UNOWNED_POLICY;
    public static final ModConfigSpec.EnumValue<Policy> CONTEXTLESS_POLICY;
    public static final ModConfigSpec.EnumValue<RecipeHiding> VIEWER_RECIPE_HIDING;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("bypass");
        CREATIVE_BYPASS = builder
                .comment("Players in creative mode ignore all restrictions.")
                .define("creativeBypass", true);
        SPECTATOR_BYPASS = builder
                .comment("Players in spectator mode ignore all restrictions.")
                .define("spectatorBypass", true);
        OP_BYPASS_LEVEL = builder
                .comment("Players with at least this permission level ignore all restrictions. 0 disables the bypass.")
                .defineInRange("opBypassLevel", 0, 0, 4);
        builder.pop();

        builder.push("stages");
        TEAM_PROVIDER = builder
                .comment("Where team-scoped stages get team membership from.",
                        "AUTO picks the best installed provider (FTB Teams if present), falling back to VANILLA scoreboard teams.")
                .defineEnum("teamProvider", TeamProviderMode.AUTO);
        FAKE_PLAYERS = builder
                .comment("Stages given to fake players (machines, automation), as \"<name or uuid>=stage1,stage2\".",
                        "Use \"*=...\" to set the stages for any fake player that has no explicit entry.")
                .defineListAllowEmpty("fakePlayers", List.of(), () -> "*=", o -> o instanceof String s && s.contains("="));
        DEPENDENCY_MODE = builder
                .comment("How a stage's \"requires\" (from its definition) is handled when it is granted.",
                        "GRANT_PARENTS also grants missing prerequisites; REFUSE skips the grant; IGNORE grants it anyway.")
                .defineEnum("dependencyMode", DependencyMode.GRANT_PARENTS);
        STRICT_STAGES = builder
                .comment("When stage definitions exist: rules that mention an undefined stage are rejected (true) or only warned about (false).")
                .define("strictStages", false);
        builder.pop();

        builder.push("items");
        RESTRICTED_TOOL_MINING = builder
                .comment("What happens when a player mines while holding a tool they may not use (action \"mine\").",
                        "BARE_HAND mines as if the hand were empty; DENY prevents breaking blocks at all.")
                .defineEnum("restrictedToolMining", ToolMining.BARE_HAND);
        INVENTORY_RESCAN_TICKS = builder
                .comment("Inventories are rechecked whenever they change. Items inserted without marking the inventory changed",
                        "(/give, some mods) are caught by a full recheck every this many ticks. 0 disables the periodic recheck.")
                .defineInRange("inventoryRescanTicks", 20, 0, 20 * 60);
        builder.pop();

        builder.push("world");
        EVICT_ON_LOGIN = builder
                .comment("Players who log in inside a dimension they may not enter are moved to the world spawn.")
                .define("evictOnLogin", true);
        builder.pop();

        builder.push("recipes");
        AUTOMATION_POLICY = builder
                .comment("Recipe gating for machines with no player present (furnaces, campfires, crafters).",
                        "OWNER uses the stages of the player who placed the block; ALLOW never gates them; DENY always blocks locked recipes.")
                .defineEnum("automationPolicy", AutomationPolicy.OWNER);
        UNOWNED_POLICY = builder
                .comment("With automationPolicy = OWNER: what to do for machines nobody owns (placed before this mod, or by non-players).")
                .defineEnum("unownedPolicy", Policy.ALLOW);
        CONTEXTLESS_POLICY = builder
                .comment("Recipe lookups made by other mods' machines and grids where no player or owner can be found.",
                        "ALLOW keeps them working; DENY blocks every recipe that is locked by any rule.")
                .defineEnum("contextlessPolicy", Policy.ALLOW);
        builder.pop();

        builder.push("viewers");
        VIEWER_RECIPE_HIDING = builder
                .comment("Which recipe viewer (JEI/REI/EMI) entries are hidden because they show an item or fluid the player may not see.",
                        "SLOT: hide an entry when one of its slots can only show hidden things (a slot that cycles through",
                        "  hidden and visible options keeps the entry visible).",
                        "STRICT: hide an entry when any slot shows anything hidden, even as one of several options.",
                        "OUTPUT: only hide recipes whose result is hidden (StageLock 0.1.2 and older).")
                .defineEnum("recipeHiding", RecipeHiding.SLOT);
        builder.pop();

        builder.push("messages");
        MESSAGE_COOLDOWN_TICKS = builder
                .comment("Minimum ticks between repeated restriction messages to the same player.")
                .defineInRange("messageCooldownTicks", 60, 0, 20 * 60);
        builder.pop();

        SPEC = builder.build();
    }

    private ServerConfig() {
    }

    /** What a hidden item or fluid hides along with it in recipe viewers. */
    public static RecipeHiding recipeHiding() {
        return SPEC.isLoaded() ? VIEWER_RECIPE_HIDING.get() : RecipeHiding.SLOT;
    }

    public enum RecipeHiding {
        SLOT,
        STRICT,
        OUTPUT
    }

    public enum DependencyMode {
        GRANT_PARENTS,
        REFUSE,
        IGNORE
    }

    public enum AutomationPolicy {
        OWNER,
        ALLOW,
        DENY
    }

    public enum Policy {
        ALLOW,
        DENY
    }

    public enum ToolMining {
        BARE_HAND,
        DENY
    }

    public enum TeamProviderMode {
        AUTO,
        FTBTEAMS,
        VANILLA,
        NONE
    }
}
