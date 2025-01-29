package l2ft.gameserver.model.entity.events.impl;

import l2ft.commons.collections.MultiValueSet;
import l2ft.commons.threading.RunnableImpl;
import l2ft.gameserver.Announcements;
import l2ft.gameserver.ThreadPoolManager;
import l2ft.gameserver.data.xml.holder.NpcHolder;
import l2ft.gameserver.idfactory.IdFactory;
import l2ft.gameserver.instancemanager.ReflectionManager;
import l2ft.gameserver.model.Creature;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.Skill;
import l2ft.gameserver.model.base.TeamType;
import l2ft.gameserver.model.entity.Reflection;
import l2ft.gameserver.model.entity.events.GlobalEvent;
import l2ft.gameserver.model.instances.FGBFlagInstance;
import l2ft.gameserver.model.instances.NpcInstance;
import l2ft.gameserver.model.instances.residences.SiegeFlagInstance;
import l2ft.gameserver.network.l2.components.SystemMsg;
import l2ft.gameserver.templates.npc.NpcTemplate;
import l2ft.gameserver.utils.ClassAbbreviations;
import l2ft.gameserver.utils.Location;
import l2ft.gameserver.dao.IslandAssaultDAO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * IslandAssaultEvent
 *
 * A "no-man's-land" approach:
 * - The EXACT node that gets destroyed becomes noMansIndex.
 * - Then both sides shift around that index (±1),
 * so the destroyer "advances" and the destroyed side retreats.
 *
 * Radar logic:
 * - We point players first to no man's land (noMansIndex),
 * - Then to the enemy front once they arrive at no man's land.
 *
 * This version retains all original ELO calculations, class-abbreviation
 * messages, and accessor methods. It also calls refreshLivePlayers()
 * after major changes so iab_live_players is updated.
 */
public class IslandAssaultEvent extends GlobalEvent {
    private static final Logger _log = LoggerFactory.getLogger(IslandAssaultEvent.class);

    // Distance threshold for "reached" checks
    private static final int WAYPOINT_PROXIMITY = 1000;

    /**
     * The index of the current "No Man's Land."
     * Initially 5, but changes to the EXACT node that got destroyed last time.
     */
    private int noMansIndex = 5;

    // Each player's "hasReachedNoMans" progress
    private final Map<Integer, Boolean> hasReachedNoMans = new ConcurrentHashMap<>();

    private ScheduledFuture<?> _radarCheckTask;

    public static final int FLAG_NPC_ID = 35062;
    private static final long RESPAWN_CYCLE = 60000L;

    private Reflection _reflection;

    // ELO constants (restored from your original code)
    private static final int BASE_ELO = 2000;
    private static final int MAX_ELO_STEAL = 150;
    private static final int MIN_ELO_STEAL = 1;
    private static final int BASE_ELO_STEAL = 10;
    private static final double STEAL_PER_ELO_GAIN = 1.0 / 100; // used if victimELO > killerELO
    private static final double STEAL_PER_ELO_LOSS = 0.5 / 100; // used if victimELO <= killerELO

    // Team-size factor, Flag ELO, Win Bonus
    private static final boolean ENABLE_TEAM_SIZE_MULTIPLIER = true;
    private static final double TEAM_SIZE_SCALE = 0.06;
    private static final double TEAM_SIZE_MAX_BONUS = 1.50;

    private static final boolean ENABLE_FLAG_ELO_BONUS = true;
    private static final int FLAG_BASE_ELO_BONUS = 5;

    private static final boolean ENABLE_WIN_BONUS = true;
    private static final int WIN_BONUS = 5;

    // Tracking total kills for scoreboard
    private int blueTotalKills = 0;
    private int redTotalKills = 0;

    // Example nodes for Island Assault
    private static final Location[] FLAG_SPAWNS = {
            new Location(-71480, 258232, -3104), // 0 (Blue Final)
            new Location(-78712, 249688, -3568), // 1
            new Location(-84744, 245224, -3720), // 2
            new Location(-94536, 238168, -3448), // 3 (Blue Rear)
            new Location(-106040, 232392, -3648), // 4 (Blue Front)
            new Location(-109672, 243032, -3520), // 5 (No Mans - default)
            new Location(-121272, 239608, -3216), // 6 (Red Front)
            new Location(-115912, 227544, -2784), // 7 (Red Rear)
            new Location(-111240, 218088, -2984), // 8
            new Location(-102456, 220872, -3120), // 9
            new Location(-100024, 212488, -3040) // 10 (Red Final)
    };

    private static final Location[] PLAYER_SPAWNS = {
            new Location(-71304, 257864, -3096), // 0 (Blue Final)
            new Location(-79464, 248760, -3568), // 1
            new Location(-85128, 224824, -3720), // 2
            new Location(-94376, 237928, -3456), // 3 (Blue Rear)
            new Location(-105448, 232824, -3712), // 4 (Blue Front)
            new Location(-108616, 242600, -3472), // 5 (Center area / NoMans)
            new Location(-120856, 240216, -3216), // 6 (Red Front)
            new Location(-116264, 227608, -2800), // 7 (Red Rear)
            new Location(-111640, 217928, -2984), // 8
            new Location(-102856, 220344, -3048), // 9
            new Location(-100312, 212424, -3032) // 10 (Red Final)
    };

    // Indices for each side's front/back
    private int t1RearFlagIndex = 3; // Blue Rear
    private int t1FrontFlagIndex = 4; // Blue Front
    private int t2FrontFlagIndex = 6; // Red Front
    private int t2RearFlagIndex = 7; // Red Rear

    private static final int BLUE_FINAL_INDEX = 0;
    private static final int RED_FINAL_INDEX = 10;

