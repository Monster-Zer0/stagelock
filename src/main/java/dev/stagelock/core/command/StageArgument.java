package dev.stagelock.core.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import dev.stagelock.api.StageIds;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Reads a stage id such as {@code iron_age} or {@code mypack:iron_age}. Brigadier's unquoted strings
 * stop at {@code :} and {@code /}, so stage ids need their own argument type.
 */
public final class StageArgument implements ArgumentType<String> {

    private static final DynamicCommandExceptionType INVALID = new DynamicCommandExceptionType(
            id -> Component.translatable("commands.stagelock.invalid_stage", id));

    public static StageArgument stage() {
        return new StageArgument();
    }

    public static String getStage(CommandContext<?> context, String name) {
        return context.getArgument(name, String.class);
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        while (reader.canRead() && isAllowed(reader.peek())) reader.skip();
        String raw = reader.getString().substring(start, reader.getCursor());
        if (!StageIds.isValid(raw)) {
            reader.setCursor(start);
            throw INVALID.createWithContext(reader, raw);
        }
        return raw;
    }

    private static boolean isAllowed(char c) {
        return c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '.' || c == ':' || c == '/' || c == '-';
    }

    @Override
    public java.util.Collection<String> getExamples() {
        return List.of("iron_age", "mypack:nether");
    }
}
