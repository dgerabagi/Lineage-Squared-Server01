package l2ft.gameserver.model.entity.tournament;

public enum TournamentType
{
	
	match2(true, false, 2, 3),
	match3(true, false, 3, 3),
	match6(false, false, 6, 3),
	match9(false, true, 9, 2);
		
	private TournamentType(boolean buffer, boolean mana, int teamSize, int teamsToFight)
	{
		_buffer = buffer;
		_mana = mana;
		_teamSize = teamSize;
		_teamsToFight = teamsToFight;
	}
	
	private final boolean _buffer;
	private final boolean _mana;
	private final int _teamSize;
	private final int _teamsToFight;
	
	public boolean getBuffer()
	{
		return _buffer;
	}
	
	public boolean getMana()
	{
		return _mana;
	}
	
	public int getTeamSize()
	{
		return _teamSize;
	}
	
	public int getTeamsToFight()
	{
		return _teamsToFight;
	}
}