    private int blueFlagsRemaining = 5;
    private int redFlagsRemaining = 5;
    private int blueFlagsDestroyed = 0;
    private int redFlagsDestroyed = 0;

    // The two IslandAssault teams
    private IslandAssaultTeam teamA; // Blue
    private IslandAssaultTeam teamB; // Red

    private ScheduledFuture<?> _respawnTask;
    private int matchId;

    // Stats (like Forgotten BG):
    private Map<Integer, Integer> killsMap = new ConcurrentHashMap<>();
    private Map<Integer, Integer> deathsMap = new ConcurrentHashMap<>();
    private Map<Integer, Integer> flagsMap = new ConcurrentHashMap<>();
    private Map<Integer, Integer> currentEloMap = new ConcurrentHashMap<>();
    private Map<Integer, Integer> primaryClassMap = new ConcurrentHashMap<>();
    private Map<Integer, Integer> secondaryClassMap = new ConcurrentHashMap<>();

    public IslandAssaultEvent(Reflection reflection) {
        super(createEventParams());
        setReflection(reflection);
    }

    private static MultiValueSet<String> createEventParams() {
        MultiValueSet<String> paramSet = new MultiValueSet<>();
        paramSet.set("id", 173);
        paramSet.set("name", "IslandAssault");
        return paramSet;
    }

    public void setReflection(Reflection reflection) {
        _reflection = reflection;
    }

    public Reflection getReflection() {
        return (_reflection != null) ? _reflection : ReflectionManager.DEFAULT;
    }

    @Override
    public boolean isInProgress() {
        // We consider it "in-progress" from startEvent() to stopEvent()
        return true;
    }

    @Override
    public boolean canRessurect(Player resurrectPlayer, Creature creature, boolean force) {
        // For simplicity, allow resurrection
        return true;
    }

    @Override
    public SystemMsg checkForAttack(Creature target, Creature attacker, Skill skill, boolean force) {
        if (!(attacker instanceof Player) || !(target instanceof Player)) {
            return null;
        }
        Player atkPlayer = (Player) attacker;
        Player tgtPlayer = (Player) target;
        TeamType atkTeam = getTeamOfPlayer(atkPlayer);
        TeamType tgtTeam = getTeamOfPlayer(tgtPlayer);
        if (atkTeam != TeamType.NONE && tgtTeam != TeamType.NONE && atkTeam != tgtTeam) {
            return null; // valid pvp
        }
        return SystemMsg.INVALID_TARGET;
    }

    // ------------------------
    // Now we do the refresh
    // ------------------------
    private void refreshLivePlayers() {
        IslandAssaultDAO dao = IslandAssaultDAO.getInstance();

        Player[] blueArr = (teamA != null)
                ? teamA.getPlayers().toArray(new Player[0])
                : new Player[0];
        Player[] redArr = (teamB != null)
                ? teamB.getPlayers().toArray(new Player[0])
                : new Player[0];

        dao.refreshLivePlayers(
                matchId,
                blueArr,
                redArr,
                killsMap,
                deathsMap,
                flagsMap,
                currentEloMap,
                primaryClassMap,
                secondaryClassMap);
    }

    // ==================== Team assignment & start ===================
    public void assignPlayersToTeams(List<Player> teamAList, List<Player> teamBList) {
        teamA = new IslandAssaultTeam(TeamType.BLUE);
        teamB = new IslandAssaultTeam(TeamType.RED);

        // Add team A
        for (Player p : teamAList) {
            if (p != null) {
                teamA.addMember(p);
                p.addEvent(this);
            }
        }
        // Add team B
        for (Player p : teamBList) {
            if (p != null) {
                teamB.addMember(p);
                p.addEvent(this);
            }
        }

        // Teleport each side
        if (teamA != null) {
            for (Player p : teamA.getPlayers()) {
                if (p != null) {
                    p.teleToLocation(PLAYER_SPAWNS[t1RearFlagIndex], getReflection());
                }
            }
        }
        if (teamB != null) {
            for (Player p : teamB.getPlayers()) {
                if (p != null) {
                    p.teleToLocation(PLAYER_SPAWNS[t2RearFlagIndex], getReflection());
                }
            }
        }

        showTeamSizes();

        // Force correct colors
        if (teamA != null) {
            for (Player p : teamA.getPlayers()) {
                if (p != null) {
                    p.setTeam(TeamType.BLUE);
                    p.broadcastUserInfo(true);
                }
            }
        }
        if (teamB != null) {
            for (Player p : teamB.getPlayers()) {
                if (p != null) {
                    p.setTeam(TeamType.RED);
                    p.broadcastUserInfo(true);
                }
            }
        }

        // Now point them all to no man's land
        pointAllPlayersToNoMansLand();
    }

