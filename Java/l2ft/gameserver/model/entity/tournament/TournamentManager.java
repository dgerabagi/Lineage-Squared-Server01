package l2ft.gameserver.model.entity.tournament;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringTokenizer;

import l2ft.commons.dbutils.DbUtils;
import l2ft.commons.threading.RunnableImpl;
import l2ft.commons.util.Rnd;
import l2ft.gameserver.ThreadPoolManager;
import l2ft.gameserver.database.DatabaseFactory;
import l2ft.gameserver.model.GameObjectsStorage;
import l2ft.gameserver.model.Party;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.utils.ReflectionUtils;

public class TournamentManager
{
	private List<TournamentTeam> _teams = new ArrayList<TournamentTeam>();
	private final Map<TournamentType, List<Party>> _waitingList;
	private final Map<TournamentType, List<TournamentMatch>> _matches;
	private static TournamentManager _instance;
	private boolean _tournamentStarted = false;
	private static final String TOURNAMENT_POINTS_NAME = "tournamentPoints";
	private static final int STARTING_POINTS = 20;
	
	private TournamentManager()
	{
		_waitingList = new HashMap<>();
		for(TournamentType type : TournamentType.values())
		{
			List<Party> teams = new ArrayList<>();
			_waitingList.put(type, teams);
		}
		_matches = new HashMap<>();
		for(TournamentType type : TournamentType.values())
		{
			List<TournamentMatch> teams = new ArrayList<>();
			_matches.put(type, teams);
		}
	}
	
	public void startTournament()
	{
		_tournamentStarted = true;

		ThreadPoolManager.getInstance().scheduleAtFixedRate(new CheckForMatches(), 60000, 60000);
		loadTeams();
	}
	
	public boolean isTournamentStarted()
	{
		return _tournamentStarted;
	}
	
	public void signTeam(Party team, TournamentType type)
	{
		team.setTournamentTeam(findMyTeam(team.getPartyLeader()));
		if(matchConditionsPassed(team, type))
		{
			_waitingList.get(type).add(team);
			for(Player member : team.getPartyMembers())
				member.sendMessage("Party has been signed for Tournament!");
		}
		else
			team.setTournamentTeam(null);
	}
	
	public List<TournamentTeam> getTeams()
	{
		return _teams;
	}
	
	public TournamentTeam findMyTeam(Player player)
	{
		for(TournamentTeam team : _teams)
			for(int memberId : team.getMembers())
				if(player.getObjectId() == memberId)
					return team;
		return null;
	}
	
	private class CheckForMatches extends RunnableImpl
	{
		@Override
		public void runImpl() throws Exception 
		{
			for(Entry<TournamentType, List<Party>> entry : _waitingList.entrySet())
			{
				List<Party> teams = entry.getValue();
				if(teams.size()>=entry.getKey().getTeamsToFight())
				{
					Party firstTeam = Rnd.get(teams);
					Party secondTeam;
					while((secondTeam=Rnd.get(teams)) == firstTeam || firstTeam.getTournamentTeam() == secondTeam.getTournamentTeam());
					
					Party[] teamArray = {firstTeam, secondTeam};

					for(Party team : teamArray)
						_waitingList.get(entry.getKey()).remove(team);
					
					new Thread(new TeleportTask(teamArray, entry.getKey(), 30)).start();
				}
			}
		}
	}
	
	public void removeMatch(TournamentType type, TournamentMatch match)
	{
		_matches.get(type).remove(match);
	}
	
	public boolean isPlayerAtTournamentStart(Player player)
	{
		if(!isTournamentStarted())
			return false;
		
		if(player.getTournamentMatch() != null && player.getTournamentMatch().isInsideZone())
			return true;
		return false;
	}
	
	public boolean canPlayerHit(Player player)
	{
		if(!isTournamentStarted())
			return true;
		
		if(player.getTournamentMatch() != null && !player.getTournamentMatch().isFightStarted())
			return false;
		return true;
	}
	
	public boolean matchConditionsPassed(Party party, TournamentType type)
	{
		String msg = "";
		if(party == null)
			return false;
		if(party.getPartyLeader() == null)
			return false;
		TournamentTeam leaderTeam = findMyTeam(party.getPartyLeader());
		
		if(party.getMemberCount() != type.getTeamSize())
			msg = "Party size needs to be "+type.getTeamSize()+"!";
		else if(party.getTournamentTeam() == null)
			msg = "Party dont have any TournamentTeam!";
		else if(leaderTeam == null)
			msg = "Party Leader needs to be in a team!";
		else if(party.getMemberCountInRange(party.getPartyLeader(), 1000) != party.getMemberCount())
			msg = "Every member of the party needs to be near Party Leader!";
		if(msg.isEmpty())
			for(Player member : party.getPartyMembers())
			{
				if(findMyTeam(member) != leaderTeam)
				{
					msg = "Every Party member needs to be in same team!";
					break;
				}
				if(member.getLevel()<80)
				{
					msg = "Every member needs to be at least level 80!";
					break;
				}
				if(member.getPet() != null)
				{
					msg = "Players cannot have pet/summon while before fight!";
					break;
				}
			}
		
		if(!msg.isEmpty())
		{
			for(Player member : party.getPartyMembers())
				member.sendMessage("Party was removed from waiting list because "+msg);
			return false;
		}
		return true;
	}
	
	public int getPlayerPoints(int objId)
	{
		Player player = GameObjectsStorage.getPlayer(objId);
		if(player == null)
			return loadPlayerPoints(objId);
		else if(player.getTournamentPoints() == -1000)
		{
			int points = loadPlayerPoints(objId);
			player.setTournamentPoints(points);
		}
		return player.getTournamentPoints();
	}
	
