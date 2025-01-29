package quests;

import java.util.ArrayList;
import java.util.List;

import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.actor.instances.player.Unlocks.UnlockedClass;
import l2ft.gameserver.model.base.ClassId;
import l2ft.gameserver.model.base.Race;
import l2ft.gameserver.model.instances.NpcInstance;
import l2ft.gameserver.model.items.ItemInstance;
import l2ft.gameserver.model.quest.Quest;
import l2ft.gameserver.model.quest.QuestState;
import l2ft.gameserver.network.l2.s2c.ExShowScreenMessage;
import l2ft.gameserver.network.l2.s2c.ExShowScreenMessage.ScreenMessageAlign;
import l2ft.gameserver.network.l2.s2c.InventoryUpdate;
import l2ft.gameserver.network.l2.s2c.SocialAction;
import l2ft.gameserver.scripts.ScriptFile;

public class _900_CharacterCreation extends Quest implements ScriptFile
{
	private final ExShowScreenMessage ERR_001 = new ExShowScreenMessage("Cannot use 2 of the same classes!", 10000,ScreenMessageAlign.TOP_CENTER, true);
	private final ExShowScreenMessage ERR_002 = new ExShowScreenMessage("Must choose 2 classes of the same race, unless second race is dwarf!", 10000,ScreenMessageAlign.TOP_CENTER, true);
	private final ExShowScreenMessage ERR_005 = new ExShowScreenMessage("Your character builder has been reset.", 10000,ScreenMessageAlign.TOP_CENTER, true);
	private final ExShowScreenMessage ERR_006 = new ExShowScreenMessage("Cannot choose FORTUNE SEEKER as a PRIMARY class!", 10000,ScreenMessageAlign.TOP_CENTER, true);
	private final ExShowScreenMessage ERR_007 = new ExShowScreenMessage("Kamaels may choose only Light armor!", 10000,ScreenMessageAlign.TOP_CENTER, true);

	private final ExShowScreenMessage BUILD_PRIMARY = new ExShowScreenMessage("Select your PRIMARY class.", 10000, ScreenMessageAlign.TOP_CENTER,true);
	private final ExShowScreenMessage BUILD_SECONDARY = new ExShowScreenMessage("Select your SECONDARY class.", 10000,ScreenMessageAlign.TOP_CENTER, true);
	private final ExShowScreenMessage BUILD_ARMOR = new ExShowScreenMessage("Select your ARMOR set.", 10000, ScreenMessageAlign.TOP_CENTER,true);
	private final ExShowScreenMessage BUILD_WEAPON1 = new ExShowScreenMessage("Select your first WEAPON.", 10000, ScreenMessageAlign.TOP_CENTER,true);
	private final ExShowScreenMessage BUILD_WEAPON2 = new ExShowScreenMessage("Select your second WEAPON.", 10000, ScreenMessageAlign.TOP_CENTER,true);

	private final ExShowScreenMessage COMPLETE = new ExShowScreenMessage("You have completed the Character Creation process. Please Wait...",10000, ScreenMessageAlign.TOP_CENTER, true);

	private final int START_NPC = 13100;
	
	@Override
	public void onLoad()
	{}

	@Override
	public void onReload()
	{}

	@Override
	public void onShutdown()
	{}

	public _900_CharacterCreation()
	{
		super(false);
		
		addStartNpc(START_NPC);
	}
	
	@Override
	public String onEvent(String event, QuestState qs, NpcInstance npc) 
	{
		Player player = qs.getPlayer();
		if(event.equalsIgnoreCase("racemenu.htm"))
		{
			player.sendPacket(BUILD_PRIMARY);
			qs.setCond(1);
		}
		else if(event.startsWith("class"))
		{
			int classId = Integer.parseInt(event.substring(5).trim());
			if(chooseClass(player, classId))
			{
				if(player.getActiveClass().getSecondaryClass() <= 6)
				{
					if(qs.getCond() == 1)
					{
						player.sendPacket(BUILD_SECONDARY);
						qs.setCond(2);
						return onTalk(npc, qs);
					}
				}
				else
				{
					player.sendPacket(BUILD_ARMOR);
					qs.setCond(3);
					return onTalk(npc, qs);
				}
			}
			else
				return onTalk(npc, qs);
		}
		else if(event.startsWith("armor"))
		{
			if(chooseItems(player, Integer.parseInt(event.substring(6)), true))
			{
				player.sendPacket(BUILD_WEAPON1);
				qs.setCond(4);
			}
			return onTalk(npc, qs);
		}
		else if(event.startsWith("weapon"))
		{
			if(chooseItems(player, Integer.parseInt(event.substring(7)), false))
			{
				if(qs.getCond() == 4)
				{
					player.sendPacket(BUILD_WEAPON2);
					qs.setCond(5);
				}
				else if(qs.getCond() == 5)
				{
					qs.setCond(6);
					return onTalk(npc, qs);
				}
				
			}
			return onTalk(npc, qs);
		}
		else if(event.equals("reset"))
		{
			List<ItemInstance> itemsToRemove = new ArrayList<ItemInstance>();
			for(ItemInstance item : player.getInventory().getItems())
			
				for(int customItemId : allItems())
					if(item.getItemId() == customItemId)
					{
						itemsToRemove.add(item);
						break;
					}
			for(ItemInstance item : itemsToRemove)
				player.getInventory().destroyItem(item);
			qs.setCond(0);
			for(int clazz : player.getUnlocks().getAllUnlocks().keys())
				player.getUnlocks().removeClass(clazz);
			player.getActiveClass().setFirstClassId(player.getPrimaryClass());
			player.getActiveClass().setSecondaryClass(1);
			player.sendPacket(ERR_005);
			return onTalk(npc, qs);
		}
		else if(event.equals("finish"))
		{
			player.teleToLocation(-125760, 38115, 1232);
			qs.playSound(SOUND_FINISH);
			qs.exitCurrentQuest(false);
			player.sendPacket(COMPLETE);
			player.getInventory().addItem(1835, 2000);
			player.getInventory().addItem(3947, 2000);
			player.broadcastPacket(new SocialAction(player.getObjectId(), SocialAction.LEVEL_UP));
			return null;
		}
		return event;
	}
	
