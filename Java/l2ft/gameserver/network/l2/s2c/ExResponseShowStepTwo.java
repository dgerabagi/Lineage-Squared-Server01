package l2ft.gameserver.network.l2.s2c;

import java.util.Collection;

import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.petition.PetitionMainGroup;
import l2ft.gameserver.model.petition.PetitionSubGroup;
import l2ft.gameserver.utils.Language;

/**
 * @author VISTALL
 */
public class ExResponseShowStepTwo extends L2GameServerPacket
{
	private Language _language;
	private PetitionMainGroup _petitionMainGroup;

	public ExResponseShowStepTwo(Player player, PetitionMainGroup gr)
	{
		_language = Language.ENGLISH;
		_petitionMainGroup = gr;
	}

	@Override
	protected void writeImpl()
	{
		writeEx(0xAF);
		Collection<PetitionSubGroup> subGroups = _petitionMainGroup.getSubGroups();
		writeD(subGroups.size());
		writeS(_petitionMainGroup.getDescription(_language));
		for(PetitionSubGroup g : subGroups)
		{
			writeC(g.getId());
			writeS(g.getName(_language));
		}
	}
}