package l2ft.commons.net.nio.impl;

public interface IClientFactory<T extends MMOClient>
{
	public T create(MMOConnection<T> con);
}