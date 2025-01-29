package handler.items;

import l2ft.gameserver.model.Playable;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.entity.tournament.TournamentManager;
import l2ft.gameserver.model.items.ItemInstance;
import l2ft.gameserver.network.l2.s2c.SkillList;
import l2ft.gameserver.network.l2.s2c.SystemMessage;
import l2ft.gameserver.tables.SkillTable;

public class SupportPower extends ScriptItemHandler
{
	private static final int[] ITEM_IDS = new int[] { 24001 };
	private static final int[] CLASS_IDS = new int[] { 97, 98, 100, 105, 107, 112, 115, 116 };

	@Override
	public int[] getItemIds()
	{
		return ITEM_IDS;
	}

	private int[] getClassIds()
	{
		return CLASS_IDS;
	}

	public boolean useItem(Playable playable, ItemInstance item, boolean ctrl)
	{
		if(playable == null || !playable.isPlayer())
			return false;

		Player player = playable.getPlayer();

		int itemId = item.getItemId();
		int classId = player.getActiveClassClassId().getId();

		if(player.isInOlympiadMode())
		{
			player.sendPacket(new SystemMessage(SystemMessage.S1_CANNOT_BE_USED_DUE_TO_UNSUITABLE_TERMS).addItemName(itemId));
			return false;
		}
		
		if(TournamentManager.getInstance().isPlayerAtTournamentStart(player))
		{
			player.sendPacket(new SystemMessage(SystemMessage.S1_CANNOT_BE_USED_DUE_TO_UNSUITABLE_TERMS).addItemName(itemId));
			return false;
		}

		if(player.getLevel() < 76)
		{
			player.sendMessage(player.isLangRus() ? "Đ Đ°Đ·Ń€ĐµŃ�ĐµĐ˝Đľ Đ¸Ń�ĐżĐľĐ»ŃŚĐ·ĐľĐ˛Đ°Ń‚ŃŚ Ń‚ĐľĐ»ŃŚĐşĐľ Ń� 3-Đą ĐżŃ€ĐľŃ„ĐµŃ�Ń�Đ¸ĐµĐą!" : "Use only a third profession!");
			player.sendMessage(player.isLangRus() ? "Đ Đ°Đ·Ń€ĐµŃ�ĐµĐ˝Đľ Đ¸Ń�ĐżĐľĐ»ŃŚĐ·ĐľĐ˛Đ°Ń‚ŃŚ Ń‚ĐľĐ»ŃŚĐşĐľ Đ˝Đ° ĐľŃ�Đ˝ĐľĐ˛Đ˝ĐľĐĽ ĐşĐ»Đ°Ń�Ń�Đµ!" : "Use only on the main class!");
			return false;
		}

		switch(classId)
		{
			case 97://Cardinal
				player.addSkill(SkillTable.getInstance().getInfo(24001, 1), false);
				player.updateStats();
				player.sendPacket(new SkillList(player));
				break;
			case 98://Hierophant
				player.addSkill(SkillTable.getInstance().getInfo(24002, 1), false);
				player.updateStats();
				player.sendPacket(new SkillList(player));
				break;
			case 100://SwordMuse
				player.addSkill(SkillTable.getInstance().getInfo(24003, 1), false);
				player.updateStats();
				player.sendPacket(new SkillList(player));
				break;
			case 105://EvaSaint
				player.addSkill(SkillTable.getInstance().getInfo(24004, 1), false);
				player.updateStats();
				player.sendPacket(new SkillList(player));
				break;
			case 107://SpectralDancer
				player.addSkill(SkillTable.getInstance().getInfo(24005, 1), false);
				player.updateStats();
				player.sendPacket(new SkillList(player));
				break;
			case 112://ShillienSaint
				player.addSkill(SkillTable.getInstance().getInfo(24006, 1), false);
				player.updateStats();
				player.sendPacket(new SkillList(player));
				break;
			case 115://Dominator
				player.addSkill(SkillTable.getInstance().getInfo(24007, 1), false);
				player.updateStats();
				player.sendPacket(new SkillList(player));
				break;
			case 116://Doomcryer
				player.addSkill(SkillTable.getInstance().getInfo(24008, 1), false);
				player.updateStats();
				player.sendPacket(new SkillList(player));
				break;
			default:
				return false;
		}
		return true;
	}
}