package l2ft.gameserver.model.entity.olympiad;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import l2ft.commons.threading.RunnableImpl;
import l2ft.commons.util.Rnd;
import l2ft.gameserver.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OlympiadManager extends RunnableImpl
{
	private static final Logger _log = LoggerFactory.getLogger(OlympiadManager.class);

	private Map<Integer, OlympiadGame> _olympiadInstances = new ConcurrentHashMap<Integer, OlympiadGame>();

	public void sleep(long time)
	{
		try
		{
			Thread.sleep(time);
		}
		catch(InterruptedException e)
		{}
	}

	@Override
	public void runImpl() throws Exception
	{
		if(Olympiad.isOlympiadEnd())
			return;

		while(Olympiad.inCompPeriod())
		{
			if(Olympiad._nobles.isEmpty())
			{
				sleep(60000);
				continue;
			}

			while(Olympiad.inCompPeriod())
			{
				// Подготовка и запуск внеклассовых боев
				if(Olympiad._team2Registers.size() >= Config.TEAM_GAME_MIN)
					prepareTeamBattles(CompType.TEAM2, Olympiad._team2Registers.values());

				// Подготовка и запуск классовых боев
				if(Olympiad._team4Registers.size() >= Config.TEAM_GAME_MIN)
					prepareTeamBattles(CompType.TEAM4, Olympiad._team4Registers.values());

				// Подготовка и запуск командных боев
				if(Olympiad._team6Registers.size() >= Config.TEAM_GAME_MIN)
					prepareTeamBattles(CompType.TEAM6, Olympiad._team6Registers.values());

				sleep(30000);
			}

			sleep(30000);
		}

		Olympiad._team2Registers.clear();
		Olympiad._team4Registers.clear();
		Olympiad._team6Registers.clear();

		// when comp time finish wait for all games terminated before execute the cleanup code
		boolean allGamesTerminated = false;

		// wait for all games terminated
		while(!allGamesTerminated)
		{
			sleep(30000);

			if(_olympiadInstances.isEmpty())
				break;

			allGamesTerminated = true;
			for(OlympiadGame game : _olympiadInstances.values())
				if(game.getTask() != null && !game.getTask().isTerminated())
					allGamesTerminated = false;
		}

		_olympiadInstances.clear();
	}

	private void prepareTeamBattles(CompType type, Collection<List<Integer>> list)
	{
		for(int i = 0; i < Olympiad.STADIUMS.length; i++)
			try
			{
				if(!Olympiad.STADIUMS[i].isFreeToUse())
					continue;
				if(list.size() < type.getMinSize())
					break;

				List<Integer> nextOpponents = nextTeamOpponents(list, type);
				if(nextOpponents == null)
					break;

				OlympiadGame game = new OlympiadGame(i, type, nextOpponents);
				game.sheduleTask(new OlympiadGameTask(game, BattleStatus.Begining, 0, 1));

				_olympiadInstances.put(i, game);

				Olympiad.STADIUMS[i].setStadiaBusy();
			}
			catch(Exception e)
			{
				_log.error("", e);
			}
	}

	public void freeOlympiadInstance(int index)
	{
		_olympiadInstances.remove(index);
		Olympiad.STADIUMS[index].setStadiaFree();
	}

	public OlympiadGame getOlympiadInstance(int index)
	{
		return _olympiadInstances.get(index);
	}

	public Map<Integer, OlympiadGame> getOlympiadGames()
	{
		return _olympiadInstances;
	}

	private List<Integer> nextTeamOpponents(Collection<List<Integer>> list, CompType type)
	{
		if(list.isEmpty())
			return null;
		List<Integer> opponents = new CopyOnWriteArrayList<Integer>();
		List<List<Integer>> a = new ArrayList<List<Integer>>();
		a.addAll(list);

		for(int i = 0; i < type.getMinSize(); i++)
		{
			if(a.size() < 1)
				continue;
			List<Integer> team = a.remove(Rnd.get(a.size()));
			for(Integer noble : team)
			{
				opponents.add(noble);
				removeOpponent(noble);
			}

			list.remove(team);
		}

		return opponents;
	}

	private void removeOpponent(Integer noble)
	{
		Olympiad._team2Registers.removeValue(noble);
		Olympiad._team4Registers.remove(noble);
		Olympiad._team6Registers.removeValue(noble);
	}
}