    @Override
    public void startEvent() {
        super.startEvent();
        Announcements.getInstance().announceToAll("The Island Assault has begun! Prepare for battle!");
        setNoExpLossForParticipants(true);
        broadcastMessage("Island Assault has started!");
        spawnFlags();

        // Insert DB match
        IslandAssaultDAO dao = IslandAssaultDAO.getInstance();
        int blueTeamSize = (teamA != null) ? teamA.getPlayers().size() : 0;
        int redTeamSize = (teamB != null) ? teamB.getPlayers().size() : 0;

        matchId = dao.insertMatch(new Timestamp(System.currentTimeMillis()), "BLUE", blueTeamSize, redTeamSize);

        // Insert participants
        if (teamA != null) {
            for (Player p : teamA.getPlayers()) {
                if (p == null)
                    continue;
                IslandAssaultDAO.PlayerStats ps = dao.loadOrCreatePlayerStats(p.getObjectId());
                int primaryClassId = p.getActiveClassClassId().getId();
                int secondaryClassId = p.getSecondaryClassId();
                dao.insertMatchParticipant(
                        matchId,
                        p.getObjectId(),
                        "BLUE",
                        ps.currentELO,
                        primaryClassId,
                        secondaryClassId);

                killsMap.put(p.getObjectId(), 0);
                deathsMap.put(p.getObjectId(), 0);
                flagsMap.put(p.getObjectId(), 0);
                currentEloMap.put(p.getObjectId(), ps.currentELO);
                primaryClassMap.put(p.getObjectId(), primaryClassId);
                secondaryClassMap.put(p.getObjectId(), secondaryClassId);
            }
        }
        if (teamB != null) {
            for (Player p : teamB.getPlayers()) {
                if (p == null)
                    continue;
                IslandAssaultDAO.PlayerStats ps = dao.loadOrCreatePlayerStats(p.getObjectId());
                int primaryClassId = p.getActiveClassClassId().getId();
                int secondaryClassId = p.getSecondaryClassId();
                dao.insertMatchParticipant(
                        matchId,
                        p.getObjectId(),
                        "RED",
                        ps.currentELO,
                        primaryClassId,
                        secondaryClassId);

                killsMap.put(p.getObjectId(), 0);
                deathsMap.put(p.getObjectId(), 0);
                flagsMap.put(p.getObjectId(), 0);
                currentEloMap.put(p.getObjectId(), ps.currentELO);
                primaryClassMap.put(p.getObjectId(), primaryClassId);
                secondaryClassMap.put(p.getObjectId(), secondaryClassId);
            }
        }

        updateLiveStatus(true);

        // IMPORTANT: populate iab_live_players initially
        refreshLivePlayers();

        // Respawn cycle
        _respawnTask = ThreadPoolManager.getInstance().scheduleAtFixedRate(
                new RespawnCycleTask(), RESPAWN_CYCLE, RESPAWN_CYCLE);

        // Radar checks
        _radarCheckTask = ThreadPoolManager.getInstance().scheduleAtFixedRate(
                new RadarProximityCheckTask(), 3000L, 3000L);
    }

    @Override
    public void stopEvent() {
        super.stopEvent();
        setNoExpLossForParticipants(false);

        // Decide winner
        String winnerTeamStr;
        if (blueFlagsRemaining == 0) {
            winnerTeamStr = "RED";
        } else if (redFlagsRemaining == 0) {
            winnerTeamStr = "BLUE";
        } else {
            // compare destroyed counts
            if (blueFlagsDestroyed > redFlagsDestroyed) {
                winnerTeamStr = "BLUE";
            } else if (redFlagsDestroyed > blueFlagsDestroyed) {
                winnerTeamStr = "RED";
            } else {
                winnerTeamStr = "TIE";
            }
        }

        if ("TIE".equals(winnerTeamStr)) {
            Announcements.getInstance().announceToAll("The Island Assault has ended in a tie!");
        } else {
            Announcements.getInstance().announceToAll("The Island Assault has ended! Winner: " + winnerTeamStr);
        }

        reviveAllParticipants();

        IslandAssaultDAO dao = IslandAssaultDAO.getInstance();
        dao.updateMatch(
                matchId,
                new Timestamp(System.currentTimeMillis()),
                "TIE".equals(winnerTeamStr) ? "TIE" : winnerTeamStr,
                blueFlagsDestroyed,
                redFlagsDestroyed);

        boolean blueWin = "BLUE".equals(winnerTeamStr);
        boolean redWin = "RED".equals(winnerTeamStr);

        finalizeStatsForTeam(teamA, blueWin);
        finalizeStatsForTeam(teamB, redWin);

        // Teleport out
        Location townLoc = new Location(83400, 148600, -3400);
        teleportAndCleanupTeam(teamA, townLoc);
        teleportAndCleanupTeam(teamB, townLoc);

        if (_respawnTask != null) {
            _respawnTask.cancel(false);
        }
        if (_radarCheckTask != null) {
            _radarCheckTask.cancel(false);
            _radarCheckTask = null;
        }

        despawnFlags();
        IslandAssaultManager.getInstance().onEventEnd();

        updateLiveStatus(false);
    }

    private void reviveAllParticipants() {
        if (teamA != null) {
            for (Player p : teamA.getPlayers()) {
                if (p != null && p.isDead()) {
                    p.doRevive();
                    p.setCurrentHpMp(p.getMaxHp(), p.getMaxMp(), true);
                    p.setCurrentCp(p.getMaxCp());
                }
            }
        }
        if (teamB != null) {
            for (Player p : teamB.getPlayers()) {
                if (p != null && p.isDead()) {
                    p.doRevive();
                    p.setCurrentHpMp(p.getMaxHp(), p.getMaxMp(), true);
                    p.setCurrentCp(p.getMaxCp());
                }
            }
        }
    }

    private void finalizeStatsForTeam(IslandAssaultTeam team, boolean teamWon) {
        if (team == null)
            return;
        IslandAssaultDAO dao = IslandAssaultDAO.getInstance();

        int winnerBonus = (ENABLE_WIN_BONUS && teamWon) ? WIN_BONUS : 0;
        for (Player p : team.getPlayers()) {
            if (p == null)
                continue;
            IslandAssaultDAO.PlayerStats ps = dao.loadOrCreatePlayerStats(p.getObjectId());
            ps.totalMatches++;

            if (teamWon) {
                ps.totalWins++;
            } else {
                ps.totalLosses++;
            }

            Integer killCountObj = killsMap.get(p.getObjectId());
            int killCount = (killCountObj == null) ? 0 : killCountObj;
            Integer deathCountObj = deathsMap.get(p.getObjectId());
            int deathCount = (deathCountObj == null) ? 0 : deathCountObj;

            if (winnerBonus > 0) {
                int oldElo = ps.currentELO;
                ps.currentELO += winnerBonus;
                if (ps.currentELO > ps.highestELO) {
                    ps.highestELO = ps.currentELO;
                }
                dao.insertEloHistory(p.getObjectId(), oldElo, ps.currentELO, matchId);
            }
            dao.updatePlayerStats(ps);
            dao.updateMatchParticipant(matchId, p.getObjectId(), killCount, deathCount, ps.currentELO);
        }
    }

