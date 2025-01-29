package npc.model;

import java.util.StringTokenizer;

import l2ft.gameserver.data.xml.holder.ResidenceHolder;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.entity.residence.Castle;
import l2ft.gameserver.model.instances.NpcInstance;
import l2ft.gameserver.network.l2.s2c.NpcHtmlMessage;
import l2ft.gameserver.scripts.Functions;
import l2ft.gameserver.templates.npc.NpcTemplate;
import l2ft.gameserver.utils.Location;

public class GlobalGatekeeperInstance  extends NpcInstance
{
	public GlobalGatekeeperInstance(int objectId, NpcTemplate template) {
		super(objectId, template);
	}

	@Override
	public void showChatWindow(Player player, final int val, Object... arg)
	{
		String filename = val == 0 ? "teleporter/"+getNpcId()+".htm" : "teleporter/"+getNpcId()+"-" + val + ".htm";		
		player.sendPacket(new NpcHtmlMessage(player, this, filename, val));
	}

	@Override
	public void onBypassFeedback(Player player, String command)
	{
		if(!canBypassCheck(player, this))
			return;
		if(command.startsWith("goto"))
		{
			StringTokenizer st = new StringTokenizer(command);
			st.nextToken();
			int[] xyz = new int[3];
			int i = 0;//goto 83432 148152 -3430 3
			while(st.hasMoreTokens())
			{
				int coord = Integer.parseInt(st.nextToken());
				if(i<3)
					xyz[i] = coord;
				i++;
			}
			if(i >= 3)
			{
				
				Location pos = Location.findPointToStay(xyz[0], xyz[1], xyz[2], 50, 100, player.getGeoIndex());
				player.teleToLocation(pos);
			}
		}
		else
		{
			if(command.startsWith("Chat"))
			{
				int val = Integer.parseInt(command.substring(5));
				showChatWindow(player, val);
			}
		}
	}
}
