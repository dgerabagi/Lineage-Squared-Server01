package l2ft.gameserver.listener.zone.impl;

import l2ft.gameserver.listener.zone.OnZoneEnterLeaveListener;
import l2ft.gameserver.model.Creature;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.Zone;
import l2ft.gameserver.model.entity.residence.Residence;
import l2ft.gameserver.model.entity.residence.ResidenceFunction;
import l2ft.gameserver.stats.Stats;
import l2ft.gameserver.stats.funcs.FuncMul;

/**
 * @author VISTALL
 * @date 16:04/03.07.2011
 */
public class ResidenceEnterLeaveListenerImpl implements OnZoneEnterLeaveListener
{
	public static final OnZoneEnterLeaveListener STATIC = new ResidenceEnterLeaveListenerImpl();

	@Override
	public void onZoneEnter(Zone zone, Creature actor)
	{
		if(!actor.isPlayer())
			return;

		Player player = (Player) actor;
		Residence residence = (Residence)zone.getParams().get("residence");

		if(residence.getOwner() == null || residence.getOwner() != player.getClan())
			return;

		if(residence.isFunctionActive(ResidenceFunction.RESTORE_HP))
		{
			double value = 1. + residence.getFunction(ResidenceFunction.RESTORE_HP).getLevel() / 100.;

			player.addStatFunc(new FuncMul(Stats.REGENERATE_HP_RATE, 0x30, residence, value));
		}

		if(residence.isFunctionActive(ResidenceFunction.RESTORE_MP))
		{
			double value = 1. + residence.getFunction(ResidenceFunction.RESTORE_MP).getLevel() / 100.;

			player.addStatFunc(new FuncMul(Stats.REGENERATE_MP_RATE, 0x30, residence, value));
		}
	}

	@Override
	public void onZoneLeave(Zone zone, Creature actor)
	{
		if(!actor.isPlayer())
			return;

		Residence residence = (Residence)zone.getParams().get("residence");

		actor.removeStatsOwner(residence);
	}
}
