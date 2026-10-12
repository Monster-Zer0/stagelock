package dev.stagelock.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Locale;
import java.util.regex.Pattern;

/** Stage id rules: lowercase, 1-64 chars, {@code [a-z0-9_.:/-]}. */
public final class StageIds {

    public static final int MAX_LENGTH = 64;
    private static final Pattern VALID = Pattern.compile("^[a-z0-9_.:/-]+$");

    public static final Codec<String> CODEC = Codec.STRING.comapFlatMap(StageIds::validate, s -> s);

    private StageIds() {
    }

    public static String normalize(String stage) {
        return stage.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isValid(String stage) {
        return stage != null && !stage.isEmpty() && stage.length() <= MAX_LENGTH && VALID.matcher(stage).matches();
    }

    public static DataResult<String> validate(String stage) {
        String normalized = normalize(stage);
        return isValid(normalized)
                ? DataResult.success(normalized)
                : DataResult.error(() -> "Invalid stage id '" + stage + "': must be 1-" + MAX_LENGTH + " chars of [a-z0-9_.:/-]");
    }
}
