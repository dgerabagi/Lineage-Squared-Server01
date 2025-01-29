package l2ft.gameserver.network.l2.s2c;

public class ExEventMatchMessage extends L2GameServerPacket
{
	@Override
	protected void writeImpl()
	{
		writeEx(0x0F);
		// TODO cS
	}
}