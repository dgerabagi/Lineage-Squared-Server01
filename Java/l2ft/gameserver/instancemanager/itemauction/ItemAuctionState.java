package l2ft.gameserver.instancemanager.itemauction;

import l2ft.commons.lang.ArrayUtils;

public enum ItemAuctionState
{
	CREATED,
	STARTED,
	FINISHED;

	public static final ItemAuctionState stateForStateId(int stateId)
	{
		return ArrayUtils.valid(values(), stateId);
	}
}