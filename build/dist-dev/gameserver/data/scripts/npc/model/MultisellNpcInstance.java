package npc.model;

import l2ft.gameserver.Config;
import l2ft.gameserver.ai.CtrlIntention;
import l2ft.gameserver.data.xml.holder.MultiSellHolder;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.instances.NpcInstance;
import l2ft.gameserver.network.l2.s2c.ActionFail;
import l2ft.gameserver.network.l2.s2c.MyTargetSelected;
import l2ft.gameserver.network.l2.s2c.StatusUpdate;
import l2ft.gameserver.network.l2.s2c.ValidateLocation;
import l2ft.gameserver.scripts.Events;
import l2ft.gameserver.templates.npc.NpcTemplate;

public class MultisellNpcInstance extends NpcInstance
{
	private int multisellId = 0;
	public MultisellNpcInstance(int objectId, NpcTemplate template)
	{
		super(objectId, template);
		getMultisellId();
	}
	
	private void getMultisellId()
	{
		switch(getNpcId())
		{
		case 4303:
			multisellId = 3667602;
			break;
		case 118:
			multisellId = 3667601;
			break;
		}
	}
	
	@Override
	public void onAction(Player player, boolean shift)
	{
		if(!isTargetable())
		{
			player.sendActionFailed();
			return;
		}

		if(player.getTarget() != this)
		{
			player.setTarget(this);
			if(player.getTarget() == this)
				player.sendPacket(new MyTargetSelected(getObjectId(), player.getLevel() - getLevel()), makeStatusUpdate(StatusUpdate.CUR_HP, StatusUpdate.MAX_HP));

			player.sendPacket(new ValidateLocation(this), ActionFail.STATIC);
			return;
		}

		if(Events.onAction(player, this, shift))
		{
			player.sendActionFailed();
			return;
		}

		if(isAutoAttackable(player))
		{
			player.getAI().Attack(this, false, shift);
			return;
		}

		if(!isInRange(player, INTERACTION_DISTANCE))
		{
			if(player.getAI().getIntention() != CtrlIntention.AI_INTENTION_INTERACT)
				player.getAI().setIntention(CtrlIntention.AI_INTENTION_INTERACT, this, null);
			return;
		}

		if(!Config.ALT_GAME_KARMA_PLAYER_CAN_SHOP && player.getKarma() > 0 && !player.isGM())
		{
			player.sendActionFailed();
			return;
		}

		// –? NPC –?–µ–ªNS–∑NZ NÄ–∞–∑–l–l–?–∞NÄ–∏–?–∞NÇNS –L–µNÄNÇ–?Nã–L –∏ N?–∏–¥NZ
		if(!Config.ALLOW_TALK_WHILE_SITTING && player.isSitting() || player.isAlikeDead())
			return;

		if(hasRandomAnimation())
			onRandomAnimation();

		player.sendActionFailed();
		player.stopMove(false);
		
		MultiSellHolder.getInstance().SeparateAndSend(multisellId, player, 0);
	}
}
