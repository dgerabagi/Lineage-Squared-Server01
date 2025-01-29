package l2ft.gameserver.network.authcomm.gspackets;

import l2ft.gameserver.network.authcomm.SendablePacket;

public class PlayerInGame extends SendablePacket
{
	private String account;
	
	public PlayerInGame(String account)
	{
		this.account = account;
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0x03);
		writeS(account);
	}
}
