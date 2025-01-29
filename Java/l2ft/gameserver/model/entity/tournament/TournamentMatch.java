package l2ft.gameserver.model.entity.tournament;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import l2ft.commons.threading.RunnableImpl;
import l2ft.commons.util.Rnd;
import l2ft.gameserver.ThreadPoolManager;
import l2ft.gameserver.instancemanager.ReflectionManager;
import l2ft.gameserver.listener.actor.OnDeathListener;
import l2ft.gameserver.model.Creature;
import l2ft.gameserver.model.GameObject;
import l2ft.gameserver.model.Party;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.Skill;
import l2ft.gameserver.model.base.InvisibleType;
import l2ft.gameserver.model.base.TeamType;
import l2ft.gameserver.model.entity.Reflection;
import l2ft.gameserver.model.instances.NpcInstance;
import l2ft.gameserver.network.l2.s2c.ExShowScreenMessage;
import l2ft.gameserver.network.l2.s2c.ExShowScreenMessage.ScreenMessageAlign;
import l2ft.gameserver.skills.TimeStamp;
import l2ft.gameserver.tables.SkillTable;
import l2ft.gameserver.taskmanager.AutoPotionsManager;
import l2ft.gameserver.utils.Location;

public class TournamentMatch extends Reflection
{
	private final Party _team1;
	private final Party _team2;
	private final Party[] _teams;
	private final TournamentType _type;
	private boolean _insideZone = false;
	private boolean _fightStarted = false;
	private boolean _fightFinished = false;
	private Location[] _choosenArena;
	private List<NpcInstance> _spawnedBuffers = new ArrayList<>();
	private DeathListener _deathListener = new DeathListener();
	private ScheduledFuture<?> _future;
	public static final int REFLECTION_ID = 500;
	public static final int BUFFER_ID = 36402;
	public static final Location[][] SPAWNS_2VS2 = {
		{new Location(130312, 58968, 3584), new Location(131179, 59752, 3536)},//skylancer
		{new Location(146904, -48504, -2272), new Location(148024, -48504, -2272)},//goddard throne
		{new Location(173624, 55384, -3464), new Location(175208, 55416, -3464)}//giants cave
		};
	public static final Location[][] SPAWNS_3VS3 = {
		{new Location(146952, -20568, -3440), new Location(146232, -18440, -3456)},//blazing swamp
		{new Location(41112, 183624, -3360), new Location(42744, 182536, -3376)}//Random Bridge (Near Giran Harbor)
		};
	public static final Location[][] SPAWNS_6VS6 = {
		{new Location(87000, -106280, -3320), new Location(84872, -106184, -3320)}//Archaic Labratory
		};
	public static final Location[][] SPAWNS_9VS9 = {
		{new Location(85576, 255416, -11664), new Location(85560, 258664, -11664)}//Garden Of Eva (Basement)
		};
	
	public TournamentMatch(Party team1, Party team2, TournamentType type)
	{
		_teams = new Party[2];
		_team1 = _teams[0] = team1;
		_team2 = _teams[1] = team2;
		_type = type;
		
		switch(type)
		{
		case match2:
			_choosenArena = Rnd.get(SPAWNS_2VS2);
			break;
		case match3:
			_choosenArena = Rnd.get(SPAWNS_3VS3);
			break;
		case match6:
			_choosenArena = Rnd.get(SPAWNS_6VS6);
			break;
		case match9:
			_choosenArena = Rnd.get(SPAWNS_9VS9);
			break;
		
		}
		
		startMatch();
	}
	
	public TournamentType getType()
	{
		return _type;
	}
	
	public Party getFirstTeam()
	{
		return _team1;
	}
	
	public Party getSecondTeam()
	{
		return _team2;
	}
	
	public boolean isInsideZone()
	{
		return _insideZone;
	}
	
	public boolean isFightStarted()
	{
		return _fightStarted;
	}
	
	private void startFight()
	{
		_fightStarted = true;
		_future = ThreadPoolManager.getInstance().schedule(new FinishFightTask(), 5*60000);
		
		ExShowScreenMessage startFightMsg = new ExShowScreenMessage("Match Started!", 2000,ScreenMessageAlign.TOP_CENTER, true);
		for(Party party : _teams)
			for(Player member : party.getPartyMembers())
			{
				member.sendPacket(startFightMsg);
				member.stopRooted();
			}
		for(NpcInstance buffer : _spawnedBuffers)
			buffer.deleteMe();
	}
	
