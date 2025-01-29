package l2ft.gameserver.network.l2.c2s;

import l2ft.gameserver.Shutdown;
import l2ft.gameserver.network.authcomm.AuthServerCommunication;
import l2ft.gameserver.network.authcomm.SessionKey;
import l2ft.gameserver.network.authcomm.gspackets.PlayerAuthRequest;
import l2ft.gameserver.network.l2.GameClient;
import l2ft.gameserver.network.l2.s2c.LoginFail;
import l2ft.gameserver.network.l2.s2c.ServerClose;

/**
 * cSddddd
 * cSdddddQ
 * loginName + keys must match what the loginserver used.
 */
public class AuthLogin extends L2GameClientPacket
{
	private String _loginName;
	private int _playKey1;
	private int _playKey2;
	private int _loginKey1;
	private int _loginKey2;
	private byte[] _data = new byte[48];

	@Override
	protected void readImpl()
	{
		_loginName = readS(32).toLowerCase();
		_playKey2 = readD();
		_playKey1 = readD();
		_loginKey1 = readD();
		_loginKey2 = readD();
		l2ft.gameserver.ccpGuard.Protection.doReadAuthLogin(getClient(), _buf, _data);
	}

	@Override
	protected void runImpl()
	{
		if(!l2ft.gameserver.ccpGuard.Protection.doAuthLogin(getClient(), _data, _loginName))
			return;
		GameClient client = getClient();
		
		SessionKey key = new SessionKey(_loginKey1, _loginKey2, _playKey1, _playKey2);
		client.setSessionId(key);
		client.setLoginName(_loginName);
		
		if(Shutdown.getInstance().getMode() != Shutdown.NONE && Shutdown.getInstance().getSeconds() <= 15)
			client.closeNow(false);
		else
		{			
			if(AuthServerCommunication.getInstance().isShutdown())
			{
				client.close(new LoginFail(LoginFail.SYSTEM_ERROR_LOGIN_LATER));
				return;
			}
			
			GameClient oldClient = AuthServerCommunication.getInstance().addWaitingClient(client);
			if(oldClient != null)
				oldClient.close(ServerClose.STATIC);
			
			AuthServerCommunication.getInstance().sendPacket(new PlayerAuthRequest(client));
		}
	}
}