package l2ft.gameserver.network.l2.s2c;

import java.util.Collection;

import l2ft.gameserver.data.xml.holder.PetitionGroupHolder;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.petition.PetitionMainGroup;
import l2ft.gameserver.utils.Language;

/**
 * @author VISTALL
 */
public class ExResponseShowStepOne extends L2GameServerPacket
{
	private Language _language;

	public ExResponseShowStepOne(Player player)
	{
		_language = Language.ENGLISH;
	}

	@Override
	protected void writeImpl()
	{
		writeEx(0xAE);
		Collection<PetitionMainGroup> petitionGroups = PetitionGroupHolder.getInstance().getPetitionGroups();
		writeD(petitionGroups.size());
		for(PetitionMainGroup group : petitionGroups)
		{
			writeC(group.getId());
			writeS(group.getName(_language));
		}
	}
}