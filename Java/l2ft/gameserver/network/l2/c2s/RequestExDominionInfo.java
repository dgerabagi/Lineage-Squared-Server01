package l2ft.gameserver.network.l2.c2s;

import l2ft.gameserver.data.xml.holder.EventHolder;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.entity.events.EventType;
import l2ft.gameserver.model.entity.events.impl.DominionSiegeRunnerEvent;
import l2ft.gameserver.network.l2.s2c.ExReplyDominionInfo;
import l2ft.gameserver.network.l2.s2c.ExShowOwnthingPos;

public class RequestExDominionInfo extends L2GameClientPacket
{
	@Override
	protected void readImpl()
	{}

	@Override
	protected void runImpl()
	{
		Player activeChar = getClient().getActiveChar();
		if(activeChar == null)
			return;

		activeChar.sendPacket(new ExReplyDominionInfo());

		DominionSiegeRunnerEvent runnerEvent = EventHolder.getInstance().getEvent(EventType.MAIN_EVENT, 1);
		if(runnerEvent.isInProgress())
			activeChar.sendPacket(new ExShowOwnthingPos());
	}
}