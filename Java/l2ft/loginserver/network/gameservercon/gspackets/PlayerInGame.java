package l2ft.loginserver.network.gameservercon.gspackets;

import l2ft.loginserver.network.gameservercon.GameServer;
import l2ft.loginserver.network.gameservercon.ReceivablePacket;

public class PlayerInGame extends ReceivablePacket
{
	private String account;

	@Override
	protected void readImpl()
	{
		account = readS();
	}

	@Override
	protected void runImpl()
	{
		GameServer gs = getGameServer();
		if(gs.isAuthed())
			gs.addAccount(account);
	}
}
