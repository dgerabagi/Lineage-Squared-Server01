package l2ft.gameserver.model.entity.events.impl;

import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.base.TeamType;
import l2ft.gameserver.utils.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class FGBTeam {
    private TeamType _teamType;
    private List<FGBTeamMember> _members = new CopyOnWriteArrayList<>();

    public FGBTeam(TeamType type) {
        _teamType = type;
    }

    public void addMember(Player player) {
        // Check class/slot logic here
        _members.add(new FGBTeamMember(player));
    }

    public boolean contains(Player player) {
        for (FGBTeamMember m : _members)
            if (m.getPlayer() == player)
                return true;
        return false;
    }

    public void removeMember(Player player) {
        FGBTeamMember toRemove = null;
        for (FGBTeamMember m : _members) {
            if (m.getPlayer() == player) {
                toRemove = m;
                break;
            }
        }
        if (toRemove != null) {
            _members.remove(toRemove); // This is safe on a CopyOnWriteArrayList.
        }
    }

    public void portPlayersTo(Location loc) {
        for (FGBTeamMember m : _members) {
            Player p = m.getPlayer();
            if (p != null && !p.isTeleporting())
                p.teleToLocation(loc);
        }
    }

    public void portPlayersBack() {
        // Teleport them back to original location if stored somewhere.
        // This can be implemented by storing their original coords before event.
        for (FGBTeamMember m : _members) {
            Player p = m.getPlayer();
            if (p != null) {
                // p.teleToLocation(...) original saved location
            }
        }
    }

    public void broadcastMessage(String msg) {
        for (FGBTeamMember m : _members) {
            Player p = m.getPlayer();
            if (p != null)
                p.sendMessage(msg);
        }
    }

    public List<Player> getDeadPlayers() {
        List<Player> result = new ArrayList<>();
        for (FGBTeamMember m : _members) {
            Player p = m.getPlayer();
            if (p != null && p.isDead())
                result.add(p);
        }
        return result;
    }

    public List<Player> getPlayers() {
        List<Player> result = new ArrayList<>();
        for (FGBTeamMember m : _members) {
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
