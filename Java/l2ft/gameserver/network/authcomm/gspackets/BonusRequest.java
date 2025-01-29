package l2ft.gameserver.network.authcomm.gspackets;

import l2ft.gameserver.network.authcomm.SendablePacket;

public class BonusRequest extends SendablePacket
{
	private String account;
	private double bonus;
	private int bonusExpire;
	
	public BonusRequest(String account, double bonus, int bonusExpire)
	{
		this.account = account;
		this.bonus = bonus;
		this.bonusExpire = bonusExpire;
	}
	
	protected void writeImpl()
	{
		writeC(0x10);
		writeS(account);
		writeF(bonus);
		writeD(bonusExpire);
	}
}