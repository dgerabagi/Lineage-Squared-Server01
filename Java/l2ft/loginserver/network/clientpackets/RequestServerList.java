package l2ft.loginserver.network.clientpackets;

import l2ft.loginserver.network.L2LoginClient;
import l2ft.loginserver.network.SessionKey;
import l2ft.loginserver.network.serverpackets.ServerList;
import l2ft.loginserver.network.serverpackets.LoginFail.LoginFailReason;

/**
 * Format: ddc
 * d: fist part of session id
 * d: second part of session id
 * c: ?
 */
public class RequestServerList extends L2LoginClientPacket
{
	private int _loginOkID1;
	private int _loginOkID2;

	@Override
	protected void readImpl()
	{
		_loginOkID1 = readD();
		_loginOkID2 = readD();
	}

	@Override
	protected void runImpl()
	{
		L2LoginClient client = getClient();
		SessionKey skey = client.getSessionKey();
		if(skey == null || !skey.checkLoginPair(_loginOkID1, _loginOkID2))
		{
			client.close(LoginFailReason.REASON_ACCESS_FAILED);
			return;
		}

		client.sendPacket(new ServerList(client.getAccount()));
	}
}