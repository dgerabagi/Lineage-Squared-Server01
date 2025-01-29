package l2ft.gameserver.model.entity.events.impl;

import l2ft.gameserver.model.Player;

public class FGBTeamMember {
    private Player _player;

    public FGBTeamMember(Player player) {
        _player = player;
    }

    public Player getPlayer() {
        return _player;
    }
}
