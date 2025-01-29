package l2ft.gameserver.model.entity.olympiad;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;

import l2ft.commons.lang.ArrayUtils;
import l2ft.gameserver.Config;
import l2ft.gameserver.cache.Msg;
import l2ft.gameserver.dao.OlympiadNobleDAO;
import l2ft.gameserver.data.xml.holder.InstantZoneHolder;
import l2ft.gameserver.model.Party;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.entity.Reflection;
import l2ft.gameserver.model.instances.DoorInstance;
import l2ft.gameserver.model.instances.NpcInstance;
import l2ft.gameserver.model.items.ItemInstance;
import l2ft.gameserver.model.quest.QuestState;
import l2ft.gameserver.network.l2.components.IStaticPacket;
import l2ft.gameserver.network.l2.components.SystemMsg;
import l2ft.gameserver.network.l2.s2c.ExOlympiadUserInfo;
import l2ft.gameserver.network.l2.s2c.ExReceiveOlympiad;
import l2ft.gameserver.network.l2.s2c.L2GameServerPacket;
import l2ft.gameserver.network.l2.s2c.SystemMessage2;
import l2ft.gameserver.scripts.Functions;
import l2ft.gameserver.templates.InstantZone;
import l2ft.gameserver.templates.StatsSet;
import l2ft.gameserver.utils.Log;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OlympiadGame
{
	private static final Logger _log = LoggerFactory.getLogger(OlympiadGame.class);

	public static final int MAX_POINTS_LOOSE = 10;

	public boolean validated = false;

	private int _winner = 0;
	private int _state = 0;

	private int _id;
	private Reflection _reflection;
	private CompType _type;

	private OlympiadTeam _team1;
	private OlympiadTeam _team2;

	private List<Player> _spectators = new CopyOnWriteArrayList<Player>();

	public OlympiadGame(int id, CompType type, List<Integer> opponents)
	{
		_type = type;
		_id = id;
		_reflection = new Reflection();
		InstantZone instantZone = InstantZoneHolder.getInstance().getInstantZone(getOlympiadStadion(type));
		_reflection.init(instantZone);

		_team1 = new OlympiadTeam(this, 1);
		_team2 = new OlympiadTeam(this, 2);
		for(int i = 0; i < opponents.size() / 2; i++)
			_team1.addMember(opponents.get(i));

		for(int i = opponents.size() / 2; i < opponents.size(); i++)
			_team2.addMember(opponents.get(i));
		Log.add("Olympiad System: Game - " + id + ": " + _team1.getName() + " Vs " + _team2.getName(), "olympiad");
	}
	
	private int getOlympiadStadion(CompType type)
	{
		switch(type)
		{
		case TEAM2:
			return 148;
		case TEAM4:
			return 150;
		case TEAM6:
			return 147;
			default:
				return 147;//not used
		}
	}

	private String getBufferSpawnGroup(int instancedZoneId)
	{
		String bufferGroup = null;
		switch(instancedZoneId)
		{
			case 147:
				bufferGroup = "olympiad_147_buffers";
				break;
			case 148:
				bufferGroup = "olympiad_148_buffers";
				break;
			case 149:
				bufferGroup = "olympiad_149_buffers";
				break;
			case 150:
				bufferGroup = "olympiad_150_buffers";
				break;
		}
		return bufferGroup;
	}

	public void addBuffers()
	{
		if(!_type.hasBuffer())
			return;

		if(getBufferSpawnGroup(_reflection.getInstancedZoneId()) != null)
			_reflection.spawnByGroup(getBufferSpawnGroup(_reflection.getInstancedZoneId()));
	}

	public void deleteBuffers()
	{
		_reflection.despawnByGroup(getBufferSpawnGroup(_reflection.getInstancedZoneId()));
	}

	public void managerShout()
	{
		for(NpcInstance npc : Olympiad.getNpcs())
		{
			String npcString;
			switch(_type)
			{
				case TEAM2:
					npcString = "Olympiad 2vs2 Team Match is going to begin in arena "+(_id+1)+" in a moment!";
					break;
				case TEAM4:
					npcString = "Olympiad 4vs4 Team Match is going to begin in arena "+(_id+1)+" in a moment!";
					break;
				case TEAM6:
					npcString = "Olympiad 6vs6 Team Match is going to begin in arena "+(_id+1)+" in a moment!";
					break;
				default:
					continue;
			}
			Functions.npcShout(npc, npcString);
		}
	}

	public void portPlayersToArena()
	{
		_team1.portPlayersToArena();
		_team2.portPlayersToArena();
	}

	public void preparePlayers()
	{
		_team1.preparePlayers();
		_team2.preparePlayers();
	}

	public void portPlayersBack()
	{
		_team1.portPlayersBack();
		_team2.portPlayersBack();
	}

	public void collapse()
	{
		portPlayersBack();
		clearSpectators();
		_reflection.collapse();
	}

	public void validateWinner(boolean aborted) throws Exception
	{
		int state = _state;
		_state = 0;

		if(validated)
		{
			Log.add("Olympiad Result: " + _team1.getName() + " vs " + _team2.getName() + " ... double validate check!!!", "olympiad");
			return;
		}
		validated = true;

		// Если игра закончилась до телепортации на стадион, то забираем очки у вышедших из игры, не засчитывая никому победу
		if(state < 1 && aborted)
		{
			_team1.takePointsForCrash();
			_team2.takePointsForCrash();
			broadcastPacket(Msg.THE_GAME_HAS_BEEN_CANCELLED_BECAUSE_THE_OTHER_PARTY_ENDS_THE_GAME, true, false);
			return;
		}

		boolean teamOneCheck = _team1.checkPlayers();
		boolean teamTwoCheck = _team2.checkPlayers();

		if(_winner <= 0)
			if(!teamOneCheck && !teamTwoCheck)
				_winner = 0;
			else if(!teamTwoCheck)
				_winner = 1; // Выиграла первая команда
			else if(!teamOneCheck)
				_winner = 2; // Выиграла вторая команда
			else if(_team1.getDamage() < _team2.getDamage()) // Вторая команда нанесла вреда меньше, чем первая
				_winner = 1; // Выиграла первая команда
			else if(_team1.getDamage() > _team2.getDamage()) // Вторая команда нанесла вреда больше, чем первая
				_winner = 2; // Выиграла вторая команда

		if(_winner == 1) // Выиграла первая команда
			winGame(_team1, _team2);
		else if(_winner == 2) // Выиграла вторая команда
			winGame(_team2, _team1);
		else
			tie();

		_team1.saveNobleData();
		_team2.saveNobleData();

		broadcastRelation();
		broadcastPacket(new SystemMessage2(SystemMsg.YOU_WILL_BE_MOVED_BACK_TO_TOWN_IN_S1_SECONDS).addInteger(20), true, true);
	}
	private double[] _pointsToManage;
	
	public void winGame(OlympiadTeam winnerTeam, OlympiadTeam looseTeam)
	{
		ExReceiveOlympiad.MatchResult packet = new ExReceiveOlympiad.MatchResult(false, winnerTeam.getName());

		TeamMember[] looserMembers = looseTeam.getMembers().toArray(new TeamMember[looseTeam.getMembers().size()]);
		TeamMember[] winnerMembers = winnerTeam.getMembers().toArray(new TeamMember[winnerTeam.getMembers().size()]);

		int[] looserPoints = managePoints(looserMembers, false);
		int[] winnerPoints = managePoints(winnerMembers, true);
		
		for(int i = 0; i < Party.MAX_SIZE; i++)
		{
			TeamMember looserMember = ArrayUtils.valid(looserMembers, i);
			TeamMember winnerMember = ArrayUtils.valid(winnerMembers, i);
			if(looserMember != null && winnerMember != null)
			{
				winnerMember.incGameCount();
				looserMember.incGameCount();

				packet.addPlayer(winnerTeam == _team1 ? 0 : 1, winnerMember, winnerPoints[i]);
				packet.addPlayer(looseTeam == _team1 ? 0 : 1, looserMember, -looserPoints[i]);
			}
		}

		for(Player player : winnerTeam.getPlayers())
		{
			ItemInstance item = player.getInventory().addItem(Config.ALT_OLY_BATTLE_REWARD_ITEM, getType().getReward());
			player.sendPacket(SystemMessage2.obtainItems(item.getItemId(), getType().getReward(), 0));
			player.sendChanges();
		}

		List<Player> teamsPlayers = new ArrayList<Player>();
		teamsPlayers.addAll(winnerTeam.getPlayers());
		teamsPlayers.addAll(looseTeam.getPlayers());
		for(Player player : teamsPlayers)
			if(player != null)
			{
				for(QuestState qs : player.getAllQuestsStates())
					if(qs.isStarted())
						qs.getQuest().onOlympiadEnd(this, qs);
			}

		broadcastPacket(packet, true, false);

		//FIXME [VISTALL] неверная мессага?
		broadcastPacket(new SystemMessage2(SystemMsg.CONGRATULATIONS_C1_YOU_WIN_THE_MATCH).addString(winnerTeam.getName()), false, true);

		//FIXME [VISTALL] нужно ли?
		//broadcastPacket(new SystemMessage2(SystemMsg.C1_HAS_EARNED_S2_POINTS_IN_THE_GRAND_OLYMPIAD_GAMES).addString(winnerTeam.getName()).addInteger(pointDiff), true, false);
		//broadcastPacket(new SystemMessage2(SystemMsg.C1_HAS_LOST_S2_POINTS_IN_THE_GRAND_OLYMPIAD_GAMES).addString(looseTeam.getName()).addInteger(pointDiff), true, false);

		Log.add("Olympiad Result: " + winnerTeam.getName() + " vs " + looseTeam.getName() + " ... (" + (int) winnerTeam.getDamage() + " vs " + (int) looseTeam.getDamage() + ") " + winnerTeam.getName() + " win", "olympiad");
	}

	public void tie()
	{
		TeamMember[] teamMembers1 = _team1.getMembers().toArray(new TeamMember[_team1.getMembers().size()]);
		TeamMember[] teamMembers2 = _team2.getMembers().toArray(new TeamMember[_team2.getMembers().size()]);

		ExReceiveOlympiad.MatchResult packet = new ExReceiveOlympiad.MatchResult(true, StringUtils.EMPTY);
		for(int i = 0; i < teamMembers1.length; i++)
			try
			{
				TeamMember member1 = ArrayUtils.valid(teamMembers1, i);
				TeamMember member2 = ArrayUtils.valid(teamMembers2, i);

				if(member1 != null)
				{
					member1.incGameCount();
					StatsSet stat1 = member1.getStat();
					packet.addPlayer(0, member1, -2);

					stat1.set(Olympiad.POINTS, stat1.getInteger(Olympiad.POINTS) - 2);
				}

				if(member2 != null)
				{
					member2.incGameCount();
					StatsSet stat2 = member2.getStat();
					packet.addPlayer(1, member2, -2);

					stat2.set(Olympiad.POINTS, stat2.getInteger(Olympiad.POINTS) - 2);
				}
			}
			catch(Exception e)
			{
				_log.error("OlympiadGame.tie(): " + e, e);
			}

		broadcastPacket(SystemMsg.THERE_IS_NO_VICTOR_THE_MATCH_ENDS_IN_A_TIE, false, true);
		broadcastPacket(packet, true, false);


		Log.add("Olympiad Result: " + _team1.getName() + " vs " + _team2.getName() + " ... tie", "olympiad");
	}

	private int[] managePoints(TeamMember[] members, boolean winner)
	{
		int partyCount = members.length;
		int[] points = new int[partyCount];
		int[] ratios = new int[partyCount];
		for(int i = 0;i<members.length;i++)
		{
			int classCombination = Olympiad.getClassCombination(members[i].getPlayer());
			points[i] = members[i].getStat().getInteger(Olympiad.POINTS+classCombination, Config.OLYMPIAD_POINTS_DEFAULT);
		}
		
		double[] pointsToManage;
		if(!winner)
		{
			pointsToManage = new double[partyCount];
			
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
			
			_pointsToManage = pointsToManage;
		
		}
		else
		{
			pointsToManage = _pointsToManage;
		}
		
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
		
		int[] pointsChanged = new int[points.length];
		
		for(int i = 0;i<points.length;i++)
		{
			int toAdd = (int) Math.floor(pointsToManage[i]);
			if(remaindersGave<=remainders && (winner && points[i]<=sortedTeamPoints[remainders] || !winner && points[i]>=sortedTeamPoints[remainders]))
			{
				toAdd++;
				remaindersGave++;
			}
			TeamMember member = members[i];
			int classCombination = Olympiad.getClassCombination(member.getPlayer());

			pointsChanged[i] = toAdd;
			if(winner)
			{
				points[i] += toAdd;
				member.getStat().set(Olympiad.COMP_WIN+classCombination, member.getStat().getInteger(Olympiad.COMP_WIN+classCombination, 0) + 1);
			}
			else
			{
				points[i] -= toAdd;
				member.getStat().set(Olympiad.COMP_LOOSE+classCombination, member.getStat().getInteger(Olympiad.COMP_LOOSE+classCombination, 0) + 1);
			}

			member.getStat().set(Olympiad.COMP_DONE+classCombination, member.getStat().getInteger(Olympiad.COMP_DONE+classCombination, 0) + 1);
			member.getStat().set(Olympiad.POINTS+classCombination, points[i]);
			

			OlympiadDatabase.saveNobleData(member.getObjectId());
			OlympiadNobleDAO.getInstance().replacePoints(member.getObjectId(), classCombination);
		}
		
		return pointsChanged;
	}

	public void openDoors()
	{
		for(DoorInstance door : _reflection.getDoors())
			door.openMe();
	}

	public int getId()
	{
		return _id;
	}

	public Reflection getReflection()
	{
		return _reflection;
	}

	public boolean isRegistered(int objId)
	{
		return _team1.contains(objId) || _team2.contains(objId);
	}

	public List<Player> getSpectators()
	{
		return _spectators;
	}

	public void addSpectator(Player spec)
	{
		_spectators.add(spec);
	}

	public void removeSpectator(Player spec)
	{
		_spectators.remove(spec);
	}

	public void clearSpectators()
	{
		for(Player pc : _spectators)
			if(pc != null && pc.isInObserverMode())
				pc.leaveOlympiadObserverMode(false);
		_spectators.clear();
	}

	public void broadcastInfo(Player sender, Player receiver, boolean onlyToSpectators)
	{
		// TODO заюзать пакеты:
		// ExEventMatchCreate
		// ExEventMatchFirecracker
		// ExEventMatchManage
		// ExEventMatchMessage
		// ExEventMatchObserver
		// ExEventMatchScore
		// ExEventMatchTeamInfo
		// ExEventMatchTeamUnlocked
		// ExEventMatchUserInfo

		if(sender != null)
			if(receiver != null)
				receiver.sendPacket(new ExOlympiadUserInfo(sender, sender.getOlympiadSide()));
			else
				broadcastPacket(new ExOlympiadUserInfo(sender, sender.getOlympiadSide()), !onlyToSpectators, true);
		else
		{
			// Рассылаем информацию о первой команде
			for(Player player : _team1.getPlayers())
				if(receiver != null)
					receiver.sendPacket(new ExOlympiadUserInfo(player, player.getOlympiadSide()));
				else
				{
					broadcastPacket(new ExOlympiadUserInfo(player, player.getOlympiadSide()), !onlyToSpectators, true);
					player.broadcastRelationChanged();
				}

			// Рассылаем информацию о второй команде
			for(Player player : _team2.getPlayers())
				if(receiver != null)
					receiver.sendPacket(new ExOlympiadUserInfo(player, player.getOlympiadSide()));
				else
				{
					broadcastPacket(new ExOlympiadUserInfo(player, player.getOlympiadSide()), !onlyToSpectators, true);
					player.broadcastRelationChanged();
				}
		}
	}

	public void broadcastRelation()
	{
		for(Player player : _team1.getPlayers())
			player.broadcastRelationChanged();

		for(Player player : _team2.getPlayers())
			player.broadcastRelationChanged();
	}

	public void broadcastPacket(L2GameServerPacket packet, boolean toTeams, boolean toSpectators)
	{
		if(toTeams)
		{
			_team1.broadcast(packet);
			_team2.broadcast(packet);
		}

		if(toSpectators && !_spectators.isEmpty())
			for(Player spec : _spectators)
				if(spec != null)
					spec.sendPacket(packet);
	}

	public void broadcastPacket(IStaticPacket packet, boolean toTeams, boolean toSpectators)
	{
		if(toTeams)
		{
			_team1.broadcast(packet);
			_team2.broadcast(packet);
		}

		if(toSpectators && !_spectators.isEmpty())
			for(Player spec : _spectators)
				if(spec != null)
					spec.sendPacket(packet);
	}

	public List<Player> getAllPlayers()
	{
		List<Player> result = new ArrayList<Player>();
		for(Player player : _team1.getPlayers())
			result.add(player);
		for(Player player : _team2.getPlayers())
			result.add(player);
		if(!_spectators.isEmpty())
			for(Player spec : _spectators)
				if(spec != null)
					result.add(spec);
		return result;
	}

	public void setWinner(int val)
	{
		_winner = val;
	}

	public OlympiadTeam getWinnerTeam()
	{
		if(_winner == 1) // Выиграла первая команда
			return _team1;
		else if(_winner == 2) // Выиграла вторая команда
			return _team2;
		return null;
	}

	public void setState(int val)
	{
		_state = val;
	}

	public int getState()
	{
		return _state;
	}

	public List<Player> getTeamMembers(Player player)
	{
		return player.getOlympiadSide() == 1 ? _team1.getPlayers() : _team2.getPlayers();
	}

	public void addDamage(Player player, double damage)
	{
		if(player.getOlympiadSide() == 1)
			_team1.addDamage(player, damage);
		else
			_team2.addDamage(player, damage);
	}

	public boolean doDie(Player player)
	{
		return player.getOlympiadSide() == 1 ? _team1.doDie(player) : _team2.doDie(player);
	}

	public boolean checkPlayersOnline()
	{
		return _team1.checkPlayers() && _team2.checkPlayers();
	}

	public boolean logoutPlayer(Player player)
	{
		return player != null && (player.getOlympiadSide() == 1 ? _team1.logout(player) : _team2.logout(player));
	}

	OlympiadGameTask _task;
	ScheduledFuture<?> _shedule;

	public synchronized void sheduleTask(OlympiadGameTask task)
	{
		if(_shedule != null)
			_shedule.cancel(false);
		_task = task;
		_shedule = task.shedule();
	}

	public OlympiadGameTask getTask()
	{
		return _task;
	}

	public BattleStatus getStatus()
	{
		if(_task != null)
			return _task.getStatus();
		return BattleStatus.Begining;
	}

	public void endGame(long time, boolean aborted)
	{
		try
		{
			validateWinner(aborted);
		}
		catch(Exception e)
		{
			_log.error("", e);
		}

		sheduleTask(new OlympiadGameTask(this, BattleStatus.Ending, 0, time));
	}

	public CompType getType()
	{
		return _type;
	}

	public String getTeamName1()
	{
		return _team1.getName();
	}

	public String getTeamName2()
	{
		return _team2.getName();
	}
}