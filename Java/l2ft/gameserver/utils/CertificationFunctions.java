package l2ft.gameserver.utils;

import java.util.Collection;

import l2ft.gameserver.data.xml.holder.SkillAcquireHolder;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.Skill;
import l2ft.gameserver.model.SkillLearn;
import l2ft.gameserver.model.base.AcquireType;
import l2ft.gameserver.model.base.ClassType2;
import l2ft.gameserver.model.instances.NpcInstance;
import l2ft.gameserver.network.l2.components.CustomMessage;
import l2ft.gameserver.network.l2.components.SystemMsg;
import l2ft.gameserver.network.l2.s2c.SkillList;
import l2ft.gameserver.scripts.Functions;


/**
 * @author VISTALL
 * @date  17:49/08.12.2010
 */
public class CertificationFunctions
{
	public static final String PATH = "villagemaster/certification/";

	public static void showCertificationList(NpcInstance npc, Player player)
	{
		if (!checkConditions(65, npc, player, true))
		{
			return;
		}

		Functions.show(PATH + "certificatelist.htm", player, npc);
	}

	public static void cancelCertification(NpcInstance npc, Player player)
	{
		if(player.getInventory().getAdena() < 10000000)
		{
			player.sendPacket(SystemMsg.YOU_DO_NOT_HAVE_ENOUGH_ADENA);
			return;
		}

		if(player.getActiveClass().isKamael())
			return;

		player.getInventory().reduceAdena(10000000);

		Collection<SkillLearn> skillLearnList = SkillAcquireHolder.getInstance().getAvailableSkills(null, AcquireType.CERTIFICATION);
		for(SkillLearn learn : skillLearnList)
		{
			Skill skill = player.getKnownSkill(learn.getId());
			if(skill != null)
			{
				player.removeSkill(skill, true);
				player.getInventory().addItem(learn.getItemId(), skill.getLevel());
			}
		}

		player.sendPacket(new SkillList(player));
		Functions.show(new CustomMessage("scripts.services.SubclassSkills.SkillsDeleted", player), player);
	}

	public static boolean checkConditions(int level, NpcInstance npc, Player player, boolean first)
	{
		if (player.getLevel() < level)
		{
			Functions.show(PATH + "certificate-nolevel.htm", player, npc, "%level%", level);
			return false;
		}

		if (player.getActiveClass().isKamael())
		{
			Functions.show(PATH + "certificate-nosub.htm", player, npc);
			return false;
		}

		if (first)
		{
			return true;
		}

		for (ClassType2 type : ClassType2.VALUES)
		{
			if (player.getInventory().getCountOf(type.getCertificateId()) > 0 || player.getInventory().getCountOf(type.getTransformationId()) > 0)
			{
				Functions.show(PATH + "certificate-already.htm", player, npc);
				return false;
			}
		}

		return true;
	}
}
