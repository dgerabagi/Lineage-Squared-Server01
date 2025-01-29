//
// C:\l2sq\Pac Project\Java\l2ft\gameserver\model\RaidBossDelevelManager.java
//
package l2ft.gameserver.model;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import l2ft.commons.dbutils.DbUtils;
import l2ft.gameserver.ThreadPoolManager;
import l2ft.gameserver.database.DatabaseFactory;
import l2ft.gameserver.model.actor.instances.player.StackClass;
import l2ft.gameserver.model.base.ClassId;
import l2ft.gameserver.model.base.Experience;
import l2ft.gameserver.model.pledge.Clan;
import l2ft.gameserver.network.l2.components.SystemMsg;
import l2ft.gameserver.network.l2.s2c.ExShowScreenMessage;
import l2ft.gameserver.network.l2.s2c.SocialAction;
import l2ft.gameserver.network.l2.s2c.SystemMessage2;
import l2ft.gameserver.tables.SkillTable;
import l2ft.gameserver.data.xml.holder.SkillAcquireHolder;
import l2ft.gameserver.model.SkillLearn;
import l2ft.commons.threading.RunnableImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages forcibly lowering a player's level for
 * low-level raidboss zones, plus deferring XP and SP, etc.
 *
 * Key points:
 * - On zone enter -> forcibly-lower if needed.
 * - While forcibly-lowered, we skip real XP and store it in a map.
 * - On leaving the Outer zone -> restore level & award the stored XP/SP (one
 * time).
 * - OverlapCheckTask cleans stale data if offline/out of zone, etc.
 */
public class RaidBossDelevelManager {
    private static final Logger _log = LoggerFactory.getLogger(RaidBossDelevelManager.class);

    private static RaidBossDelevelManager _instance;

    public static RaidBossDelevelManager getInstance() {
        if (_instance == null) {
            _instance = new RaidBossDelevelManager();
        }
        return _instance;
    }

    // Original level/exp, so we can revert
    private Map<Integer, Integer> _originalLevels; // objId -> original level
    private Map<Integer, Long> _originalExps; // objId -> original exp

    // Original skill levels so we can revert them
    private Map<Integer, Map<Integer, Integer>> _originalSkillLevels; // objId -> (skillId->origLvl)

    // The forcibly-lowered (downgraded) skill set, so we know what to remove
    private Map<Integer, Map<Integer, Integer>> _delevelSkillMap; // objId -> (skillId->newLvl)

    // Tracks which players are forcibly-lowered
    private Set<Integer> _loweredPlayers;

    // Tracks XP/SP that was “skipped” while forcibly-lowered
    private Map<Integer, Long> _deferredXp; // objId -> total XP
    private Map<Integer, Long> _deferredSp; // objId -> total SP

    // Overlap check
    private ScheduledFuture<?> _overlapTask;

    // Hard-coded hero/noble skill IDs that we do not degrade
    private static final int[] NOBLE_HERO_SKILLS = new int[] {
            // Noble
            1323, 1324, 1325, 1326, 1327,
            // Hero
            1374, 1375, 1376, 395, 396
    };

    private RaidBossDelevelManager() {
        _originalLevels = new ConcurrentHashMap<Integer, Integer>();
        _originalExps = new ConcurrentHashMap<Integer, Long>();
        _originalSkillLevels = new ConcurrentHashMap<Integer, Map<Integer, Integer>>();
        _delevelSkillMap = new ConcurrentHashMap<Integer, Map<Integer, Integer>>();

        _loweredPlayers = Collections.newSetFromMap(new ConcurrentHashMap<Integer, Boolean>());
        _deferredXp = new ConcurrentHashMap<Integer, Long>();
        _deferredSp = new ConcurrentHashMap<Integer, Long>();

        _overlapTask = ThreadPoolManager.getInstance().scheduleAtFixedRate(new OverlapCheckTask(), 5000L, 5000L);
    }

    private class OverlapCheckTask implements Runnable {
        public void run() {
            try {
                if (_loweredPlayers.isEmpty())
                    return;

                Integer[] arr = _loweredPlayers.toArray(new Integer[_loweredPlayers.size()]);
                for (int pid : arr) {
                    Player p = GameObjectsStorage.getPlayer(pid);
                    if (p == null) {
                        // player offline => cleanup
                        cleanUpPlayer(pid);
                        continue;
                    }
                    boolean stillInside = p.isInZone(Zone.ZoneType.rbdelevel_OuterZone)
                            || p.isInZone(Zone.ZoneType.rbdelevel_InnerZone);
                    if (!stillInside) {
                        // The player truly left
                        // we do not forcibly re-level or award XP automatically if the zone event
                        // missed them
                        // Just cleanup
                        cleanUpPlayer(pid);
                    }
                }
            } catch (Exception e) {
                _log.warn("[RbDelevel OverlapCheckTask] error: " + e.getMessage(), e);
            }
        }

