package l2ft.gameserver.network.l2.s2c;

public class PetitionVote extends L2GameServerPacket
{
	@Override
	protected void writeImpl()
	{
		// just trigger
		writeC(0xFC);
	}
}