    private void teleportAndCleanupTeam(IslandAssaultTeam team, Location townLoc) {
        if (team == null)
            return;
        for (Player p : team.getPlayers()) {
            if (p != null && !p.isDeleted()) {
                p.teleToLocation(townLoc, ReflectionManager.DEFAULT);
                p.addRadar(townLoc.getX(), townLoc.getY(), townLoc.getZ());
                p.removeEvent(this);
                p.setTeam(TeamType.NONE);
                p.broadcastUserInfo(true);
            }
        }
    }

    // ================== Participant mgmt ====================
    public void addParticipant(Player p, TeamType team) {
        if (team == TeamType.BLUE) {
            teamA.addMember(p);
            p.addEvent(this);
            p.teleToLocation(getSpawnForTeam(TeamType.BLUE), getReflection());
            p.setTeam(TeamType.BLUE);
        } else if (team == TeamType.RED) {
            teamB.addMember(p);
            p.addEvent(this);
            p.teleToLocation(getSpawnForTeam(TeamType.RED), getReflection());
            p.setTeam(TeamType.RED);
        }
        p.broadcastUserInfo(true);

        // Refresh scoreboard
        refreshLivePlayers();
        showTeamSizes();
        updateLiveStatus(true);

        // Also point them to no man's land
        pointPlayerToNoMansLand(p);
    }

    private Location getSpawnForTeam(TeamType t) {
        if (t == TeamType.BLUE) {
            return PLAYER_SPAWNS[t1RearFlagIndex];
        } else if (t == TeamType.RED) {
            return PLAYER_SPAWNS[t2RearFlagIndex];
        }
        // fallback
        return new Location(83400, 148600, -3400);
    }

    public void removeParticipant(Player player) {
        if (teamA != null && teamA.contains(player)) {
            teamA.removeMember(player);
        } else if (teamB != null && teamB.contains(player)) {
            teamB.removeMember(player);
        }

        if (isInProgress()) {
            IslandAssaultDAO.getInstance().setParticipantLeftEarly(matchId, player.getObjectId());
            updateLiveStatus(true);

            // Refresh scoreboard after removal
            refreshLivePlayers();
        }
        if (teamA != null && teamB != null) {
            if (teamA.getPlayers().isEmpty()) {
                endEvent(TeamType.RED);
                return;
            } else if (teamB.getPlayers().isEmpty()) {
                endEvent(TeamType.BLUE);
                return;
            }
        }
    }

    private void pointAllPlayersToNoMansLand() {
        if (teamA == null && teamB == null)
            return;
        hasReachedNoMans.clear();

        if (teamA != null) {
            for (Player p : teamA.getPlayers()) {
                pointPlayerToNoMansLand(p);
            }
        }
        if (teamB != null) {
            for (Player p : teamB.getPlayers()) {
                pointPlayerToNoMansLand(p);
            }
        }
    }

    private void pointPlayerToNoMansLand(Player p) {
        if (p == null)
            return;
        hasReachedNoMans.put(p.getObjectId(), false);
        Location noMansLoc = FLAG_SPAWNS[noMansIndex];
        p.addRadar(noMansLoc.getX(), noMansLoc.getY(), noMansLoc.getZ());
        p.sendMessage("Proceed to the current No Man's Land (index " + noMansIndex + ") first!");
    }

    // ================== Display helpers =======================
    private void showTeamSizes() {
        int teamASize = (teamA != null) ? teamA.getPlayers().size() : 0;
        int teamBSize = (teamB != null) ? teamB.getPlayers().size() : 0;
        String msg = "Current Teams: Blue: " + teamASize + " players, Red: " + teamBSize + " players.";
        if (teamA != null) {
            teamA.broadcastMessage(msg);
        }
        if (teamB != null) {
            teamB.broadcastMessage(msg);
        }
    }

    @Override
    public boolean canAttack(Creature target, Creature attacker, Skill skill, boolean force) {
        if (attacker == null || target == null)
            return false;
        Player atkPlayer = attacker.getPlayer();
        if (atkPlayer == null)
            return false;

        Player tarPlayer = target.getPlayer();
        if (tarPlayer != null) {
            TeamType atkTeam = getTeamOfPlayer(atkPlayer);
            TeamType tarTeam = getTeamOfPlayer(tarPlayer);
            if (atkTeam != TeamType.NONE && tarTeam != TeamType.NONE && atkTeam != tarTeam) {
                return true;
            }
        }

        if (target instanceof FGBFlagInstance) {
            FGBFlagInstance flag = (FGBFlagInstance) target;
            TeamType atkTeam = getTeamOfPlayer(atkPlayer);
            if (flag.isFinalFlag()) {
                return (atkTeam != TeamType.NONE && atkTeam != flag.getTeam());
            } else {
                return (atkTeam != TeamType.NONE && atkTeam != flag.getTeam() && !flag.isRearFlag());
            }
        }
        return super.canAttack(target, attacker, skill, force);
    }

