package l2ft.gameserver.model.instances;

import l2ft.gameserver.templates.npc.NpcTemplate;

public class NpcNotSayInstance extends NpcInstance
{
	public NpcNotSayInstance(final int objectID, final NpcTemplate template)
	{
		super(objectID, template);
		setHasChatWindow(false);
	}
}
