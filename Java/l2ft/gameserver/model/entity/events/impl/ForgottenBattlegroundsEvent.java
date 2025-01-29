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
import l2ft.gameserver.dao.ForgottenBattlegroundsDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;

public class ForgottenBattlegroundsEvent extends GlobalEvent {
    private static final Logger _log = LoggerFactory.getLogger(ForgottenBattlegroundsEvent.class);

    // ---------------------------------------------
    // For proximity-based two-step radar logic:
    // Adjust this range as desired (e.g., 300, 400, 500, etc.)
    private static final int FRONT_FLAG_PROXIMITY = 300;

    // Track who has or hasn't yet reached their own front
    // (cannot use Map.getOrDefault in older Java; we’ll do manual checks)
    private final Map<Integer, Boolean> hasReachedFrontFlag = new ConcurrentHashMap<>();
    private ScheduledFuture<?> _radarCheckTask;

    public static final int FLAG_NPC_ID = 35062;
    private static final long RESPAWN_CYCLE = 60000L;

    private Reflection _reflection;

    // ELO constants:
    private static final int BASE_ELO = 2000;
    private static final int MAX_ELO_STEAL = 150;
    private static final int MIN_ELO_STEAL = 1;
    private static final int BASE_ELO_STEAL = 10;
    private static final double STEAL_PER_ELO_GAIN = 1.0 / 100;
    private static final double STEAL_PER_ELO_LOSS = 0.5 / 100;

    // NEW CODE START (Team-Size factor, Flag ELO, Win Bonus)
    private static final boolean ENABLE_TEAM_SIZE_MULTIPLIER = true;
    private static final double TEAM_SIZE_SCALE = 0.06;
    private static final double TEAM_SIZE_MAX_BONUS = 1.50;

    private static final boolean ENABLE_FLAG_ELO_BONUS = true;
    private static final int FLAG_BASE_ELO_BONUS = 5;

    private static final boolean ENABLE_WIN_BONUS = true;
    private static final int WIN_BONUS = 5;
    // NEW CODE END

    private int blueTotalKills = 0;
    private int redTotalKills = 0;

    private static final Location[] FLAG_SPAWNS = {
            new Location(-52792, 190184, -3672), // 0 (Blue Final)
            new Location(-54712, 190360, -4472), // 1
            new Location(-57432, 188824, -4512), // 2
            new Location(-57432, 185032, -4512), // 3 (Blue Rear)
            new Location(-54616, 183560, -4512), // 4 (Blue Front)
            new Location(-53848, 181624, -4552), // 5 (No Mans)
            new Location(-55016, 179320, -4808), // 6 (Red Front)
            new Location(-59528, 179768, -4808), // 7 (Red Rear)
            new Location(-59512, 184520, -4808), // 8
            new Location(-56792, 186472, -4808), // 9
            new Location(-52792, 190184, -3672) // 10 (Red Final)
    };

    private static final Location[] PLAYER_SPAWNS = {
            new Location(-52744, 190440, -3496), // 0 (Blue Final)
            new Location(-54616, 190872, -4472), // 1
            new Location(-58088, 188920, -4512), // 2
            new Location(-57176, 185032, -4512), // 3 (Blue Rear spawn)
            new Location(-54680, 183848, -4512), // 4 (Blue Front spawn)
            new Location(-53976, 181608, -4552), // 5 (No Mans)
            new Location(-54952, 179528, -4808), // 6 (Red Front spawn)
            new Location(-59736, 179048, -4808), // 7 (Red Rear spawn)
            new Location(-59848, 184536, -4808), // 8
            new Location(-56968, 186024, -4808), // 9
            new Location(-52744, 190440, -3496) // 10 (Red Final)
    };

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

    private FGBTeam teamA;
    private FGBTeam teamB;

    private ScheduledFuture<?> _respawnTask;

    private int matchId;

    // Maps for kills/deaths/flags/elo/class etc.
    // Cannot rely on getOrDefault(...) in older Java.
    private Map<Integer, Integer> killsMap = new ConcurrentHashMap<>();
    private Map<Integer, Integer> deathsMap = new ConcurrentHashMap<>();
    private Map<Integer, Integer> flagsMap = new ConcurrentHashMap<>();
    private Map<Integer, Integer> currentEloMap = new ConcurrentHashMap<>();
    private Map<Integer, Integer> primaryClassMap = new ConcurrentHashMap<>();
    private Map<Integer, Integer> secondaryClassMap = new ConcurrentHashMap<>();

