package listfix.io.playlists.wpl;

import listfix.io.Constants;
import listfix.io.IPlaylistOptions;
import listfix.io.UnicodeInputStream;
import listfix.io.playlists.PlaylistWriter;
import listfix.model.playlists.Playlist;
import listfix.model.playlists.PlaylistEntry;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * A playlist writer capable of saving to WPL format.
 */
public class WPLWriter extends PlaylistWriter<StringBuilder>
{
  public WPLWriter(IPlaylistOptions options)
  {
    super(options);
  }

  @Override
  protected StringBuilder initCollector() throws Exception
  {
    return new StringBuilder();
  }

  @Override
  protected void writeHeader(StringBuilder buffer, Playlist playlist) throws Exception
  {
    final File playlistFile = playlist.getFile();
    buffer.append(getWPLHead(playlistFile));
  }

  @Override
  protected void writeEntry(StringBuilder buffer, PlaylistEntry entry, int index) throws Exception
  {
    String media = "\t\t\t<media src=\"" + XMLEncode(serializeEntry(entry)) + "\"";
    if (!entry.getCID().isEmpty())
    {
      media += " cid=\"" + entry.getCID() + "\"";
    }
    if (!entry.getTID().isEmpty())
    {
      media += " tid=\"" + entry.getTID() + "\"";
    }
    media += "/>" + Constants.BR;
    buffer.append(media);
  }

  @Override
  protected void finalize(StringBuilder buffer, Playlist playlist) throws Exception
  {
    buffer.append(getWPLFoot());

    final File playlistFile = playlist.getFile();

    File dirToSaveIn = playlistFile.getParentFile().getAbsoluteFile();
    if (!dirToSaveIn.exists())
    {
      dirToSaveIn.mkdirs();
    }
    try (FileOutputStream outputStream = new FileOutputStream(playlistFile))
    {
      Writer osw = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
      try (BufferedWriter output = new BufferedWriter(osw))
      {
        output.write(buffer.toString());
      }
    }
    playlist.setUtfFormat(true);

  }

  // WPL Helper Method
  private String XMLEncode(String s)
  {
    s = s.replaceAll("&", "&amp;");
    s = s.replaceAll("'", "&apos;");
    s = s.replaceAll("<", "&lt;");
    s = s.replaceAll(">", "&gt;");
    return s;
  }

  // WPL Helper Method
  private String getWPLHead(File listFile) throws IOException
  {
    String head = "";
    boolean newHead = false;
    try
    {
      try (BufferedReader buffer = new BufferedReader(new InputStreamReader(new UnicodeInputStream(new FileInputStream(listFile), "UTF-8"), StandardCharsets.UTF_8)))
      {
        String line = buffer.readLine();
        while (line != null)
        {
          if (line.trim().startsWith("<media"))
          {
            break;
          }
          head += line + Constants.BR;
          line = buffer.readLine();
        }
      }
      // determine if a head was read
      if (!head.contains("<?wpl"))
      {
        newHead = true;
      }
    }
    catch (Exception ex)
    {
      // Don't bother logging here, it's expected when saving out a new file
      // _logger.error(ex);
      newHead = true;
    }
    if (newHead)
    {
      head = "<?wpl version=\"1.0\"?>\r\n<smil>\r\n\t<body>\r\n\t\t<sec>\r\n";
    }
    return head;
  }

  // WPL Helper Method
  private String getWPLFoot() throws IOException
  {
    return "\t\t</sec>\r\n\t</body>\r\n</smil>";
  }

  private String serializeEntry(PlaylistEntry entry)
  {
    return entry.trackPathToString();
  }
}
