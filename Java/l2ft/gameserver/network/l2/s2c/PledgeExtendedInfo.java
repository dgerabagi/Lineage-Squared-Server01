package l2ft.gameserver.network.l2.s2c;

public class PledgeExtendedInfo extends L2GameServerPacket
{
	@Override
	protected final void writeImpl()
	{
		writeC(0x8A);
		//TODO SddSddddddddSd
	}
}