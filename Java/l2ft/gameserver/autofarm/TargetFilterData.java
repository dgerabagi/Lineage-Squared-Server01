package l2ft.gameserver.autofarm;

import java.util.HashSet;
import java.util.Set;

import l2ft.gameserver.model.ManageBbsTargets;

/**
 * Holds the set of "ignored" NPC IDs for targeting.
 * If an NPC ID is in this set => we do not attack it.
 * Otherwise => we do.
 * 
 * Now we also pull from ManageBbsTargets, so that if the user had a
 * saved ignore list from DB, we reflect that in-memory as soon as possible.
 */
public class TargetFilterData {
    private static final String KEY = "_targetFilter";

    private final Set<Integer> ignoredNpcIds = new HashSet<Integer>();

    public static TargetFilterData get(AutoFarmState state) {
        Object obj = state.getCustomData(KEY);
        if (obj instanceof TargetFilterData) {
            return (TargetFilterData) obj;
        }
        // Otherwise create new
        TargetFilterData newData = new TargetFilterData(state);
        state.setCustomData(KEY, newData);
        return newData;
    }

    /**
     * On creation, we load from ManageBbsTargets.
     */
    private TargetFilterData(AutoFarmState st) {
        if (st != null && st.getPlayer() != null) {
            int charId = st.getPlayer().getObjectId();
            // get existing set from in-memory
            Set<Integer> already = ManageBbsTargets.getInstance().getIgnoredForChar(charId);
            if (already != null && !already.isEmpty()) {
                ignoredNpcIds.addAll(already);
            }
        }
    }

    // The old methods
    public boolean isIgnored(int npcId) {
        return ignoredNpcIds.contains(npcId);
    }

    public void ignore(int npcId) {
        ignoredNpcIds.add(npcId);
    }

    public void unignore(int npcId) {
        ignoredNpcIds.remove(npcId);
    }

    public void clear() {
        ignoredNpcIds.clear();
    }
}
