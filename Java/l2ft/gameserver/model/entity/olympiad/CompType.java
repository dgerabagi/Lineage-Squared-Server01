package l2ft.gameserver.model.entity.olympiad;

import l2ft.gameserver.Config;

public enum CompType
{
	TEAM2(2, Config.ALT_OLY_TEAM_RITEM_C, 5, true, 2),
	TEAM4(2, Config.ALT_OLY_NONCLASSED_RITEM_C, 5, false, 4),
	TEAM6(2, Config.ALT_OLY_CLASSED_RITEM_C, 5, false, 6);

	private int _minSize;
	private int _reward;
	private int _looseMult;
	private boolean _hasBuffer;
	private int _teamSize;

	private CompType(int minSize, int reward, int looseMult, boolean hasBuffer, int teamSize)
	{
		_minSize = minSize;
		_reward = reward;
		_looseMult = looseMult;
		_hasBuffer = hasBuffer;
		_teamSize = teamSize;
	}

	public int getMinSize()
	{
		return _minSize;
	}

	public int getReward()
	{
		return _reward;
	}

	public int getLooseMult()
	{
		return _looseMult;
	}

	public boolean hasBuffer()
	{
		return _hasBuffer;
	}
	
	public int getTeamSize()
	{
		return _teamSize;
	}
}