        private void cleanUpPlayer(int pid) {
            _loweredPlayers.remove(pid);
            _originalLevels.remove(pid);
            _originalExps.remove(pid);
            _originalSkillLevels.remove(pid);
            _delevelSkillMap.remove(pid);
            _deferredXp.remove(pid);
            _deferredSp.remove(pid);

            Player pl = GameObjectsStorage.getPlayer(pid);
            if (pl != null) {
                pl.unsetVar("SkipRaidExp");
                pl.unsetVar("IgnoreDelevelStore");
            }
        }
    }

    /**
     * Called on zone enter. If boss <= 76 and player > boss+4 => forcibly-lower
     */
    public boolean applyDelevelIfNeeded(Player player, int bossId) {
        if (player == null)
            return false;
        int bossLvl = getBossLevel(bossId);
        if (bossLvl <= 0)
            return false;
        if (bossLvl > 76)
            return false; // skip forcibly-lower for higher-level bosses

        int forcedLevel = bossLvl + 4;
        int curLevel = player.getLevel();
        if (curLevel <= forcedLevel)
            return false;

        // Debug log
        _log.info("[RbDelevel Debug] applyDelevelIfNeeded: player=" + player.getName()
                + " => capturing currentExp(main)=" + player.getActiveClass().getExp()
                + ", secondExp=" + player.getActiveClass().getSecondaryExp());

        // store original level if not already
        if (!_originalLevels.containsKey(player.getObjectId()))
            _originalLevels.put(player.getObjectId(), curLevel);
        if (!_originalExps.containsKey(player.getObjectId()))
            _originalExps.put(player.getObjectId(), player.getExp());

        // skip storing forcibly-lowered level to DB
        player.setVar("IgnoreDelevelStore", "1");

        // store "beforeDelevel" in StackClass
        if (player.getActiveClass() != null) {
            if (player.getActiveClass().getLevelBeforeDelevel() == 0) {
                player.getActiveClass().setLevelBeforeDelevel(curLevel);
                player.getActiveClass().setExpBeforeDelevel(player.getExp());
            }
        }

        // store original skill levels if not done
        if (!_originalSkillLevels.containsKey(player.getObjectId())) {
            Map<Integer, Integer> skmap = new HashMap<Integer, Integer>();
            Skill[] arr = player.getAllSkillsArray();
            if (arr != null) {
                for (int i = 0; i < arr.length; i++) {
                    Skill s = arr[i];
                    if (s == null)
                        continue;
                    skmap.put(Integer.valueOf(s.getId()), Integer.valueOf(s.getLevel()));
                }
            }
            _originalSkillLevels.put(player.getObjectId(), skmap);
        }

        logPlayerSkillsForDebug(player);

        // figure out new skill-level set
        Map<Integer, Integer> newSet = determineDelevelSkillSet(player, forcedLevel);

        // forcibly-lower
        forceChangePlayerLevel(player, forcedLevel, true, true); // minimalExp=true

        // apply downgraded skill set
        applySkillDelevelSet(player, newSet);

        _loweredPlayers.add(player.getObjectId());

        // set “SkipRaidExp=1” so we do not add xp/sp to real char => store it
        player.setVar("SkipRaidExp", "1");

        _log.info("[RbDelevel] forcibly lowered " + player.getName()
                + " from lvl " + curLevel + " to lvl " + forcedLevel
                + ", bossId=" + bossId);
        return true;
    }

    private void logPlayerSkillsForDebug(Player player) {
        Skill[] arr = player.getAllSkillsArray();
        if (arr == null || arr.length == 0) {
            _log.info("[RbDelevel] " + player.getName() + " => currently has no skills (?)");
            return;
        }
        _log.info("[RbDelevel] " + player.getName() + " => listing current skill set (count=" + arr.length + "):");
        // If needed to show them, uncomment:
        /*
         * for(Skill sk : arr)
         * {
         * if(sk == null) continue;
         * _log.info(" => skillId=" + sk.getId() + ", lvl=" + sk.getLevel() +
         * ", name="+sk.getName());
         * }
         */
    }

