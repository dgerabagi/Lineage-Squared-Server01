package l2ft.gameserver.model.instances.residences.clanhall;

import org.apache.commons.lang3.StringUtils;
import l2ft.gameserver.model.Creature;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.Skill;
import l2ft.gameserver.model.entity.events.impl.ClanHallTeamBattleEvent;
import l2ft.gameserver.model.entity.events.objects.CTBSiegeClanObject;
import l2ft.gameserver.model.entity.events.objects.CTBTeamObject;
import l2ft.gameserver.model.instances.MonsterInstance;
import l2ft.gameserver.tables.SkillTable;
import l2ft.gameserver.templates.npc.NpcTemplate;

/**
 * @author VISTALL
 * @date 16:55/21.04.2011
 */
public abstract class CTBBossInstance extends MonsterInstance
{
	public static final Skill SKILL = SkillTable.getInstance().getInfo(5456, 1);
	private CTBTeamObject _matchTeamObject;

	public CTBBossInstance(int objectId, NpcTemplate template)
	{
		super(objectId, template);
		setHasChatWindow(false);
	}

	@Override
	public void reduceCurrentHp(double damage, Creature attacker, Skill skill, boolean awake, boolean standUp, boolean directHp, boolean canReflect, boolean transferDamage, boolean isDot, boolean sendMessage)
	{
		if(attacker.getLevel() > (getLevel() + 8) && attacker.getEffectList().getEffectsCountForSkill(SKILL.getId()) == 0)
		{
			doCast(SKILL, attacker, false);
			return;
		}

		super.reduceCurrentHp(damage, attacker, skill, awake, standUp, directHp, canReflect, transferDamage, isDot, sendMessage);
	}

	@Override
	public boolean isAttackable(Creature attacker)
	{
		CTBSiegeClanObject clan = _matchTeamObject.getSiegeClan();
		if(clan != null && attacker.isPlayable())
		{
			Player player = attacker.getPlayer();
			if(player.getClan() == clan.getClan())
				return false;
		}
		return true;
	}

	@Override
	public boolean isAutoAttackable(Creature attacker)
	{
		return isAttackable(attacker);
	}

	@Override
	public void onDeath(Creature killer)
	{
		ClanHallTeamBattleEvent event = getEvent(ClanHallTeamBattleEvent.class);
		event.processStep(_matchTeamObject);

		super.onDeath(killer);
	}

	@Override
	public String getTitle()
	{
		CTBSiegeClanObject clan = _matchTeamObject.getSiegeClan();
		return clan == null ? StringUtils.EMPTY : clan.getClan().getName();
	}

	public void setMatchTeamObject(CTBTeamObject matchTeamObject)
	{
		_matchTeamObject = matchTeamObject;
	}
}
