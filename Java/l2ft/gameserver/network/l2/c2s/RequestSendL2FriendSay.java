package l2ft.gameserver.network.l2.c2s;

import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.World;
import l2ft.gameserver.network.l2.components.SystemMsg;
import l2ft.gameserver.network.l2.s2c.L2FriendSay;
import l2ft.gameserver.taskmanager.tasks.TaskVariable.TaskType;
import l2ft.gameserver.utils.Log;

/**
 * Recieve Private (Friend) Message
 * Format: c SS
 * S: Message
 * S: Receiving Player
 */
public class RequestSendL2FriendSay extends L2GameClientPacket
{
	private String _message;
	private String _reciever;

	@Override
	protected void readImpl()
	{
		_message = readS(2048);
		_reciever = readS(16);
	}

	@Override
	protected void runImpl()
	{
		Player activeChar = getClient().getActiveChar();
		if(activeChar == null)
			return;

		if(activeChar.taskExists(TaskType.Chat_ban))
		{
			activeChar.sendPacket(SystemMsg.CHATTING_IS_CURRENTLY_PROHIBITED_);
			return;
		}

		Player targetPlayer = World.getPlayer(_reciever);
		if(targetPlayer == null)
		{
			activeChar.sendPacket(SystemMsg.THAT_PLAYER_IS_NOT_ONLINE);
			return;
		}
		
		if(targetPlayer.isBlockAll())
		{
			activeChar.sendPacket(SystemMsg.THAT_PERSON_IS_IN_MESSAGE_REFUSAL_MODE);
			return;
		}

		if(!activeChar.getFriendList().getList().containsKey(targetPlayer.getObjectId()))
			return;

		Log.LogChat("FRIENDTELL", activeChar.getName(), _reciever, _message);

		L2FriendSay frm = new L2FriendSay(activeChar.getName(), _reciever, _message);
		targetPlayer.sendPacket(frm);
	}
}