    // ================== ELO logic (restored) ========================
    private double getTeamSizeMultiplier(TeamType killerTeam) {
        if (!ENABLE_TEAM_SIZE_MULTIPLIER) {
            return 1.0;
        }
        if (killerTeam == TeamType.NONE) {
            return 1.0;
        }

        int teamASize = (teamA != null) ? teamA.getPlayers().size() : 1;
        int teamBSize = (teamB != null) ? teamB.getPlayers().size() : 1;

        int killerTeamSize = (killerTeam == TeamType.BLUE) ? teamASize : teamBSize;
        int enemyTeamSize = (killerTeam == TeamType.BLUE) ? teamBSize : teamASize;

        int diff = killerTeamSize - enemyTeamSize;
        double teamMult;
        if (diff == 0) {
            teamMult = 1.0;
        } else if (diff < 0) {
            teamMult = 1.0 + (TEAM_SIZE_SCALE * Math.abs(diff));
        } else {
            teamMult = 1.0 - (TEAM_SIZE_SCALE * diff);
        }

        if (teamMult < 0.1) {
            teamMult = 0.1;
        } else if (teamMult > TEAM_SIZE_MAX_BONUS) {
            teamMult = TEAM_SIZE_MAX_BONUS;
        }
        return teamMult;
    }

    private int calculateEloDelta(int killerElo, int victimElo, double tierMultiplier, double teamSizeMultiplier) {
        double diff = victimElo - killerElo;
        // This is your original approach:
        double additional = (diff > 0)
                ? (STEAL_PER_ELO_GAIN * diff)
                : (STEAL_PER_ELO_LOSS * diff);
        double raw = (BASE_ELO_STEAL + additional) * tierMultiplier * teamSizeMultiplier;
        if (raw < MIN_ELO_STEAL) {
            raw = MIN_ELO_STEAL;
        }
        if (raw > MAX_ELO_STEAL) {
            raw = MAX_ELO_STEAL;
        }
        return (int) Math.round(raw);
    }

    public void onPlayerKill(Player killer, Player victim) {
        if (!isParticipant(killer) || !isParticipant(victim)) {
            return;
        }

        IslandAssaultDAO dao = IslandAssaultDAO.getInstance();
        IslandAssaultDAO.PlayerStats kStats = dao.loadOrCreatePlayerStats(killer.getObjectId());
        IslandAssaultDAO.PlayerStats vStats = dao.loadOrCreatePlayerStats(victim.getObjectId());

        kStats.totalKills++;
        vStats.totalDeaths++;

        int oldKillerELO = kStats.currentELO;
        int oldVictimELO = vStats.currentELO;

        // killsMap update
        Integer killVal = killsMap.get(killer.getObjectId());
        int curKills = (killVal == null) ? 0 : killVal;
        killsMap.put(killer.getObjectId(), curKills + 1);

        // deathsMap update
        Integer deathVal = deathsMap.get(victim.getObjectId());
        int curDeaths = (deathVal == null) ? 0 : deathVal;
        deathsMap.put(victim.getObjectId(), curDeaths + 1);

        // Tally total kills
        if (getTeamOfPlayer(killer) == TeamType.BLUE) {
            blueTotalKills++;
        } else {
            redTotalKills++;
        }

        // Class multipliers (based on victim's class):
        Integer victPrimObj = primaryClassMap.get(victim.getObjectId());
        int victimPrimary = (victPrimObj == null) ? 0 : victPrimObj;
        Integer victSecObj = secondaryClassMap.get(victim.getObjectId());
        int victimSecondary = (victSecObj == null) ? 0 : victSecObj;

        double primaryMult = dao.getClassMultiplier(victimPrimary);
        double secondaryMult = dao.getClassMultiplier(victimSecondary);
        double finalMultiplier = (primaryMult + secondaryMult) / 2.0;

        double teamSizeMult = getTeamSizeMultiplier(getTeamOfPlayer(killer));
        int eloDelta = calculateEloDelta(kStats.currentELO, vStats.currentELO, finalMultiplier, teamSizeMult);

        // Adjust ELO
        kStats.currentELO = Math.max(1, kStats.currentELO + eloDelta);
        vStats.currentELO = Math.max(1, vStats.currentELO - eloDelta);

        // Possibly set new highestELO
        if (kStats.currentELO > kStats.highestELO) {
            kStats.highestELO = kStats.currentELO;
        }

        // Save DB
        dao.updatePlayerStats(kStats);
        dao.updatePlayerStats(vStats);

        dao.insertEloHistory(killer.getObjectId(), oldKillerELO, kStats.currentELO, matchId);
        dao.insertEloHistory(victim.getObjectId(), oldVictimELO, vStats.currentELO, matchId);

        dao.updateMatchParticipant(
                matchId,
                killer.getObjectId(),
                killsMap.get(killer.getObjectId()),
                deathsMap.get(killer.getObjectId()),
                kStats.currentELO);
        dao.updateMatchParticipant(
                matchId,
                victim.getObjectId(),
                killsMap.get(victim.getObjectId()),
                deathsMap.get(victim.getObjectId()),
                vStats.currentELO);

        dao.insertKill(matchId, killer.getObjectId(), victim.getObjectId(), eloDelta);

        // Some messaging with class abbreviations
        int killerTotalKills = kStats.totalKills;
        int victimTotalDeaths = vStats.totalDeaths;
        int killerBGKills = killsMap.get(killer.getObjectId());
        int victimBGDeaths = deathsMap.get(victim.getObjectId());

        // killer class
        Integer killPrimObj = primaryClassMap.get(killer.getObjectId());
        int killerPrimaryClass = (killPrimObj == null) ? 0 : killPrimObj;
        Integer killSecObj = secondaryClassMap.get(killer.getObjectId());
        int killerSecondaryClass = (killSecObj == null) ? 0 : killSecObj;

        String killerStackAbbrev = ClassAbbreviations.getAbbrev(killerPrimaryClass)
                + "/" + ClassAbbreviations.getAbbrev(killerSecondaryClass);
        String victimStackAbbrev = ClassAbbreviations.getAbbrev(victimPrimary)
                + "/" + ClassAbbreviations.getAbbrev(victimSecondary);

        killer.sendMessage("You killed " + victim.getName()
                + " (Their classes: " + victimStackAbbrev + ")"
                + " ELO gained: " + eloDelta
                + ", New ELO: " + kStats.currentELO
                + ", Your Total Kills: " + killerTotalKills
                + ", Your BG Kills: " + killerBGKills);

        victim.sendMessage("You were killed by " + killer.getName()
                + " (Their classes: " + killerStackAbbrev + ")"
                + " ELO lost: " + eloDelta
                + ", New ELO: " + vStats.currentELO
                + ", Your Total Deaths: " + victimTotalDeaths
                + ", Your BG Deaths: " + victimBGDeaths);

        // Update scoreboard
        updateLiveStatus(true);
        refreshLivePlayers();

        // Also update iab_live_players for killer & victim
        Integer killerFlagsVal = flagsMap.get(killer.getObjectId());
        int killerFlags = (killerFlagsVal == null) ? 0 : killerFlagsVal;
        Integer victimFlagsVal = flagsMap.get(victim.getObjectId());
        int victimFlags = (victimFlagsVal == null) ? 0 : victimFlagsVal;

        dao.updateLivePlayer(
                matchId,
                killer.getObjectId(),
                kStats.currentELO,
                killerBGKills,
                deathsMap.get(killer.getObjectId()),
                killerFlags);

        dao.updateLivePlayer(
                matchId,
                victim.getObjectId(),
                vStats.currentELO,
                killsMap.get(victim.getObjectId()),
                deathsMap.get(victim.getObjectId()),
                victimFlags);
    }

