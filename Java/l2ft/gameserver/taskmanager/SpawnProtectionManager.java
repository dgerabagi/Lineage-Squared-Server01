package l2ft.gameserver.taskmanager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import l2ft.commons.threading.RunnableImpl;
import l2ft.gameserver.ThreadPoolManager;
import l2ft.gameserver.model.Player;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpawnProtectionManager
{
	public static final Logger _log = LoggerFactory.getLogger(SpawnProtectionManager.class);
	private static SpawnProtectionManager _instance;
	private Map<Player, Long> _tasks = new HashMap<>();
	
	public SpawnProtectionManager()
	{
		ThreadPoolManager.getInstance().schedule(new SpawnThread(), 0);
	}
	
	public class SpawnThread extends RunnableImpl
	{
		@Override
		public void runImpl()
		{
			List<Player> toRemove = new ArrayList<>();
			for(Entry<Player, Long> task : _tasks.entrySet())
			{
				Player player = task.getKey();
				if(!player.isAggroProtected())
					toRemove.add(player);
				else if(task.getValue()<System.currentTimeMillis())
				{
					player.sendMessage("Your spawn Protection is now over!!!");
					toRemove.add(player);
				}
			}
			for(Player playerToRemove : toRemove)
				_tasks.remove(playerToRemove);
			ThreadPoolManager.getInstance().schedule(new SpawnThread(), 500);
		}
	}
	
	public void addNewPlayer(Player player, long time)
	{
		_tasks.put(player, time);
	}
	
	public static SpawnProtectionManager getInstance()
	{
		if(_instance == null)
			_instance = new SpawnProtectionManager();
		return _instance;
	}
}
