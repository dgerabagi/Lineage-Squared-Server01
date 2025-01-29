package l2ft.gameserver.network.l2.c2s;

import l2ft.gameserver.Config;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.network.l2.GameClient;
import l2ft.gameserver.network.l2.GameClient.GameClientState;
import l2ft.gameserver.network.l2.s2c.ActionFail;
import l2ft.gameserver.network.l2.s2c.CharSelected;
import l2ft.gameserver.utils.AutoBan;

public class CharacterSelected extends L2GameClientPacket
{
	private int _charSlot;

	/**
	 * Format: cdhddd
	 */
	@Override
	protected void readImpl()
	{
		_charSlot = readD();
	}

	@Override
	protected void runImpl()
	{
		GameClient client = getClient();

		if (Config.SECOND_AUTH_ENABLED && !client.getSecondaryAuth().isAuthed())
		{
			client.getSecondaryAuth().openDialog();
			return;
		}
		
		if(client.getActiveChar() != null)
			return;

		int objId = client.getObjectIdForSlot(_charSlot);
		if(AutoBan.isBanned(objId))
		{
			sendPacket(ActionFail.STATIC);
			return;
		}
		
		Player activeChar = client.loadCharFromDisk(_charSlot);
		if(activeChar == null)
		{
			sendPacket(ActionFail.STATIC);
			return;
		}

		if(activeChar.getAccessLevel() < 0)
			activeChar.setAccessLevel(0);
		
		if(!l2ft.gameserver.ccpGuard.Protection.checkPlayerWithHWID(client, activeChar.getObjectId(), activeChar.getName()))
			return;
		
		client.setState(GameClientState.IN_GAME);

		sendPacket(new CharSelected(activeChar, client.getSessionKey().playOkID1));
	}
}