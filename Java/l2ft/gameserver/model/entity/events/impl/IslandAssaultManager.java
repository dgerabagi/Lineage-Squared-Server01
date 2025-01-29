package l2ft.gameserver.model.entity.events.impl;

import l2ft.commons.collections.MultiValueSet;
import l2ft.gameserver.data.xml.holder.InstantZoneHolder;
import l2ft.gameserver.instancemanager.ReflectionManager;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.base.TeamType;
import l2ft.gameserver.model.entity.Reflection;
import l2ft.gameserver.templates.InstantZone;
import l2ft.gameserver.utils.Location;
import l2ft.gameserver.dao.IslandAssaultDAO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * IslandAssaultManager
 *
 * A new manager class for the Island Assault battleground, similar
 * to the ForgottenBattlegroundsManager. It holds queue lists for
 * Team A and Team B, starts the event, and cleans up after it ends.
 */
public class IslandAssaultManager {
    private static final IslandAssaultManager _instance = new IslandAssaultManager();

    public static IslandAssaultManager getInstance() {
        return _instance;
    }

    private static final Logger _log = LoggerFactory.getLogger(IslandAssaultManager.class);

    private List<Player> teamAWaiting = new CopyOnWriteArrayList<>();
    private List<Player> teamBWaiting = new CopyOnWriteArrayList<>();

    private IslandAssaultEvent runningEvent;
    private boolean eventRunning = false;

    private static final int MIN_PLAYERS_PER_TEAM = 1;

    public synchronized void registerPlayer(Player player, int chosenTeam) {
        if (!canJoinEvent(player)) {
            player.sendMessage("You do not meet the requirements to join Island Assault.");
            return;
        }
        unregisterPlayer(player);
        _log.info(
                "IslandAssaultManager: Player " + player.getName() + " is registering for team choice: " + chosenTeam);

        switch (chosenTeam) {
            case 1:
                teamAWaiting.add(player);
                player.sendMessage("You have been added to the Island Assault [Team A] waiting list.");
                break;
            case 2:
                teamBWaiting.add(player);
                player.sendMessage("You have been added to the Island Assault [Team B] waiting list.");
                break;
            default:
                player.sendMessage("Invalid team choice for Island Assault.");
                return;
        }

        checkStartConditions();
    }

    public synchronized void unregisterPlayer(Player player) {
        teamAWaiting.remove(player);
        teamBWaiting.remove(player);

        if (runningEvent != null && runningEvent.isParticipant(player)) {
            if (runningEvent.isInProgress()) {
                IslandAssaultDAO.getInstance().incrementLeaves(player.getObjectId());
            }
            runningEvent.removeParticipant(player);
        }
    }

    public void onPlayerLogout(Player player) {
        if (runningEvent != null && runningEvent.isParticipant(player)) {
            if (runningEvent.isInProgress()) {
                IslandAssaultDAO.getInstance().incrementLeaves(player.getObjectId());
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
        return teamAWaiting.contains(player) || teamBWaiting.contains(player);
    }

    private void checkStartConditions() {
        if (eventRunning)
            return;

        int teamACount = teamAWaiting.size();
        int teamBCount = teamBWaiting.size();

        _log.info(
                "IslandAssaultManager: Checking start conditions. Team A: " + teamACount + " , Team B: " + teamBCount);

        if (teamACount >= MIN_PLAYERS_PER_TEAM && teamBCount >= MIN_PLAYERS_PER_TEAM) {
            startEvent();
        } else {
            _log.info("IslandAssaultManager: Not enough players on each team to start Island Assault.");
        }
    }

    private void startEvent() {
        _log.info("IslandAssaultManager: Attempting to start the Island Assault event...");
        eventRunning = true;

        Reflection reflection = new Reflection();
        InstantZone instantZone = InstantZoneHolder.getInstance().getInstantZone(173);
        if (instantZone == null) {
            _log.error("IslandAssaultManager: No InstantZone found for ID 173!");
            return;
        }
        reflection.init(instantZone);

        runningEvent = new IslandAssaultEvent(reflection);
        runningEvent.assignPlayersToTeams(teamAWaiting, teamBWaiting);

        teamAWaiting.clear();
        teamBWaiting.clear();

        runningEvent.startEvent();
        _log.info("IslandAssaultManager: Island Assault event started successfully!");
    }

    public void onEventEnd() {
        eventRunning = false;
        runningEvent = null;
        _log.info("IslandAssaultManager: Island Assault event has ended. Manager reset done.");
    }

    public boolean isEventRunning() {
        return eventRunning;
    }

    public void requestLeaveEvent(Player player) {
        // 1) If the player is in the running event:
        if (runningEvent != null && runningEvent.isParticipant(player)) {
            if (runningEvent.isInProgress()) {
                IslandAssaultDAO.getInstance().incrementLeaves(player.getObjectId());
            }
            runningEvent.removeParticipant(player);

            player.teleToLocation(new Location(83400, 148600, -3400), ReflectionManager.DEFAULT);
            player.setTeam(TeamType.NONE);

            // REMOVED redundant player.removeEvent(runningEvent);
            // Because removeParticipant(player) already calls player.removeEvent(this).

            player.sendMessage("You have left Island Assault.");
            return;
        }

        // 2) If the player is just in queue (not in the event yet):
        if (isPlayerInQueue(player)) {
            unregisterPlayer(player);
            player.sendMessage("You have left the Island Assault queue.");
            return;
        }

        // 3) Otherwise:
        player.sendMessage("You are not in Island Assault.");
    }

    public synchronized void tryAddQueuedPlayersToRunningEvent() {
        if (runningEvent == null || !isEventRunning())
            return;

        int teamASize = runningEvent.getTeamPlayers(TeamType.BLUE).size();
        int teamBSize = runningEvent.getTeamPlayers(TeamType.RED).size();

        int diff = teamASize - teamBSize;

        if (diff == 0 && !teamAWaiting.isEmpty() && !teamBWaiting.isEmpty()) {
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
    }

    public void internalCheckStartConditions() {
        checkStartConditions();
    }

    public void onPlayerLogin(Player player) {
        if (runningEvent != null && runningEvent.isParticipant(player)) {
            IslandAssaultDAO.getInstance().incrementLeaves(player.getObjectId());
            runningEvent.removeParticipant(player);
            player.teleToLocation(83400, 148600, -3400);
            player.setTeam(TeamType.NONE);
        }
    }

    public void requestRespawn(Player player) {
        if (runningEvent != null && runningEvent.isParticipant(player)) {
            runningEvent.onRespawnRequestAccepted(player);
        }
    }

    public boolean canJoinEvent(Player player) {
        return player.getLevel() >= 85;
    }

    public IslandAssaultEvent getRunningEvent() {
        return runningEvent;
    }
}