    private Map<Integer, Integer> determineDelevelSkillSet(Player player, int forcedLevel) {
        Map<Integer, Integer> result = new HashMap<Integer, Integer>();
        Skill[] arr = player.getAllSkillsArray();
        if (arr == null || arr.length == 0)
            return result;

        Clan clan = player.getClan();
        Map<Integer, Integer> origMap = _originalSkillLevels.get(player.getObjectId());
        if (origMap == null)
            origMap = Collections.emptyMap();

        _log.info("[RbDelevel] determineDelevelSkillSet => forcedLevel=" + forcedLevel
                + ", totalSkills=" + arr.length);

        for (int i = 0; i < arr.length; i++) {
            Skill sk = arr[i];
            if (sk == null)
                continue;
            int sid = sk.getId();
            int sLvl = sk.getLevel();
            int storedOrigLvl = sLvl;
            if (origMap.containsKey(Integer.valueOf(sid))) {
                storedOrigLvl = origMap.get(Integer.valueOf(sid)).intValue();
            }

            if (isClanSkill(clan, sid) || isNobleOrHeroSkill(sid)) {
                // keep skill at original level
                result.put(Integer.valueOf(sid), Integer.valueOf(storedOrigLvl));
                continue;
            }

            int newLv = findSkillLevelFromNormalTree(player, sid, forcedLevel, storedOrigLvl);
            if (newLv <= 0) {
                // remove skill
                result.put(Integer.valueOf(sid), Integer.valueOf(0));
            } else if (newLv == sLvl) {
                // keep the same
                result.put(Integer.valueOf(sid), Integer.valueOf(sLvl));
            } else {
                // degrade to newLv
                result.put(Integer.valueOf(sid), Integer.valueOf(newLv));
            }
        }
        _delevelSkillMap.put(player.getObjectId(), result);
        return result;
    }

    private boolean isNobleOrHeroSkill(int skillId) {
        for (int i = 0; i < NOBLE_HERO_SKILLS.length; i++) {
            int sid = NOBLE_HERO_SKILLS[i];
            if (sid == skillId)
                return true;
        }
        return false;
    }

    private boolean isClanSkill(Clan clan, int skillId) {
        if (clan == null)
            return false;
        Skill[] arr = clan.getSkills().toArray(new Skill[clan.getSkills().size()]);
        if (arr == null)
            return false;
        for (int i = 0; i < arr.length; i++) {
            Skill s = arr[i];
            if (s == null)
                continue;
            if (s.getId() == skillId)
                return true;
        }
        return false;
    }

    private int findSkillLevelFromNormalTree(Player player, int skillId, int forcedLevel, int origSkillLvl) {
        int best = 0;
        StackClass stClass = player.getActiveClass();
        if (stClass == null)
            return 0;
        int mainClassId = stClass.getFirstClassId();
        int secondaryClassId = stClass.getSecondaryClass();

        int a = findSkillLevelForOneClass(mainClassId, skillId, forcedLevel, origSkillLvl);
        if (a > best)
            best = a;
        if (secondaryClassId > 0 && secondaryClassId != mainClassId) {
            int b = findSkillLevelForOneClass(secondaryClassId, skillId, forcedLevel, origSkillLvl);
            if (b > best)
                best = b;
        }
        return best;
    }

    private int findSkillLevelForOneClass(int classId, int skillId, int forcedLevel, int origSkillLvl) {
        if (classId <= 0)
            return 0;
        Collection<SkillLearn> skillLearns = SkillAcquireHolder.getInstance().getNormalSkillTreeForClassId(classId);
        if (skillLearns == null || skillLearns.isEmpty())
            return 0;

        int best = 0;
        for (SkillLearn sl : skillLearns) {
            if (sl.getId() == skillId) {
                if (sl.getMinLevel() <= forcedLevel && sl.getLevel() <= origSkillLvl) {
                    if (sl.getLevel() > best)
                        best = sl.getLevel();
                }
            }
        }
        return best;
    }

