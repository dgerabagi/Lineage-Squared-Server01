package l2ft.gameserver.network.l2.s2c;

/**
 * Close the CommandChannel Information window
 */
public class ExMPCCClose extends L2GameServerPacket
{
	public static final L2GameServerPacket STATIC = new ExMPCCClose();

	@Override
	protected void writeImpl()
	{
		writeEx(0x13);
	}
}
