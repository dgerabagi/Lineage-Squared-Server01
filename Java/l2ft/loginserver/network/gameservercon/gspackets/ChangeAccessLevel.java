package l2ft.loginserver.network.gameservercon.gspackets;


import l2ft.loginserver.accounts.Account;
import l2ft.loginserver.network.gameservercon.ReceivablePacket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChangeAccessLevel extends ReceivablePacket
{
	public static final Logger _log = LoggerFactory.getLogger(ChangeAccessLevel.class);

	private String account;
	private int level;	
	private int banExpire;

	@Override
	protected void readImpl()
	{
		account = readS();
		level = readD();
		banExpire = readD();
	}

	@Override
	protected void runImpl()
	{
		/**Account acc = new Account(account);
		acc.restore();
		acc.setAccessLevel(level);
		acc.setBanExpire(banExpire);
		acc.update();*/
	}
}