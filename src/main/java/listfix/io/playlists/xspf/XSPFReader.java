/*
 *  listFix() - Fix Broken Playlists!
 *  Copyright (C) 2001-2014 Jeremy Caron
 *
 *  This file is part of listFix().
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, please see http://www.gnu.org/licenses/
 */

package listfix.io.playlists.xspf;

import christophedelory.playlist.SpecificPlaylist;
import christophedelory.playlist.SpecificPlaylistFactory;
import christophedelory.playlist.xspf.Track;
import listfix.io.Constants;
import listfix.io.FileUtils;
import listfix.io.IPlaylistOptions;
import listfix.io.playlists.PlaylistReader;
import listfix.model.enums.PlaylistType;
import listfix.model.playlists.FilePlaylistEntry;
import listfix.model.playlists.PlaylistEntry;
import listfix.model.playlists.UriPlaylistEntry;
import listfix.util.UnicodeUtils;
import listfix.view.support.IProgressObserver;
import listfix.view.support.ProgressAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads in a XSPF file and returns a List containing PlaylistEntries that represent the files & URIs in the playlist.
 * @author jcaron
 */
public class XSPFReader extends PlaylistReader
{
  private String _encoding;

  private static final PlaylistType type = PlaylistType.XSPF;
  private static final Logger _logger = LogManager.getLogger(XSPFReader.class);

  /**
   *
   * @param xspfFile
   */
  public XSPFReader(IPlaylistOptions playListOptions, Path xspfFile)
  {
    super(playListOptions, xspfFile);
    _encoding = UnicodeUtils.getEncoding(xspfFile);
  }

  /**
   *
   * @return
   */
  @Override
  public String getEncoding()
  {
    return _encoding;
  }

  /**
   *
   * @param encoding
   */
  @Override
  public void setEncoding(String encoding)
  {
    _encoding = encoding;
  }

  /**
   *
   * @return
   */
  @Override
  public PlaylistType getPlaylistType()
  {
    return type;
  }

  /**
   *
   * @param observer
   * @return
   * @throws IOException
   */
  @Override
  public List<PlaylistEntry> readPlaylist(IProgressObserver<String> observer) throws IOException
  {
    // Init a progress adapter if we have a progress observer.
    ProgressAdapter<String> progress = ProgressAdapter.wrap(observer);

    List<PlaylistEntry> entriesList = new ArrayList<>();
    SpecificPlaylist loadedList = SpecificPlaylistFactory.getInstance().readFrom(playlistPath.toFile());
    if (loadedList.getProvider().getId().equals("xspf"))
    {
      christophedelory.playlist.xspf.Playlist xspfList = (christophedelory.playlist.xspf.Playlist)SpecificPlaylistFactory.getInstance().readFrom(playlistPath.toFile());

      // Set the total if we have an observer.
      progress.setTotal(xspfList.getTracks().size());

      int trackCount = 0;
      for (Track track : xspfList.getTracks())
      {
        if (observer.getCancelled())
        {
          // Bail out if the user cancelled.
          return null;
        }

        try
        {
          if (FileUtils.isURL(track.getStringContainers().get(0).getText()))
          {
            entriesList.add(new UriPlaylistEntry(new URI(track.getStringContainers().get(0).getText()), track.getTitle(), track.getDuration() != null ? track.getDuration().longValue() : -1));
          }
          else
          {
            URI uri = new URI(track.getStringContainers().get(0).getText());

            Path trackFile = Path.of(uri.getSchemeSpecificPart());
            if (trackFile.toString().startsWith(Constants.FS + Constants.FS) && !trackFile.toString().startsWith("\\\\"))
            {
              // This was a relative, non-UNC entry...
              entriesList.add(new FilePlaylistEntry(Path.of(trackFile.toString().substring(2)), track.getTitle(), track.getDuration() != null ? track.getDuration().longValue() : -1, playlistPath));
            }
            else
            {
              // Regular entry...
              entriesList.add(new FilePlaylistEntry(trackFile, getTitle(track), track.getDuration() != null ? track.getDuration().longValue() : -1, playlistPath));
            }
          }
        }
        catch (Exception ex)
        {
          _logger.error("[XSPFReader] - Could not convert lizzy entry to PlaylistEntry - " + ex, ex);
        }

        // Step forward if we have an observer.
        progress.setCompleted(trackCount++);
      }
      return entriesList;
    }
    else
    {
      throw new IOException("XSPF file was not in XSPF format!!");
    }
  }

  /**
   *
   * @return
   * @throws IOException
   */
  @Override
  public List<PlaylistEntry> readPlaylist() throws IOException
  {
    return readPlaylist(null);
  }

  private String getTitle(Track track)
  {
    if (track.getCreator() != null && track.getTitle() != null)
    {
      return track.getCreator() + " - " + track.getTitle();
    }
    else if (track.getCreator() == null && track.getTitle() != null)
    {
      return track.getTitle();
    }
    else if (track.getCreator() != null && track.getTitle() == null)
    {
      return track.getCreator();
    }
    return "";
  }
}