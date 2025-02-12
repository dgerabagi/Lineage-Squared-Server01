package l2ft.gameserver.model.instances;

import l2ft.gameserver.templates.npc.NpcTemplate;

/**
 * Это алиас L2MonsterInstance используемый для монстров, у которых нестандартные статы
 */
public class SpecialMonsterInstance extends MonsterInstance
{
	public SpecialMonsterInstance(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}

	@Override
	public boolean canChampion()
	{
		return false;
	}
}