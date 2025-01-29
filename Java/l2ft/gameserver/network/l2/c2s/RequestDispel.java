package l2ft.gameserver.network.l2.c2s;

import l2ft.gameserver.model.Creature;
import l2ft.gameserver.model.Effect;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.Skill.SkillType;
import l2ft.gameserver.skills.EffectType;

public class RequestDispel extends L2GameClientPacket
{
	private int _objectId, _id, _level;

	@Override
	protected void readImpl() throws Exception
	{
		_objectId = readD();
		_id = readD();
		_level = readD();
	}

	@Override
	protected void runImpl() throws Exception
	{
		Player activeChar = getClient().getActiveChar();
		if(activeChar == null || activeChar.getObjectId() != _objectId && activeChar.getPet() == null)
			return;

		Creature target = activeChar;
		if(activeChar.getObjectId() != _objectId)
			target = activeChar.getPet();

		for(Effect e : target.getEffectList().getAllEffects())
			if(e.getDisplayId() == _id && e.getDisplayLevel() == _level)
				if(!e.isOffensive() && e.getSkill().isSelfDispellable() && e.getSkill().getSkillType() != SkillType.TRANSFORMATION && e.getTemplate().getEffectType() != EffectType.Hourglass)
					e.exit();
				else
					return;
	}
}