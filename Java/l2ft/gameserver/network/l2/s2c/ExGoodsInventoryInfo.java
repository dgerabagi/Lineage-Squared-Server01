package l2ft.gameserver.network.l2.s2c;

import l2ft.gameserver.model.Player;

/**
 * @author VISTALL
 * @date 23:37/23.03.2011
 */
public class ExGoodsInventoryInfo extends L2GameServerPacket
{

	public ExGoodsInventoryInfo(Player player)
	{

	}

	@Override
	protected void writeImpl()
	{
		/*
		* 203DA858   PUSH Engine.205127AC                      ASCII "QdSSQccSSh"
203DA8D0   PUSH Engine.20506EFC                      ASCII "dd"*/
	}
}