    private void updateLiveStatus(boolean eventLive) {
        IslandAssaultDAO dao = IslandAssaultDAO.getInstance();
        int blueTeamSize = (teamA != null) ? teamA.getPlayers().size() : 0;
        int redTeamSize = (teamB != null) ? teamB.getPlayers().size() : 0;
        dao.updateLiveStatus(
                eventLive,
                matchId,
                blueFlagsRemaining,
                redFlagsRemaining,
                blueFlagsDestroyed,
                redFlagsDestroyed,
                blueTeamSize,
                redTeamSize,
                blueTotalKills,
                redTotalKills);
    }

    // ====================== Flag destruction & shifting ======================
    public void onFlagDestroyed(TeamType destroyedFlagOwnerTeam, Player destroyer) {
        _log.info("onFlagDestroyed: destroyedFlagOwnerTeam=" + destroyedFlagOwnerTeam
                + (destroyer != null
                        ? ", destroyer=" + destroyer.getName() + " (charId=" + destroyer.getObjectId() + ")"
                        : ", destroyer=null"));

        // 1) Figure out EXACT node that was destroyed
        int destroyedIndex;
        if (destroyedFlagOwnerTeam == TeamType.BLUE) {
            // The destroyed node was the Blue front
            destroyedIndex = t1FrontFlagIndex;
        } else {
            // The destroyed node was the Red front
            destroyedIndex = t2FrontFlagIndex;
        }

        // 2) That EXACT node is now no man's land
        noMansIndex = destroyedIndex;
        _log.info("No Man's Land is now index=" + noMansIndex);

        // 3) If we have a valid destroyer, apply ELO & stats
        if (destroyer != null && isParticipant(destroyer)) {
            IslandAssaultDAO dao = IslandAssaultDAO.getInstance();
            IslandAssaultDAO.PlayerStats ps = dao.loadOrCreatePlayerStats(destroyer.getObjectId());
            ps.flagsDestroyed++;
            dao.updatePlayerStats(ps);

            Integer oldVal = flagsMap.get(destroyer.getObjectId());
            if (oldVal == null)
                oldVal = 0;
            flagsMap.put(destroyer.getObjectId(), oldVal + 1);

            // Insert flag capture
            dao.insertFlagCapture(matchId, destroyer.getObjectId(), destroyedIndex);

            // ELO bonus for capturing
            if (ENABLE_FLAG_ELO_BONUS) {
                int oldElo = ps.currentELO;
                int bonus = FLAG_BASE_ELO_BONUS;
                if (ENABLE_TEAM_SIZE_MULTIPLIER) {
                    TeamType killerTeam = getTeamOfPlayer(destroyer);
                    double teamSizeMult = getTeamSizeMultiplier(killerTeam);
                    bonus = (int) Math.round(bonus * teamSizeMult);
                }
                ps.currentELO += bonus;
                if (ps.currentELO > ps.highestELO) {
                    ps.highestELO = ps.currentELO;
                }
                dao.updatePlayerStats(ps);

                int killsForDestroyer = (killsMap.get(destroyer.getObjectId()) != null)
                        ? killsMap.get(destroyer.getObjectId())
                        : 0;
                int deathsForDestroyer = (deathsMap.get(destroyer.getObjectId()) != null)
                        ? deathsMap.get(destroyer.getObjectId())
                        : 0;

                dao.insertEloHistory(destroyer.getObjectId(), oldElo, ps.currentELO, matchId);
                dao.updateMatchParticipant(
                        matchId,
                        destroyer.getObjectId(),
                        killsForDestroyer,
                        deathsForDestroyer,
                        ps.currentELO);

                destroyer.sendMessage("You destroyed a flag and gained " + bonus
                        + " ELO! New ELO: " + ps.currentELO);
            }
        }

        // 4) Adjust counters
        if (destroyedFlagOwnerTeam == TeamType.BLUE) {
            // A Blue flag was destroyed => advantage Red
            redFlagsDestroyed++;
            blueFlagsRemaining--;
            // Possibly redFlagsRemaining++ if you want that logic
            redFlagsRemaining++;
        } else {
            // A Red flag was destroyed => advantage Blue
            blueFlagsDestroyed++;
            redFlagsRemaining--;
            blueFlagsRemaining++;
        }

        // 5) SHIFT lines after we set noMansIndex
        shiftFrontAfterDestruction(destroyedFlagOwnerTeam, destroyedIndex);

        // 6) If one side hits zero => event ends
        if (blueFlagsRemaining == 0) {
            endEvent(TeamType.RED);
            return;
        } else if (redFlagsRemaining == 0) {
            endEvent(TeamType.BLUE);
            return;
        }

        // 7) Refresh flags & broadcast
        despawnFlags();
        spawnFlags();
        broadcastFlagCounts();

        // 8) Re-point everyone to the new noMansIndex
        pointAllPlayersToNoMansLand();

        updateLiveStatus(true);
        refreshLivePlayers();
    }

