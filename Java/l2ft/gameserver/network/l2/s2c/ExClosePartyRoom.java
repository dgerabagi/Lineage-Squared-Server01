package l2ft.gameserver.network.l2.s2c;

public class ExClosePartyRoom extends L2GameServerPacket
{
	public static L2GameServerPacket STATIC = new ExClosePartyRoom();

	@Override
	protected void writeImpl()
	{
		writeEx(0x09);
	}
}