package l2ft.gameserver.network.l2.s2c;

public class ServerClose extends L2GameServerPacket
{
	public static final L2GameServerPacket STATIC = new ServerClose();

	@Override
	protected void writeImpl()
	{
		writeC(0x20);
	}
}