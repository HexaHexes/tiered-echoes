package org.bitwisemadness.tieredechoes.common.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Rarity;
import org.bitwisemadness.tieredechoes.common.level.LevelConfig;
import org.bitwisemadness.tieredechoes.common.level.LevelCurve;
import org.bitwisemadness.tieredechoes.common.policy.TierPolicy;
import org.bitwisemadness.tieredechoes.common.tier.IngredientMatcher;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Wire codec for the tier objects.
 *
 * <p>Held apart from the packet itself because the client-side application of a sync and the
 * client-side rendering of an item both want to reason about a single tier at a time.</p>
 */
final class TierCodec {
    private TierCodec() {
    }

    static void writeTier(final FriendlyByteBuf buf, final TierDefinition tier) {
        buf.writeResourceLocation(tier.id());
        buf.writeEnum(tier.domain());
        buf.writeVarInt(tier.schemaVersion());
        buf.writeEnum(tier.displayRarity());
        buf.writeVarInt(tier.sortOrder());
        writeMatcher(buf, tier.upgradeIngredient());
        writeOptionalMatcher(buf, tier.downgradeIngredient());
        writeOptionalResource(buf, tier.previousTier());
        writeOptionalString(buf, tier.sourcePack());
        writeDoubleMap(buf, tier.attributeMultipliers());
        writeDoubleMap(buf, tier.curiosSetBonuses());
        writeDoubleMap(buf, tier.compatAmplifiers());
        buf.writeBoolean(tier.generatedDowngradeTemplate());
        buf.writeBoolean(tier.generatedUpgradeTemplate());
        writeOptionalResource(buf, tier.upgradeTemplateId());
        writeOptionalResource(buf, tier.downgradeTemplateId());
        writeOptionalResource(buf, tier.upgradeTemplateTexture());
        writeOptionalResource(buf, tier.downgradeTemplateTexture());
    }

    static TierDefinition readTier(final FriendlyByteBuf buf) {
        final ResourceLocation id = buf.readResourceLocation();
        final TierDomain domain = buf.readEnum(TierDomain.class);
        final int schemaVersion = buf.readVarInt();
        final Rarity rarity = buf.readEnum(Rarity.class);
        final int sortOrder = buf.readVarInt();
        final IngredientMatcher upgrade = readMatcher(buf);
        final Optional<IngredientMatcher> downgrade = readOptionalMatcher(buf);
        final Optional<ResourceLocation> previousTier = readOptionalResource(buf);
        final Optional<String> sourcePack = readOptionalString(buf);
        final Map<String, Double> multipliers = readDoubleMap(buf);
        final Map<String, Double> setBonuses = readDoubleMap(buf);
        final Map<String, Double> compat = readDoubleMap(buf);
        final boolean generatedDowngradeTemplate = buf.readBoolean();
        final boolean generatedUpgradeTemplate = buf.readBoolean();
        final Optional<ResourceLocation> upgradeTemplateId = readOptionalResource(buf);
        final Optional<ResourceLocation> downgradeTemplateId = readOptionalResource(buf);
        final Optional<ResourceLocation> upgradeTemplateTexture = readOptionalResource(buf);
        final Optional<ResourceLocation> downgradeTemplateTexture = readOptionalResource(buf);
        return new TierDefinition(id, domain, schemaVersion, rarity, sortOrder, upgrade,
                downgrade, previousTier, sourcePack, multipliers, setBonuses, compat,
                generatedUpgradeTemplate, generatedDowngradeTemplate,
                upgradeTemplateId, downgradeTemplateId, upgradeTemplateTexture, downgradeTemplateTexture);
    }

    /**
     * Five booleans, in field order.
     *
     * <p>Each one is a plain bit rather than a presence-flagged value: a switch is two-state by
     * nature, and the third state that the gamerules use lives on the server, which resolves before
     * sending. What a client receives is already the answer.</p>
     */
    static void writePolicy(final FriendlyByteBuf buf, final TierPolicy policy) {
        buf.writeBoolean(policy.tierSystem());
        buf.writeBoolean(policy.tierUpgrades());
        buf.writeBoolean(policy.levellingSystem());
        buf.writeBoolean(policy.levelUp());
        buf.writeBoolean(policy.downgrades());
    }

    static TierPolicy readPolicy(final FriendlyByteBuf buf) {
        return new TierPolicy(buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                buf.readBoolean(), buf.readBoolean());
    }

