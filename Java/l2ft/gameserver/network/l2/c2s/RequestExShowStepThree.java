package l2ft.gameserver.network.l2.c2s;

import l2ft.gameserver.Config;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.petition.PetitionMainGroup;
import l2ft.gameserver.model.petition.PetitionSubGroup;
import l2ft.gameserver.network.l2.s2c.ExResponseShowContents;
import l2ft.gameserver.utils.Language;

public class RequestExShowStepThree extends L2GameClientPacket
{
	private int _subId;

	@Override
	protected void readImpl()
	{
		_subId = readC();
	}

	@Override
	protected void runImpl()
	{
		Player player = getClient().getActiveChar();
		if(player == null || !Config.EX_NEW_PETITION_SYSTEM)
			return;

		PetitionMainGroup group = player.getPetitionGroup();
		if(group == null)
			return;

		PetitionSubGroup subGroup = group.getSubGroup(_subId);
		if(subGroup == null)
			return;

		player.sendPacket(new ExResponseShowContents(subGroup.getDescription(Language.ENGLISH)));
	}
}