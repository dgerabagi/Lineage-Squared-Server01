package l2ft.gameserver.instancemanager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import l2ft.commons.threading.RunnableImpl;
import l2ft.gameserver.ThreadPoolManager;
import l2ft.gameserver.listener.zone.OnZoneEnterLeaveListener;
import l2ft.gameserver.model.Creature;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.Zone;
import l2ft.gameserver.utils.ReflectionUtils;

public class FameZoneManager
{
	private static FameZoneManager _instance;
	private List<Player> _playersInside = new ArrayList<>();
	private Map<Player, Integer[]> _wonFame = new ConcurrentHashMap<>();
	private Map<Player, Integer[]> _lostFame = new ConcurrentHashMap<>();
	private ZoneListener _zoneListener = new ZoneListener();

	private static final int[] WON_LIMITERS = {20, 40, 80};
	private static final int[] LOSE_LIMITERS = {20, 40, 80};
	private static final String[] ZONES = {"[famefarm_fame_1]", "[famefarm_fame_2]"};
	
	public FameZoneManager()
	{
		_zoneListener = new ZoneListener();
		for(String zoneName : ZONES)
		{
			Zone zone = ReflectionUtils.getZone(zoneName);
			if(zone != null)
				zone.addListener(_zoneListener);
		}
		
		ThreadPoolManager.getInstance().scheduleAtFixedRate(new FameThread(), 5*60000, 5*60000);
	}
	
	private class FameThread extends RunnableImpl
	{
		@Override
		public void runImpl() throws Exception
		{
			for(Player playerInside : _playersInside)
			{
				if(playerInside.isInCombat())
					playerInside.setFame(playerInside.getFame()+20, "Inside Fame Zone");
			}
			
			for(Entry<Player, Integer[]> wonEntry : _wonFame.entrySet())
			{
				Player player = wonEntry.getKey();
				int index = 0;
				boolean changed = false;
				for(Integer points : wonEntry.getValue())
				{
					if(points > 0)
					{
						player.setFame(player.getFame() + Math.min(points, WON_LIMITERS[index]), "PvP Zone");
						changed = true;
					}
					index++;
				}
				if(changed)
					_wonFame.put(player, new Integer[] {0,0,0});
			}
			
			for(Entry<Player, Integer[]> lostEntry : _lostFame.entrySet())
			{
				Player player = lostEntry.getKey();
				int index = 0;
				boolean changed = false;
				for(Integer points : lostEntry.getValue())
				{
					if(points > 0)
					{
						player.setFame(player.getFame() - Math.min(points, LOSE_LIMITERS[index]), "PvP Zone");
						changed = true;
					}
					index++;
				}
				if(changed)
					_lostFame.put(player, new Integer[] {0,0,0});
			}
		}
	}
	
	public void addPvP(Player winner, Player loser)
	{
		int wonFame = 0;
		int lostFame = 0;
		int type = -1;
		if(loser.getFame() < 5)
			;
		else if(loser.getFame() < 100)
		{
			wonFame = 1;
			lostFame = 5;
			type = 0;
		}
		else if(loser.getFame() < 500)
		{
			wonFame = 9;
			lostFame = 10;
			type = 1;
		}
		else if(loser.getFame() < 1000)
		{
			wonFame = 18;
			lostFame = 20;
			type = 2;
		}

		if(type == -1)
			return;
		
		if(!_wonFame.containsKey(winner))
			_wonFame.put(winner, new Integer[] {0,0,0});
		if(!_lostFame.containsKey(loser))
			_lostFame.put(loser, new Integer[] {0,0,0});

		_wonFame.get(winner)[type] += wonFame;
		_lostFame.get(loser)[type] += lostFame;
	}
	
	public class ZoneListener implements OnZoneEnterLeaveListener
	{

		@Override
		public void onZoneEnter(Zone zone, Creature actor)
		{
			if(actor.isPlayer())
				_playersInside.add(actor.getPlayer());
		}

		@Override
		public void onZoneLeave(Zone zone, Creature actor)
		{
			if(actor.isPlayer())
				_playersInside.remove(actor.getPlayer());
		}
	}
	
	public static FameZoneManager getInstance()
	{
		if(_instance == null)
			_instance = new FameZoneManager();
		return _instance;
	}
}
