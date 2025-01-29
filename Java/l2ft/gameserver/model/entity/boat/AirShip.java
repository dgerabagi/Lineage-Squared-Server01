package l2ft.gameserver.model.entity.boat;

import l2ft.gameserver.model.Player;
import l2ft.gameserver.network.l2.s2c.ExAirShipInfo;
import l2ft.gameserver.network.l2.s2c.ExGetOffAirShip;
import l2ft.gameserver.network.l2.s2c.ExGetOnAirShip;
import l2ft.gameserver.network.l2.s2c.ExMoveToLocationAirShip;
import l2ft.gameserver.network.l2.s2c.ExMoveToLocationInAirShip;
import l2ft.gameserver.network.l2.s2c.ExStopMoveAirShip;
import l2ft.gameserver.network.l2.s2c.ExStopMoveInAirShip;
import l2ft.gameserver.network.l2.s2c.ExValidateLocationInAirShip;
import l2ft.gameserver.network.l2.s2c.L2GameServerPacket;
import l2ft.gameserver.templates.CharTemplate;
import l2ft.gameserver.utils.Location;

/**
 * @author VISTALL
 * @date  17:45/26.12.2010
 */
public class AirShip extends Boat
{
	public AirShip(int objectId, CharTemplate template)
	{
		super(objectId, template);
	}

	@Override
	public L2GameServerPacket infoPacket()
	{
		return new ExAirShipInfo(this);
	}

	@Override
	public L2GameServerPacket movePacket()
	{
		return new ExMoveToLocationAirShip(this);
	}

	@Override
	public L2GameServerPacket inMovePacket(Player player, Location src, Location desc)
	{
		return new ExMoveToLocationInAirShip(player,  this, src,desc);
	}

	@Override
	public L2GameServerPacket stopMovePacket()
	{
		return new ExStopMoveAirShip(this);
	}

	@Override
	public L2GameServerPacket inStopMovePacket(Player player)
	{
		return new ExStopMoveInAirShip(player);
	}

	@Override
	public L2GameServerPacket startPacket()
	{
		return null;
	}

	@Override
	public L2GameServerPacket checkLocationPacket()
	{
		return null;
	}

	@Override
	public L2GameServerPacket validateLocationPacket(Player player)
	{
		return new ExValidateLocationInAirShip(player);
	}

	@Override
	public L2GameServerPacket getOnPacket(Player player, Location location)
	{
		return new ExGetOnAirShip(player, this, location);
	}

	@Override
	public L2GameServerPacket getOffPacket(Player player, Location location)
	{
		return new ExGetOffAirShip(player, this, location);
	}

	@Override
	public boolean isAirShip()
	{
		return true;
	}

	@Override
	public void oustPlayers()
	{
		for(Player player : _players)
		{
			oustPlayer(player, getReturnLoc(), true);
		}
	}
}
