package l2ft.gameserver.network.l2.c2s;

import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.entity.SevenSignsFestival.SevenSignsFestival;
import l2ft.gameserver.network.l2.components.CustomMessage;
import l2ft.gameserver.network.l2.components.SystemMsg;
import l2ft.gameserver.network.l2.GameClient.GameClientState;
import l2ft.gameserver.network.l2.s2c.ActionFail;
import l2ft.gameserver.network.l2.s2c.CharacterSelectionInfo;
import l2ft.gameserver.network.l2.s2c.RestartResponse;

public class RequestRestart extends L2GameClientPacket
{
	/**
	 * packet type id 0x57
	 * format:      c
	 */

	@Override
	protected void readImpl()
	{}

	@Override
	protected void runImpl()
	{
		Player activeChar = getClient().getActiveChar();

		if(activeChar == null)
			return;

		if(activeChar.isInObserverMode())
		{
			activeChar.sendPacket(SystemMsg.OBSERVERS_CANNOT_PARTICIPATE, RestartResponse.FAIL, ActionFail.STATIC);
			return;
		}

		if(activeChar.isInCombat())
		{
			activeChar.sendPacket(SystemMsg.YOU_CANNOT_RESTART_WHILE_IN_COMBAT, RestartResponse.FAIL, ActionFail.STATIC);
			return;
		}

		if(activeChar.isFishing())
		{
			activeChar.sendPacket(SystemMsg.YOU_CANNOT_DO_THAT_WHILE_FISHING_2, RestartResponse.FAIL, ActionFail.STATIC);
			return;
		}

		if(activeChar.isBlocked() && !activeChar.isFlying()) // Разрешаем выходить из игры если используется сервис HireWyvern. Вернет в начальную точку.
		{
			activeChar.sendMessage(new CustomMessage("l2ft.gameserver.clientpackets.RequestRestart.OutOfControl", activeChar));
			activeChar.sendPacket(RestartResponse.FAIL, ActionFail.STATIC);
			return;
		}

		// Prevent player from restarting if they are a festival participant
		// and it is in progress, otherwise notify party members that the player
		// is not longer a participant.
		if(activeChar.isFestivalParticipant())
			if(SevenSignsFestival.getInstance().isFestivalInitialized())
			{
				activeChar.sendMessage(new CustomMessage("l2ft.gameserver.clientpackets.RequestRestart.Festival", activeChar));
				activeChar.sendPacket(RestartResponse.FAIL, ActionFail.STATIC);
				return;
			}

		if(getClient() != null)
			getClient().setState(GameClientState.AUTHED);

		l2ft.gameserver.ccpGuard.Protection.doDisconection(getClient());

		activeChar.restart();
		// send char list
		CharacterSelectionInfo cl = new CharacterSelectionInfo(getClient().getLogin(), getClient().getSessionKey().playOkID1);
		sendPacket(RestartResponse.OK, cl);
		getClient().setCharSelection(cl.getCharInfo());
	}
}