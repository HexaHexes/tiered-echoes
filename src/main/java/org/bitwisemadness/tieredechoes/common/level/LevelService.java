package org.bitwisemadness.tieredechoes.common.level;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.bitwisemadness.tieredechoes.common.policy.PolicyService;
import org.bitwisemadness.tieredechoes.common.policy.TierPolicy;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;
import org.bitwisemadness.tieredechoes.common.tier.TierService;

import java.util.OptionalInt;

/**
 * Reads and writes the per-item level an item carries.
 *
 * <p>A level belongs to the item, not to one of its ladders: an axe's weapon rung and tool rung
 * climb separate ladders, but the axe is one object with one history, and its level describes how
 * hard it has been used rather than how far it has been smithed. That is the whole split between
 * the two systems — a <em>tier</em> is what a smithing recipe made the item, a <em>level</em> is
 * what using it did.</p>
 *
 * <p>State lives at {@code TieredEchoes.level}, a sibling of the per-domain compounds rather than a
 * child of one. Experience is stored against the level the item currently holds — the pool is what
 * it is short of the next one — so a level-up spends exactly its cost and carries the remainder,
 * and the tooltip can show progress without recomputing a cumulative sum.</p>
 *
 * <p>Reads are gated on {@code levelling_system}; writes are not. The same hide-don't-erase
 * contract the tier system runs on: a world that switches levels off reports every item as level
 * one with an empty pool, and switching it back on recovers every level that was there. The gate
 * lives in this class rather than in the callers so that no read site can forget it.</p>
 */
public final class LevelService {
    private static final LevelService INSTANCE = new LevelService();

    /**
     * The NBT keys the level state is written under. Held here so the write site in this class and
     * any test reading the raw tag cannot drift apart — a mismatch would show up as every item
     * silently reverting to level one.
     */
    public static final String LEVEL_TAG = "level";
    public static final String LEVEL_VALUE_TAG = "value";
    public static final String STORED_EXPERIENCE_TAG = "stored_experience";

    private volatile LevelConfig config = LevelConfig.defaults();

    private LevelService() {
    }

    public static LevelService getInstance() {
        return INSTANCE;
    }

    /** The curves and cap in force. Replaced on every datapack reload. */
    public LevelConfig config() {
        return this.config;
    }

    public synchronized void replaceAll(final LevelConfig config) {
        this.config = config == null ? LevelConfig.defaults() : config;
    }

    /**
     * The level this item holds, or 1 when it holds none.
     *
     * <p>One is the honest answer for an item that has earned nothing: it is the level every item
     * starts at, and under the shipped curves it is a factor of 1.0 — the item presents exactly as
     * its tier describes it and no better.</p>
     */
    public int getLevel(final ItemStack stack) {
        if (!this.levelReadable()) {
            return 1;
        }
        final CompoundTag tag = readLevelTag(stack);
        if (tag == null) {
            return 1;
        }
        return Math.max(1, Math.min(tag.getInt(LEVEL_VALUE_TAG), this.config.maxLevel()));
    }

    /** The experience banked toward the next level. Zero when the system is off or nothing earned. */
    public int getStoredExperience(final ItemStack stack) {
        if (!this.levelReadable()) {
            return 0;
        }
        final CompoundTag tag = readLevelTag(stack);
        return tag == null ? 0 : Math.max(0, tag.getInt(STORED_EXPERIENCE_TAG));
    }

    /**
     * The level's half of {@code effective = 1 + (tierMultiplier − 1) × levelFactor}.
     *
     * <p>Exactly 1.0 whenever the level may not be read — the system switched off, or an item that
     * has earned nothing — which makes the callers' arithmetic a no-op by multiplication rather
     * than by branch. See {@link LevelConfig#levelFactor}.</p>
     */
    public double levelFactor(final ItemStack stack) {
        return this.config.levelFactor(this.getLevel(stack));
    }

    /**
     * Whether this item may bank experience right now: the world allows earning, and the item
     * holds at least one rung.
     *
     * <p>The rung test is what keeps a fresh item honest. Levels amplify a tier's multiplier, so an
     * item with no rung would gain a level that changes nothing — it would sit in the inventory
     * advertising progress it cannot spend. Holding a rung is read off the item's own NBT rather
     * than through the tier system's gate, because the two systems are independent: a world with
     * tiers switched off still has items that remember what they were smithed into, and their
     * levels are still theirs.</p>
     */
    public boolean canEarn(final ItemStack stack) {
        final TierPolicy policy = PolicyService.getInstance().get();
        return !stack.isEmpty()
                && policy.levellingSystem()
                && policy.levelUp()
                && this.holdsAnyRung(stack);
    }

