package l2ft.loginserver.network.gameservercon.lspackets;

import l2ft.loginserver.accounts.Account;
import l2ft.loginserver.accounts.SessionManager.Session;
import l2ft.loginserver.network.SessionKey;
import l2ft.loginserver.network.gameservercon.SendablePacket;

public class PlayerAuthResponse extends SendablePacket
{
	private String login;
	private boolean authed;
	private int playOkID1;
	private int playOkID2;
	private int loginOkID1;
	private int loginOkID2;

	public PlayerAuthResponse(Session session, boolean authed)
	{
		Account account = session.getAccount();
		this.login = account.getLogin();
		this.authed = authed;
		if(authed)
		{
			SessionKey skey = session.getSessionKey();
			playOkID1 = skey.playOkID1;
			playOkID2 = skey.playOkID2;
			loginOkID1 = skey.loginOkID1;
			loginOkID2 = skey.loginOkID2;
		}
	}

	public PlayerAuthResponse(String account)
	{
		this.login = account;
		authed = false;
	}

	@Override
	protected void writeImpl()
	{
		writeC(0x02);
		writeS(login);
		writeC(authed ? 1 : 0);
		if(authed)
		{
			writeD(playOkID1);
			writeD(playOkID2);
			writeD(loginOkID1);
			writeD(loginOkID2);
		}
	}
}
