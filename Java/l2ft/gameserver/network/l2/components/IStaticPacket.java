package l2ft.gameserver.network.l2.components;

import l2ft.gameserver.model.Player;
import l2ft.gameserver.network.l2.s2c.L2GameServerPacket;

public interface IStaticPacket
{
	L2GameServerPacket packet(Player player);
}
