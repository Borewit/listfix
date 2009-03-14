/*
 * listFix() - Fix Broken Playlists!
 * Copyright (C) 2001-2008 Jeremy Caron
 * 
 * This file is part of listFix().
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, please see http://www.gnu.org/licenses/
 */

package listfix.io;

/*
============================================================================
= Author:   Jeremy Caron
= File:     ProcessIniFile.java
= Purpose:  Read in the dirLists.ini file and return
=           a String array containing the directories listed in the file.
============================================================================
*/

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.StringTokenizer;
import java.util.Vector;
import listfix.model.AppOptions;
import listfix.model.AppOptionsEnum;

public class IniFileReader
{
    private BufferedReader B1;
    private BufferedReader B2;
    private String fname1;
    private String fname2;
    private String fs = System.getProperty("file.separator");
    private String homeDir = System.getProperty("user.home");
    private String[] mediaDirs = new String[0];
    private String[] history = new String[0];
    private String[] mediaLibrary = new String[0];
    private String[] mediaLibraryFiles = new String[0]; 
    private AppOptions options = new AppOptions();

    public IniFileReader() throws FileNotFoundException
    {
        fname1 = homeDir + fs + "dirLists.ini";
        fname2 = homeDir + fs + "listFixHistory.ini";
        File in_data1 = new File(fname1);
        if (!in_data1.exists())
        {
            throw new FileNotFoundException(in_data1.getPath());
        }
        else if (in_data1.length() == 0)
        {
            throw new FileNotFoundException("File found, but was of zero size.");
        }
        B1 = new BufferedReader(new FileReader(in_data1));
        
        File in_data2 = new File(fname2);
        if (!in_data2.exists())
        {
            throw new FileNotFoundException(in_data2.getPath());
        }
        else if (in_data2.length() == 0)
        {
            throw new FileNotFoundException("File found, but was of zero size.");
        }
        B2 = new BufferedReader(new FileReader(in_data2));
    }

    public AppOptions getAppOptions()
    {
        return options;
    }

    public void readIni() throws Exception
    {
        Vector<String> tempVector = new Vector<String>();
        // Read in base media directories
        // skip first line, contains header
        String line = B1.readLine();
        line = B1.readLine();
        while ( ( line != null) && ( !line.startsWith("[") ) )
        {
			tempVector.addElement(line);
            line = B1.readLine();
        }
        mediaDirs = new String[tempVector.size()];
        tempVector.copyInto(mediaDirs);
        tempVector.clear();
        
        // Read in app options, but only if the file contains them in this spot...
        // skip first line, contains header
        if (line.startsWith("[Options]"))
        {
            line = B1.readLine().trim();    
            while ( ( line != null) && ( !line.startsWith("[") ) )
            {
                StringTokenizer tempTizer = new StringTokenizer(line, "=");
                String optionName = tempTizer.nextToken();
                String optionValue = tempTizer.nextToken();
                Integer optionEnum = AppOptions.optionEnumTable.get(optionName);
                if (optionEnum != null)
                {
                    if (optionEnum.equals(AppOptionsEnum.AUTO_FIND_ENTRIES_ON_PLAYLIST_LOAD))
                    {
                        options.setAutoLocateEntriesOnPlaylistLoad((new Boolean(optionValue)).booleanValue());
                    }
                    else if (optionEnum.equals(AppOptionsEnum.MAX_PLAYLIST_HISTORY_SIZE))
                    {
                        options.setMaxPlaylistHistoryEntries((new Integer(optionValue)).intValue());
                    }
                    else if (optionEnum.equals(AppOptionsEnum.SAVE_RELATIVE_REFERENCES))
                    {
                        options.setSavePlaylistsWithRelativePaths((new Boolean(optionValue)).booleanValue());
                    }
					else if (optionEnum.equals(AppOptionsEnum.AUTO_REFRESH_MEDIA_LIBRARY_ON_LOAD))
                    {
                        options.setAutoRefreshMediaLibraryOnStartup((new Boolean(optionValue)).booleanValue());
                    }
					else if (optionEnum.equals(AppOptionsEnum.LOOK_AND_FEEL))
                    {
                        options.setLookAndFeel(optionValue);
                    }
					else if (optionEnum.equals(AppOptionsEnum.ALWAYS_USE_UNC_PATHS))
                    {
                        options.setAlwaysUseUNCPaths((new Boolean(optionValue)).booleanValue());
                    }
					else if (optionEnum.equals(AppOptionsEnum.PLAYLISTS_DIRECTORY))
					{
						options.setPlaylistsDirectory(optionValue);
					}
                }
                line = B1.readLine();
            }        
            tempVector.clear();
        }
        
        // Read in media library directories
        // skip first line, contains header
        line = B1.readLine();
        while ( ( line != null) && ( !line.startsWith("[") ) )
        {
            tempVector.addElement(line);
            line = B1.readLine();
        }   
        mediaLibrary = new String[tempVector.size()];
        tempVector.copyInto(mediaLibrary);
        tempVector.clear();
        
        // Read in media library files
        // skip first line, contains header
        line = B1.readLine();
        while (line != null)
        {
            tempVector.addElement(line);
            line = B1.readLine();
        }   
        mediaLibraryFiles = new String[tempVector.size()];
        tempVector.copyInto(mediaLibraryFiles);        
        tempVector.clear();
        
        // Read in history...
        line = B2.readLine(); 
        line = B2.readLine();
        while ( line != null )
        {
            tempVector.addElement(line);
            line = B2.readLine();
        }
        history = new String[tempVector.size()];
        tempVector.copyInto(history);
    }

    public void close_file() throws IOException
    {
        B1.close();
        B2.close();
    }
    
    public String[] getHistory()
    {
        return history;
    }
    
    public String[] getMediaDirs()
    {
		if (options.getAlwaysUseUNCPaths())
		{
			String[] result = new String[mediaDirs.length];
			for (int i = 0; i < result.length; i++)
			{
				UNCFile file = new UNCFile(mediaDirs[i]);
				if (file.onNetworkDrive())
				{
					result[i] = file.getUNCPath();					
				}
				else
				{
					result[i] = mediaDirs[i];
				}
			}
			return result;
		}
		else
		{
			return mediaDirs;
		}
    }
    
    public String[] getMediaLibrary()
    {
		if (options.getAlwaysUseUNCPaths())
		{
			String[] result = new String[mediaLibrary.length];
			for (int i = 0; i < result.length; i++)
			{
				UNCFile file = new UNCFile(mediaLibrary[i]);
				if (file.onNetworkDrive())
				{
					result[i] = file.getUNCPath();
				}
				else
				{
					result[i] = mediaLibrary[i];
				}
			}
			return result;
		}
		else
		{
			return mediaLibrary;
		}
    }
    
    public String[] getMediaLibraryFiles()
    {
		if (options.getAlwaysUseUNCPaths())
		{
			String[] result = new String[mediaLibraryFiles.length];
			for (int i = 0; i < result.length; i++)
			{
				UNCFile file = new UNCFile(mediaLibraryFiles[i]);
				if (file.onNetworkDrive())
				{
					result[i] = file.getUNCPath();
				}
				else
				{
					result[i] = mediaLibraryFiles[i];
				}
			}
			return result;
		}
		else
		{
			return mediaLibraryFiles;
		}
    } 
}