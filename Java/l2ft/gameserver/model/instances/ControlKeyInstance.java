package l2ft.gameserver.model.instances;

import l2ft.commons.lang.reference.HardReference;
import l2ft.gameserver.idfactory.IdFactory;
import l2ft.gameserver.model.GameObject;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.reference.L2Reference;
import l2ft.gameserver.network.l2.s2c.MyTargetSelected;

/**
 * @author VISTALL
 * @date 20:20/03.01.2011
 */
public class ControlKeyInstance extends GameObject
{
	protected HardReference<ControlKeyInstance> reference;

	public ControlKeyInstance()
	{
		super(IdFactory.getInstance().getNextId());
		reference = new L2Reference<ControlKeyInstance>(this);
	}

	@Override
	public HardReference<ControlKeyInstance> getRef()
	{
		return reference;
	}

	@Override
	public void onAction(Player player, boolean shift)
	{
		if(player.getTarget() != this)
		{
			player.setTarget(this);
			player.sendPacket(new MyTargetSelected(getObjectId(), 0));
			return;
		}

		player.sendActionFailed();
	}
}
