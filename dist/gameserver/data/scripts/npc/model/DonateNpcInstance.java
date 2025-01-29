package npc.model;

import l2ft.gameserver.dao.ItemsDAO;
import l2ft.gameserver.listener.actor.player.OnAnswerListener;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.instances.MerchantInstance;
import l2ft.gameserver.model.items.ItemInstance;
import l2ft.gameserver.network.l2.components.SystemMsg;
import l2ft.gameserver.network.l2.s2c.ConfirmDlg;
import l2ft.gameserver.templates.npc.NpcTemplate;

public class DonateNpcInstance extends MerchantInstance
{

	public DonateNpcInstance(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public void onBypassFeedback(Player player, String command)
	{
		String text = "";
		if(command.equals("gold"))
		{
			text = "Do you wish to trade 1 Gold Bar for 5 Coins?";
		}
		else if(command.equals("coin"))
		{
			text = "Do you wish to trade 5 Donate Coins for 1 Gold Bar?";
		}
		else if(command.equals("cakes"))
		{
			text = "Do you wish to buy 5 Cakes for 3 Coins?";
		}
		else if(command.equals("exp"))
		{
			text = "Do you wish to buy a 30-Day 50% XP Rune for 5 Coins?";
		}
		else if(command.equals("sp"))
		{
			text = "Do you wish to buy a 30-Day 50% SP Rune for 5 Coins?";
		}
		else if(command.equals("expAndSp"))
		{
			text = "Do you wish to buy a 30-Day 50% XP/SP Rune for 10 Coins?";
		}
		else if(command.equals("class"))
		{
			text = "Do you wish to get Unlock Class Item for 2 Coins?";
		}
		else if(command.equals("stamp"))
		{
			text = "Do you wish to buy Dualsword Craft Stamp for 1 Coin?";
		}
		else if(command.equals("grade"))
		{
			text = "Do you wish to buy Grade Rune for 5 Coins?";
		}
		else 
		{
			super.onBypassFeedback(player, command);
			return;
		}
		ConfirmDlg packet = new ConfirmDlg(SystemMsg.S1, 60000).addString(text);
		player.ask(packet, new DonationAnswer(player, command));
	}
	
	public class DonationAnswer implements OnAnswerListener
	{
		Player asked;
		String cmd;
		public DonationAnswer(Player player, String command) 
		{
			asked = player;
			cmd = command;
		}
		@Override
		public void sayYes()
		{
			ItemInstance donateItem = asked.getInventory().getItemByItemId(4037);
			if(donateItem != null)
				donateItem = ItemsDAO.getInstance().load(donateItem.getObjectId());
			
			if(cmd.equals("gold"))
			{
				if(asked.getInventory().destroyItemByItemId(4037, 5))
				{
					asked.getInventory().addItem(3470, 1);
					asked.sendMessage("You just purchased 1 Gold Bar!");
				}
				else
					asked.sendMessage("You don't have a Donation Coins!");
			}
			else if(cmd.equals("coin"))
			{
				if(asked.getInventory().destroyItemByItemId(3470, 1))
				{
					asked.getInventory().addItem(4037, 5);
					asked.sendMessage("You just purchased 5 Donate Coin!");
				}
				else
					asked.sendMessage("You don't have 1 Gold Bar!");
			}
			else if(cmd.equals("cakes"))
			{
				if(asked.getInventory().destroyItemByItemId(4037, 3))
				{
					asked.getInventory().addItem(20314, 5);
					asked.sendMessage("You just purchased 5 Cakes!");
				}
				else
					asked.sendMessage("You don't have 3 Donation Coins!");
			}
			else if(cmd.equals("exp"))
			{
				if(asked.getInventory().destroyItemByItemId(4037, 5))
				{
					asked.getInventory().addItem(20549, 1);
					asked.sendMessage("You just purchased 50% Exp Rune!");
				}
				else
					asked.sendMessage("You don't have 5 Donation Coins!");
			}
			else if(cmd.equals("sp"))
			{
				if(asked.getInventory().destroyItemByItemId(4037, 5))
				{
					asked.getInventory().addItem(20559, 1);
					asked.sendMessage("You just purchased 50% Sp Rune!");
				}
				else
					asked.sendMessage("You don't have 5 donation coins!");
			}
			else if(cmd.equals("expAndSp"))
			{
				if(asked.getInventory().destroyItemByItemId(4037, 10))
				{
					asked.getInventory().addItem(20553, 1);
					asked.sendMessage("You just purchased a 50% XP/SP Rune!");
				}
				else
					asked.sendMessage("You don't have 10 donation coins!");
			}
			else if(cmd.equals("class"))
			{
				if(asked.getInventory().destroyItemByItemId(4037, 2))
				{
					asked.getInventory().addItem(15632, 1);
					asked.sendMessage("You just bought new Item for Unlocking Class!");
				}
				else
					asked.sendMessage("You don't have 2 donation coins!");
			}
			else if(cmd.equals("stamp"))
			{
				if(asked.getInventory().destroyItemByItemId(4037, 1))
				{
					asked.getInventory().addItem(5126, 1);
					asked.sendMessage("You just bought Dualsword Craft Stamp!");
				}
				else
					asked.sendMessage("You don't have 1 donation coin!");
			}
			else if(cmd.equals("grade"))
			{
				if(asked.getInventory().destroyItemByItemId(4037, 5))
				{
					asked.getInventory().addItem(21618, 1);
					asked.sendMessage("You just bought Grade Rune!");
				}
				else
					asked.sendMessage("You don't have 5 donation coins!");
			}
		}
		@Override
		public void sayNo() 
		{}
	}
}