    /**
     * The level configuration: a cap and the two curves, each as its kind and two doubles.
     *
     * <p>Travels with the policy because the tooltip needs it — the progress line quotes the next
     * level's cost, and a client that had to guess that number would show a threshold the server
     * disagrees with. The client adopts it as-is, the same way it adopts the resolved policy: what
     * arrived is the answer that world's datapack gave.</p>
     */
    static void writeLevelConfig(final FriendlyByteBuf buf, final LevelConfig config) {
        buf.writeVarInt(config.maxLevel());
        writeCurve(buf, config.experience());
        writeCurve(buf, config.attribute());
    }

    static LevelConfig readLevelConfig(final FriendlyByteBuf buf) {
        return new LevelConfig(buf.readVarInt(), readCurve(buf), readCurve(buf));
    }

    private static void writeCurve(final FriendlyByteBuf buf, final LevelCurve curve) {
        buf.writeEnum(curve.kind());
        buf.writeDouble(curve.coefficient());
        buf.writeDouble(curve.exponent());
    }

    private static LevelCurve readCurve(final FriendlyByteBuf buf) {
        // Rebuilt through the record rather than assigned raw: a curve whose parameters make no
        // sense is a datapack bug the server should have caught, and a hostile packet carrying NaN
        // would otherwise paint NaN into every scaled attribute on the client.
        return new LevelCurve(buf.readEnum(LevelCurve.Kind.class), buf.readDouble(), buf.readDouble());
    }

    static void writeTiers(final FriendlyByteBuf buf, final List<TierDefinition> tiers) {
        buf.writeVarInt(tiers.size());
        for (final TierDefinition tier : tiers) {
            writeTier(buf, tier);
        }
    }

    static List<TierDefinition> readTiers(final FriendlyByteBuf buf) {
        final int size = buf.readVarInt();
        // A hostile or desynced server could ask us to allocate absurdly; cap it well above any
        // realistic tier count so a bad packet degrades into a truncated list, not an OOM.
        if (size < 0 || size > 4096) {
            throw new IllegalArgumentException("Tier sync packet declared an implausible tier count: " + size);
        }
        final List<TierDefinition> tiers = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            tiers.add(readTier(buf));
        }
        return tiers;
    }

    private static void writeMatcher(final FriendlyByteBuf buf, final IngredientMatcher matcher) {
        buf.writeEnum(matcher.kind());
        buf.writeResourceLocation(matcher.id());
    }
    private static IngredientMatcher readMatcher(final FriendlyByteBuf buf) {
        return new IngredientMatcher(buf.readEnum(IngredientMatcher.Kind.class), buf.readResourceLocation());
    }

    private static void writeOptionalMatcher(final FriendlyByteBuf buf, final Optional<IngredientMatcher> matcher) {
        buf.writeBoolean(matcher.isPresent());
        matcher.ifPresent(value -> writeMatcher(buf, value));
    }

    private static Optional<IngredientMatcher> readOptionalMatcher(final FriendlyByteBuf buf) {
        return buf.readBoolean() ? Optional.of(readMatcher(buf)) : Optional.empty();
    }

    private static void writeOptionalResource(final FriendlyByteBuf buf, final Optional<ResourceLocation> value) {
        buf.writeBoolean(value.isPresent());
        value.ifPresent(buf::writeResourceLocation);
    }

    private static Optional<ResourceLocation> readOptionalResource(final FriendlyByteBuf buf) {
        return buf.readBoolean() ? Optional.of(buf.readResourceLocation()) : Optional.empty();
    }

    private static void writeOptionalString(final FriendlyByteBuf buf, final Optional<String> value) {
        buf.writeBoolean(value.isPresent());
        value.ifPresent(buf::writeUtf);
    }

    private static Optional<String> readOptionalString(final FriendlyByteBuf buf) {
        return buf.readBoolean() ? Optional.of(buf.readUtf(256)) : Optional.empty();
    }

    private static void writeDoubleMap(final FriendlyByteBuf buf, final Map<String, Double> map) {
        buf.writeVarInt(map.size());
        for (final Map.Entry<String, Double> entry : map.entrySet()) {
            buf.writeUtf(entry.getKey(), 128);
            buf.writeDouble(entry.getValue());
        }
    }

    private static Map<String, Double> readDoubleMap(final FriendlyByteBuf buf) {
        final int size = buf.readVarInt();
        if (size < 0 || size > 1024) {
            throw new IllegalArgumentException("Tier sync packet declared an implausible map size: " + size);
        }
        final Map<String, Double> map = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            map.put(buf.readUtf(128), buf.readDouble());
        }
        return map;
    }
}
