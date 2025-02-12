import l2ft.gameserver.Config;
import l2ft.gameserver.cache.Msg;
import l2ft.gameserver.data.xml.holder.ResidenceHolder;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.entity.SevenSigns;
import l2ft.gameserver.model.entity.residence.Castle;
import l2ft.gameserver.model.instances.NpcInstance;
import l2ft.gameserver.network.l2.components.CustomMessage;
import l2ft.gameserver.network.l2.components.SystemMsg;
import l2ft.gameserver.network.l2.s2c.SystemMessage2;
import l2ft.gameserver.scripts.Functions;
import l2ft.gameserver.utils.Location;

public class Util extends Functions
{
	public void Gatekeeper(String[] param)
	{
		if(param.length < 4)
			throw new IllegalArgumentException();

		Player player = getSelf();
		if(player == null)
			return;

		long price = Long.parseLong(param[param.length - 1]);

		if(!NpcInstance.canBypassCheck(player, player.getLastNpc()))
			return;

		if(price > 0 && player.getAdena() < price)
		{
			player.sendPacket(Msg.YOU_DO_NOT_HAVE_ENOUGH_ADENA);
			return;
		}

		if(player.getMountType() == 2)
		{
			player.sendMessage("Đ˘ĐµĐ»ĐµĐżĐľŃ€Ń‚Đ°Ń†Đ¸ŃŹ Đ˛ĐµŃ€Ń…ĐľĐĽ Đ˝Đ° Đ˛Đ¸Đ˛ĐµŃ€Đ˝Đµ Đ˝ĐµĐ˛ĐľĐ·ĐĽĐľĐ¶Đ˝Đ°.");
			return;
		}

		/* Đ—Đ°Ń‚Ń‹Ń‡ĐşĐ°, npc Mozella Đ˝Đµ Đ˘ĐźŃ�Đ¸Ń‚ Ń‡Đ°Ń€ĐľĐ˛ Ń�Ń€ĐľĐ˛ĐµĐ˝ŃŚ ĐşĐľŃ‚ĐľŃ€Ń‹Ń… ĐżŃ€ĐµĐ˛Ń‹Ń�Đ°ĐµŃ‚ Đ·Đ°Đ´Đ°Đ˝Đ˝Ń‹Đą Đ˛ ĐşĐľĐ˝Ń„Đ¸ĐłĐµ
		 * Off Like >= 56 lvl, Đ´Đ°Đ˝Đ˝Ń‹Đµ ĐżĐľ ĐľĐłŃ€Đ°Đ˝Đ¸Ń‡ĐµĐ˝Đ¸ŃŽ lvl'a Ń�Ń�Ń‚Đ°Đ˝Đ°Đ˛Đ»Đ¸Đ˛Đ°ŃŽŃ‚Ń�ŃŹ Đ˛ altsettings.properties.
		 */
		if(player.getLastNpc() != null)
		{
			int npcId = player.getLastNpc().getNpcId();
			switch(npcId)
			{
				case 30483:
					if(player.getLevel() >= Config.CRUMA_GATEKEEPER_LVL)
					{
						show("teleporter/30483-no.htm", player);
						return;
					}
					break;
				case 32864:
				case 32865:
				case 32866:
				case 32867:
				case 32868:
				case 32869:
				case 32870:
					if(player.getLevel() < 80)
					{
						show("teleporter/"+npcId+"-no.htm", player);
						return;
					}
					break;
			}
		}

		int x = Integer.parseInt(param[0]);
		int y = Integer.parseInt(param[1]);
		int z = Integer.parseInt(param[2]);
		int castleId = param.length > 4 ? Integer.parseInt(param[3]) : 0;

		if(player.getReflection().isDefault())
		{
			Castle castle = castleId > 0 ? ResidenceHolder.getInstance().getResidence(Castle.class, castleId) : null;
			// ĐťĐµĐ»ŃŚĐ·ŃŹ Ń‚ĐµĐ»ĐµĐżĐľŃ€Ń‚Đ¸Ń€ĐľĐ˛Đ°Ń‚ŃŚŃ�ŃŹ Đ˛ ĐłĐľŃ€ĐľĐ´Đ°, ĐłĐ´Đµ Đ¸Đ´ĐµŃ‚ ĐľŃ�Đ°Đ´Đ°
			if(castle != null && castle.getSiegeEvent().isInProgress())
			{
				player.sendPacket(Msg.YOU_CANNOT_TELEPORT_TO_A_VILLAGE_THAT_IS_IN_A_SIEGE);
				return;
			}
		}

		Location pos = Location.findPointToStay(x, y, z, 50, 100, player.getGeoIndex());

		if(price > 0)
			player.reduceAdena(price, true);
		player.teleToLocation(pos);
	}

