package l2ft.gameserver.handler.voicecommands.impl;


import l2ft.commons.text.PrintfFormat;
import l2ft.gameserver.data.htm.HtmCache;
import l2ft.gameserver.handler.voicecommands.IVoicedCommandHandler;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.Player.SkillLearnType;
import l2ft.gameserver.model.entity.events.GlobalEvent;
import l2ft.gameserver.network.l2.s2c.NpcHtmlMessage;
import l2ft.gameserver.scripts.Functions;
import l2ft.gameserver.taskmanager.AutoPotionsManager;

public class Cfg extends Functions implements IVoicedCommandHandler
{
	private String[] _commandList = new String[] { "cfg" };

	public static final PrintfFormat cfg_row = new PrintfFormat("<table><tr><td width=5></td><td width=120>%s:</td><td width=100>%s</td></tr></table>");
	public static final PrintfFormat cfg_button = new PrintfFormat("<button width=%d back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\" action=\"bypass -h user_cfg %s\" value=\"%s\">");

	public boolean useVoicedCommand(String command, Player activeChar, String args)
	{
		if(args != null && args.equals("help"))
		{
			NpcHtmlMessage msg = new NpcHtmlMessage(0);
			msg.setFile("command/cfgHelp.htm");
			return true;
		}
		if(command.equals("cfg"))
			if(args != null)
			{
				String[] param = args.split(" ");
				if(param.length == 2)
				{
					if(param[0].equalsIgnoreCase("pot"))
						if(param[1].equalsIgnoreCase("on"))
							AutoPotionsManager.getInstance().addNewPlayer(activeChar);
						else if(param[1].equalsIgnoreCase("of"))
							AutoPotionsManager.getInstance().removePlayer(activeChar);

					if(param[0].equalsIgnoreCase("noe"))
						if(param[1].equalsIgnoreCase("on"))
						{
							activeChar.setVar("NoExp", "1", -1);
							activeChar.sendMessage("You will not longer recieve Experience!");
						}
						else if(param[1].equalsIgnoreCase("of"))
						{
							activeChar.unsetVar("NoExp");
							activeChar.sendMessage("You will be able to get Experience now!");
						}

					if(param[0].equalsIgnoreCase(Player.NO_TRADERS_VAR))
						if(param[1].equalsIgnoreCase("on"))
						{
							activeChar.setNotShowTraders(true);
							activeChar.setVar(Player.NO_TRADERS_VAR, "1", -1);
							activeChar.sendMessage("You will no longer see Traders!");
						}
						else if(param[1].equalsIgnoreCase("of"))
						{
							activeChar.setNotShowTraders(false);
							activeChar.unsetVar(Player.NO_TRADERS_VAR);
							activeChar.sendMessage("You will be able to see Traders from now on!");
						}
					
					if(param[0].equalsIgnoreCase(Player.NO_ANIMATION_OF_CAST_VAR))
						if(param[1].equalsIgnoreCase("on"))
						{
							activeChar.setNotShowBuffAnim(true);
							activeChar.setVar(Player.NO_ANIMATION_OF_CAST_VAR, "1", -1);
							activeChar.sendMessage("You will no longer see Casting Animations!");
						}
						else if(param[1].equalsIgnoreCase("of"))
						{
							activeChar.setNotShowBuffAnim(false);
							activeChar.unsetVar(Player.NO_ANIMATION_OF_CAST_VAR);
							activeChar.sendMessage("You will be able to see Casting Animations now!");
						}
					
					if(param[0].equalsIgnoreCase("noShift"))
						if(param[1].equalsIgnoreCase("on"))
						{
							activeChar.setVar("noShift", "1", -1);
							activeChar.sendMessage("Your shift is now classic!");
						}
						else if(param[1].equalsIgnoreCase("of"))
						{
							activeChar.unsetVar("noShift");
							activeChar.sendMessage("Your shift is no longer classic!");
						}
					
					if(param[0].equalsIgnoreCase("autoloot"))
						if(param[1].equalsIgnoreCase("on"))
						{
							activeChar.setAutoLoot(true);
							activeChar.sendMessage("Your loot is now automaticly put in inventory!");
						}
						else if(param[1].equalsIgnoreCase("of"))
						{
							activeChar.setAutoLoot(false);
							activeChar.sendMessage("Your loot is no longer automaticly put in inventory!");
						}
					
					if(param[0].equalsIgnoreCase("autoHerbs"))
						if(param[1].equalsIgnoreCase("on"))
						{
							activeChar.setAutoLootHerbs(true);
							activeChar.sendMessage("Herbs are now automaticly used!");
						}
						else if(param[1].equalsIgnoreCase("of"))
						{
							activeChar.setAutoLootHerbs(false);
							activeChar.sendMessage("Herbs are not longer automaticly used!");
						}
					
					if(param[0].equals("autoOnNonFlagged"))
						if(param[1].equalsIgnoreCase("on"))
						{
							activeChar.setVar("autoOnNonFlagged", 1, -1);
							activeChar.setAutoAttackOnNonFlagged(true);
							activeChar.sendMessage("Attack is now automatic on non flagged players!");
						}
						else if(param[1].equalsIgnoreCase("of"))
						{
							activeChar.unsetVar("autoOnNonFlagged");
							activeChar.setAutoAttackOnNonFlagged(false);
							activeChar.sendMessage("Attack is no longer automatic on non flagged players!");
						}
					
					if(param[0].equalsIgnoreCase("craftCount"))
					{
						int count = Integer.parseInt(param[1]);
						activeChar.setVar("craftCount", count, -1);
					}
					
					if(param[0].equalsIgnoreCase("skillLearn"))
					{
						activeChar.setSkillLearnType(Integer.parseInt(param[1]));
						String learnType = "One by One";
						if(activeChar.getSkillLearnType() == SkillLearnType.All_At_Once)
							learnType = "All at Once";
						else if(activeChar.getSkillLearnType() == SkillLearnType.Insta_Learn)
						{
							activeChar.rewardSkills(true);
							learnType = "Instantly";
						}
						activeChar.sendMessage("Your skills are now learned "+ learnType);
					}
				}
			}

		String dialog = HtmCache.getInstance().getNotNull("command/cfg.htm", activeChar);

		dialog = dialog.replaceFirst("%potion%", AutoPotionsManager.getInstance().playerUseAutoPotion(activeChar) ? "On" : "Off");
		dialog = dialog.replaceFirst("%herb%", activeChar.getVarB("AutoLootHerbs") ? "On" : "Off");
		dialog = dialog.replaceFirst("%noe%", activeChar.getVarB("NoExp") ? "On" : "Off");
		dialog = dialog.replaceFirst("%notraders%", activeChar.getVarB("notraders") ? "On" : "Off");
		dialog = dialog.replaceFirst("%notShowBuffAnim%", activeChar.getVarB("notShowBuffAnim") ? "On" : "Off");
		dialog = dialog.replaceFirst("%noShift%", activeChar.getVarB("noShift") ? "On" : "Off");
		dialog = dialog.replaceFirst("%autoLoot%", activeChar.isAutoLootEnabled() ? "On" : "Off");
		dialog = dialog.replaceFirst("%autoHerbs%", activeChar.isAutoLootHerbsEnabled() ? "On" : "Off");
		dialog = dialog.replaceFirst("%autoOnNonFlagged%", activeChar.getAutoAttackOnNonFlagged() ? "On" : "Off");
		dialog = dialog.replaceFirst("%craftCount%", String.valueOf(activeChar.getVarInt("craftCount", 1)));
		dialog = dialog.replaceFirst("%skillLearn%", getSkillLearn(activeChar));

		StringBuilder events = new StringBuilder();
		for(GlobalEvent e : activeChar.getEvents())
			events.append(e.toString()).append("<br>");
		dialog = dialog.replace("%events%", events.toString());

		show(dialog, activeChar);

		return true;
	}
	
	private String getSkillLearn(Player player)
	{
		String button = player.getSkillLearnType() != SkillLearnType.One_By_One ? "<button width=70 height=15 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\" action=\"bypass -h user_cfg skillLearn 0\" value=\"One-by-One\">" : "One-By-One";
		button += player.getSkillLearnType() != SkillLearnType.All_At_Once ? "<button width=70 height=15 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\" action=\"bypass -h user_cfg skillLearn 1\" value=\"All-at-Once\">": "All-At-Once";
		button += player.getSkillLearnType() != SkillLearnType.Insta_Learn ? "<button width=70 height=15 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\" action=\"bypass -h user_cfg skillLearn 2\" value=\"Insta-Learn\">": "Insta-Learn";
		return button;
	}

	public String[] getVoicedCommandList()
	{
		return _commandList;
	}
}