    /**
     * SHIFT the front line *symmetrically*, using the EXACT destroyedIndex
     * as the center.
     * newBlueFront = destroyedIndex - 1
     * newRedFront = destroyedIndex + 1
     */
    private void shiftFrontAfterDestruction(TeamType destroyedFlagOwnerTeam, int destroyedIndex) {
        int newCenter = destroyedIndex;
        int newBlueFront = Math.max(newCenter - 1, BLUE_FINAL_INDEX);
        int newBlueRear = (newBlueFront > BLUE_FINAL_INDEX) ? (newBlueFront - 1) : BLUE_FINAL_INDEX;

        int newRedFront = Math.min(newCenter + 1, RED_FINAL_INDEX);
        int newRedRear = (newRedFront < RED_FINAL_INDEX) ? (newRedFront + 1) : RED_FINAL_INDEX;

        t1FrontFlagIndex = newBlueFront;
        t1RearFlagIndex = newBlueRear;
        t2FrontFlagIndex = newRedFront;
        t2RearFlagIndex = newRedRear;

        _log.info("shiftFrontAfterDestruction => destroyedIndex="
                + destroyedIndex
                + ", Blue: t1Rear=" + t1RearFlagIndex
                + ", t1Front=" + t1FrontFlagIndex
                + ", Red: t2Front=" + t2FrontFlagIndex
                + ", t2Rear=" + t2RearFlagIndex);
    }

    private void broadcastFlagCounts() {
        String msg = "Flags left: Blue: " + blueFlagsRemaining
                + " | Red: " + redFlagsRemaining
                + " | Blue destroyed: " + blueFlagsDestroyed
                + " | Red destroyed: " + redFlagsDestroyed;
        if (teamA != null) {
            teamA.broadcastMessage(msg);
        }
        if (teamB != null) {
            teamB.broadcastMessage(msg);
        }
    }

    private void endEvent(TeamType winner) {
        broadcastMessage((winner == TeamType.BLUE ? "Blue Team" : "Red Team") + " has won!");
        stopEvent();
    }

    private void setNoExpLossForParticipants(boolean enable) {
        if (teamA != null) {
            for (Player p : teamA.getPlayers()) {
                if (p != null && !p.isDeleted()) {
                    p.setVar("NoExpLossInFGB", enable ? "true" : null, -1);
                }
            }
        }
        if (teamB != null) {
            for (Player p : teamB.getPlayers()) {
                if (p != null && !p.isDeleted()) {
                    p.setVar("NoExpLossInFGB", enable ? "true" : null, -1);
                }
            }
        }
    }

    private void despawnFlags() {
        if (getReflection() == null) {
            return;
        }
        for (NpcInstance npc : getReflection().getNpcs()) {
            if (npc instanceof SiegeFlagInstance) {
                npc.deleteMe();
            }
        }
    }

    private void spawnFlags() {
        NpcTemplate flagTemplate = NpcHolder.getInstance().getTemplate(FLAG_NPC_ID);
        if (flagTemplate == null) {
            _log.warn("IslandAssaultEvent: Flag NPC template not found for ID: " + FLAG_NPC_ID);
            return;
        }

        boolean blueFinal = (blueFlagsRemaining == 1);
        boolean redFinal = (redFlagsRemaining == 1);

        // Blue side
        if (blueFinal) {
            t1RearFlagIndex = BLUE_FINAL_INDEX;
            t1FrontFlagIndex = BLUE_FINAL_INDEX;
            spawnFlag(flagTemplate, BLUE_FINAL_INDEX, TeamType.BLUE, false, true);
        } else {
            spawnFlag(flagTemplate, t1RearFlagIndex, TeamType.BLUE, true, false);
            spawnFlag(flagTemplate, t1FrontFlagIndex, TeamType.BLUE, false, false);
        }

        // Red side
        if (redFinal) {
            t2RearFlagIndex = RED_FINAL_INDEX;
            t2FrontFlagIndex = RED_FINAL_INDEX;
            spawnFlag(flagTemplate, RED_FINAL_INDEX, TeamType.RED, false, true);
        } else {
            spawnFlag(flagTemplate, t2RearFlagIndex, TeamType.RED, true, false);
            spawnFlag(flagTemplate, t2FrontFlagIndex, TeamType.RED, false, false);
        }
    }

    private SiegeFlagInstance spawnFlag(NpcTemplate template,
            int spawnIndex,
            TeamType team,
            boolean isRearFlag,
            boolean isFinalFlag) {
        int objectId = IdFactory.getInstance().getNextId();
        FGBFlagInstance flag = new FGBFlagInstance(objectId, template, this, team, isRearFlag, isFinalFlag);

        String teamName = (team == TeamType.BLUE) ? "Blue" : "Red";
        String flagType = isFinalFlag ? "Final" : (isRearFlag ? "Rear" : "Front");
        _log.info("IslandAssaultEvent: Spawning " + teamName + " " + flagType
                + " Flag at index " + spawnIndex
                + " coords:" + FLAG_SPAWNS[spawnIndex]);

        flag.setCurrentHpMp(flag.getMaxHp(), flag.getMaxMp());
        flag.setReflection(getReflection());
        flag.spawnMe(FLAG_SPAWNS[spawnIndex]);
        return flag;
    }

