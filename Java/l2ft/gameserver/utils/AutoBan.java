package l2ft.gameserver.utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import l2ft.commons.dbutils.DbUtils;
import l2ft.gameserver.dao.CharacterDAO;
import l2ft.gameserver.database.DatabaseFactory;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.World;
import l2ft.gameserver.network.l2.components.CustomMessage;
import l2ft.gameserver.taskmanager.tasks.TaskVariable.TaskType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AutoBan
{
	private static final Logger _log = LoggerFactory.getLogger(AutoBan.class);

	public static boolean isBanned(int ObjectId)
	{
		boolean res = false;

		Connection con = null;
		PreparedStatement statement = null;
		ResultSet rset = null;
		try
		{
			con = DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement("SELECT MAX(endban) AS endban FROM bans WHERE obj_Id=? AND endban IS NOT NULL");
			statement.setInt(1, ObjectId);
			rset = statement.executeQuery();

			if(rset.next())
			{
				Long endban = rset.getLong("endban") * 1000L;
				res = endban > System.currentTimeMillis();
			}
		}
		catch(Exception e)
		{
			_log.warn("Could not restore ban data: " + e);
		}
		finally
		{
			DbUtils.closeQuietly(con, statement, rset);
		}

		return res;
	}

	public static void Banned(Player actor, int period, String msg, String GM)
	{
		int endban = 0;
		if(period == -1)
			endban = Integer.MAX_VALUE;
		else if(period > 0)
		{
			Calendar end = Calendar.getInstance();
			end.add(Calendar.DAY_OF_MONTH, period);
			endban = (int) (end.getTimeInMillis() / 1000);
		}
		else
		{
			_log.warn("Negative ban period: " + period);
			return;
		}

		String date = new SimpleDateFormat("yy.MM.dd H:mm:ss").format(new Date());
		String enddate = new SimpleDateFormat("yy.MM.dd H:mm:ss").format(new Date(endban * 1000L));
		if(endban * 1000L <= Calendar.getInstance().getTimeInMillis())
		{
			_log.warn("Negative ban period | From " + date + " to " + enddate);
			return;
		}

		Connection con = null;
		PreparedStatement statement = null;
		try
		{
			con = DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement("INSERT INTO bans (account_name, obj_id, baned, unban, reason, GM, endban) VALUES(?,?,?,?,?,?,?)");
			statement.setString(1, actor.getAccountName());
			statement.setInt(2, actor.getObjectId());
			statement.setString(3, date);
			statement.setString(4, enddate);
			statement.setString(5, msg);
			statement.setString(6, GM);
			statement.setLong(7, endban);
			statement.execute();
		}
		catch(Exception e)
		{
			_log.warn("could not store bans data:" + e);
		}
		finally
		{
			DbUtils.closeQuietly(con, statement);
		}
	}

	//offline
	public static boolean Banned(String actor, int acc_level, int period, String msg, String GM)
	{
		boolean res;
		int obj_id = CharacterDAO.getInstance().getObjectIdByName(actor);
		res = obj_id > 0;
		if(!res)
			return false;

		Connection con = null;
		PreparedStatement statement = null;
		try
		{
			con = DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement("UPDATE characters SET accesslevel=? WHERE charId=?");
			statement.setInt(1, acc_level);
			statement.setInt(2, obj_id);
			statement.executeUpdate();
			DbUtils.close(statement);

			if(acc_level < 0)
			{
				int endban = 0;
				if(period == -1)
					endban = Integer.MAX_VALUE;
				else if(period > 0)
				{
					Calendar end = Calendar.getInstance();
					end.add(Calendar.DAY_OF_MONTH, period);
					endban = (int) (end.getTimeInMillis() / 1000);
				}
				else
				{
					_log.warn("Negative ban period: " + period);
					return false;
				}

				String date = new SimpleDateFormat("yy.MM.dd H:mm:ss").format(new Date());
				String enddate = new SimpleDateFormat("yy.MM.dd H:mm:ss").format(new Date(endban * 1000L));
				if(endban * 1000L <= Calendar.getInstance().getTimeInMillis())
				{
					_log.warn("Negative ban period | From " + date + " to " + enddate);
					return false;
				}

				statement = con.prepareStatement("INSERT INTO bans (obj_id, baned, unban, reason, GM, endban) VALUES(?,?,?,?,?,?)");
				statement.setInt(1, obj_id);
				statement.setString(2, date);
				statement.setString(3, enddate);
				statement.setString(4, msg);
				statement.setString(5, GM);
				statement.setLong(6, endban);
				statement.execute();
			}
			else
			{
				DbUtils.close(statement);
				statement = con.prepareStatement("DELETE FROM bans WHERE obj_id=?");
				statement.setInt(1, obj_id);
				statement.execute();
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
			_log.warn("could not store bans data:" + e);
			res = false;
		}
		finally
		{
			DbUtils.closeQuietly(con, statement);
		}

		return res;
	}

	public static void Karma(Player actor, int karma, String msg, String GM)
	{
		Connection con = null;
		PreparedStatement statement = null;
		try
		{
			String date = new SimpleDateFormat("yy.MM.dd H:mm:ss").format(new Date());
			msg = "Add karma(" + karma + ") " + msg;
			con = DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement("INSERT INTO bans (account_name, obj_id, baned, reason, GM) VALUES(?,?,?,?,?)");
			statement.setString(1, actor.getAccountName());
			statement.setInt(2, actor.getObjectId());
			statement.setString(3, date);
			statement.setString(4, msg);
			statement.setString(5, GM);
			statement.execute();
		}
		catch(Exception e)
		{
			_log.warn("could not store bans data:" + e);
		}
		finally
		{
			DbUtils.closeQuietly(con, statement);
		}
	}

	public static void Banned(Player actor, int period, String msg)
	{
		Banned(actor, period, msg, "AutoBan");
	}

	public static boolean ChatBan(String actor, int period, String msg, String GM)
	{
		boolean res = true;
		long BanChatPeriod = period * 60000;
		int obj_id = CharacterDAO.getInstance().getObjectIdByName(actor);
		if(obj_id == 0)
			return false;
		Player plyr = World.getPlayer(actor);

		Connection con = null;
		PreparedStatement statement = null;
		if(plyr != null)
		{

			plyr.sendMessage(new CustomMessage("l2ft.Util.AutoBan.ChatBan", plyr).addString(GM).addNumber(period));
			plyr.addNewTask(TaskType.Chat_ban, BanChatPeriod);
		}
		else
			try
			{
				con = DatabaseFactory.getInstance().getConnection();
				statement = con.prepareStatement("INSERT INTO character_variables VALUES ("+obj_id+", 'user-var', '"+TaskType.Chat_ban.toString()+"', 'true', "+BanChatPeriod+")");
				statement.executeUpdate();
			}
			catch(Exception e)
			{
				res = false;
				_log.warn("Could not Chat ban player:" + e);
			}
			finally
			{
				DbUtils.closeQuietly(con, statement);
			}

		return res;
	}

	public static boolean ChatUnBan(String actor, String GM)
	{
		boolean res = true;
		Player plyr = World.getPlayer(actor);
		int obj_id = CharacterDAO.getInstance().getObjectIdByName(actor);
		if(obj_id == 0)
			return false;

		Connection con = null;
		PreparedStatement statement = null;
		if(plyr != null)
		{
			plyr.sendMessage(new CustomMessage("l2ft.Util.AutoBan.ChatUnBan", plyr).addString(GM));
			plyr.removeWholeTask(TaskType.Chat_ban);
		}
		else
			try
		{
				con = DatabaseFactory.getInstance().getConnection();
				statement = con.prepareStatement("UPDATE FROM character_variables WHERE charId=? AND name=?");
				statement.setInt(1, obj_id);
				statement.setString(2, TaskType.Chat_ban.toString());
				statement.executeUpdate();
		}
		catch(Exception e)
		{
			res = false;
			_log.warn("Could not unchat ban player:" + e);
		}
		finally
		{
			DbUtils.closeQuietly(con, statement);
		}

		return res;
	}
	
	public static void tradeBan(Player actor, Player target, long time)
	{
		target.addNewTask(TaskType.Trade_ban, time*60*1000);
		String msg = actor.getName() + " trade banned " + target.getName() + " for "+time+" minutes";

		Log.add(msg, "tradeBan", actor);
		
		target.addNewTask(TaskType.Trade_ban, time*60*1000);
		if(target.isInOfflineMode())
		{
			target.setOfflineMode(false);
			target.kick();
		}
		else if(target.isInStoreMode())
		{
			target.setPrivateStoreType(Player.STORE_PRIVATE_NONE);
			target.standUp();
			target.broadcastCharInfo();
			target.getBuyList().clear();
		}
		target.sendMessage("You have been trade banned for "+time+" minutes!");
		actor.sendMessage("You have trade banned "+target.getName()+" for "+time+" minutes!");
	}
}