package l2ft.gameserver.network.l2.c2s;

import l2ft.gameserver.ai.CtrlIntention;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.Summon;
import l2ft.gameserver.model.items.ItemInstance;
import l2ft.gameserver.network.l2.components.SystemMsg;
import l2ft.gameserver.network.l2.s2c.SystemMessage2;
import l2ft.gameserver.utils.ItemFunctions;

public class RequestPetGetItem extends L2GameClientPacket
{
	// format: cd
	private int _objectId;

	@Override
	protected void readImpl()
	{
		_objectId = readD();
	}

	@Override
	protected void runImpl()
	{
		Player activeChar = getClient().getActiveChar();
		if(activeChar == null)
			return;

		if(activeChar.isOutOfControl())
		{
			activeChar.sendActionFailed();
			return;
		}

		Summon summon = activeChar.getPet();
		if(summon == null || !summon.isPet() || summon.isDead() || summon.isActionsDisabled())
		{
			activeChar.sendActionFailed();
			return;
		}

		ItemInstance item = (ItemInstance) activeChar.getVisibleObject(_objectId);
		if(item == null)
		{
			activeChar.sendActionFailed();
			return;
		}

		if(!ItemFunctions.checkIfCanPickup(summon, item))
		{
			SystemMessage2 sm;
			if(item.getItemId() == 57)
			{
				sm = new SystemMessage2(SystemMsg.YOU_HAVE_FAILED_TO_PICK_UP_S1_ADENA);
				sm.addInteger((int)item.getCount());
			}
			else
			{
				sm = new SystemMessage2(SystemMsg.YOU_HAVE_FAILED_TO_PICK_UP_S1);
				sm.addItemName(item.getItemId());
			}
			sendPacket(sm);
			activeChar.sendActionFailed();
			return;
		}

		summon.getAI().setIntention(CtrlIntention.AI_INTENTION_PICK_UP, item, null);
	}
}