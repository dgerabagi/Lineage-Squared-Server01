package npc.model;

import java.util.ArrayList;
import java.util.List;

import l2ft.commons.threading.RunnableImpl;
import l2ft.gameserver.Config;
import l2ft.gameserver.ThreadPoolManager;
import l2ft.gameserver.model.Creature;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.instances.NpcInstance;
import l2ft.gameserver.templates.npc.NpcTemplate;

public class PvPZoneNpcInstance extends NpcInstance
{
	private static boolean _threadRunned = false;
	private static List<NpcInstance> _npcs = new ArrayList<NpcInstance>();
	public PvPZoneNpcInstance(int objectId, NpcTemplate template) 
	{
		super(objectId, template);
		if(!_threadRunned)
			ThreadPoolManager.getInstance().scheduleAtFixedRate(new GiveFlag(), 500, 500);
		_npcs.add(this);
	}
	
	public class GiveFlag extends RunnableImpl
	{
		@Override
		public void runImpl() throws Exception 
		{
			for(NpcInstance npc : _npcs)
				for(Creature character : npc.getAroundCharacters(50, 300))
					if(character.isPlayer())
						if(!character.isDead())
						{
							Player player = character.getPlayer();
							player.startPvPFlag(player);
						}
							
		}
	}
}