    private void broadcastMessage(String msg) {
        if (teamA != null) {
            teamA.broadcastMessage(msg);
        }
        if (teamB != null) {
            teamB.broadcastMessage(msg);
        }
    }

    @Override
    public void reCalcNextTime(boolean onInit) {
        // No scheduling needed
    }

    @Override
    protected long startTimeMillis() {
        return System.currentTimeMillis();
    }

    // ============== Accessors & checks (restored) ===============
    public Map<Integer, Integer> getKillsMap() {
        return killsMap;
    }

    public Map<Integer, Integer> getDeathsMap() {
        return deathsMap;
    }

    public Map<Integer, Integer> getFlagsMap() {
        return flagsMap;
    }

    public Map<Integer, Integer> getCurrentEloMap() {
        return currentEloMap;
    }

    public Map<Integer, Integer> getPrimaryClassMap() {
        return primaryClassMap;
    }

    public Map<Integer, Integer> getSecondaryClassMap() {
        return secondaryClassMap;
    }

    public int getMatchId() {
        return matchId;
    }

    public boolean isParticipant(Player player) {
        return (teamA != null && teamA.contains(player))
                || (teamB != null && teamB.contains(player));
    }

    public TeamType getTeamOfPlayer(Player player) {
        if (player == null) {
            return TeamType.NONE;
        }
        if (teamA != null && teamA.contains(player)) {
            return TeamType.BLUE;
        }
        if (teamB != null && teamB.contains(player)) {
            return TeamType.RED;
        }
        return TeamType.NONE;
    }

    public List<Player> getTeamPlayers(TeamType t) {
        if (t == TeamType.BLUE && teamA != null) {
            return teamA.getPlayers();
        }
        if (t == TeamType.RED && teamB != null) {
            return teamB.getPlayers();
        }
        return Collections.emptyList();
    }

    public void onRespawnRequestAccepted(Player player) {
        if (player == null)
            return;
        TeamType pt = getTeamOfPlayer(player);

        int spawnIndex = (pt == TeamType.BLUE) ? t1RearFlagIndex : t2RearFlagIndex;
        player.doRevive();
        player.setCurrentHpMp(player.getMaxHp(), player.getMaxMp(), true);
        player.setCurrentCp(player.getMaxCp());
        player.teleToLocation(PLAYER_SPAWNS[spawnIndex], getReflection());

        // Now point them back to noMansIndex again
        pointPlayerToNoMansLand(player);
    }

    // Radar proximity check for no man's land
    private class RadarProximityCheckTask extends RunnableImpl {
        @Override
        public void runImpl() {
            checkNoMansProximity(teamA);
            checkNoMansProximity(teamB);
        }

        private void checkNoMansProximity(IslandAssaultTeam team) {
            if (team == null)
                return;
            Location noMansLoc = FLAG_SPAWNS[noMansIndex];

            for (Player p : team.getPlayers()) {
                if (p == null || p.isAlikeDead()) {
                    continue;
                }

                Boolean alreadyObj = hasReachedNoMans.get(p.getObjectId());
                boolean alreadyReached = (alreadyObj != null && alreadyObj);
                if (alreadyReached) {
                    continue;
                }

                double dist = p.getDistance(noMansLoc);
                if (dist <= WAYPOINT_PROXIMITY) {
                    hasReachedNoMans.put(p.getObjectId(), true);
                    p.sendMessage("You have reached No Man's Land at index "
                            + noMansIndex + ". Advance toward the enemy front!");

                    // Next: point them to the enemy front
                    Location enemyFront = (team.getTeamType() == TeamType.BLUE)
                            ? FLAG_SPAWNS[t2FrontFlagIndex]
                            : FLAG_SPAWNS[t1FrontFlagIndex];
                    p.addRadar(enemyFront.getX(), enemyFront.getY(), enemyFront.getZ());
                }
            }
        }
    }

    // Respawn cycle
    private class RespawnCycleTask extends RunnableImpl {
        @Override
        public void runImpl() {
            handleRespawns(teamA);
            handleRespawns(teamB);

            if (IslandAssaultManager.getInstance().isEventRunning()) {
                IslandAssaultManager.getInstance().tryAddQueuedPlayersToRunningEvent();
            } else {
                IslandAssaultManager.getInstance().internalCheckStartConditions();
            }
        }

        private void handleRespawns(IslandAssaultTeam team) {
            if (team == null)
                return;
            List<Player> deadPlayers = team.getDeadPlayers();
            for (Player p : deadPlayers) {
                onRespawnRequestAccepted(p);
            }
        }
    }

    // =================== Nested class for the two sides ======================
    public static class IslandAssaultTeam {
        private TeamType _teamType;
        private List<Player> _players = new java.util.concurrent.CopyOnWriteArrayList<Player>();

        public IslandAssaultTeam(TeamType teamType) {
            _teamType = teamType;
        }

        public TeamType getTeamType() {
            return _teamType;
        }

        public void addMember(Player p) {
            _players.add(p);
        }

        public void removeMember(Player p) {
            _players.remove(p);
        }

        public boolean contains(Player p) {
            return _players.contains(p);
        }

        public List<Player> getPlayers() {
            return _players;
        }

        public List<Player> getDeadPlayers() {
            List<Player> dead = new java.util.ArrayList<Player>();
            for (Player pl : _players) {
                if (pl != null && pl.isDead()) {
                    dead.add(pl);
                }
            }
            return dead;
        }

        public void broadcastMessage(String msg) {
            for (Player p : _players) {
                if (p != null) {
                    p.sendMessage(msg);
                }
            }
        }
    }
}
