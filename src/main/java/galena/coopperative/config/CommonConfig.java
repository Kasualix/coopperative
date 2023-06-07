package galena.coopperative.config;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import galena.coopperative.Coopperative;
import galena.coopperative.index.CConversions;
import galena.oreganized.index.OBlocks;
import net.mehvahdjukaar.moonlight.api.platform.configs.ConfigBuilder;
import net.mehvahdjukaar.moonlight.api.platform.configs.ConfigType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class CommonConfig {

    public enum OverrideTarget {
        APPEARANCE, RECIPE, WEATHERING
    }

    private record OverrideEntry(BooleanSupplier enabled, Map<OverrideTarget, BooleanSupplier> targets) {
        boolean isEnabled(OverrideTarget target) {
            return enabled.getAsBoolean() && targets.get(target).getAsBoolean();
        }
    }

    private static CommonConfig INSTANCE;

    private static final List<ResourceLocation> OVERWRITTEN_BLOCK_IDS = findOverrideableBlocks();
    private static final Supplier<List<Block>> OVERWRITTEN_BLOCKS = Suppliers.memoize(() ->
            OVERWRITTEN_BLOCK_IDS.stream().map(ForgeRegistries.BLOCKS::getValue).toList()
    );

    private static List<ResourceLocation> findOverrideableBlocks() {
        var builder = ImmutableList.<ResourceLocation>builder();
        Stream.of(
                "repeater",
                "comparator",
                "piston",
                "sticky_piston",
                "dispenser",
                "dropper",
                "observer",
                "lever",
                "powered_rail"
        ).forEach(it -> builder.add(new ResourceLocation(it)));

        if (ModList.get().isLoaded("oreganized")) {
            builder.add(OBlocks.EXPOSER.getId());
        }

        if (ModList.get().isLoaded("supplementaries")) {
            builder.add(new ResourceLocation("supplementaries", "relayer"));
            builder.add(new ResourceLocation("supplementaries", "crank"));
        }

        return builder.build();
    }

    public static void register() {
        var builder = ConfigBuilder.create(Coopperative.MOD_ID, ConfigType.COMMON);
        builder.setSynced();
        INSTANCE = new CommonConfig(builder);
        builder.buildAndRegister().loadFromFile();
    }

    public static boolean isPossibleOverwrite(Block block) {
        var first = CConversions.getFirst(block);
        return getPossibleOverwrites().contains(first);
    }

    public static Collection<Block> getPossibleOverwrites() {
        return OVERWRITTEN_BLOCKS.get();
    }

    public static Stream<Block> getOverwrittenBlocks(OverrideTarget target) {
        return getPossibleOverwrites().stream().filter(it -> isOverwriteEnabled(it, target));
    }

    private static boolean test(Block block, Predicate<OverrideEntry> func) {
        var first = CConversions.getFirst(block);
        var key = ForgeRegistries.BLOCKS.getKey(first);
        if (!INSTANCE.enabledOverwrites.containsKey(key)) return false;
        return func.test(INSTANCE.enabledOverwrites.get(key));
    }

    public static boolean isOverwriteEnabled(Block block, OverrideTarget target) {
        return test(block, it -> it.isEnabled(target));
    }

    public static boolean isOverwriteDisabled(Block block, OverrideTarget target) {
        return test(block, it -> !it.isEnabled(target));
    }

    public static boolean isOverwriteEnabled(Block block) {
        return test(block, it -> it.enabled.getAsBoolean());
    }

    public static boolean isOverwriteDisabled(Block block) {
        return test(block, it -> !it.enabled.getAsBoolean());
    }

    private final Map<ResourceLocation, OverrideEntry> enabledOverwrites = new HashMap<>();

    public CommonConfig(ConfigBuilder builder) {
        builder.push("Enabled Copper Overrides");
        OVERWRITTEN_BLOCK_IDS.forEach(key -> {
            builder.push(key.getPath());

            var property = builder.define("enabled", true);
            var targets = ImmutableMap.<OverrideTarget, BooleanSupplier>builder();
            for (OverrideTarget target : OverrideTarget.values()) {
                var targetProperty = builder.define(target.name().toLowerCase(), true);
                targets.put(target, targetProperty::get);
            }

            enabledOverwrites.put(key, new OverrideEntry(property::get, targets.build()));

            builder.pop();
        });
        builder.pop();
    }

}
