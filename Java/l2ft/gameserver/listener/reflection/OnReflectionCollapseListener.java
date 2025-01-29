package l2ft.gameserver.listener.reflection;

import l2ft.commons.listener.Listener;
import l2ft.gameserver.model.entity.Reflection;

public interface OnReflectionCollapseListener extends Listener<Reflection>
{
	public void onReflectionCollapse(Reflection reflection);
}
