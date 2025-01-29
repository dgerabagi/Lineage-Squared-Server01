package l2ft.gameserver.instancemanager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import l2ft.commons.dbutils.DbUtils;
import l2ft.gameserver.Config;
import l2ft.gameserver.ThreadPoolManager;
import l2ft.gameserver.database.DatabaseFactory;
import l2ft.gameserver.model.GameObjectsStorage;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.utils.Log;

public class L2TopManager
{
	private static Logger _log = Logger.getLogger(L2TopManager.class.getName());

	private final static String voteWeb = Config.DATAPACK_ROOT + "/data/l2top_vote-web.txt";
	private final static String voteSms = Config.DATAPACK_ROOT + "/data/l2top_vote-sms.txt";

	private static L2TopManager _instance;

	public static L2TopManager getInstance()
	{
		if(_instance == null && Config.L2_TOP_MANAGER_ENABLED)
			_instance = new L2TopManager();
		return _instance;
	}

	public L2TopManager()
	{
		ThreadPoolManager.getInstance().scheduleAtFixedRate(new ConnectAndUpdate(), Config.L2_TOP_MANAGER_INTERVAL, Config.L2_TOP_MANAGER_INTERVAL);
		ThreadPoolManager.getInstance().scheduleAtFixedRate(new Clean(), Config.L2_TOP_MANAGER_INTERVAL, Config.L2_TOP_MANAGER_INTERVAL);
		ThreadPoolManager.getInstance().scheduleAtFixedRate(new GiveReward(), Config.L2_TOP_MANAGER_INTERVAL, Config.L2_TOP_MANAGER_INTERVAL);
		_log.info("L2TopManager: loaded sucesfully");
	}

	private void update()
	{
		String out_sms = getPage(Config.L2_TOP_SMS_ADDRESS);
		String out_web = getPage(Config.L2_TOP_WEB_ADDRESS);

		File sms = new File(voteSms);
		File web = new File(voteWeb);
		FileWriter SaveWeb = null;
		FileWriter SaveSms = null;

		try
		{
			SaveSms = new FileWriter(sms);
			SaveSms.write(out_sms);
			SaveWeb = new FileWriter(web);
			SaveWeb.write(out_web);
		}

		catch(IOException e)
		{
			e.printStackTrace();
		}

		finally
		{
			try
			{
				if(SaveSms != null)
					SaveSms.close();
				if(SaveWeb != null)
					SaveWeb.close();
			}
			catch(Exception e1)
			{
				e1.printStackTrace();
			}
		}
	}

