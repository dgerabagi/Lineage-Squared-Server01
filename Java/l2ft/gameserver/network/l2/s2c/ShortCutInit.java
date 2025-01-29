package l2ft.gameserver.network.l2.s2c;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.actor.instances.player.ShortCut;

public class ShortCutInit extends ShortCutPacket
{
	private List<ShortcutInfo> _shortCuts = Collections.emptyList();

	public ShortCutInit(Player pl)
	{
		Collection<ShortCut> shortCuts = pl.getAllShortCuts();
		_shortCuts = new ArrayList<ShortcutInfo>(shortCuts.size());
		for(ShortCut shortCut : shortCuts)
			_shortCuts.add(convert(pl, shortCut));
	}

	@Override
	protected final void writeImpl()
	{
		writeC(0x45);
		writeD(_shortCuts.size());

		for(final ShortcutInfo sc : _shortCuts)
			sc.write(this);
	}
}