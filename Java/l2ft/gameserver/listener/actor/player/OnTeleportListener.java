package l2ft.gameserver.listener.actor.player;

import l2ft.gameserver.listener.PlayerListener;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.entity.Reflection;

public interface OnTeleportListener extends PlayerListener
{
	public void onTeleport(Player player, int x, int y, int z, Reflection reflection);
}
