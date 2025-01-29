package l2ft.gameserver.network.authcomm.lspackets;

import l2ft.gameserver.network.authcomm.AuthServerCommunication;
import l2ft.gameserver.network.authcomm.ReceivablePacket;
import l2ft.gameserver.network.authcomm.gspackets.PingResponse;

public class PingRequest extends ReceivablePacket
{
	@Override
	public void readImpl()
	{
		
	}

	@Override
	protected void runImpl()
	{
		AuthServerCommunication.getInstance().sendPacket(new PingResponse());
	}
}