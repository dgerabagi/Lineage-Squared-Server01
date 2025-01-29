package l2ft.gameserver.model.entity.events.impl;

import l2ft.gameserver.model.Player;

/**
 * IslandAssaultTeamMember
 *
 * A simple wrapper similar to FGBTeamMember, but for Island Assault.
 */
public class IslandAssaultTeamMember {
    private Player _player;

    public IslandAssaultTeamMember(Player player) {
        _player = player;
    }

    public Player getPlayer() {
        return _player;
    }
}
