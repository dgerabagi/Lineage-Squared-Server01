package l2ft.gameserver.network.l2.c2s;

import l2ft.gameserver.data.xml.holder.ResidenceHolder;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.entity.residence.Fortress;
import l2ft.gameserver.network.l2.s2c.ExShowFortressMapInfo;

public class RequestFortressMapInfo extends L2GameClientPacket
{
	private int _fortressId;

	@Override
	protected void readImpl()
	{
		_fortressId = readD();
	}

	@Override
	protected void runImpl()
	{
		Player player = getClient().getActiveChar();
		if(player == null)
			return;
		Fortress fortress = ResidenceHolder.getInstance().getResidence(Fortress.class, _fortressId);
		if(fortress != null)
			sendPacket(new ExShowFortressMapInfo(fortress));
	}
}