package l2ft.gameserver.network.l2.s2c;

public class ExBR_RecentProductListPacket extends L2GameServerPacket
{
	@Override
	protected void writeImpl()
	{
		writeEx(0xDC);
		// TODO dx[dhddddcccccdd]
	}
}