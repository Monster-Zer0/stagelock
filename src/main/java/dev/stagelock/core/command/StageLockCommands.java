package dev.stagelock.core.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.serialization.JsonOps;
import dev.stagelock.api.StageRequirement;
import dev.stagelock.api.StageSet;
import dev.stagelock.api.restriction.ItemAction;
import dev.stagelock.api.restriction.ItemRestriction;
import dev.stagelock.core.enforce.Restrictions;
import dev.stagelock.core.restriction.RestrictionManager;
import dev.stagelock.core.config.CommonConfig;
import dev.stagelock.core.definition.StageDefinitions;
import dev.stagelock.core.stage.StageManager;
import dev.stagelock.core.tools.GameStagesImport;
import dev.stagelock.core.tools.RuleDump;
import dev.stagelock.core.team.TeamProviders;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class StageLockCommands {

    private static final DynamicCommandExceptionType UNKNOWN_TEAM = new DynamicCommandExceptionType(
            name -> Component.translatable("commands.stagelock.unknown_team", name));

    private static final SuggestionProvider<CommandSourceStack> KNOWN_STAGES = (context, builder) -> {
        StageManager manager = StageManager.getOrNull();
        Set<String> known = new java.util.TreeSet<>(StageDefinitions.server().all().keySet());
        if (manager != null) known.addAll(manager.store().allStages());
        return SharedSuggestionProvider.suggest(known, builder);
    };

    private StageLockCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("stagelock")
                .then(Commands.literal("grant").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("targets", GameProfileArgument.gameProfile())
                                .then(stageArg()
                                        .executes(ctx -> grant(ctx, false))
                                        .then(Commands.argument("silent", BoolArgumentType.bool())
                                                .executes(ctx -> grant(ctx, BoolArgumentType.getBool(ctx, "silent")))))))
                .then(Commands.literal("revoke").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("targets", GameProfileArgument.gameProfile())
                                .then(stageArg()
                                        .executes(ctx -> revoke(ctx, false))
                                        .then(Commands.argument("silent", BoolArgumentType.bool())
                                                .executes(ctx -> revoke(ctx, BoolArgumentType.getBool(ctx, "silent")))))))
                .then(Commands.literal("grantall").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("targets", GameProfileArgument.gameProfile())
                                .executes(StageLockCommands::grantAll)))
                .then(Commands.literal("clear").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("targets", GameProfileArgument.gameProfile())
                                .executes(StageLockCommands::clear)))
                .then(Commands.literal("list")
                        .executes(ctx -> listSelf(ctx.getSource()))
                        .then(Commands.argument("target", EntityArgument.player()).requires(s -> s.hasPermission(2))
                                .executes(ctx -> list(ctx.getSource(), EntityArgument.getPlayer(ctx, "target")))))
                .then(Commands.literal("check").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(stageArg().executes(StageLockCommands::check))))
                .then(Commands.literal("dump").requires(s -> s.hasPermission(2)).executes(StageLockCommands::dump))
                .then(Commands.literal("import").requires(s -> s.hasPermission(3))
                        .then(Commands.literal("gamestages").executes(StageLockCommands::importGameStages)))
                .then(Commands.literal("why").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(StageLockCommands::why)))
                .then(Commands.literal("global").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("grant").then(stageArg().executes(ctx -> global(ctx, true))))
                        .then(Commands.literal("revoke").then(stageArg().executes(ctx -> global(ctx, false))))
                        .then(Commands.literal("list").executes(StageLockCommands::globalList)))
                .then(Commands.literal("team").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("grant").then(teamArg().then(stageArg().executes(ctx -> team(ctx, true)))))
                        .then(Commands.literal("revoke").then(teamArg().then(stageArg().executes(ctx -> team(ctx, false)))))
                        .then(Commands.literal("list").then(teamArg().executes(StageLockCommands::teamList))));

        var node = dispatcher.register(root);
        if (CommonConfig.STAGE_ALIAS.get()) {
            dispatcher.register(Commands.literal("stage").redirect(node));
        }
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> stageArg() {
        return Commands.argument("stage", StageArgument.stage()).suggests(KNOWN_STAGES);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> teamArg() {
        return Commands.argument("team", StringArgumentType.string())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                        ctx.getSource().getServer().getScoreboard().getTeamNames(), builder));
    }

    // ---------------------------------------------------------------- player scope

    private static int grant(CommandContext<CommandSourceStack> ctx, boolean silent) throws CommandSyntaxException {
        String stage = StageArgument.getStage(ctx, "stage");
        Collection<GameProfile> targets = profiles(ctx, "targets");
        int changed = 0;
        for (GameProfile profile : targets) {
            if (StageManager.get().grantPlayer(profile.getId(), List.of(stage)).isEmpty()) continue;
            changed++;
            notifyTarget(ctx.getSource(), profile.getId(), silent, Component.translatable("message.stagelock.granted", stage));
        }
        return feedback(ctx.getSource(), "grant", stage, targets, changed);
    }

    private static int revoke(CommandContext<CommandSourceStack> ctx, boolean silent) throws CommandSyntaxException {
        String stage = StageArgument.getStage(ctx, "stage");
        Collection<GameProfile> targets = profiles(ctx, "targets");
        int changed = 0;
        for (GameProfile profile : targets) {
            if (StageManager.get().revokePlayer(profile.getId(), List.of(stage)).isEmpty()) continue;
            changed++;
            notifyTarget(ctx.getSource(), profile.getId(), silent, Component.translatable("message.stagelock.revoked", stage));
        }
        return feedback(ctx.getSource(), "revoke", stage, targets, changed);
    }

    /** Grants every defined stage. */
    private static int grantAll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<GameProfile> targets = profiles(ctx, "targets");
        List<String> all = List.copyOf(StageDefinitions.server().all().keySet());
        int total = 0;
        for (GameProfile profile : targets) total += StageManager.get().grantPlayer(profile.getId(), all).size();
        int granted = total;
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.stagelock.grantall.success", granted, targets.size()), true);
        return granted;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<GameProfile> targets = profiles(ctx, "targets");
        int total = 0;
        for (GameProfile profile : targets) {
            total += StageManager.get().clearPlayer(profile.getId()).size();
        }
        int removed = total;
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.stagelock.clear.success", removed, targets.size()), true);
        return removed;
    }

    private static int listSelf(CommandSourceStack source) throws CommandSyntaxException {
        return list(source, source.getPlayerOrException());
    }

    private static int list(CommandSourceStack source, ServerPlayer target) {
        StageManager manager = StageManager.get();
        StageSet effective = manager.stages(target);
        Set<String> own = manager.store().player(target.getUUID());
        String team = manager.teamOf(target.getUUID()).orElse("-");
        source.sendSuccess(() -> Component.translatable("commands.stagelock.list.header",
                target.getDisplayName(), effective.size(), team), false);
        source.sendSuccess(() -> Component.translatable("commands.stagelock.list.player", join(own)), false);
        source.sendSuccess(() -> Component.translatable("commands.stagelock.list.effective", join(effective.asSet())), false);
        return effective.size();
    }

    private static int check(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        String stage = StageArgument.getStage(ctx, "stage");
        boolean has = StageManager.get().stages(target).has(stage);
        ctx.getSource().sendSuccess(() -> Component.translatable(
                has ? "commands.stagelock.check.has" : "commands.stagelock.check.missing",
                target.getDisplayName(), stage), false);
        return has ? 1 : 0;
    }

    private static int dump(CommandContext<CommandSourceStack> ctx) {
        try {
            var file = RuleDump.write(ctx.getSource().getServer(),
                    net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("stagelock").resolve("dump.md"));
            ctx.getSource().sendSuccess(() -> Component.translatable("commands.stagelock.dump.success", file.toString()), false);
            return 1;
        } catch (java.io.IOException e) {
            ctx.getSource().sendFailure(Component.translatable("commands.stagelock.dump.failed", e.getMessage()));
            return 0;
        }
    }

    private static int importGameStages(CommandContext<CommandSourceStack> ctx) {
        try {
            var result = GameStagesImport.run(ctx.getSource().getServer());
            ctx.getSource().sendSuccess(() -> Component.translatable("commands.stagelock.import.success",
                    result.stages(), result.players(), result.skipped()), true);
            return result.stages();
        } catch (java.io.IOException e) {
            ctx.getSource().sendFailure(Component.translatable("commands.stagelock.import.failed", e.getMessage()));
            return 0;
        }
    }

    /** Explains which rules apply to the target's main-hand item and whether they currently block it. */
    private static int why(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        ItemStack stack = target.getMainHandItem();
        CommandSourceStack source = ctx.getSource();
        var entries = RestrictionManager.server().items().matching(stack);
        if (entries.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.stagelock.why.none", stack.getDisplayName()), false);
            return 0;
        }
        StageSet stages = StageManager.get().stages(target);
        boolean exempt = Restrictions.isExempt(target);
        source.sendSuccess(() -> Component.translatable("commands.stagelock.why.header",
                stack.getDisplayName(), entries.size(), exempt ? Component.translatable("commands.stagelock.why.exempt") : ""), false);
        for (var entry : entries) {
            ItemRestriction rule = entry.rule();
            boolean met = rule.requires().test(stages);
            String requirement = StageRequirement.CODEC.encodeStart(JsonOps.INSTANCE, rule.requires()).getOrThrow().toString();
            String actions = Arrays.stream(ItemAction.values()).filter(rule::blocks)
                    .map(a -> a.name().toLowerCase(Locale.ROOT)).collect(Collectors.joining(", "));
            source.sendSuccess(() -> Component.literal(" - " + entry.id() + " ")
                    .append(Component.translatable(met ? "commands.stagelock.why.met" : "commands.stagelock.why.unmet")
                            .withStyle(met ? ChatFormatting.GREEN : ChatFormatting.RED))
                    .append(Component.literal(" requires " + requirement + "; blocks: " + actions).withStyle(ChatFormatting.GRAY)), false);
        }
        // When several locked rules match, the strictest decides each action; show which one that is.
        Map<ResourceLocation, List<String>> decided = new java.util.LinkedHashMap<>();
        for (ItemAction action : ItemAction.values()) {
            var hit = RestrictionManager.server().items().find(stack, stages, action);
            if (hit == null) continue;
            String name = action.name().toLowerCase(Locale.ROOT);
            if (action == ItemAction.HOLD) name += "=" + hit.rule().actions().hold().getSerializedName();
            decided.computeIfAbsent(hit.id(), id -> new java.util.ArrayList<>()).add(name);
        }
        decided.forEach((id, actions) -> source.sendSuccess(() -> Component.translatable("commands.stagelock.why.decides",
                id.toString(), String.join(", ", actions)).withStyle(ChatFormatting.YELLOW), false));
        return entries.size();
    }

    // ---------------------------------------------------------------- global & team

    private static int global(CommandContext<CommandSourceStack> ctx, boolean grant) {
        String stage = StageArgument.getStage(ctx, "stage");
        Set<String> changed = grant
                ? StageManager.get().grantGlobal(List.of(stage))
                : StageManager.get().revokeGlobal(List.of(stage));
        String key = "commands.stagelock.global." + (grant ? "grant" : "revoke") + (changed.isEmpty() ? ".unchanged" : ".success");
        ctx.getSource().sendSuccess(() -> Component.translatable(key, stage), true);
        return changed.size();
    }

    private static int globalList(CommandContext<CommandSourceStack> ctx) {
        Set<String> stages = StageManager.get().store().global();
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.stagelock.global.list", join(stages)), false);
        return stages.size();
    }

    private static int team(CommandContext<CommandSourceStack> ctx, boolean grant) throws CommandSyntaxException {
        String team = resolveTeam(ctx);
        String stage = StageArgument.getStage(ctx, "stage");
        Set<String> changed = grant
                ? StageManager.get().grantTeam(team, List.of(stage))
                : StageManager.get().revokeTeam(team, List.of(stage));
        String key = "commands.stagelock.team." + (grant ? "grant" : "revoke") + (changed.isEmpty() ? ".unchanged" : ".success");
        ctx.getSource().sendSuccess(() -> Component.translatable(key, stage, team), true);
        return changed.size();
    }

    private static int teamList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String team = resolveTeam(ctx);
        Set<String> stages = StageManager.get().store().team(team);
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.stagelock.team.list", team, join(stages)), false);
        return stages.size();
    }

    private static String resolveTeam(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "team");
        return TeamProviders.current().resolveTeam(ctx.getSource().getServer(), name)
                .orElseThrow(() -> UNKNOWN_TEAM.create(name));
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Like {@link GameProfileArgument#getGameProfiles}, but also works on servers without a profile
     * cache (game-test servers), where vanilla's plain-name lookup throws a NullPointerException.
     * There, a name resolves to an online player only.
     */
    private static Collection<GameProfile> profiles(CommandContext<CommandSourceStack> ctx, String argument) throws CommandSyntaxException {
        var server = ctx.getSource().getServer();
        if (server.getProfileCache() != null
                || ctx.getArgument(argument, GameProfileArgument.Result.class) instanceof GameProfileArgument.SelectorResult) {
            return GameProfileArgument.getGameProfiles(ctx, argument);
        }
        for (var node : ctx.getNodes()) {
            if (!node.getNode().getName().equals(argument)) continue;
            ServerPlayer player = server.getPlayerList().getPlayerByName(node.getRange().get(ctx.getInput()));
            if (player != null) return List.of(player.getGameProfile());
        }
        throw GameProfileArgument.ERROR_UNKNOWN_PLAYER.create();
    }

    private static void notifyTarget(CommandSourceStack source, UUID target, boolean silent, Component message) {
        if (silent) return;
        ServerPlayer player = source.getServer().getPlayerList().getPlayer(target);
        if (player != null) player.sendSystemMessage(message);
    }

    private static int feedback(CommandSourceStack source, String action, String stage, Collection<GameProfile> targets, int changed) {
        if (targets.size() == 1) {
            String name = targets.iterator().next().getName();
            String key = "commands.stagelock." + action + (changed == 0 ? ".unchanged" : ".success");
            source.sendSuccess(() -> Component.translatable(key, stage, name), true);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.stagelock." + action + ".multiple", stage, changed, targets.size()), true);
        }
        return changed;
    }

    private static String join(Collection<String> stages) {
        return stages.isEmpty() ? "-" : String.join(", ", stages.stream().sorted().toList());
    }
}
