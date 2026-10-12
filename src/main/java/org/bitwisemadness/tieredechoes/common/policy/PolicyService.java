package org.bitwisemadness.tieredechoes.common.policy;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.bitwisemadness.tieredechoes.common.gamerule.TEGameRules;

/**
 * Holds the switches gameplay reads, resolved against the world's gamerules.
 *
 * <p>Kept in its own service rather than on the reloader because these are read from code — recipe
 * publication, tier NBT reads — that has no reason to know about datapack loading, and because the
 * resolution step needs a server that only the reloader and the gamerule callbacks ever see.</p>
 *
 * <p>Two values live here and the difference between them is the whole point. The <em>declared</em>
 * policy is what {@code tier_policies} asked for; the <em>resolved</em> policy is that answer with
 * any per-world gamerule override applied on top. Gameplay always reads the resolved one, so a
 * datapack author's intent and an operator's override cannot disagree at a read site.</p>
 *
 * <p>Resolution happens on the server and the resolved value is what goes over the wire, because
 * Minecraft 1.20.1 has no gamerule sync packet: a client cannot ask a server what its rules are. A
 * client therefore holds an already-resolved policy and must never try to resolve it again — see
 * {@link #applyResolved}.</p>
 */
public final class PolicyService {
    private static final PolicyService INSTANCE = new PolicyService();

    private volatile TierPolicy declared = TierPolicy.defaults();
    private volatile TierPolicy resolved = TierPolicy.defaults();

    private PolicyService() {
    }

    public static PolicyService getInstance() {
        return INSTANCE;
    }

    /**
     * The switches gameplay obeys: the declared policy with this world's gamerules applied.
     *
     * <p>On a client this is the value the server sent, already resolved. On a server it is
     * recomputed whenever the declaration or a rule changes.</p>
     */
    public TierPolicy get() {
        return this.resolved;
    }

    /**
     * What {@code tier_policies} declared, before any gamerule touched it.
     *
     * <p>Kept separate from {@link #get} so that flipping a rule back to "follow the datapack" can
     * recover the author's intent rather than the value the previous override had left behind.</p>
     */
    public TierPolicy declared() {
        return this.declared;
    }

    /**
     * Adopt a freshly loaded datapack policy on the server, re-resolving against the current rules.
     *
     * <p>The server may not exist yet on the very first reload, in which case there is nothing to
     * override against and the declared value stands. The gamerule callbacks run again once the
     * world is up, so a stale resolution cannot survive.</p>
     */
    public void replaceFromDatapack(final TierPolicy policy) {
        this.declared = policy == null ? TierPolicy.defaults() : policy;
        this.resolved = TEGameRules.applyOverrides(ServerLifecycleHooks.getCurrentServer(), this.declared);
    }

    /**
     * Re-apply the world's gamerules to the declared policy, typically after {@code /gamerule}.
     *
     * <p>Called from the rule's own change callback, which is the only signal that fires when an
     * operator types the command. The result has to be re-broadcast, because the clients that were
     * already connected resolved against the old value.</p>
     */
    public void applyOverrides(final MinecraftServer server) {
        this.resolved = TEGameRules.applyOverrides(server, this.declared);
    }

    /**
     * Adopt the policy a server sent, without resolving it.
     *
     * <p>Client-side only. What arrived is already the answer for that world; running the override
     * step here would resolve a server's intent against a client's local gamerules, which on a
     * client are defaults nobody has ever set.</p>
     */
    public void applyResolved(final TierPolicy policy) {
        this.resolved = policy == null ? TierPolicy.defaults() : policy;
    }
}
