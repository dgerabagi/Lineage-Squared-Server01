package npc.model;

import java.util.Arrays;
import java.util.List;

import l2ft.gameserver.model.GameObjectsStorage;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.base.Experience;
import l2ft.gameserver.model.base.Race;
import l2ft.gameserver.model.instances.NpcInstance;
import l2ft.gameserver.model.quest.QuestState;
import l2ft.gameserver.network.l2.s2c.NpcHtmlMessage;
import l2ft.gameserver.network.l2.s2c.PlaySound;
import l2ft.gameserver.network.l2.s2c.RadarControl;
import l2ft.gameserver.scripts.Functions;
import l2ft.gameserver.templates.npc.NpcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NewbieGuideInstance extends NpcInstance
{
	private static final Logger _log = LoggerFactory.getLogger(NewbieGuideInstance.class);
	private static final List<?> mainHelpers = Arrays.asList(30598, 30599, 30600, 30601, 30602, 32135);

	public NewbieGuideInstance(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}

	@Override
	public void showChatWindow(Player player, int val, Object... arg)
	{
		if(val == 0 && mainHelpers.contains(getNpcId()))
			if(player.getActiveClassClassId().getLevel() == 1)
			{
				if(player.getVar("NewGuidReward") == null)
				{
					QuestState qs = player.getQuestState("_999_T1Tutorial");
					if(qs != null)
						qs.unset("step");

					player.setVar("NewGuidReward", "1", -1);
					boolean isMage = (player.getActiveClassClassId().getRace() != Race.orc) && player.getActiveClassClassId().isMage();
					if(isMage)
					{
						player.sendPacket(new PlaySound("tutorial_voice_027"));
						Functions.addItem(player, 5790, 100); // Spiritshot
					}
					else
					{
						player.sendPacket(new PlaySound("tutorial_voice_026"));
						Functions.addItem(player, 5789, 200); // Soulshot
					}
					Functions.addItem(player, 8594, 2); // Recovery Scroll: NG
					if(player.getLevel() == 1)
						player.addExpAndSp(Experience.LEVEL[2] - player.getExp(), 50, 0, 0, true, false);
					else
						player.addExpAndSp(0, 50, 0, 0, true, false);
				}
				if(player.getLevel() < 6) // FIXME: ĐµŃ�Đ»Đ¸ ĐżĐľĐ»Ń�Ń‡Đ¸Ń‚ŃŚ 6 Đ»ĐµĐ˛ĐµĐ» Đ˛Đľ Đ˛Ń€ĐµĐĽŃŹ ĐşĐ˛ĐµŃ�Ń‚Đ° Ń‚Đľ Đ˝Đ°ĐłŃ€Đ°Đ´Ń� Đ˝Đµ Đ´Đ°Đ´Ń�Ń‚
					if(player.isQuestCompleted("_001_LettersOfLove") || player.isQuestCompleted("_002_WhatWomenWant") || player.isQuestCompleted("_004_LongLivethePaagrioLord") || player.isQuestCompleted("_005_MinersFavor") || player.isQuestCompleted("_166_DarkMass") || player.isQuestCompleted("_174_SupplyCheck"))
					{
						if(!player.getVarB("ng1"))
						{
							String oldVar = player.getVar("ng1");
							player.setVar("ng1", oldVar == null ? "1" : String.valueOf(Integer.parseInt(oldVar) + 1), -1);
							player.addExpAndSp(Experience.LEVEL[6] - player.getExp(), 127, 0, 0, true, false);
							//FIXME [G1ta0] Đ´Đ°Ń‚ŃŚ Đ°Đ´ĐµĐ˝Ń‹, Ń‚ĐľĐ»ŃŚĐşĐľ ĐµŃ�Đ»Đ¸ ĐżĐµŃ€Đ˛Ń‹Đą Ń‡Đ°Ń€ Đ˝Đ° Đ°ĐşĐşĐµ
							player.addAdena(11567);
						}
						player.sendPacket(new NpcHtmlMessage(player, this, "newbiehelper/q1-2.htm", val));
						return;
					}
					else
					{
						player.sendPacket(new NpcHtmlMessage(player, this, "newbiehelper/q1-1.htm", val).replace("%tonpc%", getQuestNpc(1, player)));
						return;
					}
				if(player.getLevel() < 10)
					if(player.getVarB("p1q2"))
					{
						if(!player.getVarB("ng2"))
						{
							String oldVar = player.getVar("ng2");
							player.setVar("ng2", oldVar == null ? "1" : String.valueOf(Integer.parseInt(oldVar) + 1), -1);
							long addexp = Experience.LEVEL[10] - player.getExp();
							player.addExpAndSp(addexp, addexp / 24, 0, 0, true, false);
							//FIXME [G1ta0] Đ´Đ°Ń‚ŃŚ Đ°Đ´ĐµĐ˝Ń‹ ?
						}
						player.sendPacket(new NpcHtmlMessage(player, this, "newbiehelper/q3-1.htm", val).replace("%tonpc%", getQuestNpc(3, player)));
						return;
					}
					else
					{
						player.sendPacket(new NpcHtmlMessage(player, this, "newbiehelper/q2-1.htm", val).replace("%tonpc%", getQuestNpc(2, player)));
						return;
					}
				if(player.getLevel() < 15)
					if(player.getVarB("p1q3"))
					{
						if(!player.getVarB("ng3"))
						{
							String oldVar = player.getVar("ng3");
							player.setVar("ng3", oldVar == null ? "1" : String.valueOf(Integer.parseInt(oldVar) + 1), -1);
							long addexp = Experience.LEVEL[15] - player.getExp();
							player.addExpAndSp(addexp, addexp / 22, 0, 0, true, false);
							//FIXME [G1ta0] Đ´Đ°Ń‚ŃŚ Đ°Đ´ĐµĐ˝Ń‹, Ń‚ĐľĐ»ŃŚĐşĐľ ĐµŃ�Đ»Đ¸ ĐżĐµŃ€Đ˛Ń‹Đą Ń‡Đ°Ń€ Đ˝Đ° Đ°ĐşĐşĐµ
							player.addAdena(38180);
						}
						player.sendPacket(new NpcHtmlMessage(player, this, "newbiehelper/q4-1.htm", val).replace("%tonpc%", getQuestNpc(4, player)));
						return;
					}
					else
					{
						player.sendPacket(new NpcHtmlMessage(player, this, "newbiehelper/q3-1.htm", val).replace("%tonpc%", getQuestNpc(3, player)));
						return;
					}
				if(player.getLevel() < 18)
					if(player.getVarB("p1q4"))
					{
						if(!player.getVarB("ng4"))
						{
							String oldVar = player.getVar("ng4");
							player.setVar("ng4", oldVar == null ? "1" : String.valueOf(Integer.parseInt(oldVar) + 1), -1);
							long addexp = Experience.LEVEL[18] - player.getExp();
							player.addExpAndSp(addexp, addexp / 5, 0, 0, true, false);
							//FIXME [G1ta0] Đ´Đ°Ń‚ŃŚ Đ°Đ´ĐµĐ˝Ń‹, Ń‚ĐľĐ»ŃŚĐşĐľ ĐµŃ�Đ»Đ¸ ĐżĐµŃ€Đ˛Ń‹Đą Ń‡Đ°Ń€ Đ˝Đ° Đ°ĐşĐşĐµ
							player.addAdena(10018);
						}
						player.sendPacket(new NpcHtmlMessage(player, this, "newbiehelper/q4-2.htm", val));
						return;
					}
					else
					{
						player.sendPacket(new NpcHtmlMessage(player, this, "newbiehelper/q4-1.htm", val).replace("%tonpc%", getQuestNpc(4, player)));
						return;
					}

				player.sendPacket(new NpcHtmlMessage(player, this, "newbiehelper/q-no.htm", val));
				return;
			}
			else
			{
				player.sendPacket(new NpcHtmlMessage(player, this, "newbiehelper/q-no.htm", val));
				return;
			}
		super.showChatWindow(player, val);
	}

	public String getQuestNpc(int quest, Player player)
	{
		int val = 0;
		switch(quest)
		{
			case 1: // level 2
				switch(getNpcId())
				{
					case 30598: // Human
						val = 30048; // Darin, _001_LettersOfLove
						break;
					case 30599: // Elf
						val = 30223; // Arujien, _002_WhatWomenWant
						break;
					case 30600: // Dark Elf
						val = 30130; // Undrias, _166_DarkMass
						break;
					case 30601: // Dwarf
						val = 30554; // Bolter, _005_MinersFavor
						break;
					case 30602: // Orc
						val = 30578; // Nakusin, _004_LongLivethePaagrioLord
						break;
					case 32135: // Kamael
						val = 32173; // Marcela, _174_SupplyCheck
						break;
				}
				break;
			case 2: // level 6
				switch(getNpcId())
				{
					case 30598: // Human
						val = 30039; // Gilbert, _257_GuardIsBusy
						break;
					case 30599: // Elf
						val = 30221; // Rayen, _260_HuntTheOrcs
						break;
					case 30600: // Dark Elf
						val = 30357; // Kristin, _265_ChainsOfSlavery
						break;
					case 30601: // Dwarf
						val = 30535; // Filaur, _293_HiddenVein
						break;
					case 30602: // Orc
						val = 30566; // Varkees, _273_InvadersOfHolyland
						break;
					case 32135: // Kamael
						val = 32173; // Marcela, _281_HeadForTheHills
						break;
				}
				break;
			case 3: // level 10
				switch(player.getActiveClassClassId())
				{
					case fighter:
						val = 30008; // Roien, _101_SwordOfSolidarity
						break;
					case mage:
						val = 30017; // Gallint, _104_SpiritOfMirror
						break;
					case elvenFighter:
					case elvenMage:
						val = 30218; // Kendell, _105_SkirmishWithOrcs
						break;
					case darkFighter:
					case darkMage:
						val = 30358; // Thifiell, _106_ForgottenTruth
						break;
					case orcFighter:
					case orcMage:
						val = 30568; // Hatos, _107_MercilessPunishment
						break;
					case dwarvenFighter:
						val = 30523; // Gouph, _108_JumbleTumbleDiamondFuss
						break;
					case maleSoldier:
					case femaleSoldier:
						val = 32138; // Kekropus, _175_TheWayOfTheWarrior
						break;
				}
				break;
			case 4: // level 15
				switch(getNpcId())
				{
					case 30598: // Human
						val = 30050; // Elias, _151_CureforFeverDisease
						break;
					case 30599: // Elf
						val = 30222; // Alshupes, _261_CollectorsDream
						break;
					case 30600: // Dark Elf
						val = 30145; // Vlasty, _169_OffspringOfNightmares
						break;
					case 30601: // Dwarf
						val = 30519; // Mion, _296_SilkOfTarantula
						break;
					case 30602: // Orc
						val = 30571; // Tanapi, _276_HestuiTotem
						break;
					case 32135: // Kamael
						val = 32133; // Perwan, _283_TheFewTheProudTheBrave
						break;
				}
				break;
		}

		if(val == 0)
		{
			_log.warn("WTF? L2NewbieGuideInstance " + getNpcId() + " not found next step " + quest + " for " + player.getActiveClassClassId());
			return null;
		}

		NpcInstance npc = GameObjectsStorage.getByNpcId(val);
		if(npc == null)
			return "";

		player.sendPacket(new RadarControl(2, 1, npc.getLoc()));// ĐŁĐ±Đ¸Ń€Đ°ĐµĐĽ Ń„Đ»Đ°Đ¶ĐľĐş Đ˝Đ° ĐşĐ°Ń€Ń‚Đµ Đ¸ Ń�Ń‚Ń€ĐµĐ»ĐşŃ� Đ˝Đ° ĐşĐľĐĽĐżĐ°Ń�Đµ
		player.sendPacket(new RadarControl(0, 2, npc.getLoc()));// ĐˇŃ‚Đ°Đ˛Đ¸ĐĽ Ń„Đ»Đ°Đ¶ĐľĐş Đ˝Đ° ĐşĐ°Ń€Ń‚Đµ Đ¸ Ń�Ń‚Ń€ĐµĐ»ĐşŃ� Đ˝Đ° ĐşĐľĐĽĐżĐ°Ń�Đµ

		return npc.getName();
	}

	@Override
	public String getHtmlPath(int npcId, int val, Player player)
	{
		String pom;
		if(val == 0)
			pom = "" + npcId;
		else
			pom = npcId + "-" + val;

		return "newbiehelper/" + pom + ".htm";
	}
}
