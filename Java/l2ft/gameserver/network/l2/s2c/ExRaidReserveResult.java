package l2ft.gameserver.network.l2.s2c;

public class ExRaidReserveResult extends L2GameServerPacket
{
	@Override
	protected void writeImpl()
	{
		writeEx(0xB6);
		// TODO dx[dddd]
	}
}