	public int loadPlayerPoints(int objId)
	{
		int points = STARTING_POINTS;
		Connection con = null;
		PreparedStatement offline = null;
		ResultSet rs = null;
		try
		{
			con = DatabaseFactory.getInstance().getConnection();
			offline = con.prepareStatement("SELECT value FROM character_variables WHERE obj_id = ? AND name=?");
			offline.setInt(1, objId);
			offline.setString(2, TOURNAMENT_POINTS_NAME);
			rs = offline.executeQuery();
			if(rs.next())
			{
				String value = rs.getString("value");
				points = Integer.parseInt(value);
			}
		}
		catch(Exception e)
		{
			System.out.println(e);
		}
		finally
		{
			DbUtils.closeQuietly(con, offline, rs);
		}
		return points;
	}
	
	public void updateTournamentPoints(Player player, int newPoints)
	{
		player.setVar(TOURNAMENT_POINTS_NAME, newPoints, -1);
		player.setTournamentPoints(newPoints);
	}
	
	public List<Party> getWaitingList(TournamentType type)
	{
		return _waitingList.get(type);
	}
	
	public List<TournamentMatch> getMatches(TournamentType type)
	{
		return _matches.get(type);
	}
	

	
	private class TeleportTask extends RunnableImpl
	{
		private int _sec;
		private Party[] _teams;
		private TournamentType _type;
		private TeleportTask(Party[] teams, TournamentType type, int sec)
		{
			_sec = sec;
			_teams = teams;
			_type = type;
		}
		@Override
		public void runImpl() throws Exception
		{
			for(Party team : _teams)
				if(!TournamentManager.getInstance().matchConditionsPassed(team, _type))
				{
					for(Party party : _teams)
					{
						party.setTournamentTeam(null);
						for(Player member : party.getPartyMembers())
						{
							member.sendMessage("Match has been cancelled!");
							member.setTournamentMatch(null);
						}
					}
					return;
				}
			
			for(Party party : _teams)
				for(Player member : party.getPartyMembers())
					member.sendMessage("You are going to be teleported in "+_sec+" seconds!");
			
			int nextDelay = 0;
			if(_sec == 30)
			{
				nextDelay = _sec = 15;
			}
			else if(_sec == 15 || _sec == 10)
			{
				_sec = _sec - 5;
				nextDelay = 5;
			}
			else if(_sec > 0)
			{
				_sec --;
				nextDelay = 1;
			}

			if(nextDelay == 0)
			{
				startMatch(_teams, _type);
			}
			else
			{
				ThreadPoolManager.getInstance().schedule(new TeleportTask(_teams, _type, _sec), nextDelay*1000);
			}
		}
	}
	
	private List<Integer> getMembersFromDatabase(String membersString)
	{
		List<Integer> ids = new ArrayList<>();
		StringTokenizer st = new StringTokenizer(membersString, ";");
		while(st.hasMoreTokens())
		{
			String nextToken = st.nextToken();
			if(!nextToken.isEmpty())
				ids.add(Integer.parseInt(nextToken));
		}
		return ids;
	}
	
	public void startMatch(Party[] teams, TournamentType type)
	{
		TournamentMatch match = new TournamentMatch(teams[0], teams[1], type);
		for(Party pt : teams)
			for(Player member : pt.getPartyMembers())
				ReflectionUtils.enterReflection(member, match, TournamentMatch.REFLECTION_ID);
		
		_matches.get(type).add(match);
		
	}

	private void loadTeams()
	{
		Connection con = null;
		PreparedStatement offline = null;
		ResultSet rs = null;
		try
		{
			con = DatabaseFactory.getInstance().getConnection();
			offline = con.prepareStatement("SELECT * FROM tournament_teams");
			rs = offline.executeQuery();
			while(rs.next())
			{
				String name = rs.getString("TeamName");
				int leaderId = rs.getInt("LeaderId");
				TournamentTeam team = new TournamentTeam(name);
				team.setLeader(leaderId);
				List<Integer> members = getMembersFromDatabase(rs.getString("MembersIds"));
				for(int id : members)
					team.addMember(id);
				_teams.add(team);
			}
		}
		catch(Exception e)
		{
			System.out.println(e);
		}
		finally
		{
			DbUtils.closeQuietly(con, offline, rs);
		}
	}
	
	private String getStringForMembersDatabase(TournamentTeam team)
	{
		String text = "";
		for(int id : team.getMembers())
			text += id+";";
		text = text.substring(0, text.length()-1);
		return text;
	}
	
	public void saveTeams()
	{
		Connection con = null;
		PreparedStatement statement = null;
		try
		{
			con = DatabaseFactory.getInstance().getConnection();
			for(TournamentTeam team : _teams)
			{
				statement = con.prepareStatement("REPLACE INTO tournament_teams (TeamName, LeaderId, MembersIds) VALUES(?,?,?)");
				statement.setString(1, team.getName());
				statement.setInt(2, team.getLeaderId());
				statement.setString(3, getStringForMembersDatabase(team));
				statement.execute();
				DbUtils.close(statement);
			}
		}
		catch (Exception e)
		{
			System.out.println(e);
		}
		finally
		{
			DbUtils.closeQuietly(con, statement);
		}
	}
	
	public void removeTeam(TournamentTeam team)
	{
		Connection con = null;
		PreparedStatement statement = null;
		try
		{
			con = DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement("DELETE FROM tournament_teams WHERE TeamName=?");
			statement.setString(1, team.getName());
			statement.execute();
		}
		catch(Exception e)
		{
			System.out.println(e);
		}
		finally
		{
			DbUtils.closeQuietly(con, statement);
		}
	}
	
	public static TournamentManager getInstance()
	{
		if(_instance == null)
			_instance = new TournamentManager();
		return _instance;
	}
}
