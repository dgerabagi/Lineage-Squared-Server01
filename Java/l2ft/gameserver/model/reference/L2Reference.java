package l2ft.gameserver.model.reference;

import l2ft.commons.lang.reference.AbstractHardReference;

public class L2Reference<T> extends AbstractHardReference<T>
{
	public L2Reference(T reference)
	{
		super(reference);
	}
}
