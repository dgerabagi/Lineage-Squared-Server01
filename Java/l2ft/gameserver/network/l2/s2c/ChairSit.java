package l2ft.gameserver.network.l2.s2c;

import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.instances.StaticObjectInstance;

/**
 * format: d
 */
public class ChairSit extends L2GameServerPacket
{
	private int _objectId;
	private int _staticObjectId;

	public ChairSit(Player player, StaticObjectInstance throne)
	{
		_objectId = player.getObjectId();
		_staticObjectId = throne.getUId();
	}

	@Override
	protected final void writeImpl()
	{
		writeC(0xed);
		writeD(_objectId);
		writeD(_staticObjectId);
	}
}