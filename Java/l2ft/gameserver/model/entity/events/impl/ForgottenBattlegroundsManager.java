package l2ft.gameserver.model.entity.events.impl;

import l2ft.commons.collections.MultiValueSet;
import l2ft.gameserver.data.xml.holder.InstantZoneHolder;
import l2ft.gameserver.instancemanager.ReflectionManager;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.base.TeamType;
import l2ft.gameserver.model.entity.Reflection;
import l2ft.gameserver.templates.InstantZone;
import l2ft.gameserver.utils.Location;
import l2ft.gameserver.dao.ForgottenBattlegroundsDAO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ForgottenBattlegroundsManager {
    private static final ForgottenBattlegroundsManager _instance = new ForgottenBattlegroundsManager();

    public static ForgottenBattlegroundsManager getInstance() {
        return _instance;
    }

    private static final Logger _log = LoggerFactory.getLogger(ForgottenBattlegroundsManager.class);

    private List<Player> teamAWaiting = new CopyOnWriteArrayList<>();
    private List<Player> teamBWaiting = new CopyOnWriteArrayList<>();
    private List<Player> firstAvailableWaiting = new CopyOnWriteArrayList<>();

    private ForgottenBattlegroundsEvent runningEvent;
    private boolean eventRunning = false;
    private static final int MIN_PLAYERS_PER_TEAM = 1;

    public synchronized void registerPlayer(Player player, int chosenTeam) {
        if (!canJoinEvent(player)) {
            player.sendMessage("You do not meet the requirements to join the Forgotten Battlegrounds.");
            return;
        }

        unregisterPlayer(player);

        _log.info("ForgottenBattlegroundsManager: Player " + player.getName() + " is registering for team choice: "
                + chosenTeam);

        switch (chosenTeam) {
            case 0:
                firstAvailableWaiting.add(player);
                player.sendMessage("You have been added to the 'First Available' queue.");
                break;
            case 1:
                teamAWaiting.add(player);
                player.sendMessage("You have been added to Team A's waiting list.");
                break;
            case 2:
                teamBWaiting.add(player);
                player.sendMessage("You have been added to Team B's waiting list.");
                break;
        }

        checkStartConditions();
    }

    public synchronized void unregisterPlayer(Player player) {
        teamAWaiting.remove(player);
        teamBWaiting.remove(player);
        firstAvailableWaiting.remove(player);
        if (runningEvent != null && runningEvent.isParticipant(player)) {
            // If event is in progress and participant is inside, mark leaves
            if (runningEvent.isInProgress()) {
                ForgottenBattlegroundsDAO.getInstance().incrementLeaves(player.getObjectId());
            }
            runningEvent.removeParticipant(player);
        }
    }

    public void onPlayerLogout(Player player) {
        // If in event, increment leaves
        if (runningEvent != null && runningEvent.isParticipant(player)) {
            if (runningEvent.isInProgress()) {
                ForgottenBattlegroundsDAO.getInstance().incrementLeaves(player.getObjectId());
            }
            runningEvent.removeParticipant(player);
        }
        unregisterPlayer(player);
    }

    public int getTeamAQueueCount() {
        return teamAWaiting.size();
    }

    public int getTeamBQueueCount() {
        return teamBWaiting.size();
    }

    public synchronized boolean isPlayerInQueue(Player player) {
        return teamAWaiting.contains(player) || teamBWaiting.contains(player) || firstAvailableWaiting.contains(player);
    }

    private void startEvent() {
        _log.info("ForgottenBattlegroundsManager: Attempting to start the Forgotten Battlegrounds event...");
        eventRunning = true;
        Reflection reflection = new Reflection();
        InstantZone instantZone = InstantZoneHolder.getInstance().getInstantZone(173);
        if (instantZone == null) {
            _log.error("ForgottenBattlegroundsManager: No InstantZone found for ID 173!");
            return;
        }
        reflection.init(instantZone);

        runningEvent = new ForgottenBattlegroundsEvent(reflection);
        runningEvent.assignPlayersToTeams(teamAWaiting, teamBWaiting);

        teamAWaiting.clear();
        teamBWaiting.clear();
        firstAvailableWaiting.clear();

        runningEvent.startEvent();
        _log.info("ForgottenBattlegroundsManager: Forgotten Battlegrounds event started successfully!");
    }

    private void checkStartConditions() {
        if (eventRunning)
            return;

        balanceFirstAvailable();

        int teamACount = teamAWaiting.size();
        int teamBCount = teamBWaiting.size();

        _log.info("ForgottenBattlegroundsManager: Checking start conditions. Team A: " + teamACount + " , Team B: "
                + teamBCount);

        if (teamACount >= MIN_PLAYERS_PER_TEAM && teamBCount >= MIN_PLAYERS_PER_TEAM) {
            startEvent();
        } else {
            _log.info("ForgottenBattlegroundsManager: Not enough players on each team to start the event.");
        }
    }

    private void balanceFirstAvailable() {
        int diff = teamAWaiting.size() - teamBWaiting.size();
        for (Player p : new ArrayList<>(firstAvailableWaiting)) {
            if (diff > 0) {
                teamBWaiting.add(p);
                firstAvailableWaiting.remove(p);
                diff--;
            } else {
                teamAWaiting.add(p);
                firstAvailableWaiting.remove(p);
                diff++;
            }
        }
    }

    public boolean canJoinEvent(Player player) {
        return player.getLevel() >= 85;
    }

    public ForgottenBattlegroundsEvent getRunningEvent() {
        return runningEvent;
    }

    public void onEventEnd() {
        eventRunning = false;
        runningEvent = null;
    }

    public boolean isEventRunning() {
        return eventRunning;
    }

    public void requestLeaveEvent(Player player) {
        ForgottenBattlegroundsEvent event = getRunningEvent();
        if (event != null && event.isParticipant(player)) {
            // Increment leaves since leaving early
            if (event.isInProgress()) {
                ForgottenBattlegroundsDAO.getInstance().incrementLeaves(player.getObjectId());
            }

            event.removeParticipant(player);
            player.teleToLocation(new Location(83400, 148600, -3400), ReflectionManager.DEFAULT);
            player.addRadar(83400, 148600, -3400);

            player.setTeam(TeamType.NONE);
            player.removeEvent(event);
            player.sendMessage("You have left the Forgotten Battlegrounds.");
            return;
        }
        if (isPlayerInQueue(player)) {
            unregisterPlayer(player);
            player.sendMessage("You have left the queue.");
            return;
        }
        player.sendMessage("You are not in the Forgotten Battlegrounds.");
    }

    public synchronized void tryAddQueuedPlayersToRunningEvent() {
        if (runningEvent == null || !isEventRunning())
            return;

        int teamASize = runningEvent.getTeamPlayers(TeamType.BLUE).size();
        int teamBSize = runningEvent.getTeamPlayers(TeamType.RED).size();

        int diff = teamASize - teamBSize;

        if (diff == 0 && teamAWaiting.size() > 0 && teamBWaiting.size() > 0) {
            Player aPlayer = teamAWaiting.remove(0);
            Player bPlayer = teamBWaiting.remove(0);
            runningEvent.addParticipant(aPlayer, TeamType.BLUE);
            runningEvent.addParticipant(bPlayer, TeamType.RED);
            return;
        }

        if (diff < 0 && !teamAWaiting.isEmpty()) {
            Player aPlayer = teamAWaiting.remove(0);
            runningEvent.addParticipant(aPlayer, TeamType.BLUE);
        } else if (diff > 0 && !teamBWaiting.isEmpty()) {
            Player bPlayer = teamBWaiting.remove(0);
            runningEvent.addParticipant(bPlayer, TeamType.RED);
        }

        balanceFirstAvailable();
    }

    public void internalCheckStartConditions() {
        checkStartConditions();
    }

    public void onPlayerLogin(Player player) {
        if (runningEvent != null && runningEvent.isParticipant(player)) {
            // Increment leaves if still in progress
            if (runningEvent.isInProgress()) {
                ForgottenBattlegroundsDAO.getInstance().incrementLeaves(player.getObjectId());
            }
            runningEvent.removeParticipant(player);
            player.teleToLocation(83400, 148600, -3400);
            player.addRadar(83400, 148600, -3400);
        }
    }

    public void requestRespawn(Player player) {
        if (runningEvent != null && runningEvent.isParticipant(player)) {
            runningEvent.onRespawnRequestAccepted(player);
        }
    }
}
