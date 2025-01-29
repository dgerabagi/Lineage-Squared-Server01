package l2ft.gameserver.taskmanager;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import l2ft.gameserver.ThreadPoolManager;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.taskmanager.tasks.TaskVariable;
import l2ft.gameserver.taskmanager.tasks.TaskVariable.TaskType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayerTaskManager implements Runnable
{
	public static final Logger _log = LoggerFactory.getLogger(PlayerTaskManager.class);
	private static PlayerTaskManager _instance;
	
	private Map<Player, Map<Long, TaskVariable>> _tasks = new HashMap<>();
	
	public PlayerTaskManager()
	{
		ThreadPoolManager.getInstance().scheduleAtFixedRate(this, 1000, 1000);
	}
	@Override
	public final void run()
	{
		try
		{
			Map<Player, Long> timesToRemove = new HashMap<>();
			for(Entry<Player, Map<Long, TaskVariable>> player : _tasks.entrySet())
				for(Entry<Long, TaskVariable> task : player.getValue().entrySet())
					if(task.getKey() < System.currentTimeMillis())
					{
						task.getValue().over();
						player.getKey().removeTaskFromList(task.getValue().getType());
						timesToRemove.put(player.getKey(), task.getKey());
					}
			for(Entry<Player, Long> player : timesToRemove.entrySet())
				_tasks.get(player.getKey()).remove(player.getValue());
		}
		catch(Exception e)
		{
			_log.error("Exception: RunnableImpl.run(): " + e, e);
		}
	}
	
	/**
	 * @param player
	 * @param type
	 * @param time in miliseconds(current + x)
	 */
	public void addNewTask(Player player, TaskType type, long time)
	{
		TaskVariable var = new TaskVariable(player, type, time);
		if(!_tasks.containsKey(player))
		{
			Map<Long, TaskVariable> map = new HashMap<Long, TaskVariable>();
			_tasks.put(player, map);
		}
		_tasks.get(player).put(time, var);
	}
	
	public long getExpireTime(Player player, TaskType type)
	{
		Map<Long, TaskVariable> tasks = _tasks.get(player);
		for(Entry<Long, TaskVariable> task : tasks.entrySet())
			if(task.getValue().getType() == type)
				return task.getKey() - System.currentTimeMillis();
		return 0;
	}
	
	public void finishTask(Player player, TaskType type)
	{
		Map<Long, TaskVariable> tasks = _tasks.get(player);
		if(_tasks == null)
			return;
		for(Entry<Long, TaskVariable> task : tasks.entrySet())
			if(task.getValue().getType() == type)
			{
				task.getValue().over();
				_tasks.remove(task.getValue());
				return;
			}
	}
	
	public void shutDown(Player player)
	{
		Map<Long, TaskVariable> tasks = _tasks.get(player);
		if(tasks == null)
			return;
		for(Entry<Long, TaskVariable> task : tasks.entrySet())
			task.getValue().shutdown();
		_tasks.remove(player);
	}
	
	public static PlayerTaskManager getInstance()
	{
		if(_instance == null)
			_instance = new PlayerTaskManager();
		return _instance;
	}
}