    /**
     * Modified version that accepts `minimalExp` param:
     * - If minimalExp==true, set the player's exp to the minimal for newLevel
     * - Else, do not overwrite the player's current/oldExp
     * - Then re-check skills & broadcast
     */
    private void forceChangePlayerLevel(Player player, int newLevel, boolean broadcast, boolean minimalExp) {
        _log.info("[RbDelevel Debug] forceChangePlayerLevel => player=" + player.getName()
                + ", newLevel=" + newLevel
                + ", oldLevel=" + player.getLevel()
                + ", minimalExp=" + minimalExp);

        if (newLevel < 1)
            newLevel = 1;

        if (minimalExp) {
            // set exp to minimal for that level
            long neededExp = Experience.LEVEL[Math.min(newLevel, Experience.getMaxLevel())];
            player.getActiveClass().setFirstExp(neededExp);
        }
        // else do NOT overwrite player's exp

        player.getActiveClass().setLevel(newLevel);
        // forcibly re-check skill levels
        player.checkSkills();
        player.updateStats();

        if (broadcast) {
            // Show the level-up animation
            player.broadcastPacket(new SocialAction(player.getObjectId(), SocialAction.LEVEL_UP));
            // send user info
            player.broadcastCharInfo();
        }
    }

    private void applySkillDelevelSet(Player player, Map<Integer, Integer> newMap) {
        _log.info("[RbDelevel] applySkillDelevelSet => removing normal skills, re-adding downgraded set...");
        Clan clan = player.getClan();
        Skill[] arr = player.getAllSkillsArray();
        if (arr != null) {
            for (int i = 0; i < arr.length; i++) {
                Skill s = arr[i];
                if (s == null)
                    continue;
                if (isClanSkill(clan, s.getId()) || isNobleOrHeroSkill(s.getId()))
                    continue;
                player.removeSkill(s, false);
            }
        }
        for (Map.Entry<Integer, Integer> e : newMap.entrySet()) {
            int sid = e.getKey().intValue();
            int lvl = e.getValue().intValue();
            if (lvl < 1)
                continue;
            Skill newSk = SkillTable.getInstance().getInfo(sid, lvl);
            if (newSk != null) {
                player.addSkill(newSk, false);
            }
        }
        player.updateStats();
    }

    /**
     * Called by Player’s addExpAndSp if “SkipRaidExp=1” is set:
     * We store that XP & SP in _deferredXp / _deferredSp to add later.
     */
    public void storeDeferredXp(Player pl, long xpValue, long spValue) {
        if (pl == null || (xpValue <= 0 && spValue <= 0))
            return;

        Integer oid = Integer.valueOf(pl.getObjectId());

        Long oldXp = _deferredXp.get(oid);
        if (oldXp == null)
            oldXp = Long.valueOf(0L);

        Long oldSp = _deferredSp.get(oid);
        if (oldSp == null)
            oldSp = Long.valueOf(0L);

        _log.info("[RbDelevel Debug] storeDeferredXp => player=" + pl.getName()
                + ", xpValue=" + xpValue
                + ", spValue=" + spValue
                + ", old totalXp=" + oldXp
                + ", old totalSp=" + oldSp);

        _deferredXp.put(oid, Long.valueOf(oldXp.longValue() + xpValue));
        _deferredSp.put(oid, Long.valueOf(oldSp.longValue() + spValue));
    }

    /**
     * On leaving the OUTER zone, we restore the player’s original level,
     * then grant any XP/SP that was accumulated while forcibly-lowered.
     */
    public void distributeGainedXp(Player pl) {
        if (pl == null)
            return;

        Long storedXP = _deferredXp.remove(pl.getObjectId());
        Long storedSP = _deferredSp.remove(pl.getObjectId());
        if (storedXP == null)
            storedXP = Long.valueOf(0L);
        if (storedSP == null)
            storedSP = Long.valueOf(0L);

        if (storedXP.longValue() <= 0 && storedSP.longValue() <= 0)
            return;

        _log.info("[RbDelevel Debug] distributeGainedXp => awarding storedXP="
                + storedXP + ", storedSP=" + storedSP
                + " to " + pl.getName());

        // 1) Because we do not want addExpAndSp() to re-store these amounts,
        // we must first unset "SkipRaidExp"
        pl.unsetVar("SkipRaidExp");

        // 2) Now actually add the XP/SP
        pl.sendMessage("While forcibly-lowered, you earned " + storedXP.longValue()
                + " XP and " + storedSP.longValue() + " SP. Granting it now!");
        pl.addExpAndSp(storedXP.longValue(), storedSP.longValue());

        _log.info("[RbDelevel] distributeGainedXp => awarding "
                + storedXP + " xp & " + storedSP + " sp to " + pl.getName());
    }

    /**
     * @return true if the player is forcibly-lowered
     */
    public boolean isPlayerStillLowered(Player pl) {
        if (pl == null)
            return false;
        return _loweredPlayers.contains(Integer.valueOf(pl.getObjectId()));
    }

