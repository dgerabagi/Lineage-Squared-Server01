package l2ft.gameserver.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A simple in-memory store for each player's "ignored NPC IDs."
 * 
 * Key = charId => set of ignored NPC IDs.
 */
public final class ManageBbsTargets {
    private static final ManageBbsTargets _instance = new ManageBbsTargets();
    private final Map<Integer, Set<Integer>> ignoredMap = new HashMap<Integer, Set<Integer>>();

    private ManageBbsTargets() {
    }

    public static ManageBbsTargets getInstance() {
        return _instance;
    }

    /**
     * Returns the ignored set for a given charId (read-only).
     */
    public synchronized Set<Integer> getIgnoredForChar(int charId) {
        Set<Integer> s = ignoredMap.get(Integer.valueOf(charId));
        if (s == null)
            return Collections.emptySet();
        return s;
    }

    /**
     * Overwrites the entire set for a charId.
     */
    public synchronized void setIgnored(int charId, Set<Integer> npcIds) {
        if (npcIds == null)
            npcIds = new HashSet<Integer>();
        ignoredMap.put(charId, npcIds);
    }

    /**
     * Adds a single ignored ID to that player's set.
     */
    public synchronized void ignore(int charId, int npcId) {
        Set<Integer> s = ignoredMap.get(charId);
        if (s == null) {
            s = new HashSet<Integer>();
            ignoredMap.put(charId, s);
        }
        s.add(npcId);
    }

    /**
     * Removes a single ignored ID from that player's set.
     */
    public synchronized void unignore(int charId, int npcId) {
        Set<Integer> s = ignoredMap.get(charId);
        if (s != null) {
            s.remove(npcId);
        }
    }

    /**
     * Clears all ignores for that player.
     */
    public synchronized void clearIgnored(Player player) {
        if (player != null)
            ignoredMap.remove(player.getObjectId());
    }
}
