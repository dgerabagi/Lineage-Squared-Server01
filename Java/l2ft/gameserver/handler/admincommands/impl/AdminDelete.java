package l2ft.gameserver.handler.admincommands.impl;


import org.apache.commons.lang3.math.NumberUtils;
import l2ft.gameserver.handler.admincommands.IAdminCommandHandler;
import l2ft.gameserver.model.GameObject;
import l2ft.gameserver.model.GameObjectsStorage;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.Spawner;
import l2ft.gameserver.model.instances.NpcInstance;
import l2ft.gameserver.network.l2.components.SystemMsg;

public class AdminDelete implements IAdminCommandHandler
{
	private static enum Commands
	{
		admin_delete
	}

	@Override
	public boolean useAdminCommand(Enum comm, String[] wordList, String fullString, Player activeChar)
	{
		Commands command = (Commands) comm;

		if(!activeChar.getPlayerAccess().CanEditNPC)
			return false;

		switch(command)
		{
			case admin_delete:
				GameObject obj = wordList.length == 1 ? activeChar.getTarget() : GameObjectsStorage.getNpc(NumberUtils.toInt(wordList[1]));
				if(obj != null && obj.isNpc())
				{
					NpcInstance target = (NpcInstance) obj;
					target.deleteMe();

					Spawner spawn = target.getSpawn();
					if(spawn != null)
						spawn.stopRespawn();
				}
				else
					activeChar.sendPacket(SystemMsg.INVALID_TARGET);
				break;
		}

		return true;
	}

	@Override
	public Enum[] getAdminCommandEnum()
	{
		return Commands.values();
	}
}