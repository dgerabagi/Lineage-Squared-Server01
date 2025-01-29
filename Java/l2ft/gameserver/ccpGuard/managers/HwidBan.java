package l2ft.gameserver.ccpGuard.managers;

import java.sql.ResultSet;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.Map;

import l2ft.gameserver.ccpGuard.ProtectInfo;
import l2ft.gameserver.network.l2.GameClient;
import l2ft.gameserver.database.DatabaseFactory;
import l2ft.commons.dbutils.DbUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HwidBan
{
	protected static Logger _log = LoggerFactory.getLogger(ProtectManager.class);
	private static HwidBan _instance;
	private static Map<Integer, L2HwidBan> _lists;

	public static HwidBan getInstance()
	{
		if (_instance == null)
		{
			_instance = new HwidBan();
		}
		return _instance;
	}

	public static void reload()
	{
		_instance = new HwidBan();
	}

	public HwidBan()
	{
		_lists = new HashMap<Integer, L2HwidBan>();
		load();
	}

	private void load()
	{
		Connection con = null;
		PreparedStatement statement = null;
		ResultSet rset = null;
		String HWID = "";
		int counterHwidBan = 0;
		try
		{
			con = DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement("SELECT * FROM hwid_bans");
			rset = statement.executeQuery();
			while (rset.next())
			{

				HWID = rset.getString("HWID");
				L2HwidBan hb = new L2HwidBan(counterHwidBan);
				hb.setHwidBan(HWID);
				_lists.put(counterHwidBan, hb);
				counterHwidBan++;
			}
		}
		catch (Exception E)
		{
		}
		finally
		{
			DbUtils.closeQuietly(con, statement, rset);
			_log.info("Loading " + counterHwidBan + " banned HwId");
		}
	}

	public static boolean checkFullHWIDBanned(ProtectInfo pi)
	{
		if (_lists.size() == 0)
		{
			return false;
		}
		for (int i = 0; i < _lists.size(); i++)
		{
			if (_lists.get(i).getHwid().equals(pi.getHWID()))
			{
				return true;
			}
		}
		return false;
	}

	public static int getCountHwidBan()
	{
		return _lists.size();
	}

	public static void addHwidBan(GameClient client)
	{
		/**String hwid = client._prot_info.getHWID();
		int counterHwidBan = _lists.size();
		L2HwidBan hb = new L2HwidBan(counterHwidBan);
		hb.setHwidBan(hwid);
		_lists.put(counterHwidBan, hb);
		Connection con = null;
		PreparedStatement statement = null;
		try
		{
			con = DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement("INSERT INTO hwid_bans SET HWID=?");
			statement.setString(1, hwid);
			statement.execute();
		}
		catch (Exception e)
		{
		}
		finally
		{
			DbUtils.closeQuietly(con, statement);
			_log.info("Update " + counterHwidBan + " banned HwId");
		}*/
	}
}