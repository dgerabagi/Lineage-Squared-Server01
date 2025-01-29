package l2ft.gameserver.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Files
{
	private static final Logger _log = LoggerFactory.getLogger(Files.class);

	/**
	 * Сохраняет строку в файл в кодировке UTF-8.<br>
	 * Если такой файл существует, то перезаписывает его.
	 * @param path путь к файлу
	 * @param string сохраняемая строка
	 */
	public static void writeFile(String path, String string)
	{
		try
		{
			FileUtils.writeStringToFile(new File(path), string, "UTF-8");
		}
		catch(IOException e)
		{
			_log.error("Error while saving file : " + path, e);
		}
	}

	public static boolean copyFile(String srcFile, String destFile)
	{
		try
		{
			FileUtils.copyFile(new File(srcFile), new File(destFile), false);
			return true;
		}
		catch(IOException e)
		{
			_log.error("Error while copying file : " + srcFile + " to " + destFile, e);
		}

		return false;
	}

	public static String read(String name)
	{
		if(name == null)
			return null;

		File file = new File("./" + name);

		if(!file.exists())
			return null;

		String content = null;

		BufferedReader br = null;
		try
		{
			br = new BufferedReader(new UnicodeReader(new FileInputStream(file), "UTF-8"));
			StringBuffer sb = new StringBuffer();
			String s = "";
			while((s = br.readLine()) != null)
				sb.append(s).append("\n");
			content = sb.toString();
			sb = null;
		}
		catch(Exception e)
		{}
		finally
		{
			try
			{
				if(br != null)
					br.close();
			}
			catch(Exception e1)
			{}
		}

		return content;
	}
}