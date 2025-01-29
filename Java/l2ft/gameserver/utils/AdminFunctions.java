package l2ft.gameserver.utils;

import l2ft.gameserver.Config;
import l2ft.gameserver.instancemanager.CursedWeaponsManager;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.World;

public final class AdminFunctions
{
	public final static Location JAIL_SPAWN = new Location(-114648, -249384, -2984);
	
	private AdminFunctions() {}

	public static boolean kick(String player, String reason)
	{
		Player plyr = World.getPlayer(player);
		if (plyr == null)
			return false;

		return kick(plyr, reason);
	}


	public static boolean kick(Player player, String reason)
	{
		if(player.isCursedWeaponEquipped())
		{
			player.setPvpFlag(0);
			CursedWeaponsManager.getInstance().dropPlayer(player);
		}

		player.kick();

		return true;
	}
}