	private static String getPage(String address)
	{
		StringBuffer buf = new StringBuffer();
		Socket s;
		try
		{
			s = new Socket("l2top.ru", 80);

			s.setSoTimeout(30000);
			String request = "GET " + address + " HTTP/1.1\r\n" + "User-Agent: http:\\" + Config.EXTERNAL_HOSTNAME + " server\r\n" + "Host: http:\\" + Config.EXTERNAL_HOSTNAME + " \r\n" + "Accept: */*\r\n" + "Connection: close\r\n" + "\r\n";
			s.getOutputStream().write(request.getBytes());
			BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), "Cp1251"));

			for(String line = in.readLine(); line != null; line = in.readLine())
			{
				buf.append(line);
				buf.append("\r\n");
			}
			s.close();
		}
		catch(Exception e)
		{
			buf.append("Connection error");
		}
		return buf.toString();
	}

	private void parse(boolean sms)
	{
		try
		{
			BufferedReader in = new BufferedReader(new FileReader(sms ? voteSms : voteWeb));
			String line = in.readLine();
			while(line != null)
			{
				Calendar cal = Calendar.getInstance();
				int year = cal.get(Calendar.YEAR);
				if(line.startsWith("" + year))
					try
					{
						StringTokenizer st = new StringTokenizer(line, "\t -:");
						cal.set(Calendar.YEAR, Integer.parseInt(st.nextToken()));
						cal.set(Calendar.MONTH, Integer.parseInt(st.nextToken()));
						cal.set(Calendar.DAY_OF_MONTH, Integer.parseInt(st.nextToken()));
						cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(st.nextToken()));
						cal.set(Calendar.MINUTE, Integer.parseInt(st.nextToken()));
						cal.set(Calendar.SECOND, Integer.parseInt(st.nextToken()));
						cal.set(Calendar.MILLISECOND, 0);
						String nick = st.nextToken();
						int mult = 1;
						if(sms)
							mult = Integer.parseInt(new StringBuffer(st.nextToken()).delete(0, 1).toString());
						if(cal.getTimeInMillis() + Config.L2_TOP_SAVE_DAYS * 86400000 > System.currentTimeMillis())
							checkAndSaveFromDb(cal.getTimeInMillis(), nick, mult);
					}
					catch(NoSuchElementException nsee)
					{
						continue;
					}
				line = in.readLine();
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}

	private synchronized void clean()
	{
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DAY_OF_YEAR, -Config.L2_TOP_SAVE_DAYS);
		Connection con = null;
		PreparedStatement statement = null;
		ResultSet rset = null;
		try
		{
			con = DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement("DELETE FROM character_l2top_votes WHERE date<?");
			statement.setLong(1, cal.getTimeInMillis());
			statement.execute();
		}
		catch(SQLException e)
		{
			e.printStackTrace();
		}
		finally
		{
			DbUtils.closeQuietly(con, statement, rset);
		}
	}

	private synchronized void checkAndSaveFromDb(long date, String nick, int mult)
	{
		Connection con = null;
		PreparedStatement statement = null;
		ResultSet rset = null;
		try
		{
			con = DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement("SELECT obj_Id FROM characters WHERE char_name=?");
			statement.setString(1, nick);
			rset = statement.executeQuery();
			int objId = 0;
			if(rset.next())
				objId = rset.getInt("obj_Id");
			if(objId > 0)
			{
				DbUtils.closeQuietly(statement, rset);
				statement = con.prepareStatement("SELECT * FROM character_l2top_votes WHERE id=? AND date=? AND multipler=?");
				statement.setInt(1, objId);
				statement.setLong(2, date);
				statement.setInt(3, mult);
				rset = statement.executeQuery();
				if(!rset.next())
				{
					DbUtils.closeQuietly(statement, rset);
					statement = con.prepareStatement("INSERT INTO character_l2top_votes (date, id, nick, multipler) values (?,?,?,?)");
					statement.setLong(1, date);
					statement.setInt(2, objId);
					statement.setString(3, nick);
					statement.setInt(4, mult);
					statement.execute();
				}
			}
		}
		catch(SQLException e)
		{
			e.printStackTrace();
		}
		finally
		{
			DbUtils.closeQuietly(con, statement, rset);
		}
	}

	private synchronized void giveReward()
	{
		Connection con = null;
		PreparedStatement statement = null;
		ResultSet rset = null;
		try
		{
			con = DatabaseFactory.getInstance().getConnection();
			for(Player player : GameObjectsStorage.getAllPlayers())
			{
				int objId = player.getObjectId();
				int mult = 0;
				statement = con.prepareStatement("SELECT multipler FROM character_l2top_votes WHERE id=? AND has_reward=0");
				statement.setInt(1, objId);
				rset = statement.executeQuery();
				while(rset.next())
					mult += rset.getInt("multipler");

				DbUtils.closeQuietly(statement, rset);
				statement = con.prepareStatement("UPDATE character_l2top_votes SET has_reward=1 WHERE id=?");
				statement.setInt(1, objId);
				statement.executeUpdate();
				if(mult > 0)
				{
					player.sendMessage("The administration server " + Config.L2_TOP_SERVER_ADDRESS + " thank you for your vote");
					for(int i = 0; i < Config.L2_TOP_REWARD.length; i += 2)
					{
						player.getInventory().addItem(Config.L2_TOP_REWARD[i], Config.L2_TOP_REWARD[i + 1] * mult);
						Log.add(player.getName() + " | " + player.getObjectId() + " | L2Top reward item ID | " + Config.L2_TOP_REWARD[i] + " | L2Top reward count | " + Config.L2_TOP_REWARD[i + 1] * mult + " |", "l2top");
					}
				}
				DbUtils.closeQuietly(statement);
			}
		}
		catch(SQLException e)
		{
			e.printStackTrace();
		}
		finally
		{
			DbUtils.closeQuietly(con, statement, rset);
		}
	}

	private class ConnectAndUpdate implements Runnable
	{
		@Override
		public void run()
		{
			update();
			parse(true);
			parse(false);
		}
	}

	private class Clean implements Runnable
	{
		@Override
		public void run()
		{
			clean();
		}
	}

	private class GiveReward implements Runnable
	{
		@Override
		public void run()
		{
			giveReward();
		}
	}
}