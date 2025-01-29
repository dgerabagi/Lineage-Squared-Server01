package l2ft.gameserver.network.l2.c2s;

import l2ft.gameserver.Config;
import l2ft.gameserver.network.l2.s2c.KeyPacket;
import l2ft.gameserver.network.l2.s2c.SendStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProtocolVersion extends L2GameClientPacket
{
	private static final Logger _log = LoggerFactory.getLogger(ProtocolVersion.class);

	private int _version;

	@Override
	protected void readImpl()
	{
		_version = readD();
	}

	@Override
	protected void runImpl()
	{
		if(_version == -2)
		{
			_client.closeNow(false);
			return;
		}
		else if(_version == -3)
		{
			_log.info("Status request from IP : " + getClient().getIpAddr());
			getClient().close(new SendStatus());
			return;
		}
		else if(_version < Config.MIN_PROTOCOL_REVISION || _version > Config.MAX_PROTOCOL_REVISION)
		{
			_log.warn("Unknown protocol revision : " + _version + ", client : " + _client);
			getClient().close(new KeyPacket(null));
			return;
		}

		getClient().setRevision(_version);
		sendPacket(new KeyPacket(_client.enableCrypt()));
	}

	@Override
	public String getType()
	{
		return getClass().getSimpleName();
	}
}