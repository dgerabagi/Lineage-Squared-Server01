package services;

import java.util.Date;

import l2ft.gameserver.Config;
import l2ft.gameserver.dao.AccountBonusDAO;
import l2ft.gameserver.data.htm.HtmCache;
import l2ft.gameserver.data.xml.holder.ItemHolder;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.actor.instances.player.Bonus;
import l2ft.gameserver.network.authcomm.AuthServerCommunication;
import l2ft.gameserver.network.authcomm.gspackets.BonusRequest;
import l2ft.gameserver.network.l2.components.SystemMsg;
import l2ft.gameserver.network.l2.s2c.ExBR_PremiumState;
import l2ft.gameserver.scripts.Functions;
import l2ft.gameserver.utils.Log;

public class RateBonus extends Functions
{
	public void list()
	{
		Player player = getSelf();
		if(Config.SERVICES_RATE_TYPE == Bonus.NO_BONUS)
		{
			show(HtmCache.getInstance().getNotNull("npcdefault.htm", player), player);
			return;
		}
		String html = null;
		if(player.getNetConnection().getBonus() <= 1)
		{
			html = HtmCache.getInstance().getNotNull("scripts/services/RateBonus.htm", player);
			String add = "";
			for(int i = 0; i < Config.SERVICES_RATE_BONUS_DAYS.length; i++)
				add += "<a action=\"bypass -h scripts_services.RateBonus:get " + i + "\">" //
						+ (int) (Config.SERVICES_RATE_BONUS_VALUE[i] * 100 - 100) + //
						"% for " + Config.SERVICES_RATE_BONUS_DAYS[i] + //
						" days - " + Config.SERVICES_RATE_BONUS_PRICE[i] + //
						" " + ItemHolder.getInstance().getTemplate(Config.SERVICES_RATE_BONUS_ITEM[i]).getName() + "</a><br>";
			html = html.replaceFirst("%toreplace%", add);
		}
		else
		{
			html = HtmCache.getInstance().getNotNull("scripts/services/RateBonusAlready.htm", player);
			int endtime = player.getNetConnection().getBonusExpire();
			if(endtime >= System.currentTimeMillis() / 1000L)
				html = HtmCache.getInstance().getNotNull("scripts/services/RateBonusAlready.htm", player).replaceFirst("endtime", new Date(endtime * 1000L).toString());
		}
		show(html, player);
	}

	public void get(String[] param)
	{
		Player player = getSelf();
		if(Config.SERVICES_RATE_TYPE == Bonus.NO_BONUS)
		{
			show(HtmCache.getInstance().getNotNull("npcdefault.htm", player), player);
			return;
		}
		int i = Integer.parseInt(param[0]);
		if(!player.getInventory().destroyItemByItemId(Config.SERVICES_RATE_BONUS_ITEM[i], Config.SERVICES_RATE_BONUS_PRICE[i]))
		{
			if(Config.SERVICES_RATE_BONUS_ITEM[i] == 57)
				player.sendPacket(SystemMsg.YOU_DO_NOT_HAVE_ENOUGH_ADENA);
			else
				player.sendPacket(SystemMsg.INCORRECT_ITEM_COUNT);
			return;
		}
		if(Config.SERVICES_RATE_TYPE == Bonus.BONUS_GLOBAL_ON_AUTHSERVER && AuthServerCommunication.getInstance().isShutdown())
		{
			list();
			return;
		}
		Log.add(player.getName() + "|" + player.getObjectId() + "|rate bonus|" + Config.SERVICES_RATE_BONUS_VALUE[i] + "|" + Config.SERVICES_RATE_BONUS_DAYS[i] + "|", "services");
		double bonus = Config.SERVICES_RATE_BONUS_VALUE[i];
		int bonusExpire = (int) (System.currentTimeMillis() / 1000L) + Config.SERVICES_RATE_BONUS_DAYS[i] * 24 * 60 * 60;
		switch(Config.SERVICES_RATE_TYPE)
		{
			case Bonus.BONUS_GLOBAL_ON_AUTHSERVER:
				AuthServerCommunication.getInstance().sendPacket(new BonusRequest(player.getAccountName(), bonus, bonusExpire));
				break;
			case Bonus.BONUS_GLOBAL_ON_GAMESERVER:
				AccountBonusDAO.getInstance().insert(player.getAccountName(), bonus, bonusExpire);
				break;
		}
		player.getNetConnection().setBonus(bonus);
		player.getNetConnection().setBonusExpire(bonusExpire);
		player.stopBonusTask();
		player.startBonusTask();
		if(player.getParty() != null)
			player.getParty().recalculatePartyData();
		player.sendPacket(new ExBR_PremiumState(player, true));
		show(HtmCache.getInstance().getNotNull("scripts/services/RateBonusGet.htm", player), player);
	}
}