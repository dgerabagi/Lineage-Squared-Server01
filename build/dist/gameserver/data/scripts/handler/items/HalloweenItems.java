package handler.items;

import l2ft.commons.threading.RunnableImpl;
import l2ft.gameserver.Announcements;
import l2ft.gameserver.ThreadPoolManager;
import l2ft.gameserver.data.xml.holder.NpcHolder;
import l2ft.gameserver.model.Playable;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.SimpleSpawner;
import l2ft.gameserver.model.instances.NpcInstance;
import l2ft.gameserver.model.items.ItemInstance;

public class HalloweenItems extends ScriptItemHandler
{
	public class DeSpawnScheduleTimerTask extends RunnableImpl
	{
		SimpleSpawner spawnedNpc = null;

		public DeSpawnScheduleTimerTask(SimpleSpawner spawn)
		{
			spawnedNpc = spawn;
		}

		@Override
		public void runImpl() throws Exception
		{
			spawnedNpc.deleteAll();
		}
	}

	private static int SKOODY_ITEM = 20709;
	private static int WOOLDY_ITEM = 20707;

	private static final int DESPAWN_TIME = 60000; //1 min

	@Override
	public boolean useItem(Playable playable, ItemInstance item, boolean ctrl)
	{
		Player activeChar = (Player) playable;

		if (!activeChar.getInventory().destroyItem(item, 1L))
			return false;
		
		int npcId = 0;
		if(item.getItemId() == SKOODY_ITEM)
			npcId = 4303;
		else if(item.getItemId() == WOOLDY_ITEM)
			npcId = 118;

		SimpleSpawner spawn = new SimpleSpawner(NpcHolder.getInstance().getTemplate(npcId));
		spawn.setLoc(activeChar.getLoc());
		NpcInstance npc = spawn.doSpawn(true);
		
		Announcements.getInstance().announceToAll(playable.getPlayer().getName()+" summoned "+npc.getName()+"!");
		ThreadPoolManager.getInstance().schedule(new DeSpawnScheduleTimerTask(spawn), DESPAWN_TIME);
		return true;
	}
	
	private static final int[] ITEMS = {SKOODY_ITEM, WOOLDY_ITEM};
	@Override
	public int[] getItemIds()
	{
		return ITEMS;
	}
}
