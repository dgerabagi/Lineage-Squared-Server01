package l2ft.gameserver.network.l2.s2c;

public class SkillRemainSec extends L2GameServerPacket
{
	@Override
	protected void writeImpl()
	{
		writeC(0xD8);
		//TODO ddddddd
	}
}