	private void finishMatch(boolean closeThread)
	{
		Party winner = getWinner();
		Party loser = getOtherTeam(winner);
		
		managePoints(winner, true);
		managePoints(loser, false);
		
		for(Party team : _teams)
		{
			for(Player player : team.getPartyMembers())
			{
				player.sendMessage("You have now "+player.getTournamentPoints()+" Tournament Points!");
				player.setTournamentMatch(null);
				player.teleToLocation(new Location(83224, 149560, -3472));
				player.setReflection(ReflectionManager.DEFAULT);
				player.setTeam(TeamType.NONE);
				player.removeListener(_deathListener);
				if(player.isDead())
					player.doRevive();
				player.setCurrentHpMp(player.getMaxHp(), player.getMaxMp());
				player.setCurrentCp(player.getMaxCp());
			}
			team.setTournamentTeam(null);
		}
		TournamentManager.getInstance().removeMatch(getType(), this);
		_fightFinished = true;
		_future.cancel(false);
		for(GameObject o : _objects)
			if(o.isPlayer())
				if(o.getInvisibleType() == InvisibleType.NORMAL && !o.getPlayer().isGM())
					o.getPlayer().setInvisibleType(InvisibleType.NONE);
		collapse();
	}
	
	private void managePoints(Party party, boolean winner)
	{
		int partyCount = party.getMemberCount();
		int[] points = new int[partyCount];
		int[] ratios = new int[partyCount];
		for(int i = 0;i<partyCount;i++)
			points[i] = TournamentManager.getInstance().getPlayerPoints(party.getPartyMembers().get(i).getObjectId());
		
		double[] pointsToManage = new double[partyCount];
		int weakest = 10000;
		int totalPoints = 0;
		int teamRisk = 0;
		int totalRatio = 0;
		for(int i : points)
		{
			if(i<weakest)
				weakest = i;
		}
		for(int i = 0;i<ratios.length;i++)
			ratios[i] = (int) Math.floor(points[i]/weakest);
		  
		for(int i : points)
			totalPoints += i;
		
		teamRisk = (int) Math.floor(totalPoints/6);
		  
		for(int i : ratios)
			totalRatio += i;

		for(int i = 0;i<pointsToManage.length;i++)
			pointsToManage[i] = ratios[i]*teamRisk/(double)totalRatio;
		  
		double decimals = 0;
		for(double d : pointsToManage)
			for(int i = 0;i<1000;i++)
				if(i>=d && d<i+1)
				{
					decimals += i-d;
					break;
				}
		  
		int remainders = (int) Math.round(decimals) - 1;
		int remaindersGave = 0;
		  
		int[] sortedTeamPoints = new int[points.length];
		for(int i = 0;i<points.length;i++)
			sortedTeamPoints[i] = points[i];
		Arrays.sort(sortedTeamPoints);
		
		if(!winner)
		{
			int[] newArray = new int[points.length];
			int index = 0;
			for(int i = sortedTeamPoints.length-1;i>=0;i--)
			{
				newArray[index] = sortedTeamPoints[i];
				index++;
			}
			sortedTeamPoints = newArray;
		}
			  
		for(int i = 0;i<pointsToManage.length;i++)
		{
			pointsToManage[i] = Math.floor(pointsToManage[i]);
			if(pointsToManage[i] == 0)
			{
				remainders --;
				pointsToManage[i]++;
			}
		}
			  
		for(int i = 0;i<points.length;i++)
		{
			int toAdd = (int) Math.floor(pointsToManage[i]);
			if(remaindersGave<=remainders && (winner && points[i]<=sortedTeamPoints[remainders] || !winner && points[i]>=sortedTeamPoints[remainders]))
			{
				toAdd++;
				remaindersGave++;
			}
				  
			if(winner)
				points[i] += toAdd;
			else
				points[i] -= toAdd;
			TournamentManager.getInstance().updateTournamentPoints(party.getPartyMembers().get(i), points[i]);
		}
	}
	
	public Party getWinner()
	{
		Party winner;
		if(_team1 == null)
			winner = _team2;
		else if(_team2 == null)
			winner = _team1;
		else
		{
			int firstAliveMembers = _team1.getMemberCount();
			for(Player player : _team1)
				if(player.isDead())
					firstAliveMembers--;
			int secondAliveMembers = _team2.getMemberCount();
			for(Player player : _team2)
				if(player.isDead())
					secondAliveMembers--;
			if(firstAliveMembers < secondAliveMembers)
				winner = _team2;
			else
				winner = _team1;
		}
		return winner;
	}
	
