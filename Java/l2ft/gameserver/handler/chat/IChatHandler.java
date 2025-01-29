package l2ft.gameserver.handler.chat;

import l2ft.gameserver.network.l2.components.ChatType;

/**
 * @author VISTALL
 * @date 18:16/12.03.2011
 */
public interface IChatHandler
{
	void say();

	ChatType getType();
}