    public ForgottenBattlegroundsEvent(Reflection reflection) {
        super(createEventParams());
        setReflection(reflection);
    }

    private static MultiValueSet<String> createEventParams() {
        MultiValueSet<String> paramSet = new MultiValueSet<>();
        paramSet.set("id", 173);
        paramSet.set("name", "ForgottenBattlegrounds");
        return paramSet;
    }

    public void setReflection(Reflection reflection) {
        _reflection = reflection;
    }

    public Reflection getReflection() {
        return _reflection != null ? _reflection : ReflectionManager.DEFAULT;
    }

    @Override
    public boolean isInProgress() {
        return true;
    }

    @Override
    public boolean canRessurect(Player resurrectPlayer, Creature creature, boolean force) {
        return true;
    }

    @Override
    public SystemMsg checkForAttack(Creature target, Creature attacker, Skill skill, boolean force) {
        if (!(attacker instanceof Player) || !(target instanceof Player))
            return null;

        Player atkPlayer = (Player) attacker;
        Player tgtPlayer = (Player) target;

        TeamType atkTeam = getTeamOfPlayer(atkPlayer);
        TeamType tgtTeam = getTeamOfPlayer(tgtPlayer);

        if (atkTeam != TeamType.NONE && tgtTeam != TeamType.NONE && atkTeam != tgtTeam) {
            return null;
        }

        return SystemMsg.INVALID_TARGET;
    }

    public void assignPlayersToTeams(List<Player> teamAList, List<Player> teamBList) {
        teamA = new FGBTeam(TeamType.BLUE);
        teamB = new FGBTeam(TeamType.RED);

        for (Player p : teamAList) {
            if (p != null) {
                teamA.addMember(p);
                p.addEvent(this);
            }
        }
        for (Player p : teamBList) {
            if (p != null) {
                teamB.addMember(p);
                p.addEvent(this);
            }
        }

        // Teleport each team to rear spawns
        for (Player p : teamA.getPlayers()) {
            if (p != null)
                p.teleToLocation(PLAYER_SPAWNS[t1RearFlagIndex], getReflection());
        }
        for (Player p : teamB.getPlayers()) {
            if (p != null)
                p.teleToLocation(PLAYER_SPAWNS[t2RearFlagIndex], getReflection());
        }

        showTeamSizes();
        updateRadarForAllPlayers();

        int teamASize = teamA.getPlayers().size();
        int teamBSize = teamB.getPlayers().size();

        // Indicate enemy front for each side
        Location enemyFrontFlagForA = FLAG_SPAWNS[t2FrontFlagIndex];
        for (Player p : teamA.getPlayers()) {
            if (p != null) {
                p.addRadar(enemyFrontFlagForA.getX(), enemyFrontFlagForA.getY(), enemyFrontFlagForA.getZ());
                p.sendMessage("Current Teams: Blue: " + teamASize + " players, Red: " + teamBSize + " players.");
            }
        }
        Location enemyFrontFlagForB = FLAG_SPAWNS[t1FrontFlagIndex];
        for (Player p : teamB.getPlayers()) {
            if (p != null) {
                p.addRadar(enemyFrontFlagForB.getX(), enemyFrontFlagForB.getY(), enemyFrontFlagForB.getZ());
                p.sendMessage("Current Teams: Blue: " + teamASize + " players, Red: " + teamBSize + " players.");
            }
        }
        Location ownFrontFlagForA = FLAG_SPAWNS[t1FrontFlagIndex];
        for (Player p : teamA.getPlayers()) {
            if (p != null) {
                p.addRadar(ownFrontFlagForA.getX(), ownFrontFlagForA.getY(), ownFrontFlagForA.getZ());
                p.sendMessage("Pointing you to your own front flag, secure it first!");
            }
        }
        Location ownFrontFlagForB = FLAG_SPAWNS[t2FrontFlagIndex];
        for (Player p : teamB.getPlayers()) {
            if (p != null) {
                p.addRadar(ownFrontFlagForB.getX(), ownFrontFlagForB.getY(), ownFrontFlagForB.getZ());
                p.sendMessage("Pointing you to your own front flag, secure it first!");
            }
        }

        // Force team colors
        for (Player p : teamA.getPlayers()) {
            if (p != null) {
                p.setTeam(TeamType.BLUE);
                p.broadcastUserInfo(true);
            }
        }
        for (Player p : teamB.getPlayers()) {
            if (p != null) {
                p.setTeam(TeamType.RED);
                p.broadcastUserInfo(true);
            }
        }
    }