    /**
     * Banks experience and takes every level it pays for.
     *
     * <p>One award can cross several levels — a single kill on a low-level item is worth more than
     * one step — so this is a loop rather than a single comparison, and the remainder the last step
     * did not need stays in the pool. The new level comes back when at least one step was taken,
     * which is the caller's cue for a message; the writes happen in place, because the stack in a
     * player's hand is the item being progressed and there is no transition to apply.</p>
     *
     * <p>An item at the cap neither gains nor stores: there is no next level to be short of, and a
     * pool with nowhere to go would be state that means nothing.</p>
     */
    public OptionalInt addExperience(final ItemStack stack, final int amount) {
        if (amount <= 0 || !this.canEarn(stack)) {
            return OptionalInt.empty();
        }
        final CompoundTag tag = writeLevelTag(stack);
        final int max = this.config.maxLevel();
        int level = Math.max(1, tag.getInt(LEVEL_VALUE_TAG));
        if (level >= max) {
            return OptionalInt.empty();
        }
        // A long throughout the walk: an int would wrap the moment a datapack's curve costs more
        // than the pool can hold, and a wrapped pool is a pool that levels items for free.
        long pool = Math.max(0, tag.getInt(STORED_EXPERIENCE_TAG)) + (long) amount;
        boolean advanced = false;
        while (level < max) {
            final int cost = this.config.experienceToReach(level + 1);
            if (pool < cost) {
                break;
            }
            pool -= cost;
            level++;
            advanced = true;
        }
        // At the cap there is no next level to be short of, so whatever the last step did not need
        // is dropped rather than stored as progress toward nothing.
        tag.putInt(LEVEL_VALUE_TAG, level);
        tag.putInt(STORED_EXPERIENCE_TAG, level >= max ? 0 : (int) pool);
        return advanced ? OptionalInt.of(level) : OptionalInt.empty();
    }

    /**
     * Whether the item names a rung on any ladder at all — the question both the award path and
     * the tooltip ask.
     *
     * <p>Read straight off the NBT rather than through {@code TierService#readAllTiersFromNbt},
     * because that read is gated on {@code tier_system} and this question is not: which ladders an
     * item is <em>on</em> is a fact about the item, and the tier system's switch only decides
     * whether the game is currently looking at that fact. The rung is still resolved against the
     * loaded ladder, so an id no datapack declares any more does not count as holding a rung.</p>
     */
    public boolean holdsAnyRung(final ItemStack stack) {
        if (stack.isEmpty() || !stack.hasTag()
                || !stack.getTag().contains(TierService.ROOT_TAG)
                || !stack.getTag().getCompound(TierService.ROOT_TAG).contains(TierService.DOMAINS_TAG)) {
            return false;
        }
        final CompoundTag domains = stack.getTag().getCompound(TierService.ROOT_TAG).getCompound(TierService.DOMAINS_TAG);
        final TierService tiers = TierService.getInstance();
        for (final String key : domains.getAllKeys()) {
            final TierDomain domain = TierDomain.byName(key).orElse(null);
            if (domain == null) {
                continue;
            }
            final ResourceLocation tierId = ResourceLocation.tryParse(
                    domains.getCompound(key).getString(TierService.TIER_ID_TAG));
            if (tierId != null && tiers.getTier(tierId, domain).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private boolean levelReadable() {
        return PolicyService.getInstance().get().levellingSystem();
    }

    /** The level compound, or null when the item has never held one. Never attaches. */
    private static CompoundTag readLevelTag(final ItemStack stack) {
        if (stack.isEmpty() || !stack.hasTag()
                || !stack.getTag().contains(TierService.ROOT_TAG)
                || !stack.getTag().getCompound(TierService.ROOT_TAG).contains(LEVEL_TAG)) {
            return null;
        }
        return stack.getTag().getCompound(TierService.ROOT_TAG).getCompound(LEVEL_TAG);
    }

    /** The level compound, attached for writing. Deliberately not gated: writes survive a switch. */
    private static CompoundTag writeLevelTag(final ItemStack stack) {
        final CompoundTag te = stack.getOrCreateTagElement(TierService.ROOT_TAG);
        if (!te.contains(LEVEL_TAG)) {
            te.put(LEVEL_TAG, new CompoundTag());
        }
        return te.getCompound(LEVEL_TAG);
    }
}
