package npc.model;

import java.util.ArrayList;
import java.util.List;

import l2ft.commons.threading.RunnableImpl;
import l2ft.gameserver.Config;
import l2ft.gameserver.ThreadPoolManager;
import l2ft.gameserver.model.Creature;
import l2ft.gameserver.model.instances.NpcInstance;
import l2ft.gameserver.templates.npc.NpcTemplate;

public class FameZoneNpcInstance extends NpcInstance
{
	private static boolean _threadRunned = false;
	private static List<NpcInstance> _npcs = new ArrayList<NpcInstance>();
	public FameZoneNpcInstance(int objectId, NpcTemplate template) {
		super(objectId, template);
		if(!_threadRunned)
			ThreadPoolManager.getInstance().scheduleAtFixedRate(new GiveReward(), 5*60000, 5*60000);
		_npcs.add(this);
	}
	
	public class GiveReward extends RunnableImpl
	{
		@Override
		public void runImpl() throws Exception 
		{
			for(NpcInstance npc : _npcs)
				for(Creature character : npc.getAroundCharacters(50, 300))
					if(character.isPlayer())
						if(!character.isDead())
							character.getPlayer().setFame(character.getPlayer().getFame() + Config.FAME_FOR_ZONE, "Standing inside fame zone");
		}
	}
}