	public void SSGatekeeper(String[] param)
	{
		if(param.length < 4)
			throw new IllegalArgumentException();

		Player player = getSelf();
		if(player == null)
			return;

		int type = Integer.parseInt(param[3]);

		if(!NpcInstance.canBypassCheck(player, player.getLastNpc()))
			return;

		if(type > 0)
		{
			int player_cabal = SevenSigns.getInstance().getPlayerCabal(player);
			int period = SevenSigns.getInstance().getCurrentPeriod();
			if(period == SevenSigns.PERIOD_COMPETITION && player_cabal == SevenSigns.CABAL_NULL)
			{
				player.sendPacket(Msg.USED_ONLY_DURING_A_QUEST_EVENT_PERIOD);
				return;
			}

			int winner;
			if(period == SevenSigns.PERIOD_SEAL_VALIDATION && (winner = SevenSigns.getInstance().getCabalHighestScore()) != SevenSigns.CABAL_NULL)
			{
				if(winner != player_cabal)
					return;
				if(type == 1 && SevenSigns.getInstance().getSealOwner(SevenSigns.SEAL_AVARICE) != player_cabal)
					return;
				if(type == 2 && SevenSigns.getInstance().getSealOwner(SevenSigns.SEAL_GNOSIS) != player_cabal)
					return;
			}
		}

		player.teleToLocation(Integer.parseInt(param[0]), Integer.parseInt(param[1]), Integer.parseInt(param[2]));
	}

	public void QuestGatekeeper(String[] param)
	{
		if(param.length < 5)
			throw new IllegalArgumentException();

		Player player = getSelf();
		if(player == null)
			return;

		long count = Long.parseLong(param[3]);
		int item = Integer.parseInt(param[4]);

		if(!NpcInstance.canBypassCheck(player, player.getLastNpc()))
			return;

		if(count > 0)
		{
			if(!player.getInventory().destroyItemByItemId(item, count))
			{
				player.sendPacket(Msg.YOU_DO_NOT_HAVE_ENOUGH_REQUIRED_ITEMS);
				return;
			}
			player.sendPacket(SystemMessage2.removeItems(item, count));
		}

		int x = Integer.parseInt(param[0]);
		int y = Integer.parseInt(param[1]);
		int z = Integer.parseInt(param[2]);

		Location pos = Location.findPointToStay(x, y, z, 20, 70, player.getGeoIndex());

		player.teleToLocation(pos);
	}

	public void ReflectionGatekeeper(String[] param)
	{
		if(param.length < 5)
			throw new IllegalArgumentException();

		Player player = getSelf();
		if(player == null)
			return;

		player.setReflection(Integer.parseInt(param[4]));

		Gatekeeper(param);
	}

	/**
	 * Đ�Ń�ĐżĐľĐ»ŃŚĐ·Ń�ĐµŃ‚Ń�ŃŹ Đ´Đ»ŃŹ Ń‚ĐµĐ»ĐµĐżĐľŃ€Ń‚Đ°Ń†Đ¸Đ¸ Đ·Đ° Newbie Token, ĐżŃ€ĐľĐ˛ĐµŃ€ŃŹĐµŃ‚ Ń�Ń€ĐľĐ˛ĐµĐ˝ŃŚ Đ¸ ĐżĐµŃ€ĐµĐ´Đ°ĐµŃ‚
	 * ĐżĐ°Ń€Đ°ĐĽĐµŃ‚Ń€Ń‹ Đ˛ QuestGatekeeper
	 */
	public void TokenJump(String[] param)
	{
		Player player = getSelf();
		if(player == null)
			return;
		if(player.getLevel() <= 19)
			QuestGatekeeper(param);
		else
			show("Only for newbies", player);
	}

	public void NoblessTeleport()
	{
		Player player = getSelf();
		if(player == null)
			return;
		if(player.isNoble() || Config.ALLOW_NOBLE_TP_TO_ALL)
			show("scripts/noble.htm", player);
		else
			show("scripts/nobleteleporter-no.htm", player);
	}

