package l2ft.gameserver.listener.actor.player;

import l2ft.gameserver.listener.PlayerListener;
import l2ft.gameserver.model.Player;

public interface OnPlayerEnterListener extends PlayerListener
{
	public void onPlayerEnter(Player player);
}
