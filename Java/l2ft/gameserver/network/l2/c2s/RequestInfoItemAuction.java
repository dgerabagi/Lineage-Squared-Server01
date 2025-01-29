package l2ft.gameserver.network.l2.c2s;

import l2ft.gameserver.instancemanager.itemauction.ItemAuction;
import l2ft.gameserver.instancemanager.itemauction.ItemAuctionInstance;
import l2ft.gameserver.instancemanager.itemauction.ItemAuctionManager;
import l2ft.gameserver.model.Creature;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.instances.NpcInstance;
import l2ft.gameserver.network.l2.s2c.ExItemAuctionInfo;

/**
 * @author n0nam3
 */
public final class RequestInfoItemAuction extends L2GameClientPacket
{
	private int _instanceId;

	@Override
	protected final void readImpl()
	{
		_instanceId = readD();
	}

	@Override
	protected final void runImpl()
	{
		final Player activeChar = getClient().getActiveChar();
		if(activeChar == null)
			return;

		activeChar.getAndSetLastItemAuctionRequest();

		final ItemAuctionInstance instance = ItemAuctionManager.getInstance().getManagerInstance(_instanceId);
		if(instance == null)
			return;

		final ItemAuction auction = instance.getCurrentAuction();
		NpcInstance broker = activeChar.getLastNpc();
		if(auction == null || broker == null || broker.getNpcId() != _instanceId || activeChar.getDistance(broker.getX(), broker.getY()) > Creature.INTERACTION_DISTANCE)
			return;

		activeChar.sendPacket(new ExItemAuctionInfo(true, auction, instance.getNextAuction()));
	}
}