    public void removePlayerFromLoweredSet(Player pl) {
        if (pl == null)
            return;
        _loweredPlayers.remove(Integer.valueOf(pl.getObjectId()));
        _log.info("[RbDelevel Debug] removePlayerFromLoweredSet => removed "
                + pl.getName()
                + " from forcibly-lowered set. No double awarding possible now.");
    }

    public void restoreOriginalLevel(Player pl) {
        if (pl == null)
            return;
        Integer oldLvlObj = _originalLevels.remove(pl.getObjectId());
        Long oldExpObj = _originalExps.remove(pl.getObjectId());

        _log.info("[RbDelevel Debug] restoreOriginalLevel => player=" + pl.getName()
                + ", oldLvlObj=" + oldLvlObj
                + ", oldExpObj=" + oldExpObj
                + ", activeClassExpBefore=" + pl.getActiveClass().getExp()
                + ", secondExpBefore=" + pl.getActiveClass().getSecondaryExp());

        if (oldLvlObj != null) {
            pl.unsetVar("IgnoreDelevelStore");

            // 1) Re-apply original EXP
            if (oldExpObj != null) {
                pl.getActiveClass().setFirstExp(oldExpObj.longValue());
            }

            // 2) Now set old level, minimalExp=false
            forceChangePlayerLevel(pl, oldLvlObj.intValue(), true, false);
        }
        restoreOriginalSkills(pl);

        // Clear the “beforeDelevel”
        if (pl.getActiveClass() != null) {
            pl.getActiveClass().setLevelBeforeDelevel(0);
            pl.getActiveClass().setExpBeforeDelevel(0);
        }
    }

    private void restoreOriginalSkills(Player pl) {
        if (pl == null)
            return;
        Map<Integer, Integer> map = _originalSkillLevels.remove(pl.getObjectId());
        if (map == null)
            return;

        Skill[] arr = pl.getAllSkillsArray();
        Clan clan = pl.getClan();
        if (arr != null) {
            for (int i = 0; i < arr.length; i++) {
                Skill s = arr[i];
                if (s == null)
                    continue;
                if (isClanSkill(clan, s.getId()) || isNobleOrHeroSkill(s.getId()))
                    continue;
                pl.removeSkill(s, false);
            }
        }
        for (Map.Entry<Integer, Integer> e : map.entrySet()) {
            int sid = e.getKey().intValue();
            int slv = e.getValue().intValue();
            if (slv < 1)
                continue;
            Skill real = SkillTable.getInstance().getInfo(sid, slv);
            if (real != null)
                pl.addSkill(real, false);
        }
        pl.updateStats();
    }

    /**
     * If forced-lower triggered mid-fight, record originalExp if not done
     */
    public void recordOriginalExp(Player pl) {
        if (pl == null)
            return;
        if (!_originalExps.containsKey(pl.getObjectId()))
            _originalExps.put(pl.getObjectId(), Long.valueOf(pl.getExp()));
    }

    public String getBossName(int bossId) {
        String name = "Unknown";
        Connection con = null;
        PreparedStatement pst = null;
        ResultSet rs = null;
        try {
            con = DatabaseFactory.getInstance().getConnection();
            pst = con.prepareStatement("SELECT name FROM raidboss_status WHERE id=?");
            pst.setInt(1, bossId);
            rs = pst.executeQuery();
            if (rs.next())
                name = rs.getString("name");
        } catch (Exception e) {
            _log.warn("getBossName: error bossId=" + bossId + " e=" + e.getMessage(), e);
        } finally {
            DbUtils.closeQuietly(con, pst, rs);
        }
        return name;
    }

    /**
     * For logging or from DB: gets the boss’s level from raidboss_status
     */
    public int getBossLevel(int bossId) {
        int lvl = 0;
        Connection con = null;
        PreparedStatement pst = null;
        ResultSet rs = null;
        try {
            con = DatabaseFactory.getInstance().getConnection();
            pst = con.prepareStatement("SELECT level FROM raidboss_status WHERE id=?");
            pst.setInt(1, bossId);
            rs = pst.executeQuery();
            if (rs.next())
                lvl = rs.getInt("level");
        } catch (Exception e) {
            _log.warn("getBossLevel: error bossId=" + bossId + " e=" + e.getMessage(), e);
        } finally {
            DbUtils.closeQuietly(con, pst, rs);
        }
        return lvl;
    }

    // If you also want boss name, store a small cache or read from DB similarly

}
