package l2ft.gameserver.network.l2.s2c;

public class FlySelfDestination extends L2GameServerPacket
{
	@Override
	protected void writeImpl()
	{
		writeEx(0x43);
		// TODO dddd
	}
}