	public boolean isPlayerAtTournament(Player player)
	{
		for(Party pt : _teams)
			if(pt.containsMember(player))
				return true;
		return false;
	}
	
	private Party getOtherTeam(Party team)
	{
		if(_teams[0] == team)
			return _teams[1];
		return _teams[0];
	}
	
	public void setSpawnedBuffers(List<NpcInstance> npcs)
	{
		_spawnedBuffers = npcs;
	}
	
	private void startMatch()
	{
		_insideZone = true;//Players will be no longer able to use potions
		
		for(Player firstMembers : _team1)
		{
			firstMembers.teleToLocation(Location.findPointToStay(_choosenArena[0].getX(), _choosenArena[0].getY(), _choosenArena[0].getZ(), 20, 50, firstMembers.getGeoIndex()), this);
		}
		for(Player firstMembers : _team2)
		{
			firstMembers.teleToLocation(Location.findPointToStay(_choosenArena[1].getX(), _choosenArena[1].getY(), _choosenArena[1].getZ(), 20, 50, firstMembers.getGeoIndex()), this);
		}
		
		
		_spawnedBuffers.add(addSpawnWithoutRespawn(BUFFER_ID, _choosenArena[0], 0));
		_spawnedBuffers.add(addSpawnWithoutRespawn(BUFFER_ID, _choosenArena[1], 0));

		for(Party party : _teams)
			for(Player member : party.getPartyMembers())
			{
				if(AutoPotionsManager.getInstance().playerUseAutoPotion(member))
					AutoPotionsManager.getInstance().removePlayer(member);
				member.setCurrentCp(member.getMaxCp());
				member.setCurrentHpMp(member.getMaxHp(), member.getMaxMp(), true);
				member.addListener(_deathListener);
				if(party == _team1)
					member.setTeam(TeamType.RED);
				else
					member.setTeam(TeamType.BLUE);
				member.startRooted();
				member.setTournamentMatch(this);
				member.getEffectList().stopAllEffects();
				for(TimeStamp sts : member.getSkillReuses())
				{
					if(sts == null)
						continue;
					Skill skill = SkillTable.getInstance().getInfo(sts.getId(), sts.getLevel());
					if(skill == null)
						continue;
					if(sts.getReuseBasic() <= 15 * 60000L)
						member.enableSkill(skill);
				}
			}
		
		new Thread(new FightStartTask(60)).start();
	}
	
	private class DeathListener implements OnDeathListener
	{
		@Override
		public void onDeath(Creature actor, Creature killer)
		{
			for(Party pt : _teams)
				if(pt == null)
				{
					finishMatch(false);
					return;
				}
			int firstAliveMembers = _team1.getMemberCount();
			for(Player player : _team1)
				if(player.isDead())
					firstAliveMembers--;
			if(firstAliveMembers == 0)
				finishMatch(true);
			else
			{
				firstAliveMembers = _team2.getMemberCount();
				for(Player member : _team2.getPartyMembers())
					if(member.isDead())
						firstAliveMembers--;
				
				if(firstAliveMembers== 0)
					finishMatch(true);
			}
		}
	}
	
	private class FinishFightTask extends RunnableImpl
	{
		@Override
		public void runImpl() throws Exception 
		{
			if(!_fightFinished)
			{
				boolean inFight = false;
				for(Party pt : _teams)
					if(pt == null)
					{
						inFight = false;
						break;
					}
					else
						for(Player member : pt.getPartyMembers())
							if(member.isInCombat())
								inFight = true;
				if(!inFight)
				{
					finishMatch(false);
				}
				else
					_future = ThreadPoolManager.getInstance().schedule(new FinishFightTask(), 60000);
			}
		}
	}
	
	private class FightStartTask extends RunnableImpl
	{
		private int _sec;
		private FightStartTask(int sec)
		{
			_sec = sec;
		}
		@Override
		public void runImpl() throws Exception
		{
			
			for(Party party : _teams)
				for(Player member : party.getPartyMembers())
					member.sendMessage("Fight will start in "+_sec+" seconds!");

			int nextDelay = 0;
			if(_sec == 30 || _sec == 60)
			{
				nextDelay = _sec = _sec/2;
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
				startFight();
			else
			{
				ThreadPoolManager.getInstance().schedule(new FightStartTask(_sec), nextDelay*1000);
			}
		}
	}
}
