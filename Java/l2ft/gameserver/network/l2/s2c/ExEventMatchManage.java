package l2ft.gameserver.network.l2.s2c;

public class ExEventMatchManage extends L2GameServerPacket
{
	@Override
	protected void writeImpl()
	{
		writeEx(0x30);
		// TODO dccScScd[ccdSdd]
	}
}