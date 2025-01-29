package l2ft.gameserver.templates.spawn;

import l2ft.gameserver.utils.Location;

/**
 * @author VISTALL
 * @date 4:08/19.05.2011
 */
public interface SpawnRange
{
	Location getRandomLoc(int geoIndex);
}
