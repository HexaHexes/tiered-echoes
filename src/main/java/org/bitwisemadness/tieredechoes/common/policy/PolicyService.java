package org.bitwisemadness.tieredechoes.common.policy;

import org.bitwisemadness.tieredechoes.common.config.TieredEchoesConfig;

/**
 * Holds the currently applied {@link TierPolicy}.
 *
 * <p>Kept in its own service rather than on the reloader because the policy is read from gameplay
 * code (branch selection, xp handling, recipe generation) that has no reason to know about datapack
 * loading.</p>
 */
public final class PolicyService {
    private static final PolicyService INSTANCE = new PolicyService();

    private volatile TierPolicy current = TierPolicy.defaults();

    private PolicyService() {
    }

    public static PolicyService getInstance() {
        return INSTANCE;
    }

    public TierPolicy get() {
        return this.current;
    }

    /**
     * Whether shift-right-click attunement consumes the offhand ingredient. A datapack that states
     * {@code consume_attunement_item} wins; otherwise the operator's config decides.
     */
    public boolean consumeAttunementItem() {
        return this.current.declaredConsumeAttunementItem()
                .orElseGet(() -> TieredEchoesConfig.CONSUME_ATTUNEMENT_ITEM.get());
    }

    public void replace(final TierPolicy policy) {
        this.current = policy == null ? TierPolicy.defaults() : policy;
    }
}