	public void PayPage(String[] param)
	{
		if(param.length < 2)
			throw new IllegalArgumentException();

		Player player = getSelf();
		if(player == null)
			return;

		String page = param[0];
		int item = Integer.parseInt(param[1]);
		long price = Long.parseLong(param[2]);

		if(getItemCount(player, item) < price)
		{
			player.sendPacket(item == 57 ? Msg.YOU_DO_NOT_HAVE_ENOUGH_ADENA : SystemMsg.INCORRECT_ITEM_COUNT);
			return;
		}

		removeItem(player, item, price);
		show(page, player);
	}

	public void SimpleExchange(String[] param)
	{
		if(param.length < 4)
			throw new IllegalArgumentException();

		Player player = getSelf();
		if(player == null)
			return;

		int itemToTake = Integer.parseInt(param[0]);
		long countToTake = Long.parseLong(param[1]);
		int itemToGive = Integer.parseInt(param[2]);
		long countToGive = Long.parseLong(param[3]);

		if(getItemCount(player, itemToTake) < countToTake)
		{
			player.sendPacket(itemToTake == 57 ? Msg.YOU_DO_NOT_HAVE_ENOUGH_ADENA : SystemMsg.INCORRECT_ITEM_COUNT);
			return;
		}

		removeItem(player, itemToTake, countToTake);
		addItem(player, itemToGive, countToGive);
	}
	public void MakeEchoCrystal(String[] param)
	{
		if(param.length < 2)
			throw new IllegalArgumentException();

		Player player = getSelf();
		if(player == null)
			return;

		if(!NpcInstance.canBypassCheck(player, player.getLastNpc()))
			return;

		int crystal = Integer.parseInt(param[0]);
		int score = Integer.parseInt(param[1]);

		if(crystal < 4411 || crystal > 4417)
			return;

		if(getItemCount(player, score) == 0)
		{
			player.getLastNpc().onBypassFeedback(player, "Chat 1");
			return;
		}

		if(getItemCount(player, 57) < 200)
		{
			player.sendPacket(Msg.YOU_DO_NOT_HAVE_ENOUGH_ADENA);
			return;
		}

		removeItem(player, 57, 200);
		addItem(player, crystal, 1);
	}

	public void TakeNewbieWeaponCoupon()
	{
		Player player = getSelf();
		if(player == null)
			return;
		if(!Config.ALT_ALLOW_SHADOW_WEAPONS)
		{
			show(new CustomMessage("common.Disabled", player), player);
			return;
		}
		if(player.getLevel() > 19 || player.getActiveClassClassId().getLevel() > 1)
		{
			show("Your level is too high!", player);
			return;
		}
		if(player.getLevel() < 6)
		{
			show("Your level is too low!", player);
			return;
		}
		if(player.getVarB("newbieweapon"))
		{
			show("Your already got your newbie weapon!", player);
			return;
		}
		addItem(player, 7832, 5);
		player.setVar("newbieweapon", "true", -1);
	}

	public void TakeAdventurersArmorCoupon()
	{
		Player player = getSelf();
		if(player == null)
			return;
		if(!Config.ALT_ALLOW_SHADOW_WEAPONS)
		{
			show(new CustomMessage("common.Disabled", player), player);
			return;
		}
		if(player.getLevel() > 39 || player.getActiveClassClassId().getLevel() > 2)
		{
			show("Your level is too high!", player);
			return;
		}
		if(player.getLevel() < 20 || player.getActiveClassClassId().getLevel() < 2)
		{
			show("Your level is too low!", player);
			return;
		}
		if(player.getVarB("newbiearmor"))
		{
			show("Your already got your newbie weapon!", player);
			return;
		}
		addItem(player, 7833, 1);
		player.setVar("newbiearmor", "true", -1);
	}

	public void enter_dc()
	{
		Player player = getSelf();
		NpcInstance npc = getNpc();
		if(player == null || npc == null)
			return;

		if(!NpcInstance.canBypassCheck(player, npc))
			return;

		player.setVar("DCBackCoords", player.getLoc().toXYZString(), -1);
		player.teleToLocation(-114582, -152635, -6742);
	}

	public void exit_dc()
	{
		Player player = getSelf();
		NpcInstance npc = getNpc();
		if(player == null || npc == null)
			return;

		if(!NpcInstance.canBypassCheck(player, npc))
			return;

		String var = player.getVar("DCBackCoords");
		if(var == null || var.isEmpty())
		{
			player.teleToLocation(new Location(43768, -48232, -800), 0);
			return;
		}
		player.teleToLocation(Location.parseLoc(var), 0);
		player.unsetVar("DCBackCoords");
	}
}