	@Override
	public String onFirstTalk(NpcInstance npc, Player player) 
	{
		String htmltext = null;
		if(npc.getNpcId() == START_NPC)
			htmltext = "1000.htm";
		return htmltext;
	}
	
	@Override
	public String onTalk(NpcInstance npc, QuestState st)
	{
		String htmltext = "noquest";
		int npcId = npc.getNpcId();
		int cond = st.getCond();
		
		if(cond <= 0 || cond > 10)
			cond = 0;
		if(npcId == START_NPC)
		{
			switch(cond)
			{
			case 0:
				htmltext = "1000.htm";
				break;
			case 1:
				htmltext = "racemenu.htm";
				break;
			case 2:
				htmltext = "1000-"+(st.getPlayer().getActiveClassClassId().getRace().ordinal()+1)+".htm";
				break;
			case 3:
				htmltext = "armor.htm";
				break;
			case 4:
			case 5:
				htmltext = "weapon.htm";
				break;
			case 6:
				htmltext = "finish.htm";
				break;
			}
		}
		
		return htmltext;
	}
	
	public boolean chooseClass(Player player, int classId)
	{
		ClassId clazz = ClassId.values()[classId];
		
		int currentClassesSize = 0;
		for(Object obj : player.getUnlocks().getAllUnlocks().getValues())
			if(((UnlockedClass)obj).getId() >= 88)
				currentClassesSize++;
		if(currentClassesSize >= 2)
			return false;
		
		if (player.getActiveClassClassId().getLevel() <= 1)
		{
			if (clazz.getRace() == Race.dwarf) {
				player.sendPacket(ERR_006);
				return false;
			}
			player.addNewStackClass(classId, true);
		}
		else
		{
			int firstClassId = player.getActiveClass().getFirstClassId();
			ClassId firstClass = ClassId.values()[firstClassId];
			
			if (firstClass == clazz)
			{
				player.sendPacket(ERR_001);
				return false;
			}
			if(firstClass.getRace() != clazz.getRace() && clazz.getRace() != Race.dwarf)
			{
				player.sendPacket(ERR_002);
				return false;
			}
			player.addNewStackClass(classId, false);
		}
		return true;
	}

	public boolean chooseItems(Player player, int itemVal, boolean armor)
	{
		if (armor && player.getRace() == Race.kamael && itemVal != 3)
		{
			player.sendPacket(ERR_007);
			return false;
		}
		
		int[] items = armor ? getArmor(itemVal) : new int[] {getWeapon(player, itemVal)};
		
		InventoryUpdate iu = new InventoryUpdate();
		for (int id : items)
		{
			ItemInstance newItem = player.getInventory().addItem(id, 1);
			player.getInventory().equipItem(newItem);
			iu.addModifiedItem(newItem);
		}
		if(armor)
			for(int id : getJewely())
			{
				ItemInstance newItem = player.getInventory().addItem(id, 1);
				player.getInventory().equipItem(newItem);
				iu.addModifiedItem(newItem);
			}
		
		player.sendPacket(iu);
		player.broadcastCharInfo();
		return true;
	}
	
	private int[] getJewely()
	{
		return new int[] {846, 114, 1506, 9899, 1508};//ear, ear, neck, ring, ring
	}
	
	private int[] getArmor(int id) 
	{
		switch (id) 
		{
		case 1:
			return new int[] { 4224, 4225, 4226, 4227, 43, 4222 }; // Dream Set
		case 2:
			return new int[] { 4228, 4229, 4230, 4231, 43, 4223 }; // Ubiquitous
		case 3:
			return new int[] { 43, 23, 2386, 51, 39, 4222 }; // Wooden Armor set
		case 4:
			return new int[] { 44, 1101, 1104, 51, 39, 4222 }; // Devotion set
		default:
			return new int[] {};
		}
	}
	
	private int getWeapon(Player player, int id) 
	{
		switch (id) 
		{
		case 1:
			return 4220; // Dream Knife
		case 2:
			return 4219; // Dream Sword
		case 3:
			return 4221; // Ubiquitous Axe
			// case 4: return 4222; //Dream Shield
			// case 5: return 4223; //Ubiquitous Shield
		case 6:
			return 100; // Voodoo Doll
		case 7:
			return 177; // Mage Staff
		case 8:
			player.getInventory().addItem(17, 500);
			player.getInventory().addItem(9632, 500);
			return 273; // Composite Bow
		case 9:
			return 257; // Viper Fang
		case 10:
			return 16; // Great Spear
		case 11:
			return 5284; // Zweihander
		}
		return 0;
	}
	private static int[] allItems()
	{
		int[] items = {4224, 4225, 4226, 4227, 43, 4222, 4228, 4229, 4230, 4231, 4223, 23, 2386, 51, 39, 44, 1101, 1104, 4220, 4219, 4221, 100, 177, 273, 257, 16, 5284, 17, 846, 114, 1506, 9899, 1508, 1834, 3947};
		
		return items;
	}
	
	@Override
	public boolean isVisible()
	{
		return false;
	}
}