    @Override
    public void startEvent() {
        super.startEvent();
        Announcements.getInstance().announceToAll("The Forgotten Battlegrounds has begun! Prepare for battle!");
        setNoExpLossForParticipants(true);
        broadcastMessage("Forgotten Battlegrounds has started!");
        spawnFlags();

        ForgottenBattlegroundsDAO dao = ForgottenBattlegroundsDAO.getInstance();
        int blueTeamSize = teamA.getPlayers().size();
        int redTeamSize = teamB.getPlayers().size();

        matchId = dao.insertMatch(new Timestamp(System.currentTimeMillis()), "BLUE", blueTeamSize, redTeamSize);

        // Insert participants
        for (Player p : teamA.getPlayers()) {
            if (p == null)
                continue;
            ForgottenBattlegroundsDAO.PlayerStats ps = dao.loadOrCreatePlayerStats(p.getObjectId());
            int primaryClassId = p.getActiveClassClassId().getId();
            int secondaryClassId = p.getSecondaryClassId();
            dao.insertMatchParticipant(matchId, p.getObjectId(), "BLUE", ps.currentELO, primaryClassId,
                    secondaryClassId);

            killsMap.put(p.getObjectId(), 0);
            deathsMap.put(p.getObjectId(), 0);
            flagsMap.put(p.getObjectId(), 0);
            currentEloMap.put(p.getObjectId(), ps.currentELO);
            primaryClassMap.put(p.getObjectId(), primaryClassId);
            secondaryClassMap.put(p.getObjectId(), secondaryClassId);
        }
        for (Player p : teamB.getPlayers()) {
            if (p == null)
                continue;
            ForgottenBattlegroundsDAO.PlayerStats ps = dao.loadOrCreatePlayerStats(p.getObjectId());
            int primaryClassId = p.getActiveClassClassId().getId();
            int secondaryClassId = p.getSecondaryClassId();
            dao.insertMatchParticipant(matchId, p.getObjectId(), "RED", ps.currentELO, primaryClassId,
                    secondaryClassId);

            killsMap.put(p.getObjectId(), 0);
            deathsMap.put(p.getObjectId(), 0);
            flagsMap.put(p.getObjectId(), 0);
            currentEloMap.put(p.getObjectId(), ps.currentELO);
            primaryClassMap.put(p.getObjectId(), primaryClassId);
            secondaryClassMap.put(p.getObjectId(), secondaryClassId);
        }

        updateLiveStatus(true);
        refreshLivePlayers();

        // Respawn cycle
        _respawnTask = ThreadPoolManager.getInstance().scheduleAtFixedRate(new RespawnCycleTask(), RESPAWN_CYCLE,
                RESPAWN_CYCLE);

        // Radar proximity checks
        hasReachedFrontFlag.clear();
        _radarCheckTask = ThreadPoolManager.getInstance().scheduleAtFixedRate(new RadarProximityCheckTask(), 3000L,
                3000L);
    }

    private void refreshLivePlayers() {
        ForgottenBattlegroundsDAO dao = ForgottenBattlegroundsDAO.getInstance();
        Player[] blueArr = teamA.getPlayers().toArray(new Player[0]);
        Player[] redArr = teamB.getPlayers().toArray(new Player[0]);
        dao.refreshLivePlayers(matchId, blueArr, redArr,
                killsMap, deathsMap, flagsMap, currentEloMap,
                primaryClassMap, secondaryClassMap);
    }

