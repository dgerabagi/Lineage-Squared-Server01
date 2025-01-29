package l2ft.gameserver.model.entity.events.impl;

import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.base.TeamType;
import l2ft.gameserver.utils.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * IslandAssaultTeam
 *
 * A direct clone of FGBTeam, but renamed for Island Assault usage.
 */
public class IslandAssaultTeam {
    private TeamType _teamType;
    private List<IslandAssaultTeamMember> _members = new CopyOnWriteArrayList<>();

    public IslandAssaultTeam(TeamType type) {
        _teamType = type;
    }

    public void addMember(Player player) {
        // Just like FGBTeam, store in an object wrapper
        _members.add(new IslandAssaultTeamMember(player));
    }

    public boolean contains(Player player) {
        for (IslandAssaultTeamMember m : _members)
            if (m.getPlayer() == player)
                return true;
        return false;
    }

    public void removeMember(Player player) {
        IslandAssaultTeamMember toRemove = null;
        for (IslandAssaultTeamMember m : _members) {
            if (m.getPlayer() == player) {
                toRemove = m;
                break;
            }
        }
        if (toRemove != null) {
            _members.remove(toRemove);
        }
    }

    /**
     * Teleports all team members to the given location.
     */
    public void portPlayersTo(Location loc) {
        for (IslandAssaultTeamMember m : _members) {
            Player p = m.getPlayer();
            if (p != null && !p.isTeleporting())
                p.teleToLocation(loc);
        }
    }

    /**
     * If needed, teleports them back to a stored original location.
     * In FGBTeam, we left this unimplemented. Same here.
     */
    public void portPlayersBack() {
        for (IslandAssaultTeamMember m : _members) {
            Player p = m.getPlayer();
            if (p != null) {
                // p.teleToLocation(originalLoc);
            }
        }
    }

    /**
     * Broadcast a chat message to all players on this team.
     */
    public void broadcastMessage(String msg) {
        for (IslandAssaultTeamMember m : _members) {
            Player p = m.getPlayer();
            if (p != null)
                p.sendMessage(msg);
        }
    }

    /**
     * Gather all dead players from this team.
     */
    public List<Player> getDeadPlayers() {
        List<Player> result = new ArrayList<>();
        for (IslandAssaultTeamMember m : _members) {
            Player p = m.getPlayer();
            if (p != null && p.isDead())
                result.add(p);
        }
        return result;
    }

    /**
     * Return a list of all players currently in this team.
     */
    public List<Player> getPlayers() {
        List<Player> result = new ArrayList<>();
        for (IslandAssaultTeamMember m : _members) {
            Player p = m.getPlayer();
            if (p != null)
                result.add(p);
        }
        return result;
    }

    public TeamType getTeamType() {
        return _teamType;
    }
}
