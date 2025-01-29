package l2ft.gameserver.network.l2.s2c;

public class PledgeSkillListAdd extends L2GameServerPacket
{
	private int _skillId;
	private int _skillLevel;

	public PledgeSkillListAdd(int skillId, int skillLevel)
	{
		_skillId = skillId;
		_skillLevel = skillLevel;
	}

	@Override
	protected final void writeImpl()
	{
		writeEx(0x3b);
		writeD(_skillId);
		writeD(_skillLevel);
	}
}