    @Override
    public void stopEvent() {
        super.stopEvent();
        setNoExpLossForParticipants(false);

        // Determine winner
        String winnerTeamStr;
        if (blueFlagsRemaining == 0) {
            winnerTeamStr = "RED";
        } else if (redFlagsRemaining == 0) {
            winnerTeamStr = "BLUE";
        } else {
            if (blueFlagsDestroyed > redFlagsDestroyed) {
                winnerTeamStr = "BLUE";
            } else if (redFlagsDestroyed > blueFlagsDestroyed) {
                winnerTeamStr = "RED";
            } else {
                winnerTeamStr = "TIE";
            }
        }

        if ("TIE".equals(winnerTeamStr)) {
            Announcements.getInstance().announceToAll("The Forgotten Battlegrounds have ended in a tie!");
        } else {
            Announcements.getInstance()
                    .announceToAll("The Forgotten Battlegrounds have ended! Winner: " + winnerTeamStr);
        }

        reviveAllParticipants();

        ForgottenBattlegroundsDAO dao = ForgottenBattlegroundsDAO.getInstance();
        dao.updateMatch(matchId,
                new Timestamp(System.currentTimeMillis()),
                "TIE".equals(winnerTeamStr) ? "TIE" : winnerTeamStr,
                blueFlagsDestroyed,
                redFlagsDestroyed);

        // ELO finalization
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
        ForgottenBattlegroundsManager.getInstance().onEventEnd();

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

    private void finalizeStatsForTeam(FGBTeam team, boolean teamWon) {
        if (team == null)
            return;

        ForgottenBattlegroundsDAO dao = ForgottenBattlegroundsDAO.getInstance();
        int winnerBonus = (ENABLE_WIN_BONUS && teamWon) ? WIN_BONUS : 0;

        for (Player p : team.getPlayers()) {
            if (p == null)
                continue;

            ForgottenBattlegroundsDAO.PlayerStats ps = dao.loadOrCreatePlayerStats(p.getObjectId());
            ps.totalMatches++;
            if (teamWon)
                ps.totalWins++;
            else
                ps.totalLosses++;

            Integer killCount = killsMap.get(p.getObjectId());
            if (killCount == null)
                killCount = 0;
            Integer deathCount = deathsMap.get(p.getObjectId());
            if (deathCount == null)
                deathCount = 0;

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

    private void teleportAndCleanupTeam(FGBTeam team, Location townLoc) {
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
        refreshLivePlayers();
        showTeamSizes();
        updateLiveStatus(true);
    }

    private Location getSpawnForTeam(TeamType team) {
        if (team == TeamType.BLUE) {
            return PLAYER_SPAWNS[t1RearFlagIndex];
        } else if (team == TeamType.RED) {
            return PLAYER_SPAWNS[t2RearFlagIndex];
        }
        return new Location(83400, 148600, -3400);
    }

    private void updateRadarForAllPlayers() {
        Location enemyFrontFlagForA = FLAG_SPAWNS[t2FrontFlagIndex];
        Location enemyFrontFlagForB = FLAG_SPAWNS[t1FrontFlagIndex];

        if (teamA != null) {
            for (Player p : teamA.getPlayers()) {
                if (p != null) {
                    p.addRadar(enemyFrontFlagForA.getX(), enemyFrontFlagForA.getY(), enemyFrontFlagForA.getZ());
                    p.sendMessage("Indicator updated to enemy flag!");
                }
            }
        }
        if (teamB != null) {
            for (Player p : teamB.getPlayers()) {
                if (p != null) {
                    p.addRadar(enemyFrontFlagForB.getX(), enemyFrontFlagForB.getY(), enemyFrontFlagForB.getZ());
                    p.sendMessage("Indicator updated to enemy flag!");
                }
            }
        }
    }

    private void showTeamSizes() {
        int teamASize = teamA != null ? teamA.getPlayers().size() : 0;
        int teamBSize = teamB != null ? teamB.getPlayers().size() : 0;
        String msg = "Current Teams: Blue: " + teamASize + " players, Red: " + teamBSize + " players.";
        if (teamA != null)
            teamA.broadcastMessage(msg);
        if (teamB != null)
            teamB.broadcastMessage(msg);
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

    public void removeParticipant(Player player) {
        if (teamA != null && teamA.contains(player))
            teamA.removeMember(player);
        else if (teamB != null && teamB.contains(player))
            teamB.removeMember(player);

        if (isInProgress()) {
            ForgottenBattlegroundsDAO.getInstance().setParticipantLeftEarly(matchId, player.getObjectId());
            updateLiveStatus(true);
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

    public void onFlagDestroyed(TeamType destroyedFlagOwnerTeam, Player destroyer) {
        _log.info("onFlagDestroyed called: destroyedFlagOwnerTeam=" + destroyedFlagOwnerTeam
                + (destroyer != null
                        ? ", destroyer=" + destroyer.getName() + " (charId=" + destroyer.getObjectId() + ")"
                        : ", destroyer=null"));

        ForgottenBattlegroundsDAO dao = ForgottenBattlegroundsDAO.getInstance();
        int destroyedIndex = (destroyedFlagOwnerTeam == TeamType.BLUE) ? t1FrontFlagIndex : t2FrontFlagIndex;

        if (destroyer != null && isParticipant(destroyer)) {
            ForgottenBattlegroundsDAO.PlayerStats ps = dao.loadOrCreatePlayerStats(destroyer.getObjectId());
            ps.flagsDestroyed++;
            dao.updatePlayerStats(ps);

            Integer fv = flagsMap.get(destroyer.getObjectId());
            if (fv == null)
                fv = 0;
            flagsMap.put(destroyer.getObjectId(), fv + 1);

            dao.insertFlagCapture(matchId, destroyer.getObjectId(), destroyedIndex);
            _log.info("Flag capture inserted matchId=" + matchId + ", charId=" + destroyer.getObjectId()
                    + ", flagIndex=" + destroyedIndex);

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

                int killsForThisDestroyer = killsMap.containsKey(destroyer.getObjectId())
                        ? killsMap.get(destroyer.getObjectId())
                        : 0;
                int deathsForThisDestroyer = deathsMap.containsKey(destroyer.getObjectId())
                        ? deathsMap.get(destroyer.getObjectId())
                        : 0;

                dao.insertEloHistory(destroyer.getObjectId(), oldElo, ps.currentELO, matchId);
                dao.updateMatchParticipant(matchId, destroyer.getObjectId(), killsForThisDestroyer,
                        deathsForThisDestroyer, ps.currentELO);

                destroyer.sendMessage("You destroyed a flag and gained " + bonus + " ELO! New ELO: " + ps.currentELO);
            }
        }

        if (destroyedFlagOwnerTeam == TeamType.BLUE) {
            redFlagsDestroyed++;
            blueFlagsRemaining--;
            redFlagsRemaining++;
        } else {
            blueFlagsDestroyed++;
            redFlagsRemaining--;
            blueFlagsRemaining++;
        }

        adjustFlagsAfterDestruction(destroyedFlagOwnerTeam, destroyedIndex);

        if (blueFlagsRemaining == 0) {
            endEvent(TeamType.RED);
            return;
        } else if (redFlagsRemaining == 0) {
            endEvent(TeamType.BLUE);
            return;
        }

        despawnFlags();
        spawnFlags();
        broadcastFlagCounts();
        updateRadarForAllPlayers();
        updateLiveStatus(true);
        refreshLivePlayers();
    }

    private void adjustFlagsAfterDestruction(TeamType destroyedFlagOwnerTeam, int destroyedFlagIndex) {
        int newCenter = destroyedFlagIndex;
        _log.info("Adjusting flags around destroyedIndex=" + newCenter);

        int newBlueFront = Math.max(newCenter - 1, BLUE_FINAL_INDEX);
        int newBlueRear = (newBlueFront > BLUE_FINAL_INDEX) ? (newBlueFront - 1) : BLUE_FINAL_INDEX;

        int newRedFront = Math.min(newCenter + 1, RED_FINAL_INDEX);
        int newRedRear = (newRedFront < RED_FINAL_INDEX) ? (newRedFront + 1) : RED_FINAL_INDEX;

        if (newBlueFront == BLUE_FINAL_INDEX) {
            t1FrontFlagIndex = BLUE_FINAL_INDEX;
            t1RearFlagIndex = BLUE_FINAL_INDEX;
        } else {
            t1FrontFlagIndex = newBlueFront;
            t1RearFlagIndex = newBlueRear;
        }

        if (newRedFront == RED_FINAL_INDEX) {
            t2FrontFlagIndex = RED_FINAL_INDEX;
            t2RearFlagIndex = RED_FINAL_INDEX;
        } else {
            t2FrontFlagIndex = newRedFront;
            t2RearFlagIndex = newRedRear;
        }

        _log.info("Flag indices after: t1Rear=" + t1RearFlagIndex
                + ", t1Front=" + t1FrontFlagIndex
                + ", center=" + newCenter
                + ", t2Front=" + t2FrontFlagIndex
                + ", t2Rear=" + t2RearFlagIndex);
    }

    private void broadcastFlagCounts() {
        String msg = "Flags left: Blue: " + blueFlagsRemaining
                + " | Red: " + redFlagsRemaining
                + " | Blue destroyed: " + blueFlagsDestroyed
                + " | Red destroyed: " + redFlagsDestroyed;
        if (teamA != null)
            teamA.broadcastMessage(msg);
        if (teamB != null)
            teamB.broadcastMessage(msg);
    }

    public void endEvent(TeamType winner) {
        broadcastMessage((winner == TeamType.BLUE ? "Blue Team" : "Red Team") + " has won!");
        stopEvent();
    }

    @Override
    public void reCalcNextTime(boolean onInit) {
        // Provide some logic. If you don’t need advanced scheduling,
        // you could simply do nothing:
        // e.g.:
        if (onInit) {
            // Or leave empty
        }
        // No actual scheduling is done here, so the method is effectively a stub.
    }

    @Override
    protected long startTimeMillis() {
        // Return something meaningful, e.g. system time or your scheduled start
        // If you just need to compile, you could do:
        return System.currentTimeMillis();
    }

    private void broadcastMessage(String msg) {
        if (teamA != null)
            teamA.broadcastMessage(msg);
        if (teamB != null)
            teamB.broadcastMessage(msg);
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

    private void spawnFlags() {
        NpcTemplate flagTemplate = NpcHolder.getInstance().getTemplate(FLAG_NPC_ID);
        if (flagTemplate == null) {
            _log.warn("Flag NPC template not found for ID: " + FLAG_NPC_ID);
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

    private SiegeFlagInstance spawnFlag(NpcTemplate template, int spawnIndex, TeamType team,
            boolean isRearFlag, boolean isFinalFlag) {
        int objectId = IdFactory.getInstance().getNextId();
        FGBFlagInstance flag = new FGBFlagInstance(objectId, template, this, team, isRearFlag, isFinalFlag);

        String teamName = (team == TeamType.BLUE) ? "Blue" : "Red";
        String flagType = isFinalFlag ? "Final" : (isRearFlag ? "Rear" : "Front");
        _log.info("Spawning " + teamName + " " + flagType + " Flag at index " + spawnIndex
                + " coords:" + FLAG_SPAWNS[spawnIndex]);

        flag.setCurrentHpMp(flag.getMaxHp(), flag.getMaxMp());
        flag.setReflection(getReflection());
        flag.spawnMe(FLAG_SPAWNS[spawnIndex]);
        return flag;
    }

    private void despawnFlags() {
        if (getReflection() == null)
            return;
        for (NpcInstance npc : getReflection().getNpcs()) {
            if (npc instanceof SiegeFlagInstance) {
                npc.deleteMe();
            }
        }
    }

    public boolean isParticipant(Player player) {
        return (teamA != null && teamA.contains(player)) || (teamB != null && teamB.contains(player));
    }

    public TeamType getTeamOfPlayer(Player player) {
        if (player == null)
            return TeamType.NONE;
        if (teamA != null && teamA.contains(player))
            return TeamType.BLUE;
        if (teamB != null && teamB.contains(player))
            return TeamType.RED;
        return TeamType.NONE;
    }

    public List<Player> getTeamPlayers(TeamType team) {
        if (team == TeamType.BLUE && teamA != null) {
            return teamA.getPlayers();
        } else if (team == TeamType.RED && teamB != null) {
            return teamB.getPlayers();
        }
        return Collections.emptyList();
    }

    // Team-size factor for ELO calculations
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
        if (diff == 0) {
            return 1.0;
        }

        double teamMult;
        if (diff < 0) {
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
        double additional = (diff > 0) ? (STEAL_PER_ELO_GAIN * diff) : (STEAL_PER_ELO_LOSS * diff);
        double raw = (BASE_ELO_STEAL + additional) * tierMultiplier * teamSizeMultiplier;
        if (raw < MIN_ELO_STEAL)
            raw = MIN_ELO_STEAL;
        if (raw > MAX_ELO_STEAL)
            raw = MAX_ELO_STEAL;
        return (int) Math.round(raw);
    }

    public void onPlayerKill(Player killer, Player victim) {
        if (!isParticipant(killer) || !isParticipant(victim))
            return;

        ForgottenBattlegroundsDAO dao = ForgottenBattlegroundsDAO.getInstance();
        ForgottenBattlegroundsDAO.PlayerStats kStats = dao.loadOrCreatePlayerStats(killer.getObjectId());
        ForgottenBattlegroundsDAO.PlayerStats vStats = dao.loadOrCreatePlayerStats(victim.getObjectId());

        kStats.totalKills++;
        vStats.totalDeaths++;

        Integer kKills = killsMap.get(killer.getObjectId());
        if (kKills == null)
            kKills = 0;
        killsMap.put(killer.getObjectId(), kKills + 1);

        Integer vDeaths = deathsMap.get(victim.getObjectId());
        if (vDeaths == null)
            vDeaths = 0;
        deathsMap.put(victim.getObjectId(), vDeaths + 1);

        TeamType killerTeam = getTeamOfPlayer(killer);
        if (killerTeam == TeamType.BLUE) {
            blueTotalKills++;
        } else if (killerTeam == TeamType.RED) {
            redTotalKills++;
        }

        int oldKillerELO = kStats.currentELO;
        int oldVictimELO = vStats.currentELO;

        double primaryMult = dao.getClassMultiplier(primaryClassMap.get(victim.getObjectId()));
        double secondaryMult = dao.getClassMultiplier(secondaryClassMap.get(victim.getObjectId()));
        double finalMultiplier = (primaryMult + secondaryMult) / 2.0;

        double teamSizeMult = getTeamSizeMultiplier(killerTeam);
        int eloDelta = calculateEloDelta(kStats.currentELO, vStats.currentELO, finalMultiplier, teamSizeMult);

        kStats.currentELO = Math.max(1, kStats.currentELO + eloDelta);
        vStats.currentELO = Math.max(1, vStats.currentELO - eloDelta);

        if (kStats.currentELO > kStats.highestELO) {
            kStats.highestELO = kStats.currentELO;
        }

        dao.updatePlayerStats(kStats);
        dao.updatePlayerStats(vStats);

        dao.insertEloHistory(killer.getObjectId(), oldKillerELO, kStats.currentELO, matchId);
        dao.insertEloHistory(victim.getObjectId(), oldVictimELO, vStats.currentELO, matchId);

        dao.updateMatchParticipant(matchId, killer.getObjectId(), killsMap.get(killer.getObjectId()),
                deathsMap.get(killer.getObjectId()), kStats.currentELO);
        dao.updateMatchParticipant(matchId, victim.getObjectId(), killsMap.get(victim.getObjectId()),
                deathsMap.get(victim.getObjectId()), vStats.currentELO);

        dao.insertKill(matchId, killer.getObjectId(), victim.getObjectId(), eloDelta);

        currentEloMap.put(killer.getObjectId(), kStats.currentELO);
        currentEloMap.put(victim.getObjectId(), vStats.currentELO);

        int killerTotalKills = kStats.totalKills;
        int victimTotalDeaths = vStats.totalDeaths;
        int killerBGKills = killsMap.get(killer.getObjectId());
        int victimBGDeaths = deathsMap.get(victim.getObjectId());

        int killerPrimaryClass = primaryClassMap.get(killer.getObjectId());
        int killerSecondaryClass = secondaryClassMap.get(killer.getObjectId());
        int victimPrimaryClass = primaryClassMap.get(victim.getObjectId());
        int victimSecondaryClass = secondaryClassMap.get(victim.getObjectId());

        String killerStackAbbrev = ClassAbbreviations.getAbbrev(killerPrimaryClass) + "/"
                + ClassAbbreviations.getAbbrev(killerSecondaryClass);
        String victimStackAbbrev = ClassAbbreviations.getAbbrev(victimPrimaryClass) + "/"
                + ClassAbbreviations.getAbbrev(victimSecondaryClass);

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

        updateLiveStatus(true);
        dao.updateLivePlayer(matchId, killer.getObjectId(),
                kStats.currentELO,
                killerBGKills,
                deathsMap.get(killer.getObjectId()),
                flagsMap.get(killer.getObjectId()));
        dao.updateLivePlayer(matchId, victim.getObjectId(),
                vStats.currentELO,
                killsMap.get(victim.getObjectId()),
                victimBGDeaths,
                flagsMap.get(victim.getObjectId()));
    }

    private void updateLiveStatus(boolean eventLive) {
        ForgottenBattlegroundsDAO dao = ForgottenBattlegroundsDAO.getInstance();
        int blueTeamSize = (teamA != null) ? teamA.getPlayers().size() : 0;
        int redTeamSize = (teamB != null) ? teamB.getPlayers().size() : 0;
        dao.updateLiveStatus(eventLive, matchId,
                blueFlagsRemaining, redFlagsRemaining,
                blueFlagsDestroyed, redFlagsDestroyed,
                blueTeamSize, redTeamSize,
                blueTotalKills, redTotalKills);
    }

    private class RadarProximityCheckTask extends RunnableImpl {
        @Override
        public void runImpl() {
            checkTeamFrontProximity(teamA);
            checkTeamFrontProximity(teamB);
        }

        private void checkTeamFrontProximity(FGBTeam team) {
            if (team == null)
                return;
            for (Player p : team.getPlayers()) {
                if (p == null || p.isAlikeDead())
                    continue;

                boolean alreadyReached = hasReachedFrontFlag.containsKey(p.getObjectId())
                        ? hasReachedFrontFlag.get(p.getObjectId())
                        : false;
                if (alreadyReached)
                    continue;

                Location ownFront = (team.getTeamType() == TeamType.BLUE)
                        ? FLAG_SPAWNS[t1FrontFlagIndex]
                        : FLAG_SPAWNS[t2FrontFlagIndex];

                double distance = p.getDistance(ownFront);
                if (distance <= FRONT_FLAG_PROXIMITY) {
                    hasReachedFrontFlag.put(p.getObjectId(), true);

                    Location enemyFront = (team.getTeamType() == TeamType.BLUE)
                            ? FLAG_SPAWNS[t2FrontFlagIndex]
                            : FLAG_SPAWNS[t1FrontFlagIndex];

                    p.addRadar(enemyFront.getX(), enemyFront.getY(), enemyFront.getZ());
                    p.sendMessage("You have reached your front flag. Advance toward the enemy front!");
                }
            }
        }
    }

    private class RespawnCycleTask extends RunnableImpl {
        @Override
        public void runImpl() {
            handleRespawns(teamA);
            handleRespawns(teamB);

            if (ForgottenBattlegroundsManager.getInstance().isEventRunning()) {
                ForgottenBattlegroundsManager.getInstance().tryAddQueuedPlayersToRunningEvent();
            } else {
                ForgottenBattlegroundsManager.getInstance().internalCheckStartConditions();
            }
        }

        private void handleRespawns(FGBTeam team) {
            if (team == null)
                return;
            List<Player> deadPlayers = team.getDeadPlayers();
            for (Player p : deadPlayers) {
                onRespawnRequestAccepted(p);
            }
        }
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

        if (pt == TeamType.BLUE) {
            Location ownFront = FLAG_SPAWNS[t1FrontFlagIndex];
            player.addRadar(ownFront.getX(), ownFront.getY(), ownFront.getZ());
            player.sendMessage("Head to your front flag first!");
        } else if (pt == TeamType.RED) {
            Location ownFront = FLAG_SPAWNS[t2FrontFlagIndex];
            player.addRadar(ownFront.getX(), ownFront.getY(), ownFront.getZ());
            player.sendMessage("Head to your front flag first!");
        }
        hasReachedFrontFlag.put(player.getObjectId(), false);
    }

    // Accessors to the internal maps (used by scoreboard scripts, etc.):
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

    // ---------------------------------------------------------------
    // Nested FGBTeam class to hold that team's players
    // ---------------------------------------------------------------
    public static class FGBTeam {
        private TeamType _teamType;
        private List<Player> _players = new CopyOnWriteArrayList<>();

        public FGBTeam(TeamType teamType) {
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
            List<Player> dead = new ArrayList<>();
            for (Player pl : _players) {
                if (pl != null && pl.isDead()) {
                    dead.add(pl);
                }
            }
            return dead;
        }

        public void broadcastMessage(String msg) {
            for (Player p : _players) {
                if (p != null)
                    p.sendMessage(msg);
            }
        }
    }
}
