package l2ft.loginserver.network;

import java.nio.channels.SocketChannel;

import l2ft.loginserver.Config;
import l2ft.loginserver.IpBanManager;
import l2ft.loginserver.ThreadPoolManager;
import l2ft.loginserver.network.serverpackets.Init;
import l2ft.commons.net.nio.impl.IAcceptFilter;
import l2ft.commons.net.nio.impl.IClientFactory;
import l2ft.commons.net.nio.impl.IMMOExecutor;
import l2ft.commons.net.nio.impl.MMOConnection;
import l2ft.commons.threading.RunnableImpl;


public class SelectorHelper implements IMMOExecutor<L2LoginClient>, IClientFactory<L2LoginClient>, IAcceptFilter
{
	@Override
	public void execute(Runnable r)
	{
		ThreadPoolManager.getInstance().execute(r);
	}

	@Override
	public L2LoginClient create(MMOConnection<L2LoginClient> con)
	{
		final L2LoginClient client = new L2LoginClient(con);
		client.sendPacket(new Init(client));
		ThreadPoolManager.getInstance().schedule(new RunnableImpl()
		{
			@Override
			public void runImpl()
			{
				client.closeNow(false);
			}
		}, Config.LOGIN_TIMEOUT);
		return client;
	}

	@Override
	public boolean accept(SocketChannel sc)
	{
		return !IpBanManager.getInstance().isIpBanned(sc.socket().getInetAddress().getHostAddress());
	}
}