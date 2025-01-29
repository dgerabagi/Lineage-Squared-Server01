package l2ft.gameserver.network.l2.c2s;

import l2ft.gameserver.Config;
import l2ft.gameserver.network.l2.s2c.Ex2ndPasswordCheck;

/**
 * Format: (ch)
 */
public class RequestEx2ndPasswordCheck extends L2GameClientPacket
{
	@Override
	protected void readImpl()
	{
		
	}
	
	@Override
	protected void runImpl()
	{
		if (!Config.SECOND_AUTH_ENABLED || getClient().getSecondaryAuth().isAuthed())
		{
			sendPacket(new Ex2ndPasswordCheck(Ex2ndPasswordCheck.PASSWORD_OK));
			return;
		}
		
		getClient().getSecondaryAuth().openDialog();
	}
}