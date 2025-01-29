package l2ft.gameserver.network.l2.c2s;

import l2ft.gameserver.model.base.ClassId;
import l2ft.gameserver.network.l2.s2c.NewCharacterSuccess;
import l2ft.gameserver.tables.CharTemplateTable;

public class NewCharacter extends L2GameClientPacket
{
	@Override
	protected void readImpl()
	{}

	@Override
	protected void runImpl()
	{
		NewCharacterSuccess ct = new NewCharacterSuccess();

		ct.addChar(CharTemplateTable.getInstance().getTemplate(ClassId.fighter.getId(), ClassId.fighter, false, false));
		ct.addChar(CharTemplateTable.getInstance().getTemplate(ClassId.mage.getId(), ClassId.mage,false, false));
		ct.addChar(CharTemplateTable.getInstance().getTemplate(ClassId.elvenFighter.getId(), ClassId.elvenFighter,false, false));
		ct.addChar(CharTemplateTable.getInstance().getTemplate(ClassId.elvenMage.getId(), ClassId.elvenMage,false, false));
		ct.addChar(CharTemplateTable.getInstance().getTemplate(ClassId.darkFighter.getId(), ClassId.darkFighter, false,false));
		ct.addChar(CharTemplateTable.getInstance().getTemplate(ClassId.darkMage.getId(), ClassId.darkMage,false, false));
		ct.addChar(CharTemplateTable.getInstance().getTemplate(ClassId.orcFighter.getId(), ClassId.orcFighter,false, false));
		ct.addChar(CharTemplateTable.getInstance().getTemplate(ClassId.orcMage.getId(), ClassId.orcMage,false, false));
		ct.addChar(CharTemplateTable.getInstance().getTemplate(ClassId.dwarvenFighter.getId(), ClassId.dwarvenFighter,false, false));
		ct.addChar(CharTemplateTable.getInstance().getTemplate(ClassId.maleSoldier.getId(), ClassId.maleSoldier,false, false));
		ct.addChar(CharTemplateTable.getInstance().getTemplate(ClassId.femaleSoldier.getId(), ClassId.femaleSoldier,false, false));

		sendPacket(ct);
	}
}