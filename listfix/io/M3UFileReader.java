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
= File:     M3UFileReader.java
= Purpose:  Read in the playlist file and return a Vector containing 
=           PlaylistEntries that represent the files in the playlist.
============================================================================
*/

import listfix.model.PlaylistEntry;
import listfix.view.support.*;
import listfix.tasks.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.util.StringTokenizer;
import java.util.Vector;
import listfix.controller.Task;

public class M3UFileReader
{
    private BufferedReader buffer;
    private String fs = System.getProperty("file.separator");
    private Vector results = new Vector();
    private long fileLength = 0;

    public M3UFileReader(File in_data) throws FileNotFoundException
    {
        buffer = new BufferedReader(new FileReader(in_data));
        fileLength = in_data.length();
    }

    public Vector readM3U(Task input) throws IOException
    {
        StringBuilder cache = new StringBuilder();
        String line1 = buffer.readLine(); // ignore line 1
        cache.append(line1);
        String line2 = "";
        line1 = buffer.readLine();
        cache.append(line1);
        if (line1 != null)
        {
            if (!line1.startsWith("#EXTINF"))
            {
                line2 = line1;
                line1 = "";
            }
            else
            {
                line2 = buffer.readLine();
                cache.append(line2);
            }        
            while (line1 != null)
            {
                processEntry(line1, line2);
                input.notifyObservers((int)((double)cache.length()/(double)(fileLength + 1.0) * 100.0));
                line1 = buffer.readLine();
                if (line1 != null)
                {
                    if (!line1.startsWith("#EXTINF"))
                    {
                        line2 = line1;
                        line1 = "";
                    }
                    else
                    {
                        line2 = buffer.readLine();
                        cache.append(line2);
                    }    
                }
            }
        }
        return results;
    }
    
    public Vector readM3U() throws IOException
    {
        String line1 = buffer.readLine(); // ignore line 1
        String line2 = "";
        line1 = buffer.readLine();
        if (line1 != null)
        {
            if (!line1.startsWith("#EXTINF"))
            {
                line2 = line1;
                line1 = "";
            }
            else
            {
                line2 = buffer.readLine();
            }        
            while (line1 != null)
            {
                processEntry(line1, line2);
                line1 = buffer.readLine();
                if (line1 != null)
                {
                    if (!line1.startsWith("#EXTINF"))
                    {
                        line2 = line1;
                        line1 = "";
                    }
                    else
                    {
                        line2 = buffer.readLine();
                    }    
                }
            }
        }
        return results;
    }

    private void processEntry(String L1, String L2) throws IOException
    {
        StringTokenizer pathTokenizer = null;
        StringBuilder path = new StringBuilder();
        if (L2.indexOf("://") >= 0)
        {
            // do nothing, leave tokenizer null
        }
        else if (fs.equalsIgnoreCase("/")) //OS Specific Hack
        {
            if (!L2.startsWith("\\\\"))
            {
                path.append("/");
            }
            pathTokenizer = new StringTokenizer(L2, ":\\/");
        }
        else if (fs.equalsIgnoreCase(":")) //OS Specific Hack
        {
            pathTokenizer = new StringTokenizer(L2, ":\\/");
        }
        else if (fs.equalsIgnoreCase("\\")) //OS Specific Hack
        {
            pathTokenizer = new StringTokenizer(L2, "\\/");
            if (!L2.startsWith("\\\\") && L2.startsWith("\\"))
            {
                path.append("\\");
            }
        }
        
        if (pathTokenizer != null)
        {
            String fileName = "";
            String extInf = L1;
            if (L2.startsWith("\\\\"))
            {
                path.append("\\\\");
            }

            String firstToken = "";
            int tokenNumber = 0;
            while (pathTokenizer.hasMoreTokens())
            {
                File firstPathToExist = null;
                String word = pathTokenizer.nextToken();
                if (tokenNumber == 0)
                {
                    firstToken = word;
                }
                if (tokenNumber == 0 && !L2.startsWith("\\\\") && !PlaylistEntry.emptyDirectories.contains(word + fs))
                {
                    // This token is the closest thing we have to the notion of a 'drive' on any OS... 
                    // make a file out of this and see if it has any files.
                    File testFile = new File(path.toString() + word + fs);
                    if (!(testFile.exists() && testFile.isDirectory() && testFile.list().length > 0))
                    {
                        PlaylistEntry.emptyDirectories.add(path.toString() + word + fs);
                    }
                }
                else if (L2.startsWith("\\\\") && !PlaylistEntry.emptyDirectories.contains(path.toString() + word + fs) && pathTokenizer.countTokens() >= 1)
                {
                    // Handle UNC paths specially
                    File testFile = new File(path.toString() + word + fs);
                    boolean exists = testFile.exists();
                    if (exists && firstPathToExist == null)
                    {
                        firstPathToExist = testFile;
                    }
                    if (!(exists && testFile.isDirectory() && testFile.list().length > 0) && pathTokenizer.countTokens() == 1)
                    {
                        PlaylistEntry.emptyDirectories.add(path.toString() + word + fs);
                        if (firstPathToExist == null)
                        {
                            PlaylistEntry.emptyDirectories.add("\\\\" + firstToken);
                        }
                    }
                }
                if (pathTokenizer.hasMoreTokens())
                {
                    path.append(word);
                    path.append(fs);
                }
                else
                {
                    fileName = word;
                }
                tokenNumber++;
            }
            results.addElement(new PlaylistEntry(path.toString(), fileName, extInf));
        }
        else
        {
            try
            {
                results.addElement(new PlaylistEntry(new URI(L2.trim()), L1));
            }
            catch (Exception e)
            {
                // eat the error for now.
                e.printStackTrace();
            }
        }
    }

    public void closeFile() throws IOException
    {
        buffer.close();
    }
}