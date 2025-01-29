package l2ft.gameserver.network.l2.c2s;

import l2ft.gameserver.instancemanager.MatchingRoomManager;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.matching.MatchingRoom;

/**
 * @author VISTALL
 */
public class RequestExJoinMpccRoom extends L2GameClientPacket
{
	private int _roomId;

	@Override
	protected void readImpl() throws Exception
	{
   		_roomId = readD();
	}

	@Override
	protected void runImpl() throws Exception
	{
		Player player = getClient().getActiveChar();
		if(player == null)
			return;

		if(player.getMatchingRoom() != null)
			return;

		MatchingRoom room = MatchingRoomManager.getInstance().getMatchingRoom(MatchingRoom.CC_MATCHING, _roomId);
		if(room == null)
			return;

		room.addMember(player);
	}
}