package handler.items;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import l2ft.commons.dbutils.DbUtils;
import l2ft.commons.threading.RunnableImpl;
import l2ft.gameserver.ThreadPoolManager;
import l2ft.gameserver.data.xml.holder.NpcHolder;
import l2ft.gameserver.database.DatabaseFactory;
import l2ft.gameserver.model.Playable;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.SimpleSpawner;
import l2ft.gameserver.model.instances.NpcInstance;
import l2ft.gameserver.model.items.ItemInstance;
import l2ft.gameserver.scripts.ScriptFile;
import l2ft.gameserver.utils.Location;
import events.Christmas.cakeAI;

public class Cake extends ScriptItemHandler
{
	public static class DeSpawnScheduleTimerTask extends RunnableImpl
	{
		NpcInstance cake;

		public DeSpawnScheduleTimerTask(NpcInstance npc)
		{
			cake = npc;
		}

		@Override
		public void runImpl() throws Exception
		{
			cake.deleteMe();
			_cakeTasks.remove(cake);
		}
	}
	private static int _itemId = 21595; // Cake Item

	private static int _npcId = 106; // Cake npc

	private static final int DESPAWN_TIME = 3600000; //60 min

	@Override
	public boolean useItem(Playable playable, ItemInstance item, boolean ctrl)
	{
		Player activeChar = (Player) playable;
		
		if (!activeChar.getInventory().destroyItem(item, 1L))
			return false;
		
		spawnNpc(_npcId, activeChar.getLoc(), activeChar.getName(), DESPAWN_TIME);
		return true;
	}
	
	private static Map<NpcInstance, ScheduledFuture<?>> _cakeTasks = new HashMap<NpcInstance, ScheduledFuture<?>>();
	
	public static void spawnNpc(int npcId, Location loc, String title, long despawnTime)
	{
		SimpleSpawner spawn = new SimpleSpawner(NpcHolder.getInstance().getTemplate(npcId));
		spawn.setLoc(loc);
		NpcInstance npc = spawn.doSpawn(false);
		npc.setTitle(title);
		spawn.respawnNpc(npc);
		npc.setAI(new cakeAI(npc));
		
		_cakeTasks.put(npc, ThreadPoolManager.getInstance().schedule(new DeSpawnScheduleTimerTask(npc), despawnTime));
	}
	
	public static Map<NpcInstance, ScheduledFuture<?>> getCakeTasks()
	{
		return _cakeTasks;
	}
	
	@Override
	public int[] getItemIds()
	{
		return new int[] {_itemId};
	}
}
