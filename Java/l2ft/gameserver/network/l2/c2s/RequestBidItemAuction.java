package l2ft.gameserver.network.l2.c2s;

import l2ft.gameserver.instancemanager.itemauction.ItemAuction;
import l2ft.gameserver.instancemanager.itemauction.ItemAuctionInstance;
import l2ft.gameserver.instancemanager.itemauction.ItemAuctionManager;
import l2ft.gameserver.model.Creature;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.instances.NpcInstance;
import l2ft.gameserver.model.items.ItemInstance;

/**
 * @author n0nam3
 */
public final class RequestBidItemAuction extends L2GameClientPacket
{
	private int _instanceId;
	private long _bid;

	@Override
	protected final void readImpl()
	{
		_instanceId = readD();
		_bid = readQ();
	}

	@Override
	protected final void runImpl()
	{
		final Player activeChar = getClient().getActiveChar();
		if(activeChar == null)
			return;
		ItemInstance adena = activeChar.getInventory().getItemByItemId(57);
		if(_bid < 0 || _bid > adena.getCount())
			return;

		final ItemAuctionInstance instance = ItemAuctionManager.getInstance().getManagerInstance(_instanceId);
		NpcInstance broker = activeChar.getLastNpc();
		if(broker == null || broker.getNpcId() != _instanceId || activeChar.getDistance(broker.getX(), broker.getY()) > Creature.INTERACTION_DISTANCE)
			return;
		if(instance != null)
		{
			final ItemAuction auction = instance.getCurrentAuction();
			if(auction != null)
				auction.registerBid(activeChar, _bid);
		}
	}
}