package l2ft.gameserver.network.l2.c2s;

import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.entity.olympiad.Olympiad;
import l2ft.gameserver.network.l2.components.SystemMsg;
import l2ft.gameserver.network.l2.s2c.ExReceiveOlympiad;

public class RequestOlympiadMatchList extends L2GameClientPacket
{
	@Override
	protected void readImpl() throws Exception
	{
		// trigger
	}

	@Override
	protected void runImpl() throws Exception
	{
		Player player = getClient().getActiveChar();
		if(player == null)
			return;

		if(!Olympiad.inCompPeriod() || Olympiad.isOlympiadEnd())
		{
			player.sendPacket(SystemMsg.THE_GRAND_OLYMPIAD_GAMES_ARE_NOT_CURRENTLY_IN_PROGRESS);
			return;
		}

		player.sendPacket(new ExReceiveOlympiad.MatchList());
	}
}
