package l2ft.gameserver.model.instances;

import l2ft.gameserver.model.Player;
import l2ft.gameserver.templates.npc.NpcTemplate;

@Deprecated
public class NoActionNpcInstance extends NpcInstance
{
	public NoActionNpcInstance(final int objectID, final NpcTemplate template)
	{
		super(objectID, template);
	}

	@Override
	public void onAction(final Player player, final boolean dontMove)
	{
		player.sendActionFailed();
	}
}
