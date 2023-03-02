package listfix.view;

import com.jcabi.manifests.Manifests;
import com.jgoodies.looks.plastic.PlasticLookAndFeel;
import com.jgoodies.looks.plastic.theme.DarkStar;
import com.jgoodies.looks.plastic.theme.LightGray;
import com.jgoodies.looks.plastic.theme.SkyBlue;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.*;
import java.awt.event.*;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javax.swing.*;
import javax.swing.JPopupMenu.Separator;
import javax.swing.border.TitledBorder;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.FontUIResource;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.xml.bind.JAXBException;
import listfix.config.*;
import listfix.controller.ListFixController;
import listfix.controller.MediaLibraryOperator;
import listfix.exceptions.MediaDirNotFoundException;
import listfix.io.*;
import listfix.io.filters.PlaylistFileFilter;
import listfix.json.JsonAppOptions;
import listfix.model.BatchRepair;
import listfix.model.BatchRepairItem;
import listfix.model.PlaylistHistory;
import listfix.model.enums.PlaylistType;
import listfix.model.playlists.Playlist;
import listfix.model.playlists.PlaylistFactory;
import listfix.swing.IDocumentChangeListener;
import listfix.swing.JDocumentComponent;
import listfix.swing.JDocumentTabbedPane;
import listfix.util.ArrayFunctions;
import listfix.util.ExStack;
import listfix.util.FileTypeSearch;
import listfix.view.controls.JTransparentTextArea;
import listfix.view.controls.PlaylistEditCtrl;
import listfix.view.dialogs.*;
import listfix.view.support.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author jcaron
 */
public final class GUIScreen extends JFrame implements DropTargetListener, IListFixGui
{
  
  private final JFileChooser _jOpenPlaylistFileChooser = new JFileChooser();
  private final JFileChooser _jSaveFileChooser = new JFileChooser();
  private final FolderChooser _jMediaDirChooser = new FolderChooser();
  private final List<Playlist> _openPlaylists = new ArrayList<>();
  private final Image applicationIcon = new ImageIcon(Objects.requireNonNull(getClass().getResource("/images/icon.png"))).getImage();
  private final listfix.view.support.SplashScreen splashScreen = new listfix.view.support.SplashScreen("images/listfixSplashScreen.png");

  private ListFixController _listFixController = null;
  private Playlist _currentPlaylist;
  private IPlaylistModifiedListener _playlistListener;

  private static final Logger _logger = LogManager.getLogger(GUIScreen.class);

  private static final String applicationVersion = Manifests.read("Implementation-Version");

  private static final int keyEventRenameFile = KeyEvent.VK_F2;

  /**
   * The components should only be enabled when 1 or more playlists are loaded
   */
  private Component[] componentsRequireActivePlaylist;

  /**
   * Creates new form GUIScreen
   */
  public GUIScreen()
  {
    splashScreen.setIconImage(applicationIcon);

    preInitComponents();

    // Netbeans-generated form init code
    initComponents();

    postInitComponents();
  }

  /**
   * Show the splash screen with initial text, load the media library & option files into memory, then prepare to init the UI
   */
  private void preInitComponents()
  {
    splashScreen.setVisible(true);
    splashScreen.setStatusBar("Loading Media Library & Options...");
    _listFixController = ListFixController.getInstance();
    splashScreen.setStatusBar("Initializing UI...");
  }

  private IAppOptions getApplicationConfig()
  {
    return this._listFixController.getApplicationConfiguration().getConfig();
  }

  /**
   * All UI components have been instantiated at this point.
   */
  private void postInitComponents()
  {
    // Set the user-selected font and look & feel
    final IAppOptions appConfig = this.getApplicationConfig();
    setApplicationFont(appConfig.getAppFont());
    this.setLookAndFeel(appConfig.getLookAndFeel());

    configureFileAndFolderChoosers();

    // Warn the user if no media directories have been defined, and set the
    if (this._listFixController.getShowMediaDirWindow())
    {
      JOptionPane.showMessageDialog(
        this,
        new JTransparentTextArea("You need to add a media directory before you can find the new locations of your files.  See help for more information."),
        "Reminder",
        JOptionPane.INFORMATION_MESSAGE);
    }
    else
    {
      _lstMediaLibraryDirs.setListData(new Vector<>(_listFixController.getMediaLibrary().getMediaDirectories()));
    }

    updateMediaDirButtons();
    updateRecentMenu();
    refreshStatusLabel(null);

    ((CardLayout) _playlistPanel.getLayout()).show(_playlistPanel, "_gettingStartedPanel");

    initPlaylistListener();

    if (!WinampHelper.isWinampInstalled())
    {
      _batchRepairWinampMenuItem.setVisible(false);
      _extractPlaylistsMenuItem.setVisible(false);
    }

    // drag-n-drop support for the playlist directory tree
    _playlistDirectoryTree.setTransferHandler(createPlaylistTreeTransferHandler());
    _playlistDirectoryTree.setRootVisible(false);
    // Show tooltips
    ToolTipManager.sharedInstance().registerComponent(_playlistDirectoryTree);
    _playlistDirectoryTree.setCellRenderer(new TreeTooltipRenderer());

    // A constructor with side-effects, required to support opening playlists that are dragged in...
    // Java... what voodoo/nonsense is this?
    new DropTarget(this, this);

    _playlistDirectoryTree.getSelectionModel().addTreeSelectionListener(e -> {
      boolean hasSelected = _playlistDirectoryTree.getSelectionCount() > 0;
      _btnOpenSelected.setEnabled(hasSelected);
      _btnQuickRepair.setEnabled(hasSelected);
      _btnDeepRepair.setEnabled(hasSelected);
    });

    // addAt popup menu to playlist tree on right-click
    _playlistDirectoryTree.addMouseListener(createPlaylistTreeMouseListener());

    _playlistDirectoryTree.getModel().addTreeModelListener(new TreeModelListener()
    {
      @Override
      public void treeNodesChanged(TreeModelEvent e)
      {
        recheckStatusOfOpenPlaylists();
      }

      @Override
      public void treeNodesInserted(TreeModelEvent e)
      {
        recheckStatusOfOpenPlaylists();
      }

      @Override
      public void treeNodesRemoved(TreeModelEvent e)
      {
        recheckStatusOfOpenPlaylists();
      }

      @Override
      public void treeStructureChanged(TreeModelEvent e)
      {
        recheckStatusOfOpenPlaylists();
      }
    });

    // Load the position the window was in when it was last closed.
    WindowSaver windowSaver = new WindowSaver(this._listFixController.getApplicationState());
    windowSaver.loadSettings(this);

    // Set the position of the divider in the left split pane.
    _leftSplitPane.setDividerLocation(.7);

    updateMenuItemStatuses();

    // JCaron - 2012.05.03 - Global listener for Ctrl-Tab and Ctrl-Shift-Tab
    configureGlobalKeyboardListener();

    // Stop showing the loading screen
    splashScreen.setVisible(false);
  }

  private void recheckStatusOfOpenPlaylists()
  {
    for (Playlist playlist : _openPlaylists)
    {
      playlist.updateModifiedStatus();
    }
  }

  private void configureGlobalKeyboardListener()
  {
    KeyEventPostProcessor pp = new KeyEventPostProcessor()
    {
      @Override
      public boolean postProcessKeyEvent(KeyEvent e)
      {
        // Advance the selected tab on Ctrl-Tab (make sure Shift isn't pressed)
        if (ctrlTabWasPressed(e) && _playlistTabbedPane.getDocumentCount() > 1)
        {
          _playlistTabbedPane.nextDocument();
        }

        // Regress the selected tab on Ctrl-Shift-Tab
        if (ctrlShiftTabWasPressed(e) && _playlistTabbedPane.getDocumentCount() > 1)
        {
          _playlistTabbedPane.prevPlaylist();
        }

        return true;
      }

      private boolean ctrlShiftTabWasPressed(KeyEvent e)
      {
        return (e.getKeyCode() == KeyEvent.VK_TAB) && ((e.getModifiers() & KeyEvent.CTRL_MASK) != 0) && ((e.getModifiers() & KeyEvent.SHIFT_MASK) != 0) && _playlistTabbedPane.getDocumentCount() > 0 && e.getID() == KeyEvent.KEY_PRESSED;
      }

      private boolean ctrlTabWasPressed(KeyEvent e)
      {
        return (e.getKeyCode() == KeyEvent.VK_TAB) && ((e.getModifiers() & KeyEvent.CTRL_MASK) != 0) && ((e.getModifiers() & KeyEvent.SHIFT_MASK) == 0) && _playlistTabbedPane.getDocumentCount() > 0 && e.getID() == KeyEvent.KEY_PRESSED;
      }
    };

    DefaultKeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventPostProcessor(pp);
  }

  private MouseAdapter createPlaylistTreeMouseListener()
  {
    return new MouseAdapter()
    {
      @Override
      public void mousePressed(MouseEvent e)
      {
        handleMouseEvent(e);
      }

      @Override
      public void mouseReleased(MouseEvent e)
      {
        handleMouseEvent(e);
      }

      private void handleMouseEvent(MouseEvent e)
      {
        Point p = e.getPoint();
        if (e.isPopupTrigger())
        {
          int rowIx = _playlistDirectoryTree.getRowForLocation(p.x, p.y);
          boolean isOverItem = rowIx >= 0;
          if (isOverItem && (e.getModifiers() & ActionEvent.CTRL_MASK) > 0)
          {
            _playlistDirectoryTree.addSelectionRow(rowIx);
          }
          else
          {
            if (isOverItem && !_playlistDirectoryTree.isRowSelected(rowIx))
            {
              _playlistDirectoryTree.setSelectionRow(rowIx);
            }
          }

          final TreePath[] selectPath = _playlistDirectoryTree.getSelectionPaths();
          final boolean allTopLevel = selectPath != null && Arrays.stream(selectPath)
            .allMatch(path -> path.getParentPath() != null && path.getParentPath().getParentPath() == null);

          _miRemovePlaylistDirectory.setEnabled(allTopLevel);

          if (_playlistDirectoryTree.getSelectionCount() > 0)
          {
            _miExactMatchesSearch.setEnabled(true);
            _miClosestMatchesSearch.setEnabled(true);
            _miOpenSelectedPlaylists.setEnabled(true);
            boolean singleFileSelected = _playlistDirectoryTree.getSelectionCount() == 1;
            _miRenameSelectedItem.setEnabled(singleFileSelected
              && !allTopLevel
              && Files.isRegularFile(GUIScreen.this.getSelectedPlaylistTreeNodes().get(0).getUserObject())
            );
            _miDeleteFile.setEnabled(true);
          }
          else
          {
            _miExactMatchesSearch.setEnabled(false);
            _miClosestMatchesSearch.setEnabled(false);
            _miOpenSelectedPlaylists.setEnabled(false);
            _miDeleteFile.setEnabled(false);
            _miRenameSelectedItem.setEnabled(false);
          }

          _miRefreshDirectoryTree.setEnabled(_playlistDirectoryTree.getRowCount() > 0);
          _playlistTreeRightClickMenu.show(e.getComponent(), p.x, p.y);
        }
        else
        {
          int selRow = _playlistDirectoryTree.getRowForLocation(p.x, p.y);
          TreePath selPath = _playlistDirectoryTree.getPathForLocation(p.x, p.y);
          if (selRow != -1)
          {
            if (e.getClickCount() == 2)
            {
              playlistDirectoryTreeNodeDoubleClicked(selPath);
            }
          }
          else
          {
            _playlistDirectoryTree.setSelectionRow(-1);
          }
        }
      }
    };
  }

  private TransferHandler createPlaylistTreeTransferHandler()
  {
    return new TransferHandler()
    {
      @Override
      public boolean canImport(JComponent comp, DataFlavor[] transferFlavors)
      {
        return Arrays.stream(transferFlavors).anyMatch(DataFlavor.javaFileListFlavor :: equals);
      }

      /**
       * Causes a transfer to occur from a clipboard or a drag and drop operation.
       * @param support the object containing the details of the transfer, not <code>null</code>.
       * @return true if the data was inserted into the component, false otherwise
       */
      @Override
      public boolean importData(TransferHandler.TransferSupport support)
      {
        final Transferable transferable = support.getTransferable();
        if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
        {
          try
          {
            List<File> fileList = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
            return fileList.stream()
              .filter(File :: isDirectory)
              .map(folder -> {
                GUIScreen.this.addPlaylist(folder);
                return true;
              })
              .reduce(false, (t, v) -> true);
          }
          catch (Exception e)
          {
            _logger.error("Dragging onto playlists directory failed", e);
          }
        }
        return false;
      }

      @Override
      public int getSourceActions(JComponent c)
      {
        return MOVE;
      }

      @Override
      protected Transferable createTransferable(JComponent c)
      {
        try
        {
          ArrayList<String> paths = new ArrayList<>();

          for (int selRow : _playlistDirectoryTree.getSelectionRows())
          {
            TreePath selPath = _playlistDirectoryTree.getPathForRow(selRow);
            paths.add(FileTreeNodeGenerator.treePathToFileSystemPath(selPath).toString());
          }

          String serializedPaths = StringArrayListSerializer.serialize(paths);
          return new StringSelection(serializedPaths);
        }
        catch (IOException ex)
        {
          _logger.warn(ex);
          return null;
        }

      }
    };
  }

  private void configureFileAndFolderChoosers()
  {
    _jOpenPlaylistFileChooser.setDialogTitle("Choose Playlists...");
    _jOpenPlaylistFileChooser.setAcceptAllFileFilterUsed(false);
    _jOpenPlaylistFileChooser.setFileFilter(new PlaylistFileFilter());
    _jOpenPlaylistFileChooser.setMultiSelectionEnabled(true);

    _jMediaDirChooser.setDialogTitle("Specify a media directory...");
    _jMediaDirChooser.setAcceptAllFileFilterUsed(false);
    _jMediaDirChooser.setMinimumSize(new Dimension(400, 500));
    _jMediaDirChooser.setPreferredSize(new Dimension(400, 500));

    _jSaveFileChooser.setDialogTitle("Save File:");
    _jSaveFileChooser.setAcceptAllFileFilterUsed(false);
    _jSaveFileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
    _jSaveFileChooser.addChoosableFileFilter(new FileNameExtensionFilter("M3U Playlist (*.m3u, *.m3u8)", "m3u8", "m3u"));
    _jSaveFileChooser.addChoosableFileFilter(new FileNameExtensionFilter("PLS Playlist (*.pls)", "pls"));
    _jSaveFileChooser.addChoosableFileFilter(new FileNameExtensionFilter("WPL Playlist (*.wpl)", "wpl"));
    _jSaveFileChooser.addChoosableFileFilter(new FileNameExtensionFilter("XSPF Playlist (*.xspf)", "xspf"));
    _jSaveFileChooser.addChoosableFileFilter(new FileNameExtensionFilter("iTunes Playlist (*.xml)", "xml"));
  }

  @Override
  public void dragEnter(DropTargetDragEvent dtde)
  {

  }

  @Override
  public void dragExit(DropTargetEvent dte)
  {

  }

  @Override
  public void dragOver(DropTargetDragEvent dtde)
  {

  }

  @Override
  public void dropActionChanged(DropTargetDragEvent dtde)
  {

  }

  @Override
  public void drop(DropTargetDropEvent dtde)
  {
    try
    {
      // Ok, get the dropped object and try to figure out what it is
      Transferable tr = dtde.getTransferable();
      DataFlavor[] flavors = tr.getTransferDataFlavors();
      for (DataFlavor flavor : flavors)
      {
        // Check for file lists specifically
        if (flavor.isFlavorJavaFileListType() || flavor.isFlavorTextType())
        {
          // Accept the drop...
          dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
          Object data = tr.getTransferData(flavor);
          if (data instanceof List)
          {
            // Process the drop, in this case, of file or folder paths from the OS.
            List list = (List) data;
            for (Object list1 : list)
            {
              if (list1 instanceof Path)
              {
                Path tempFile = (Path) list1;
                if (Playlist.isPlaylist(tempFile))
                {
                  openPlaylist(tempFile);
                }
                else if (Files.isDirectory(tempFile))
                {
                  List<Path> playlists = PlaylistScanner.getAllPlaylists(tempFile);
                  for (Path f : playlists)
                  {
                    openPlaylist(f);
                  }
                }
              }
            }
          }
          else if (data instanceof InputStreamReader)
          {
            try (InputStreamReader list = (InputStreamReader) data; BufferedReader temp = new BufferedReader(list))
            {
              String filePath = temp.readLine();
              while (filePath != null && !filePath.isEmpty())
              {
                openPlaylist(Path.of(new URI(filePath)));
                filePath = temp.readLine();
              }
            }
          }
          else if (data instanceof String)
          {
            // Magically delicious string coming from the playlist panel
            String input = (String) data;
            List<String> paths = StringArrayListSerializer.deserialize(input);

            // Turn this into a list of files, and reuse the processing code above
            for (String path : paths)
            {
              Path tempFile = Path.of(path);
              if (Playlist.isPlaylist(tempFile))
              {
                openPlaylist(tempFile);
              }
              else if (Files.isDirectory(tempFile))
              {
                List<Path> playlists = PlaylistScanner.getAllPlaylists(tempFile);
                for (Path f : playlists)
                {
                  openPlaylist(f);
                }
              }
            }
          }
          // If we made it this far, everything worked.
          dtde.dropComplete(true);
          return;
        }
      }
      // Hmm, the user must not have dropped a file list
      dtde.rejectDrop();
    }
    catch (UnsupportedFlavorException | IOException | ClassNotFoundException | URISyntaxException e)
    {
      _logger.warn(e);
      dtde.rejectDrop();
    }
  }

  public IAppOptions getOptions()
  {
    return this._listFixController.getApplicationConfiguration().getConfig();
  }

  public IMediaLibrary getMediaLibrary()
  {
    return this._listFixController.getMediaLibrary();
  }

  private void fireOptionsPopup()
  {
    final ApplicationOptionsConfiguration applicationConfiguration = this._listFixController.getApplicationConfiguration();
    AppOptionsDialog optDialog = new AppOptionsDialog(this, "listFix() options", true, (JsonAppOptions) applicationConfiguration.getConfig());
    JsonAppOptions options = optDialog.showDialog();
    if (optDialog.getResultCode() == AppOptionsDialog.OK)
    {
      this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
      _listFixController.setAppOptions(options);
      _listFixController.getHistory().setCapacity(options.getMaxPlaylistHistoryEntries());
      final MediaLibraryConfiguration mediaLibraryConfiguration = _listFixController.getMediaLibraryConfiguration();
      if (options.getAlwaysUseUNCPaths())
      {
        mediaLibraryConfiguration.switchMediaLibraryToUNCPaths();
        _lstMediaLibraryDirs.setListData(new Vector<>(_listFixController.getMediaLibrary().getMediaDirectories()));
      }
      else
      {
        _listFixController.switchMediaLibraryToMappedDrives();
        _lstMediaLibraryDirs.setListData(new Vector<>(_listFixController.getMediaLibrary().getMediaDirectories()));
      }
      try
      {
        applicationConfiguration.write();
      }
      catch (IOException e)
      {
        _logger.error("Writing application configuration", e);
        throw new RuntimeException("Writing application configuration", e);
      }
      updateRecentMenu();
      setApplicationFont(options.getAppFont());
      setLookAndFeel(options.getLookAndFeel());
      setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }
  }

  /**
   * This method is called from within the constructor to initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents()
  {
    _playlistTreeRightClickMenu = new JPopupMenu();
    _miRefreshDirectoryTree = new JMenuItem();
    _miRemovePlaylistDirectory = new JMenuItem();
    _miOpenSelectedPlaylists = new JMenuItem();
    _miOpenSelectedPlaylistLocation = new JMenuItem();
    _miExactMatchesSearch = new JMenuItem();
    _miClosestMatchesSearch = new JMenuItem();
    _miDeleteFile = new JMenuItem();
    _miRenameSelectedItem = new JMenuItem();
    _statusPanel = new JPanel();
    statusLabel = new JLabel();
    _splitPane = new JSplitPane();
    _leftSplitPane = new JSplitPane();
    _mediaLibraryPanel = new JPanel();
    _mediaLibraryButtonPanel = new JPanel();
    _addMediaDirButton = new JButton();
    _removeMediaDirButton = new JButton();
    _refreshMediaDirsButton = new JButton();
    _mediaLibraryScrollPane = new JScrollPane();
    _lstMediaLibraryDirs = new JList<String>(new String[]{"Please Add A Media Directory..."});
    _playlistDirectoryPanel = new JPanel();
    _treeScrollPane = new JScrollPane();
    _playlistDirectoryTree = new JTree();
    _playlistsDirectoryButtonPanel = new JPanel();
    _btnSetPlaylistsDir = new JButton();
    _btnRefresh = new JButton();
    _btnOpenSelected = new JButton();
    _btnQuickRepair = new JButton();
    _btnDeepRepair = new JButton();
    _playlistPanel = new JPanel();
    _gettingStartedPanel = new JPanel();
    _verticalPanel = new JPanel();
    _openIconButton = new JButton();
    _spacerPanel = new JPanel();
    _newIconButton = new JButton();
    _docTabPanel = new JPanel();
    _playlistTabbedPane = new JDocumentTabbedPane();
    _mainMenuBar = new JMenuBar();
    _fileMenu = new JMenu();
    _newPlaylistMenuItem = new JMenuItem();
    _loadMenuItem = new JMenuItem();
    _openPlaylistLocationMenuItem = new JMenuItem();
    _closeMenuItem = new JMenuItem();
    _closeAllMenuItem = new JMenuItem();
    jSeparator1 = new Separator();
    _saveMenuItem = new JMenuItem();
    _saveAsMenuItem = new JMenuItem();
    _saveAllMenuItem = new JMenuItem();
    jSeparator2 = new Separator();
    _miReload = new JMenuItem();
    _miReloadAll = new JMenuItem();
    jSeparator3 = new Separator();
    recentMenu = new JMenu();
    _clearHistoryMenuItem = new JMenuItem();
    jSeparator4 = new Separator();
    _appOptionsMenuItem = new JMenuItem();
    jSeparator5 = new Separator();
    _exitMenuItem = new JMenuItem();
    _repairMenu = new JMenu();
    _miBatchRepair = new JMenuItem();
    _miExactMatchRepairOpenPlaylists = new JMenuItem();
    _miClosestMatchRepairOpenPlaylists = new JMenuItem();
    _batchRepairWinampMenuItem = new JMenuItem();
    _extractPlaylistsMenuItem = new JMenuItem();
    _helpMenu = new JMenu();
    _helpMenuItem = new JMenuItem();
    _updateCheckMenuItem = new JMenuItem();
    _aboutMenuItem = new JMenuItem();

    _miRemovePlaylistDirectory.setText("Remove Playlist Directory");
    _miRemovePlaylistDirectory.setToolTipText("Remove playlist directory from configuration");
    _miRemovePlaylistDirectory.addActionListener(evt -> this.removePlaylistDirectory(this._playlistDirectoryTree.getSelectionPaths()));
    _playlistTreeRightClickMenu.add(_miRemovePlaylistDirectory);

    _miRefreshDirectoryTree.setText("Refresh");
    _miRefreshDirectoryTree.addActionListener(evt -> _miRefreshDirectoryTreeActionPerformed());
    _playlistTreeRightClickMenu.add(_miRefreshDirectoryTree);

    _miOpenSelectedPlaylists.setText("Open");
    _miOpenSelectedPlaylists.addActionListener(evt -> _miOpenSelectedPlaylistsActionPerformed());
    _playlistTreeRightClickMenu.add(_miOpenSelectedPlaylists);

    _miOpenSelectedPlaylistLocation.setText("Open Playlist Location");
    _miOpenSelectedPlaylistLocation.addActionListener(evt -> this.openPlaylistFoldersFromPlaylistTree());
    _playlistTreeRightClickMenu.add(_miOpenSelectedPlaylistLocation);

    _miExactMatchesSearch.setText("Find Exact Matches");
    _miExactMatchesSearch.addActionListener(evt -> _miExactMatchesSearchActionPerformed());
    _playlistTreeRightClickMenu.add(_miExactMatchesSearch);

    _miClosestMatchesSearch.setText("Find Closest Matches");
    _miClosestMatchesSearch.addActionListener(evt -> _miClosestMatchesSearchActionPerformed());
    _playlistTreeRightClickMenu.add(_miClosestMatchesSearch);

    _miDeleteFile.setMnemonic('D');
    _miDeleteFile.setText("Delete file(s)");
    _miDeleteFile.setToolTipText("Delete selected file(s)");
    _miDeleteFile.addActionListener(evt -> _miDeletePlaylistActionPerformed());
    _playlistTreeRightClickMenu.add(_miDeleteFile);

    _miRenameSelectedItem.setMnemonic('R');
    _miRenameSelectedItem.setAccelerator(KeyStroke.getKeyStroke(keyEventRenameFile, 0));
    _miRenameSelectedItem.setText("Rename");
    _miRenameSelectedItem.setToolTipText("Rename selected file or folder");
    _miRenameSelectedItem.addActionListener(evt -> _miRenameSelectedItemActionPerformed());
    _playlistTreeRightClickMenu.add(_miRenameSelectedItem);

    setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    setTitle(String.format("listFix() version %s", applicationVersion));
    setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
    setIconImage(this.applicationIcon);
    setMinimumSize(new Dimension(600, 149));
    setName("mainFrame"); // NOI18N
    addComponentListener(new ComponentAdapter()
    {
      @Override public void componentResized(ComponentEvent evt)
      {
        formComponentResized();
      }
    });
    addWindowListener(new WindowAdapter()
    {
      @Override public void windowClosing(WindowEvent evt)
      {
        exitForm();
      }
    });

    _statusPanel.setBorder(BorderFactory.createEtchedBorder());
    _statusPanel.setLayout(new BorderLayout());

    statusLabel.setForeground(new Color(75, 75, 75));
    statusLabel.setHorizontalAlignment(SwingConstants.TRAILING);
    statusLabel.setText("Untitled List     Number of entries in list: 0     Number of lost entries: 0     Number of URLs: 0     Number of open playlists: 0");
    _statusPanel.add(statusLabel, BorderLayout.WEST);

    getContentPane().add(_statusPanel, BorderLayout.SOUTH);

    _splitPane.setDividerSize(7);
    _splitPane.setContinuousLayout(true);
    _splitPane.setMaximumSize(null);
    _splitPane.setOneTouchExpandable(true);
    _splitPane.setPreferredSize(new Dimension(785, 489));

    _leftSplitPane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
    _leftSplitPane.setDividerLocation(280);
    _leftSplitPane.setDividerSize(7);
    _leftSplitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);
    _leftSplitPane.setContinuousLayout(true);
    _leftSplitPane.setMaximumSize(null);
    _leftSplitPane.setOneTouchExpandable(true);

    _mediaLibraryPanel.setBorder(BorderFactory.createTitledBorder(null, "Media Directories", TitledBorder.LEFT, TitledBorder.TOP));
    _mediaLibraryPanel.setAlignmentX(0.0F);
    _mediaLibraryPanel.setAlignmentY(0.0F);
    _mediaLibraryPanel.setLayout(new BorderLayout());

    _mediaLibraryButtonPanel.setMaximumSize(null);

    _addMediaDirButton.setText("Add");
    _addMediaDirButton.setToolTipText("Where do you keep your music?");
    _addMediaDirButton.setFocusable(false);
    _addMediaDirButton.setMargin(new Insets(2, 8, 2, 8));
    _addMediaDirButton.setMinimumSize(new Dimension(53, 25));
    _addMediaDirButton.addActionListener(new ActionListener()
    {
      @Override public void actionPerformed(ActionEvent evt)
      {
        _addMediaDirButtonActionPerformed();
      }
    });
    _mediaLibraryButtonPanel.add(_addMediaDirButton);

    _removeMediaDirButton.setText("Remove");
    _removeMediaDirButton.setToolTipText("Remove a directory from the search list");
    _removeMediaDirButton.setFocusable(false);
    _removeMediaDirButton.setMargin(new Insets(2, 8, 2, 8));
    _removeMediaDirButton.setMinimumSize(new Dimension(73, 25));
    _removeMediaDirButton.addActionListener(evt -> _removeMediaDirButtonActionPerformed());
    _mediaLibraryButtonPanel.add(_removeMediaDirButton);

    _refreshMediaDirsButton.setText("Refresh");
    _refreshMediaDirsButton.setToolTipText("The contents of your media library are cached; refresh to pickup changes");
    _refreshMediaDirsButton.setFocusable(false);
    _refreshMediaDirsButton.setMargin(new Insets(2, 8, 2, 8));
    _refreshMediaDirsButton.setMinimumSize(new Dimension(71, 25));
    _refreshMediaDirsButton.addActionListener(new ActionListener()
    {
      @Override public void actionPerformed(ActionEvent evt)
      {
        _refreshMediaDirsButtonActionPerformed();
      }
    });
    _mediaLibraryButtonPanel.add(_refreshMediaDirsButton);

    _mediaLibraryPanel.add(_mediaLibraryButtonPanel, BorderLayout.SOUTH);

    _mediaLibraryScrollPane.setMaximumSize(null);
    _mediaLibraryScrollPane.setMinimumSize(null);

    _lstMediaLibraryDirs.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    _lstMediaLibraryDirs.setMaximumSize(null);
    _lstMediaLibraryDirs.setMinimumSize(null);
    _lstMediaLibraryDirs.setPreferredSize(null);
    _mediaLibraryScrollPane.setViewportView(_lstMediaLibraryDirs);

    _mediaLibraryPanel.add(_mediaLibraryScrollPane, BorderLayout.CENTER);

    _leftSplitPane.setBottomComponent(_mediaLibraryPanel);

    _playlistDirectoryPanel.setBorder(BorderFactory.createTitledBorder(null, "Playlists Directories", TitledBorder.LEFT, TitledBorder.TOP));
    _playlistDirectoryPanel.setAlignmentX(0.0F);
    _playlistDirectoryPanel.setAlignmentY(0.0F);
    _playlistDirectoryPanel.setLayout(new BorderLayout());

    _treeScrollPane.setMaximumSize(null);
    _treeScrollPane.setMinimumSize(null);

    _playlistDirectoryTree.setDragEnabled(true);
    _playlistDirectoryTree.setMaximumSize(null);
    _playlistDirectoryTree.setMinimumSize(null);
    _playlistDirectoryTree.setPreferredSize(null);
    _playlistDirectoryTree.addKeyListener(new KeyAdapter()
    {
      @Override public void keyPressed(KeyEvent evt)
      {
        _playlistDirectoryTreeKeyPressed(evt);
      }
    });
    _treeScrollPane.setViewportView(_playlistDirectoryTree);

    _playlistDirectoryPanel.add(_treeScrollPane, BorderLayout.CENTER);

    _playlistsDirectoryButtonPanel.setMaximumSize(null);
    _playlistsDirectoryButtonPanel.setMinimumSize(new Dimension(300, 35));
    _playlistsDirectoryButtonPanel.setName(""); // NOI18N

    _btnSetPlaylistsDir.setText("Add");
    _btnSetPlaylistsDir.setToolTipText("Adds another playlist directory (folder) to the configuration");
    _btnSetPlaylistsDir.setMargin(new Insets(2, 8, 2, 8));
    _btnSetPlaylistsDir.addActionListener(evt -> _btnAddPlaylistsDirActionPerformed());
    _playlistsDirectoryButtonPanel.add(_btnSetPlaylistsDir);

    _btnRefresh.setText("Refresh");
    _btnRefresh.setToolTipText("Refresh Playlists Tree");
    _btnRefresh.setFocusable(false);
    _btnRefresh.setMargin(new Insets(2, 8, 2, 8));
    _btnRefresh.setMinimumSize(new Dimension(71, 25));
    _btnRefresh.addActionListener(evt -> _btnRefreshActionPerformed());
    _playlistsDirectoryButtonPanel.add(_btnRefresh);

    _btnOpenSelected.setText("Open");
    _btnOpenSelected.setToolTipText("Open Selected Playlist(s)");
    _btnOpenSelected.setEnabled(false);
    _btnOpenSelected.setFocusable(false);
    _btnOpenSelected.setMargin(new Insets(2, 8, 2, 8));
    _btnOpenSelected.setMinimumSize(new Dimension(71, 25));
    _btnOpenSelected.addActionListener(evt -> _btnOpenSelectedActionPerformed());
    _playlistsDirectoryButtonPanel.add(_btnOpenSelected);

    _btnQuickRepair.setText("Quick");
    _btnQuickRepair.setToolTipText("Quick Batch Repair");
    _btnQuickRepair.setEnabled(false);
    _btnQuickRepair.setMargin(new Insets(2, 8, 2, 8));
    _btnQuickRepair.addActionListener(evt -> _btnQuickRepairActionPerformed());
    _playlistsDirectoryButtonPanel.add(_btnQuickRepair);

    _btnDeepRepair.setText("Deep");
    _btnDeepRepair.setToolTipText("Deep Batch Repair");
    _btnDeepRepair.setEnabled(false);
    _btnDeepRepair.setMargin(new Insets(2, 8, 2, 8));
    _btnDeepRepair.addActionListener(evt -> _btnDeepRepairActionPerformed());
    _playlistsDirectoryButtonPanel.add(_btnDeepRepair);

    _playlistDirectoryPanel.add(_playlistsDirectoryButtonPanel, BorderLayout.PAGE_END);

    _leftSplitPane.setTopComponent(_playlistDirectoryPanel);

    _splitPane.setLeftComponent(_leftSplitPane);

    _playlistPanel.setBackground(SystemColor.window);
    _playlistPanel.setLayout(new CardLayout());

    _gettingStartedPanel.setBackground(new Color(255, 255, 255));
    _gettingStartedPanel.setLayout(new GridBagLayout());

    _verticalPanel.setBackground(new Color(255, 255, 255));
    _verticalPanel.setLayout(new BoxLayout(_verticalPanel, BoxLayout.Y_AXIS));

    _openIconButton.setIcon(new ImageIcon(getClass().getResource("/images/open-big.png"))); // NOI18N
    _openIconButton.setText("Open a Playlist");
    _openIconButton.setToolTipText("Open a Playlist");
    _openIconButton.setAlignmentY(0.0F);
    _openIconButton.setFocusable(false);
    _openIconButton.setHorizontalTextPosition(SwingConstants.CENTER);
    _openIconButton.setIconTextGap(-2);
    _openIconButton.setMaximumSize(new Dimension(220, 180));
    _openIconButton.setMinimumSize(new Dimension(220, 180));
    _openIconButton.setPreferredSize(new Dimension(220, 180));
    _openIconButton.setVerticalAlignment(SwingConstants.TOP);
    _openIconButton.setVerticalTextPosition(SwingConstants.BOTTOM);
    _openIconButton.addActionListener(new ActionListener()
    {
      @Override public void actionPerformed(ActionEvent evt)
      {
        _openIconButtonActionPerformed1();
      }
    });
    _verticalPanel.add(_openIconButton);

    _spacerPanel.setBackground(new Color(255, 255, 255));
    _verticalPanel.add(_spacerPanel);

    _newIconButton.setIcon(new ImageIcon(getClass().getResource("/images/icon_new_file.png"))); // NOI18N
    _newIconButton.setText("New Playlist");
    _newIconButton.setToolTipText("New Playlist");
    _newIconButton.setAlignmentY(0.0F);
    _newIconButton.setFocusable(false);
    _newIconButton.setHorizontalTextPosition(SwingConstants.CENTER);
    _newIconButton.setIconTextGap(3);
    _newIconButton.setMaximumSize(new Dimension(220, 180));
    _newIconButton.setMinimumSize(new Dimension(220, 180));
    _newIconButton.setPreferredSize(new Dimension(220, 180));
    _newIconButton.setVerticalTextPosition(SwingConstants.BOTTOM);
    _newIconButton.addActionListener(new ActionListener()
    {
      @Override public void actionPerformed(ActionEvent evt)
      {
        _newIconButtonActionPerformed();
      }
    });
    _verticalPanel.add(_newIconButton);

    _gettingStartedPanel.add(_verticalPanel, new GridBagConstraints());

    _playlistPanel.add(_gettingStartedPanel, "_gettingStartedPanel");

    _docTabPanel.setLayout(new BorderLayout());
    _docTabPanel.add(_playlistTabbedPane, BorderLayout.CENTER);

    _playlistPanel.add(_docTabPanel, "_docTabPanel");

    _splitPane.setRightComponent(_playlistPanel);

    getContentPane().add(_splitPane, BorderLayout.CENTER);

    _mainMenuBar.setBorder(null);

    _fileMenu.setMnemonic('F');
    _fileMenu.setText("File");

    _newPlaylistMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK));
    _newPlaylistMenuItem.setMnemonic('N');
    _newPlaylistMenuItem.setText("New Playlist");
    _newPlaylistMenuItem.setToolTipText("Creates a New Playlist");
    _newPlaylistMenuItem.addActionListener(evt -> _newPlaylistMenuItemActionPerformed(evt));
    _fileMenu.add(_newPlaylistMenuItem);

    _loadMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
    _loadMenuItem.setMnemonic('O');
    _loadMenuItem.setText("Open Playlist");
    _loadMenuItem.setToolTipText("Opens a Playlist");
    _loadMenuItem.addActionListener(evt -> openIconButtonActionPerformed());
    _fileMenu.add(_loadMenuItem);

    _openPlaylistLocationMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK));
    _openPlaylistLocationMenuItem.setMnemonic('E');
    _openPlaylistLocationMenuItem.setText("Open Playlist Location");
    _openPlaylistLocationMenuItem.setToolTipText("Opens playlist folder in Explorer");
    _openPlaylistLocationMenuItem.addActionListener(evt -> openPlaylistLocation(this._currentPlaylist));
    _fileMenu.add(_openPlaylistLocationMenuItem);


    _closeMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK));
    _closeMenuItem.setMnemonic('C');
    _closeMenuItem.setText("Close");
    _closeMenuItem.setToolTipText("Closes The Current Playlist");
    _closeMenuItem.addActionListener(evt -> _closeMenuItemActionPerformed());
    _fileMenu.add(_closeMenuItem);

    _closeAllMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK));
    _closeAllMenuItem.setMnemonic('A');
    _closeAllMenuItem.setText("Close All");
    _closeAllMenuItem.setToolTipText("Closes All Open Playlists");
    _closeAllMenuItem.addActionListener(evt -> _closeAllMenuItemActionPerformed());
    _fileMenu.add(_closeAllMenuItem);
    _fileMenu.add(jSeparator1);

    _saveMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
    _saveMenuItem.setMnemonic('S');
    _saveMenuItem.setText("Save");
    _saveMenuItem.addActionListener(evt -> _saveMenuItemActionPerformed());
    _fileMenu.add(_saveMenuItem);

    _saveAsMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK));
    _saveAsMenuItem.setMnemonic('V');
    _saveAsMenuItem.setText("Save As");
    _saveAsMenuItem.addActionListener(evt -> _saveAsMenuItemActionPerformed());
    _fileMenu.add(_saveAsMenuItem);

    _saveAllMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.ALT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK));
    _saveAllMenuItem.setMnemonic('S');
    _saveAllMenuItem.setText("Save All");
    _saveAllMenuItem.setToolTipText("Save All Open Playlists");
    _saveAllMenuItem.addActionListener(evt -> _saveAllMenuItemActionPerformed());
    _fileMenu.add(_saveAllMenuItem);
    _fileMenu.add(jSeparator2);

    _miReload.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK));
    _miReload.setMnemonic('R');
    _miReload.setText("Reload");
    _miReload.setToolTipText("Reloads The Currently Open Playlist");
    _miReload.addActionListener(evt -> _miReloadActionPerformed());
    _fileMenu.add(_miReload);

    _miReloadAll.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK));
    _miReloadAll.setMnemonic('l');
    _miReloadAll.setText("Reload All");
    _miReloadAll.setToolTipText("Reloads All Currently Open Playlists");
    _miReloadAll.addActionListener(evt -> _miReloadAllActionPerformed());
    _fileMenu.add(_miReloadAll);
    _fileMenu.add(jSeparator3);

    recentMenu.setText("Recent Playlists");
    recentMenu.setToolTipText("Recently Opened Playlists");
    _fileMenu.add(recentMenu);

    _clearHistoryMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK));
    _clearHistoryMenuItem.setMnemonic('H');
    _clearHistoryMenuItem.setText("Clear Playlist History");
    _clearHistoryMenuItem.setToolTipText("Clears the recently opened playlist history");
    _clearHistoryMenuItem.addActionListener(evt -> _clearHistoryMenuItemActionPerformed());
    _fileMenu.add(_clearHistoryMenuItem);
    _fileMenu.add(jSeparator4);

    _appOptionsMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.ALT_DOWN_MASK));
    _appOptionsMenuItem.setMnemonic('p');
    _appOptionsMenuItem.setText("Options...");
    _appOptionsMenuItem.setToolTipText("Opens the Options Screen");
    _appOptionsMenuItem.addActionListener(evt -> _appOptionsMenuItemActionPerformed());
    _fileMenu.add(_appOptionsMenuItem);
    _fileMenu.add(jSeparator5);

    _exitMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F4, InputEvent.ALT_DOWN_MASK));
    _exitMenuItem.setMnemonic('x');
    _exitMenuItem.setText("Exit");
    _exitMenuItem.addActionListener(evt -> _exitMenuItemActionPerformed());
    _fileMenu.add(_exitMenuItem);

    _mainMenuBar.add(_fileMenu);

    _repairMenu.setText("Repair");

    _miBatchRepair.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_M, InputEvent.CTRL_DOWN_MASK));
    _miBatchRepair.setMnemonic('E');
    _miBatchRepair.setText("Exact Matches Repair...");
    _miBatchRepair.setToolTipText("Runs an \"Exact Matches Repair\" on lists chosen from the file system");
    _miBatchRepair.addActionListener(evt -> onMenuBatchRepairActionPerformed());
    _repairMenu.add(_miBatchRepair);

    _miExactMatchRepairOpenPlaylists.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK));
    _miExactMatchRepairOpenPlaylists.setText("Quick Repair Currently Open Playlists");
    _miExactMatchRepairOpenPlaylists.setToolTipText("Runs an \"Exact Matches Repair\" on all open playlists");
    _miExactMatchRepairOpenPlaylists.addActionListener(evt -> _miExactMatchRepairOpenPlaylistsonMenuBatchRepairActionPerformed());
    _repairMenu.add(_miExactMatchRepairOpenPlaylists);

    _miClosestMatchRepairOpenPlaylists.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.ALT_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK));
    _miClosestMatchRepairOpenPlaylists.setText("Deep Repair Currently Open Playlists");
    _miClosestMatchRepairOpenPlaylists.setToolTipText("Runs an \"Closest Matches Repair\" on all open playlists");
    _miClosestMatchRepairOpenPlaylists.addActionListener(evt -> _miClosestMatchRepairOpenPlaylistsonMenuBatchRepairActionPerformed());
    _repairMenu.add(_miClosestMatchRepairOpenPlaylists);

    _batchRepairWinampMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.ALT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK));
    _batchRepairWinampMenuItem.setMnemonic('B');
    _batchRepairWinampMenuItem.setText("Batch Repair Winamp Media Library Playlists...");
    _batchRepairWinampMenuItem.addActionListener(evt -> _batchRepairWinampMenuItemActionPerformed());
    _repairMenu.add(_batchRepairWinampMenuItem);

    _extractPlaylistsMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK));
    _extractPlaylistsMenuItem.setMnemonic('W');
    _extractPlaylistsMenuItem.setText("Extract Winamp Media Library Playlists");
    _extractPlaylistsMenuItem.setToolTipText("Extract Winamp Media Library Playlists");
    _extractPlaylistsMenuItem.addActionListener(evt -> _extractPlaylistsMenuItemActionPerformed());
    _repairMenu.add(_extractPlaylistsMenuItem);

    _mainMenuBar.add(_repairMenu);

    _helpMenu.setMnemonic('H');
    _helpMenu.setText("Help");

    _helpMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0));
    _helpMenuItem.setMnemonic('H');
    _helpMenuItem.setText("Help");
    _helpMenuItem.setToolTipText("Open listFix() documentation");
    _helpMenuItem.addActionListener(evt -> _helpMenuItemActionPerformed());
    _helpMenu.add(_helpMenuItem);

    _updateCheckMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_U, InputEvent.CTRL_DOWN_MASK));
    _updateCheckMenuItem.setMnemonic('C');
    _updateCheckMenuItem.setText("Check For Updates");
    _updateCheckMenuItem.setToolTipText("Opens the listFix() download site");
    _updateCheckMenuItem.addActionListener(evt -> _updateCheckMenuItemActionPerformed());
    _helpMenu.add(_updateCheckMenuItem);

    _aboutMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.ALT_DOWN_MASK));
    _aboutMenuItem.setMnemonic('A');
    _aboutMenuItem.setText("About");
    _aboutMenuItem.setToolTipText("Version info and such...");
    _aboutMenuItem.addActionListener(evt -> _aboutMenuItemActionPerformed());
    _helpMenu.add(_aboutMenuItem);

    _mainMenuBar.add(_helpMenu);

    _playlistTabbedPane.addDocumentChangeListener(new IDocumentChangeListener<PlaylistEditCtrl>()
    {
      @Override
      public boolean tryClosingDocument(JDocumentComponent<PlaylistEditCtrl> document)
      {
        return GUIScreen.this.tryCloseTab(document);
      }

      @Override
      public void documentOpened(JDocumentComponent<PlaylistEditCtrl> document)
      {
        updateMenuItemStatuses();
      }

      @Override
      public void documentClosed(JDocumentComponent<PlaylistEditCtrl> document)
      {
        final Playlist playlist = getPlaylistFromDocumentComponent(document);
        cleanupOnTabClose(playlist);
        if (_playlistTabbedPane.getDocumentCount() == 0)
        {
          ((CardLayout) _playlistPanel.getLayout()).show(_playlistPanel, "_gettingStartedPanel");
          currentTabChanged();
          updateMenuItemStatuses();
        }
      }

      @Override
      public void documentActivated(JDocumentComponent doc)
      {
        GUIScreen.this.currentTabChanged(doc);
      }

    });

    setJMenuBar(_mainMenuBar);

    this.componentsRequireActivePlaylist = new Component[]{
      _openPlaylistLocationMenuItem,
      _saveAllMenuItem,
      _saveAsMenuItem,
      _saveMenuItem,

      _closeAllMenuItem,
      _closeMenuItem,

      _miReload,
      _miReloadAll,

      _miExactMatchRepairOpenPlaylists,
      _miClosestMatchRepairOpenPlaylists,
    };

    splashScreen.setIconImage(applicationIcon);

    this.updatePlaylistDirectoryPanel();

    pack();
  }// </editor-fold>//GEN-END:initComponents

  private void runClosestMatchesSearchOnSelectedLists() throws IOException
  {
    _logger.debug("Run Closest Matches Search...");
    List<Path> files = this.getRecursiveSelectedFilesFromTreePlaylists();
    if (files.isEmpty())
    {
      return;
    }
    BatchRepair br = new BatchRepair(_listFixController.getMediaLibrary(), files.get(0).toFile());
    br.setDescription("Closest Matches Search");
    for (Path file : files)
    {
      br.add(new BatchRepairItem(file.toFile(), this.getOptions()));
    }
    MultiListBatchClosestMatchResultsDialog dlg = new MultiListBatchClosestMatchResultsDialog(this, true, br, this.getOptions());
    if (!dlg.getUserCancelled())
    {
      if (br.isEmpty())
      {
        JOptionPane.showMessageDialog(this, new JTransparentTextArea("There was nothing to fix in the list(s) that were processed."));
      }
      else
      {
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
        updatePlaylistDirectoryPanel();
      }
    }
  }

  private void runExactMatchesSearchOnSelectedPlaylists() throws IOException
  {
    _logger.debug("Run Exact Matches Search...");
    List<Path> files = this.getRecursiveSelectedFilesFromTreePlaylists();
    if (files.isEmpty())
    {
      _logger.info("Abort search, no files selected");
      return;
    }
    BatchRepair br = new BatchRepair(_listFixController.getMediaLibrary(), files.get(0).toFile());
    br.setDescription("Exact Matches Search");
    for (Path file : files)
    {
      br.add(new BatchRepairItem(file.toFile(), this.getOptions()));
    }
    BatchExactMatchesResultsDialog dlg = new BatchExactMatchesResultsDialog(this, true, br, this);
    if (!dlg.getUserCancelled())
    {
      if (br.isEmpty())
      {
        JOptionPane.showMessageDialog(this, new JTransparentTextArea("There was nothing to fix in the list(s) that were processed."));
      }
      else
      {
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
        updatePlaylistDirectoryPanel();
      }
    }
  }

  private void openTreeSelectedPlaylists()
  {
    this.getSelectedFilesFromTreePlaylists().forEach(toOpen -> {
      if (Files.isDirectory(toOpen))
      {
        List<Path> files =  new FileTypeSearch().findFiles(toOpen, new PlaylistFileFilter());
        for (Path f : files)
        {
          openPlaylist(f);
        }
      }
      else
      {
        openPlaylist(toOpen);
      }
    });
  }

  private List<Path> getRecursiveSelectedFilesFromTreePlaylists() throws IOException
  {
    List<Path> files = new ArrayList<>();
    for (Path file : this.getSelectedFilesFromTreePlaylists())
    {
      if (Files.isRegularFile(file))
      {
        files.add(file);
      }
      else
      {
        // We're dealing w/ a folder, get all the lists it contains.
        files.addAll(PlaylistScanner.getAllPlaylists(file));
      }
    }
    return files;
  }

  private List<Path> getSelectedFilesFromTreePlaylists()
  {
    TreePath[] paths = this._playlistDirectoryTree.getSelectionPaths();
    if (paths == null)
    {
      return Collections.emptyList();
    }
    return Arrays.stream(paths).map(FileTreeNodeGenerator :: treePathToFileSystemPath).collect(Collectors.toList());
  }

  private void deleteTreeSelectedPlaylists()
  {
    int[] selRows = _playlistDirectoryTree.getSelectionRows();
    if (selRows == null ||
      selRows.length == 0 ||
      JOptionPane.showConfirmDialog(this, new JTransparentTextArea("Are you sure you want to delete the selected file?"), "Delete Selected File?", JOptionPane.YES_NO_OPTION) == JOptionPane.NO_OPTION)
    {
      return;
    }
    final List<Path> playlistsDirectoriesToRemove = new ArrayList<>();
    for (TreePath selPath : this.getSelectedPlaylistTreePaths())
    {
      PlaylistTreeNode treeNode = (PlaylistTreeNode) selPath.getLastPathComponent();
      Path toDelete = ((PlaylistTreeNode)selPath.getLastPathComponent()).getUserObject();
      if (Files.isDirectory(toDelete))
      {
        try {
          FileUtils.deleteDirectory(toDelete);
          Files.delete(toDelete);
        } catch(IOException ioe) {
          final String message = String.format("Failed to delete folder: %s: %s", toDelete, ioe.getMessage());
          _logger.error(message, ioe);
          JOptionPane.showMessageDialog(this, message, "Deleting Playlist Failed", JOptionPane.WARNING_MESSAGE);
        }
        if (treeNode.getParent() == null)
        {
          // Node is a configured playlist directory
          playlistsDirectoriesToRemove.add(treeNode.getUserObject());
        }
      }
      else
      {
        if (Files.isWritable(toDelete))
        {
          try
          {
            Files.delete(toDelete);
          }
          catch (IOException e)
          {
            throw new RuntimeException(e);
          }
          this._playlistDirectoryTree.makeVisible(selPath);
          DefaultTreeModel treeModel = (DefaultTreeModel) this._playlistDirectoryTree.getModel();
          treeModel.removeNodeFromParent(treeNode);
          this._playlistTabbedPane.remove(toDelete);
        }
        else
        {
          final String fileShortname = toDelete.getFileName().toString();
          JOptionPane.showMessageDialog(this, String.format("Failed to delete playlist: %s", fileShortname), "Deleting Playlist Failed", JOptionPane.WARNING_MESSAGE);
        }
      }
    }
    this.removePlaylistDirectory(playlistsDirectoriesToRemove);
  }

  public List<TreePath> getSelectedPlaylistTreePaths()
  {
    int[] selRows = _playlistDirectoryTree.getSelectionRows();
    if (selRows != null)
    {
      return Arrays.stream(selRows).mapToObj(i -> _playlistDirectoryTree.getPathForRow(i)).collect(Collectors.toList());
    }
    return Collections.emptyList();
  }

  public List<PlaylistTreeNode> getSelectedPlaylistTreeNodes()
  {
    return this.getSelectedPlaylistTreePaths().stream().map(path -> (PlaylistTreeNode)path.getLastPathComponent()).collect(Collectors.toList());
  }

  private void renameTreeSelectedNode()
  {
    List<PlaylistTreeNode> selectedTreeNodes = getSelectedPlaylistTreeNodes();
    if (selectedTreeNodes.size() != 1)
    {
      _logger.debug(String.format("Will only rename when exactly 1 file is selected, got %s.", selectedTreeNodes.size()));
      return;
    }

    for (PlaylistTreeNode treeNode : selectedTreeNodes)
    {
      Path nodePath = treeNode.getUserObject();
      String str = treeNode.toString();
      String reply = JOptionPane.showInputDialog(this, new JTransparentTextArea("Rename " + str), nodePath.getFileName().toString());
      if (reply != null && !reply.equals(""))
      {
        final Path destPath = nodePath.getParent().resolve(reply);
        _logger.info(String.format("Rename playlist \"%s\" to \"%s\"", nodePath, destPath));
        try
        {
          Files.move(nodePath, destPath);
        }
        catch (IOException ioe)
        {
          _logger.warn("Renaming playlist failed", ioe);
          JOptionPane.showMessageDialog(this, new JTransparentTextArea("Failed to rename file."), "File playlist failed", JOptionPane.ERROR_MESSAGE);
          continue;
        }
        treeNode.setUserObject(destPath);
        ((DefaultTreeModel) _playlistDirectoryTree.getModel()).nodeChanged(treeNode);
        JDocumentComponent<PlaylistEditCtrl> doc = this._playlistTabbedPane.getDocument(nodePath);
        if (doc != null)
        {
          // Update playlist editor, if open
          doc.setPath(destPath);
        }

      }
    }
  }

  private void playlistDirectoryTreeNodeDoubleClicked(TreePath selPath)
  {
    Path toOpen = FileTreeNodeGenerator.treePathToFileSystemPath(selPath);
    if (Files.isRegularFile(toOpen))
    {
      this.openPlaylist(toOpen);
    }
  }

  private void _clearHistoryMenuItemActionPerformed()//GEN-FIRST:event__clearHistoryMenuItemActionPerformed
  {//GEN-HEADEREND:event__clearHistoryMenuItemActionPerformed
    try
    {
      _listFixController.clearM3UHistory();
    }
    catch (IOException e)
    {
      _logger.error("Error clear M3U history", e);
    }
    updateRecentMenu();
  }//GEN-LAST:event__clearHistoryMenuItemActionPerformed

  private void _saveAsMenuItemActionPerformed()//GEN-FIRST:event__saveAsMenuItemActionPerformed
  {//GEN-HEADEREND:event__saveAsMenuItemActionPerformed
    if (_currentPlaylist == null)
    {
      return;
    }

    this.showPlaylistSaveAsDialog(_currentPlaylist);
  }//GEN-LAST:event__saveAsMenuItemActionPerformed

  private void openIconButtonActionPerformed()//GEN-FIRST:event_openIconButtonActionPerformed
  {//GEN-HEADEREND:event_openIconButtonActionPerformed
    if (_currentPlaylist != null)
    {
      _jOpenPlaylistFileChooser.setSelectedFile(_currentPlaylist.getFile());
    }
    int response = _jOpenPlaylistFileChooser.showOpenDialog(this);
    if (response == JFileChooser.APPROVE_OPTION)
    {
      File[] playlists = _jOpenPlaylistFileChooser.getSelectedFiles();
      for (File file : playlists)
      {
        this.openPlaylist(file.toPath());
      }
    }
  }//GEN-LAST:event_openIconButtonActionPerformed(java.awt.event.ActionEvent evt)

  private void openPlaylistFoldersFromPlaylistTree()
  {
    this.getSelectedFilesFromTreePlaylists().stream()
      .map(file -> Files.isDirectory(file) ? file : file.getParent())
      .filter(Objects :: nonNull)
      .distinct()
      .forEach(this :: openFolderInExplorerPerformed);
  }

  private void openPlaylistLocation(Playlist playList)
  {
    this.openFolderInExplorerPerformed(playList.getPath().getParent());
  }

  private void openFolderInExplorerPerformed(Path folder)
  {
    if (folder != null)
    {
      try
      {
        Desktop.getDesktop().open(folder.toFile());
      }
      catch (IOException e)
      {
        _logger.warn("Failed to open playlist folder location", e);
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  public IApplicationConfiguration getApplicationConfiguration()
  {
    return this._listFixController;
  }

  @Override
  public void openPlaylist(final Path playlistPath)
  {
    // do nothing if the file is already open.
    for (Playlist list : _openPlaylists)
    {
      if (list.getPath().equals(playlistPath))
      {
        _playlistTabbedPane.setActiveDocument(playlistPath);
        return;
      }
    }

    try
    {
      if (!Files.exists(playlistPath))
      {
        JOptionPane.showMessageDialog(this, new JTransparentTextArea("File Not Found."), "Open Playlist Error", JOptionPane.ERROR_MESSAGE);
        return;
      }
    }
    catch (HeadlessException ex)
    {
      _logger.error("Open playlist error", ex);
      JOptionPane.showMessageDialog(this, new JTransparentTextArea(ExStack.textFormatErrorForUser("There was a problem opening the file you selected.", ex.getCause())),
        "Open Playlist Error", JOptionPane.ERROR_MESSAGE);
      return;
    }

    JDocumentComponent<PlaylistEditCtrl> tempComp = _playlistTabbedPane.getDocument(playlistPath);
    if (tempComp == null)
    {
      PlaylistType type = Playlist.determinePlaylistTypeFromExtension(playlistPath);

      ProgressWorker<Playlist, String> worker = new ProgressWorker<>()
      {
        @Override
        protected Playlist doInBackground() throws Exception
        {
          this.setMessage("Please wait while your playlist is opened and analyzed.");
          Playlist list = PlaylistFactory.getPlaylist(playlistPath, this, GUIScreen.this.getOptions());
          if (getApplicationConfig().getAutoLocateEntriesOnPlaylistLoad())
          {
            list.repair(GUIScreen.this.getMediaLibrary(), this);
          }
          return list;
        }

        @Override
        protected void done()
        {
          Playlist list;
          try
          {
            list = get();
          }
          catch (CancellationException ex)
          {
            return;
          }
          catch (InterruptedException | ExecutionException ex)
          {
            _logger.error("Open playlist error", ex);

            JOptionPane.showMessageDialog(GUIScreen.this, new JTransparentTextArea(ExStack.textFormatErrorForUser("There was a problem opening the file you selected, are you sure it was a playlist?", ex.getCause())),
              "Open Playlist Error", JOptionPane.ERROR_MESSAGE);
            return;
          }

          openNewTabForPlaylist(list);

          // update playlist history
          PlaylistHistory history = _listFixController.getHistory();
          history.add(playlistPath.toString());
          try
          {
            history.write();
          }
          catch (IOException e)
          {
            _logger.error("Error", e);
          }

          updateRecentMenu();
        }
      };

      boolean textOnly = type == PlaylistType.ITUNES || type == PlaylistType.XSPF;
      // Can't show a progress dialog for these as we have no way to track them at present.
      final String filename = playlistPath.getFileName().toString();
      ProgressDialog pd = new ProgressDialog(this, true, worker, "Loading '" + (filename.length() > 70 ? filename.substring(0, 70) : filename) + "'...", textOnly, true);
      pd.setVisible(true);
    }
    else
    {
      _playlistTabbedPane.setActiveDocument(playlistPath);
    }
  }

  @Override public void openNewTabForPlaylist(Playlist playlist)
  {
    PlaylistEditCtrl editor = new PlaylistEditCtrl(this);
    editor.setPlaylist(playlist);

    // Add the tab to the tabbed pane
    final Path path = playlist.getPath();
    final ImageIcon icon = getIconForPlaylist(editor.getPlaylist());

    final JDocumentComponent<PlaylistEditCtrl> tempComp = _playlistTabbedPane.openDocument(editor, path, icon);
    _playlistTabbedPane.setActiveDocument(path);

    // Tie the DocumentComponent and the Playlist in the editor together via listeners, so the former can update when the latter is modified
    playlist.addModifiedListener(
      list1 -> {
        updateTabTitleForPlaylist(list1, tempComp);
        tempComp.setIcon(getIconForPlaylist(list1));
        tempComp.setPath(list1.getPath());
      }
    );

    // Update the list of open playlists
    _openPlaylists.add(playlist);

    // update title and status bar if list was modified during loading (due to fix on load option)
    if (playlist.isModified())
    {
      refreshStatusLabel(playlist);
      updateTabTitleForPlaylist(playlist, tempComp);
    }

    if (_playlistTabbedPane.getDocumentCount() == 1)
    {
      ((CardLayout) _playlistPanel.getLayout()).show(_playlistPanel, "_docTabPanel");
    }
  }

  private ImageIcon getIconForPlaylist(Playlist list)
  {
    ImageIcon icon;
    int missing = list.getMissingCount();
    if (missing > 0)
    {
      icon = ImageIcons.IMG_MISSING;
    }
    else
    {
      if (list.getFixedCount() > 0 || list.isModified())
      {
        icon = ImageIcons.IMG_FIXED;
      }
      else
      {
        icon = ImageIcons.IMG_FOUND;
      }
    }
    return icon;
  }

  public void updateCurrentTab(Playlist list)
  {
    final JDocumentComponent<PlaylistEditCtrl> activeDoc = _playlistTabbedPane.getActiveTab();
    PlaylistEditCtrl oldEditor = activeDoc.getComponent();

    activeDoc.setPath(list.getPath());
    oldEditor.setPlaylist(list, true);

    // update playlist history
    PlaylistHistory history = _listFixController.getHistory();
    try
    {
      history.add(list.getFile().getCanonicalPath());
      history.write();
    }
    catch (IOException ex)
    {
      _logger.warn(ex);
    }

    updateRecentMenu();
  }


  public void updateRecentMenu()
  {
    recentMenu.removeAll();
    String[] files = _listFixController.getRecentM3Us();
    if (files.length == 0)
    {
      JMenuItem temp = new JMenuItem("Empty");
      temp.setEnabled(false);
      recentMenu.add(temp);
    }
    else
    {
      for (String file : files)
      {
        JMenuItem temp = new JMenuItem(file);
        temp.addActionListener(this :: recentPlaylistActionPerformed);
        recentMenu.add(temp);
      }
    }
  }

  private void updateAllComponentTreeUIs()
  {
    SwingUtilities.updateComponentTreeUI(this);
    SwingUtilities.updateComponentTreeUI(_jOpenPlaylistFileChooser);
    SwingUtilities.updateComponentTreeUI(_jMediaDirChooser);
    SwingUtilities.updateComponentTreeUI(_jSaveFileChooser);
    SwingUtilities.updateComponentTreeUI(_playlistTreeRightClickMenu);
    SwingUtilities.updateComponentTreeUI(_playlistTabbedPane);
  }

  private Playlist getPlaylistFromDocumentComponent(JDocumentComponent ctrl)
  {
    return ((PlaylistEditCtrl) ctrl.getComponent()).getPlaylist();
  }

  private void handlePlaylistSave(final Playlist list) throws HeadlessException
  {
    if (list.isNew())
    {
      this.showPlaylistSaveAsDialog(list);
    }
    else
    {
      try
      {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        ProgressWorker<Void, String> worker = new ProgressWorker<>()
        {
          @Override
          protected Void doInBackground() throws Exception
          {
            boolean saveRelative = ListFixController.getInstance().getAppOptions().getSavePlaylistsWithRelativePaths();
            list.save(saveRelative, this);
            return null;
          }
        };
        ProgressDialog pd = new ProgressDialog(this, true, worker, "Saving...", list.getType() == PlaylistType.ITUNES || list.getType() == PlaylistType.XSPF, false);
        pd.setMessage("Please wait while your playlist is saved to disk.");
        pd.setVisible(true);
        worker.get();
      }
      catch (InterruptedException | ExecutionException ex)
      {
        _logger.error("Error saving your playlist", ex);
        JOptionPane.showMessageDialog(this, new JTransparentTextArea("Sorry, there was an error saving your playlist.  Please try again, or file a bug report."));
      }
      finally
      {
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
      }
    }
  }

  @Override
  public boolean showPlaylistSaveAsDialog(Playlist playlist)
  {
    _jSaveFileChooser.setSelectedFile(playlist.getFile());
    int rc = _jSaveFileChooser.showSaveDialog(this);
    if (rc == JFileChooser.APPROVE_OPTION)
    {
      try
      {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        final File newPlaylistFile = _jSaveFileChooser.getSelectedFile();

        // prompt for confirmation if the file already exists...
        if (newPlaylistFile.exists())
        {
          int result = JOptionPane.showConfirmDialog(this, new JTransparentTextArea("You picked a file that already exists, should I really overwrite it?"), "File Exists Warning", JOptionPane.YES_NO_OPTION);
          if (result == JOptionPane.NO_OPTION)
          {
            return false;
          }
        }

        this.savePlaylistAs(playlist, newPlaylistFile);

        return true;
      }
      catch (CancellationException e)
      {
        return false;
      }
      catch (HeadlessException | InterruptedException | ExecutionException | IOException e)
      {
        _logger.error("Error saving your playlist", e);
        JOptionPane.showMessageDialog(this, new JTransparentTextArea("Sorry, there was an error saving your playlist.  Please try again, or file a bug report."));
        return false;
      }
      finally
      {
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
      }
    }
    return false;
  }

  @Override
  public void savePlaylist(Playlist playlist) throws InterruptedException, IOException, ExecutionException
  {
    this.savePlaylistAs(playlist, playlist.getFile());
  }

  @Override
  public void savePlaylistAs(Playlist playlist, File saveAsFile) throws InterruptedException, IOException, ExecutionException
  {
    final Path originalPlaylistPath = playlist.getPath();
    final Path saveAsPath = saveAsFile.toPath();
    ProgressWorker<Void, String> worker = new ProgressWorker<>()
    {
      @Override
      protected Void doInBackground() throws Exception
      {
        playlist.saveAs(saveAsFile, this);
        return null;
      }
    };

    ProgressDialog pd = new ProgressDialog(this, true, worker, "Saving...", playlist.getType() == PlaylistType.ITUNES || playlist.getType() == PlaylistType.XSPF, false);
    pd.setMessage("Please wait while your playlist is saved to disk.");
    pd.setVisible(true);

    worker.get();

    updatePlaylistDirectoryPanel();

    // update playlist history
    PlaylistHistory history = _listFixController.getHistory();
    history.add(playlist.getFile().getPath());
    history.write();
    updateRecentMenu();

    if (!originalPlaylistPath.equals(saveAsPath))
    {
      _playlistTabbedPane.renameDocument(originalPlaylistPath, saveAsPath);
    }
  }

  private void updateMenuItemStatuses()
  {
    boolean enable = _playlistTabbedPane.getDocumentCount() > 0;
    Arrays.stream(componentsRequireActivePlaylist).forEach(c -> c.setEnabled(enable));
  }

  private void currentTabChanged()
  {
    this.currentTabChanged(_playlistTabbedPane.getActiveTab());
  }

  private void currentTabChanged(JDocumentComponent documentComponent)
  {
    Playlist list = documentComponent != null ? ((PlaylistEditCtrl) documentComponent.getComponent()).getPlaylist() : null;
    if (list == _currentPlaylist)
    {
      return;
    }

    // remove listeners from current playlist
    if (_currentPlaylist != null)
    {
      _currentPlaylist.removeModifiedListener(_playlistListener);
    }

    _currentPlaylist = list;

    refreshStatusLabel(_currentPlaylist);
    if (_currentPlaylist != null)
    {
      _currentPlaylist.addModifiedListener(_playlistListener);
    }
  }

  // Setup the listener for changes to the current playlist.  Essentially turns around and calls onPlaylistModified().
  private void initPlaylistListener()
  {
    _playlistListener = new IPlaylistModifiedListener()
    {
      @Override
      public void playlistModified(Playlist list)
      {
        onPlaylistModified(list);
      }
    };
  }

  private void onPlaylistModified(Playlist list)
  {
    refreshStatusLabel(list);
  }

  private void refreshStatusLabel(Playlist list)
  {
    if (list != null)
    {
      String fmt = "Currently Open: %s%s     Number of entries in list: %d     Number of lost entries: %d     Number of URLs: %d     Number of open playlists: %d";
      String txt = String.format(fmt, list.getFilename(), list.isModified() ? "*" : "", list.size(), list.getMissingCount(), list.getUrlCount(), _playlistTabbedPane.getDocumentCount());
      statusLabel.setText(txt);
    }
    else
    {
      statusLabel.setText("No list(s) loaded");
    }
  }

  private void updateTabTitleForPlaylist(Playlist list, JDocumentComponent comp)
  {
    comp.setPath(list.getPath());
  }

  public boolean tryCloseAllTabs()
  {
    boolean result = true;
    while (_playlistTabbedPane.getDocumentCount() > 0 && result)
    {
      result = result && tryCloseTab(_playlistTabbedPane.getActiveTab());
    }
    return result;
  }

  public void runExactMatchOnAllTabs()
  {
    this._playlistTabbedPane.getAllEmbeddedMainComponent().forEach(ctrl -> {
      this._playlistTabbedPane.setActiveDocument(ctrl.getPlaylist().getPath());
      ctrl.locateMissingFiles();
    });
  }

  public void runClosestMatchOnAllTabs()
  {
    this._playlistTabbedPane.getAllEmbeddedMainComponent().forEach(ctrl -> {
      this._playlistTabbedPane.setActiveDocument(ctrl.getPlaylist().getPath());
      ctrl.locateMissingFiles();
      ctrl.bulkFindClosestMatches();
    });

  }

  private void reloadAllTabs()
  {
    this._playlistTabbedPane.getAllEmbeddedMainComponent().forEach(ctrl -> {
      this._playlistTabbedPane.setActiveDocument(ctrl.getPlaylist().getPath());
      ctrl.reloadPlaylist();
    });
  }

  public boolean tryCloseTab(JDocumentComponent<PlaylistEditCtrl> ctrl)
  {
    final Playlist playlist = getPlaylistFromDocumentComponent(ctrl);
    if (playlist.isModified())
    {
      Object[] options =
        {
          "Save", "Save As", "Don't Save", "Cancel"
        };
      int rc = JOptionPane.showOptionDialog(this, new JTransparentTextArea("The playlist \"" + playlist.getFilename() + "\" has been modified. Do you want to save the changes?"), "Confirm Close",
        JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[2]);

      if (rc == 0)
      {
        ProgressWorker<Boolean, String> worker = new ProgressWorker<>()
        {
          @Override
          protected Boolean doInBackground() throws Exception
          {
            boolean saveRelative = ListFixController.getInstance().getAppOptions().getSavePlaylistsWithRelativePaths();
            playlist.save(saveRelative, this);
            return true;
          }
        };
        ProgressDialog pd = new ProgressDialog(this, true, worker, "Saving...", false, false);
        pd.setVisible(true);

        try
        {
          boolean savedOk = worker.get();
          if (savedOk)
          {
            cleanupOnTabClose(playlist);
            return true;
          }
        }
        catch (InterruptedException ignored)
        {
          // Cancelled
        }
        catch (ExecutionException ex)
        {
          _logger.error("Save Error", ex);
          JOptionPane.showMessageDialog(GUIScreen.this, ex.getCause(), "Save Error", JOptionPane.ERROR_MESSAGE);
        }
        return false;
      }
      else if (rc == 1)
      {
        return this.showPlaylistSaveAsDialog(playlist);
      }
      else if (rc == 2)
      {
        cleanupOnTabClose(playlist);
        return true;
      }
      return false;
    }
    return true;
  }

  private void cleanupOnTabClose(Playlist list)
  {
    _openPlaylists.remove(list);
  }

  private void confirmCloseApp()
  {
    for (Playlist list : _openPlaylists)
    {
      if (list.isModified())
      {
        Object[] options =
          {
            "Discard Changes and Exit", "Cancel"
          };
        int rc = JOptionPane.showOptionDialog(this, new JTransparentTextArea("You have unsaved changes. Do you really want to discard these changes and exit?"), "Confirm Close",
          JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[1]);
        if (rc == JOptionPane.NO_OPTION)
        {
          return;
        }
        else
        {
          break;
        }
      }
    }

    try
    {
      this._listFixController.getApplicationConfiguration().write();
    }
    catch (IOException e)
    {
     _logger.error("Failed to save application settings", e);
    }
    System.exit(0);
  }

  private void _addMediaDirButtonActionPerformed()//GEN-FIRST:event__addMediaDirButtonActionPerformed
  {//GEN-HEADEREND:event__addMediaDirButtonActionPerformed
    int response = _jMediaDirChooser.showOpenDialog(this);
    if (response == JFileChooser.APPROVE_OPTION)
    {
      try
      {
        UNCFile mediaDir = new UNCFile(_jMediaDirChooser.getSelectedFile());
        if (getApplicationConfig().getAlwaysUseUNCPaths())
        {
          if (mediaDir.onNetworkDrive())
          {
            mediaDir = new UNCFile(mediaDir.getUNCPath());
          }
        }
        final String dir = mediaDir.getPath();

        // first let's see if this is a subdirectory of any of the media directories already in the list, and error out if so...
        if (ArrayFunctions.containsStringPrefixingAnotherString(_listFixController.getMediaLibrary().getMediaDirectories(), dir, !ListFixController.FILE_SYSTEM_IS_CASE_SENSITIVE))
        {
          JOptionPane.showMessageDialog(this, new JTransparentTextArea("The directory you attempted to add is a subdirectory of one already in your media library, no change was made."),
            "Reminder", JOptionPane.INFORMATION_MESSAGE);
          return;
        }
        else
        {
          // Now check if any of the media directories is a subdirectory of the one we're adding and remove the media directory if so.
          int matchCount = 0;
          for (String dirToCheck : _listFixController.getMediaLibrary().getMediaDirectories())
          {
            if (dirToCheck.startsWith(dir))
            {
              // Only showing the message the first time we find this condition...
              if (matchCount == 0)
              {
                JOptionPane.showMessageDialog(this,
                  new JTransparentTextArea("One or more of your existing media directories is a subdirectory of the directory you just added.  These directories will be removed from your list automatically."),
                  "Reminder", JOptionPane.INFORMATION_MESSAGE);
              }
              removeMediaDir(dirToCheck);
              matchCount++;
            }
          }
        }

        ProgressWorker<Void, Void> worker = new ProgressWorker<Void, Void>()
        {
          @Override
          protected Void doInBackground()
          {
            MediaLibraryOperator operator = new MediaLibraryOperator(this);
            operator.addDirectory(dir);
            return null;
          }
        };
        ProgressDialog pd = new ProgressDialog(this, true, worker, "Updating Media Library...", true, true);
        pd.setVisible(true);

        try
        {
          worker.get();
          _lstMediaLibraryDirs.setListData(new Vector<>(_listFixController.getMediaLibrary().getMediaDirectories()));
        }
        catch (InterruptedException | CancellationException ex)
        {
        }
        catch (ExecutionException ex)
        {
          _logger.error(ex);
        }
      }
      catch (HeadlessException e)
      {
        JOptionPane.showMessageDialog(this, new JTransparentTextArea("An error has occurred, media directory could not be added."));
        _logger.error("Error adding media directory", e);
      }
    }
    else
    {
      _jMediaDirChooser.cancelSelection();
    }
    updateMediaDirButtons();
  }//GEN-LAST:event__addMediaDirButtonActionPerformed

  private void _removeMediaDirButtonActionPerformed()//GEN-FIRST:event__removeMediaDirButtonActionPerformed
  {//GEN-HEADEREND:event__removeMediaDirButtonActionPerformed
    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    try
    {
      String selection = (String) _lstMediaLibraryDirs.getSelectedValue();
      if (selection != null)
      {
        if (!selection.equals("Please Add A Media Directory..."))
        {
          _listFixController.getMediaLibraryConfiguration().removeMediaDir(selection);
          _lstMediaLibraryDirs.setListData(new Vector<>(_listFixController.getMediaLibrary().getMediaDirectories()));
        }
      }
      setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }
    catch (MediaDirNotFoundException e)
    {
      setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
      JOptionPane.showMessageDialog(this, new JTransparentTextArea("An error has occured, files in the media directory you removed may not have been completely removed from the library.  Please refresh the library."));
      _logger.warn(e);
    }
    updateMediaDirButtons();
  }//GEN-LAST:event__removeMediaDirButtonActionPerformed

  private void removeMediaDir(String mediaDirectory)
  {
    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    try
    {
      _listFixController.getMediaLibraryConfiguration().removeMediaDir(mediaDirectory);
      _lstMediaLibraryDirs.setListData(new Vector<>(_listFixController.getMediaLibrary().getMediaDirectories()));
      setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }
    catch (MediaDirNotFoundException e)
    {
      setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
      JOptionPane.showMessageDialog(this, new JTransparentTextArea("An error has occured, files in the media directory you removed may not have been completely removed from the library.  Please refresh the library."));
      _logger.warn(e);
    }
    updateMediaDirButtons();
  }

  private void _aboutMenuItemActionPerformed()
  {
    JOptionPane.showMessageDialog(this, "listFix( ) v" + applicationVersion + "\n\nBrought To You By: " +
        "\n          Borewit" +
        "\n          Jeremy Caron (firewyre) " +
        "\n          Kennedy Akala (kennedyakala)" +
        "\n          John Peterson (johnpeterson)" +
        "\n\nProject home: https://github.com/Borewit/listFix",
      "About", JOptionPane.INFORMATION_MESSAGE);
  }

  private void _exitMenuItemActionPerformed()//GEN-FIRST:event__exitMenuItemActionPerformed
  {//GEN-HEADEREND:event__exitMenuItemActionPerformed
    confirmCloseApp();
  }//GEN-LAST:event__exitMenuItemActionPerformed

  /**
   * Exit the Application
   */
  private void exitForm()//GEN-FIRST:event_exitForm
  {//GEN-HEADEREND:event_exitForm
    confirmCloseApp();
  }//GEN-LAST:event_exitForm

  private void _refreshMediaDirsButtonActionPerformed()//GEN-FIRST:event__refreshMediaDirsButtonActionPerformed
  {//GEN-HEADEREND:event__refreshMediaDirsButtonActionPerformed
    refreshMediaDirs();
  }//GEN-LAST:event__refreshMediaDirsButtonActionPerformed

  private void refreshMediaDirs()
  {
    ProgressWorker<Void, Void> worker = new ProgressWorker<Void, Void>()
    {
      @Override
      protected Void doInBackground()
      {
        MediaLibraryOperator operator = new MediaLibraryOperator(this);
        operator.refresh();
        return null;
      }
    };
    ProgressDialog pd = new ProgressDialog(this, true, worker, "Updating Media Library...", true, true);
    pd.setVisible(true);

    try
    {
      worker.get();
      _lstMediaLibraryDirs.setListData(_listFixController.getMediaLibrary().getMediaDirectories().toArray(new String[]{}));
    }
    catch (InterruptedException | CancellationException ex)
    {
      _logger.warn("Cancelled");
    }
    catch (ExecutionException ex)
    {
      _logger.error("Error refresh media directories", ex);
      throw new RuntimeException(ex);
    }
  }

  private void _appOptionsMenuItemActionPerformed()//GEN-FIRST:event__appOptionsMenuItemActionPerformed
  {//GEN-HEADEREND:event__appOptionsMenuItemActionPerformed
    fireOptionsPopup();
  }//GEN-LAST:event__appOptionsMenuItemActionPerformed

  private void _miExactMatchesSearchActionPerformed()//GEN-FIRST:event__miExactMatchesSearchActionPerformed
  {//GEN-HEADEREND:event__miExactMatchesSearchActionPerformed
    try
    {
      runExactMatchesSearchOnSelectedPlaylists();
    }
    catch (IOException e)
    {
      throw new RuntimeException(e);
    }
  }//GEN-LAST:event__miExactMatchesSearchActionPerformed

  private void _helpMenuItemActionPerformed()
  {//GEN-FIRST:event__helpMenuItemActionPerformed
    BrowserLauncher.launch("https://github.com/Borewit/listFix");
  }//GEN-LAST:event__helpMenuItemActionPerformed

  private void _updateCheckMenuItemActionPerformed()
  {//GEN-FIRST:event__updateCheckMenuItemActionPerformed
    BrowserLauncher.launch("https://github.com/Borewit/listFix");
  }//GEN-LAST:event__updateCheckMenuItemActionPerformed

  private void _batchRepairWinampMenuItemActionPerformed()
  {//GEN-FIRST:event__batchRepairWinampMenuItemActionPerformed
    final BatchRepair br = WinampHelper.getWinampBatchRepair(_listFixController.getMediaLibrary(), this.getOptions());
    if (br == null || br.isEmpty())
    {
      JOptionPane.showMessageDialog(this, new JTransparentTextArea("Could not find any WinAmp Media Library playlists"));
      return;
    }

    BatchExactMatchesResultsDialog dlg = new BatchExactMatchesResultsDialog(this, true, br, this);
    if (!dlg.getUserCancelled())
    {
      if (br.isEmpty())
      {
        JOptionPane.showMessageDialog(this, new JTransparentTextArea("There was nothing to fix in the list(s) that were processed."));
      }
      else
      {
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
      }
    }
  }//GEN-LAST:event__batchRepairWinampMenuItemActionPerformed

  private void onMenuBatchRepairActionPerformed() //GEN-FIRST:event_onMenuBatchRepairActionPerformed
  {//GEN-HEADEREND:event_onMenuBatchRepairActionPerformed
    JFileChooser dlg = new JFileChooser();
    dlg.setDialogTitle("Select Playlists and/or Directories");
    dlg.setAcceptAllFileFilterUsed(false);
    dlg.addChoosableFileFilter(new PlaylistFileFilter());
    dlg.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
    dlg.setMultiSelectionEnabled(true);
    if (dlg.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
    {
      // build complete list of playlists
      List<Path> files = new ArrayList<>();
      for (File file : dlg.getSelectedFiles())
      {
        Path path = file.toPath();
        if (Files.isRegularFile(path))
        {
          files.add(path);
        }
        else
        {
          try
          {
            files.addAll(PlaylistScanner.getAllPlaylists(path));
          }
          catch (IOException e)
          {
            throw new RuntimeException(e);
          }
        }
      }
      if (files.isEmpty())
      {
        return;
      }

      File rootDir = dlg.getCurrentDirectory();
      BatchRepair br = new BatchRepair(_listFixController.getMediaLibrary(), rootDir);
      br.setDescription("Exact Matches Search");
      for (Path file : files)
      {
        br.add(new BatchRepairItem(file.toFile(), this.getOptions()));
      }

      BatchExactMatchesResultsDialog repairDlg = new BatchExactMatchesResultsDialog(this, true, br, this);
      if (!repairDlg.getUserCancelled())
      {
        if (br.isEmpty())
        {
          JOptionPane.showMessageDialog(this, new JTransparentTextArea("There was nothing to fix in the list(s) that were processed."));
        }
        else
        {
          repairDlg.setLocationRelativeTo(this);
          repairDlg.setVisible(true);
          updatePlaylistDirectoryPanel();
        }
      }
    }
  }//GEN-LAST:event_onMenuBatchRepairActionPerformed

  private void _openIconButtonActionPerformed1()//GEN-FIRST:event__openIconButtonActionPerformed1
  {//GEN-HEADEREND:event__openIconButtonActionPerformed1
    if (_currentPlaylist != null)
    {
      _jOpenPlaylistFileChooser.setSelectedFile(_currentPlaylist.getFile());
    }
    int response = _jOpenPlaylistFileChooser.showOpenDialog(this);
    if (response == JFileChooser.APPROVE_OPTION)
    {
      File[] playlists = _jOpenPlaylistFileChooser.getSelectedFiles();
      for (File file : playlists)
      {
        this.openPlaylist(file.toPath());
      }
    }
    else
    {
      _jOpenPlaylistFileChooser.cancelSelection();
    }
  }//GEN-LAST:event__openIconButtonActionPerformed1

  private void _saveMenuItemActionPerformed()//GEN-FIRST:event__saveMenuItemActionPerformed
  {//GEN-HEADEREND:event__saveMenuItemActionPerformed
    if (_currentPlaylist == null)
    {
      return;
    }

    handlePlaylistSave(_currentPlaylist);
  }//GEN-LAST:event__saveMenuItemActionPerformed

  private void _newIconButtonActionPerformed()//GEN-FIRST:event__newIconButtonActionPerformed
  {//GEN-HEADEREND:event__newIconButtonActionPerformed
    try
    {
      _currentPlaylist = new Playlist(this.getOptions());
      Path path = _currentPlaylist.getPath();
      PlaylistEditCtrl editor = new PlaylistEditCtrl(this);
      editor.setPlaylist(_currentPlaylist);
      final JDocumentComponent tempComp = _playlistTabbedPane.openDocument(editor, path);
      _playlistTabbedPane.setActiveDocument(path);

      _openPlaylists.add(_currentPlaylist);

      refreshStatusLabel(_currentPlaylist);
      _currentPlaylist.addModifiedListener(_playlistListener);

      _currentPlaylist.addModifiedListener(list -> {
        updateTabTitleForPlaylist(list, tempComp);
        tempComp.setIcon(getIconForPlaylist(list));
        tempComp.setPath(list.getPath());
      });

      if (_playlistTabbedPane.getDocumentCount() == 1)
      {
        ((CardLayout) _playlistPanel.getLayout()).show(_playlistPanel, "_docTabPanel");
      }
    }
    catch (IOException ex)
    {
      _logger.error("Error creating a new playlist", ex);
      JOptionPane.showMessageDialog(this,
        new JTransparentTextArea(ExStack.textFormatErrorForUser("Sorry, there was an error creating a new playlist.  Please try again, or file a bug report.", ex.getCause())),
        "New Playlist Error",
        JOptionPane.ERROR_MESSAGE);
    }

  }//GEN-LAST:event__newIconButtonActionPerformed

  private void _newPlaylistMenuItemActionPerformed(ActionEvent evt)//GEN-FIRST:event__newPlaylistMenuItemActionPerformed
  {//GEN-HEADEREND:event__newPlaylistMenuItemActionPerformed
    _newIconButtonActionPerformed();
  }//GEN-LAST:event__newPlaylistMenuItemActionPerformed

  private void _extractPlaylistsMenuItemActionPerformed()//GEN-FIRST:event__extractPlaylistsMenuItemActionPerformed
  {//GEN-HEADEREND:event__extractPlaylistsMenuItemActionPerformed
    final JFileChooser dlg = new JFileChooser();
    dlg.setDialogTitle("Extract to...");
    dlg.setAcceptAllFileFilterUsed(true);
    dlg.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    dlg.setMultiSelectionEnabled(false);
    int response = dlg.showOpenDialog(this);

    if (response == JFileChooser.APPROVE_OPTION)
    {
      ProgressWorker<Void, Void> worker = new ProgressWorker<Void, Void>()
      {
        @Override
        protected Void doInBackground() throws Exception
        {
          try
          {
            WinampHelper.extractPlaylists(dlg.getSelectedFile(), this);
          }
          catch (JAXBException | IOException ex)
          {
            JOptionPane.showMessageDialog(GUIScreen.this, new JTransparentTextArea("Sorry, there was a problem extracting your playlists.  The error was: " + ex.getMessage()), "Extraction Error", JOptionPane.ERROR_MESSAGE);
            _logger.warn(ex);
          }
          finally
          {
            return null;
          }
        }
      };
      ProgressDialog pd = new ProgressDialog(this, true, worker, "Extracting...", false, true);
      pd.setVisible(true);
    }
  }//GEN-LAST:event__extractPlaylistsMenuItemActionPerformed

  private void _closeMenuItemActionPerformed()//GEN-FIRST:event__closeMenuItemActionPerformed
  {//GEN-HEADEREND:event__closeMenuItemActionPerformed
    if (_playlistTabbedPane.getDocumentCount() > 0)
    {
      _playlistTabbedPane.closeActiveDocument();
    }
  }//GEN-LAST:event__closeMenuItemActionPerformed

  private void _btnRefreshActionPerformed()//GEN-FIRST:event__btnRefreshActionPerformed
  {//GEN-HEADEREND:event__btnRefreshActionPerformed
    updatePlaylistDirectoryPanel();
  }//GEN-LAST:event__btnRefreshActionPerformed

  private void _btnOpenSelectedActionPerformed()//GEN-FIRST:event__btnOpenSelectedActionPerformed
  {//GEN-HEADEREND:event__btnOpenSelectedActionPerformed
    openTreeSelectedPlaylists();
  }//GEN-LAST:event__btnOpenSelectedActionPerformed

  private void _miOpenSelectedPlaylistsActionPerformed()//GEN-FIRST:event__miOpenSelectedPlaylistsActionPerformed
  {//GEN-HEADEREND:event__miOpenSelectedPlaylistsActionPerformed
    openTreeSelectedPlaylists();
  }//GEN-LAST:event__miOpenSelectedPlaylistsActionPerformed

  private void _btnAddPlaylistsDirActionPerformed()//GEN-FIRST:event__btnSetPlaylistsDirActionPerformed
  {//GEN-HEADEREND:event__btnSetPlaylistsDirActionPerformed
    int response = _jMediaDirChooser.showOpenDialog(this);
    if (response == JFileChooser.APPROVE_OPTION)
    {
      this.addPlaylist(_jMediaDirChooser.getSelectedFile().getAbsoluteFile());
    }
  }//GEN-LAST:event__btnSetPlaylistsDirActionPerformed

  private void _btnQuickRepairActionPerformed()//GEN-FIRST:event__btnQuickRepairActionPerformed
  {//GEN-HEADEREND:event__btnQuickRepairActionPerformed
    try
    {
      runExactMatchesSearchOnSelectedPlaylists();
    }
    catch (IOException e)
    {
      throw new RuntimeException(e);
    }
  }//GEN-LAST:event__btnQuickRepairActionPerformed

  private void _closeAllMenuItemActionPerformed()//GEN-FIRST:event__closeAllMenuItemActionPerformed
  {//GEN-HEADEREND:event__closeAllMenuItemActionPerformed
    _playlistTabbedPane.closeAll();
  }//GEN-LAST:event__closeAllMenuItemActionPerformed

  private void _miRefreshDirectoryTreeActionPerformed()//GEN-FIRST:event__miRefreshDirectoryTreeActionPerformed
  {//GEN-HEADEREND:event__miRefreshDirectoryTreeActionPerformed
    updatePlaylistDirectoryPanel();
  }//GEN-LAST:event__miRefreshDirectoryTreeActionPerformed

  private void _btnDeepRepairActionPerformed()//GEN-FIRST:event__btnDeepRepairActionPerformed
  {//GEN-HEADEREND:event__btnDeepRepairActionPerformed
    try
    {
      runClosestMatchesSearchOnSelectedLists();
    }
    catch (IOException e)
    {
      throw new RuntimeException(e);
    }
  }//GEN-LAST:event__btnDeepRepairActionPerformed

  private void _miClosestMatchesSearchActionPerformed()//GEN-FIRST:event__miClosestMatchesSearchActionPerformed
  {//GEN-HEADEREND:event__miClosestMatchesSearchActionPerformed
    try
    {
      runClosestMatchesSearchOnSelectedLists();
    }
    catch (IOException e)
    {
      throw new RuntimeException(e);
    }
  }//GEN-LAST:event__miClosestMatchesSearchActionPerformed

  private void _miReloadActionPerformed()//GEN-FIRST:event__miReloadActionPerformed
  {//GEN-HEADEREND:event__miReloadActionPerformed
    if (_playlistTabbedPane.getActiveTab() != null)
    {
      ((PlaylistEditCtrl) _playlistTabbedPane.getActiveTab().getComponent()).reloadPlaylist();
    }
  }//GEN-LAST:event__miReloadActionPerformed

  private void formComponentResized()//GEN-FIRST:event_formComponentResized
  {//GEN-HEADEREND:event_formComponentResized
    // Set the position of the divider in the left split pane.
    _leftSplitPane.setDividerLocation(.7);
  }//GEN-LAST:event_formComponentResized

  private void _miDeletePlaylistActionPerformed()//GEN-FIRST:event__miDeletePlaylistActionPerformed
  {//GEN-HEADEREND:event__miDeletePlaylistActionPerformed
    deleteTreeSelectedPlaylists();
  }//GEN-LAST:event__miDeletePlaylistActionPerformed

  private void _miRenameSelectedItemActionPerformed()//GEN-FIRST:event__miRenameSelectedItemActionPerformed
  {//GEN-HEADEREND:event__miRenameSelectedItemActionPerformed
    renameTreeSelectedNode();
  }//GEN-LAST:event__miRenameSelectedItemActionPerformed

  private void _playlistDirectoryTreeKeyPressed(KeyEvent evt)//GEN-FIRST:event__playlistDirectoryTreeKeyPressed
  {//GEN-HEADEREND:event__playlistDirectoryTreeKeyPressed
    if (evt.getKeyCode() == KeyEvent.VK_ENTER)
    {
      _btnOpenSelectedActionPerformed();
    }
  }//GEN-LAST:event__playlistDirectoryTreeKeyPressed

  private void _miExactMatchRepairOpenPlaylistsonMenuBatchRepairActionPerformed()//GEN-FIRST:event__miExactMatchRepairOpenPlaylistsonMenuBatchRepairActionPerformed
  {//GEN-HEADEREND:event__miExactMatchRepairOpenPlaylistsonMenuBatchRepairActionPerformed
    runExactMatchOnAllTabs();
  }//GEN-LAST:event__miExactMatchRepairOpenPlaylistsonMenuBatchRepairActionPerformed

  private void _saveAllMenuItemActionPerformed()//GEN-FIRST:event__saveAllMenuItemActionPerformed
  {//GEN-HEADEREND:event__saveAllMenuItemActionPerformed
    for (int i = 0; i < _playlistTabbedPane.getDocumentCount(); i++)
    {
      Playlist list = getPlaylistFromDocumentComponent(_playlistTabbedPane.getComponentAt(i));
      handlePlaylistSave(list);
    }
  }//GEN-LAST:event__saveAllMenuItemActionPerformed

  private void _miClosestMatchRepairOpenPlaylistsonMenuBatchRepairActionPerformed()//GEN-FIRST:event__miClosestMatchRepairOpenPlaylistsonMenuBatchRepairActionPerformed
  {//GEN-HEADEREND:event__miClosestMatchRepairOpenPlaylistsonMenuBatchRepairActionPerformed
    runClosestMatchOnAllTabs();
  }//GEN-LAST:event__miClosestMatchRepairOpenPlaylistsonMenuBatchRepairActionPerformed

  private void _miReloadAllActionPerformed()//GEN-FIRST:event__miReloadAllActionPerformed
  {//GEN-HEADEREND:event__miReloadAllActionPerformed
    reloadAllTabs();
  }//GEN-LAST:event__miReloadAllActionPerformed

  /**
   * 
   */
  public void setApplicationFont(Font font)
  {
    Enumeration<Object> enumer = UIManager.getDefaults().keys();
    while (enumer.hasMoreElements())
    {
      Object key = enumer.nextElement();
      Object value = UIManager.get(key);
      if (value instanceof Font || value instanceof FontUIResource)
      {
        UIManager.put(key, new FontUIResource(font));
      }
    }

    UIManager.put("OptionPane.buttonFont", new FontUIResource(font));
    updateAllComponentTreeUIs();
  }

  /**
   * 
   */
  public static void InitApplicationFont(Font font)
  {
    Enumeration enumer = UIManager.getDefaults().keys();
    while (enumer.hasMoreElements())
    {
      Object key = enumer.nextElement();
      Object value = UIManager.get(key);
      if (value instanceof Font || value instanceof FontUIResource)
      {
        UIManager.put(key, new FontUIResource(font));
      }
    }

    UIManager.put("OptionPane.messsageFont", font);
    UIManager.put("OptionPane.buttonFont", font);
  }

  private void updateMediaDirButtons()
  {
    if (_lstMediaLibraryDirs.getModel().getSize() == 0)
    {
      _removeMediaDirButton.setEnabled(false);
      _refreshMediaDirsButton.setEnabled(false);
    }
    else if (_lstMediaLibraryDirs.getModel().getSize() != 0)
    {
      _removeMediaDirButton.setEnabled(true);
      _refreshMediaDirsButton.setEnabled(true);
    }
  }

  private void updatePlaylistDirectoryPanel()
  {
    final Cursor restoreCursorState = this.getCursor();
    try
    {
      this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

      List<Path> playListDirFiles = this.getApplicationConfig().getPlaylistDirectories().stream()
        .map(Path :: of)
        .collect(Collectors.toList());

      PlaylistTreeNode playlistTreeNode = FileTreeNodeGenerator.addNodes(null, playListDirFiles);

      Enumeration<TreePath> treeStateEnum = saveExpansionState(this._playlistDirectoryTree);
      try
      {
        DefaultTreeModel treeModel = (DefaultTreeModel) this._playlistDirectoryTree.getModel();
        treeModel.setRoot(playlistTreeNode);
        loadExpansionState(this._playlistDirectoryTree, treeStateEnum);
      }
      finally
      {
        loadExpansionState(this._playlistDirectoryTree, treeStateEnum);
      }
    }
    finally
    {
      this.setCursor(restoreCursorState);
    }
  }

  private void removePlaylistDirectory(TreePath[] selectedPath)
  {
    if (selectedPath != null)
    {
      removePlaylistDirectory(Arrays.stream(selectedPath)
        .map(FileTreeNodeGenerator :: treePathToFileSystemPath)
        .collect(Collectors.toList()));
    }
  }

  private void removePlaylistDirectory(Collection<Path> selectedPath)
  {
    selectedPath.forEach(playlistDirectory -> {
      _logger.info(String.format("Removing playlist directory from configuration: %s", playlistDirectory));
      this.getApplicationConfig().getPlaylistDirectories().remove(playlistDirectory.toString());
    });
    this.updatePlaylistDirectoryPanel();
    try
    {
      this._listFixController.getApplicationConfiguration().write();
    }
    catch (IOException e)
    {
      throw new RuntimeException("Failed to write updated application configuration", e);
    }
  }

  /**
   * Save the expansion state of a tree.
   *
   * @return expanded tree path as Enumeration
   */
  private Enumeration<TreePath> saveExpansionState(JTree tree)
  {
    return tree.getExpandedDescendants(new TreePath(tree.getModel().getRoot()));
  }

  /**
   * Restore the expansion state of a JTree.
   *
   * @param enumeration An enumeration of expansion state. You can get it using {@link #saveExpansionState(javax.swing.JTree)}.
   */
  private void loadExpansionState(JTree tree, Enumeration<TreePath> enumeration)
  {
    if (enumeration != null)
    {
      while (enumeration.hasMoreElements())
      {
        TreePath treePath = enumeration.nextElement();
        // tree.
        tree.expandPath(treePath);
      }
    }
  }

  private void recentPlaylistActionPerformed(ActionEvent evt)
  {
    JMenuItem temp = (JMenuItem) evt.getSource();
    Path playlist = Path.of(temp.getText());
    openPlaylist(playlist);
  }

  private void setLookAndFeel(String className)
  {
    try
    {
      String realClassName = className;
      if (className.equalsIgnoreCase("com.jgoodies.looks.plastic.theme.DarkStar"))
      {
        PlasticLookAndFeel.setPlasticTheme(new DarkStar());
        realClassName = "com.jgoodies.looks.plastic.PlasticLookAndFeel";
      }
      else if (className.equals("com.jgoodies.looks.plastic.theme.SkyBlue"))
      {
        PlasticLookAndFeel.setPlasticTheme(new SkyBlue());
        realClassName = "com.jgoodies.looks.plastic.PlasticLookAndFeel";
      }
      else if (className.equalsIgnoreCase("com.jgoodies.looks.plastic.PlasticLookAndFeel"))
      {
        PlasticLookAndFeel.setPlasticTheme(new LightGray());
      }
      else if (className.equalsIgnoreCase("com.jgoodies.looks.plastic.Plastic3DLookAndFeel"))
      {
        PlasticLookAndFeel.setPlasticTheme(new LightGray());
      }
      else if (className.equalsIgnoreCase("com.jgoodies.looks.plastic.PlasticXPLookAndFeel"))
      {
        PlasticLookAndFeel.setPlasticTheme(new LightGray());
      }

      UIManager.setLookAndFeel(realClassName);
      updateAllComponentTreeUIs();
    }
    catch (ClassNotFoundException | InstantiationException | IllegalAccessException |
           UnsupportedLookAndFeelException ex)
    {
      _logger.error("Error while changing look & feel", ex);
    }
  }

  /**
   * @param args the command line arguments
   */
  public static void main(String[] args) throws IOException, InterruptedException, InvocationTargetException
  {
    _logger.info(String.format("Starting ListFix() version \"%s\"...", applicationVersion));

    // EDT Exception
    SwingUtilities.invokeAndWait(() -> {
      // We are in the event dispatching thread
      Thread.currentThread().setUncaughtExceptionHandler((thread, e) -> {
        _logger.error("Uncaught Exception", e);
      });
    });

    IAppOptions tempOptions = ApplicationOptionsConfiguration.load().getConfig();
    InitApplicationFont(tempOptions.getAppFont());
    GUIScreen mainWindow = new GUIScreen();

    if (mainWindow.getLocation().equals(new Point(0, 0)))
    {
      DisplayMode dm = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDisplayMode();
      Dimension labelSize = mainWindow.getPreferredSize();
      mainWindow.setLocation(dm.getWidth() / 2 - (labelSize.width / 2), dm.getHeight() / 2 - (labelSize.height / 2));
    }
    mainWindow.setVisible(true);

    if (mainWindow.getOptions().getAutoRefreshMediaLibraryOnStartup())
    {
      mainWindow.refreshMediaDirs();
    }

    for (String arg : args)
    {
      try
      {
        mainWindow.openPlaylist(Path.of(arg));
      }
      catch (Exception ex)
      {
        _logger.error("Error opening playlists from command line", ex);
      }
    }
  }

  private void addPlaylist(File playlistFile)
  {
    if (playlistFile.exists())
    {
      _logger.info(String.format("Add playlist directory to configuration: %s", playlistFile));
      this.getApplicationConfig().getPlaylistDirectories().add(playlistFile.getAbsolutePath());
    }
    else
    {
      JOptionPane.showMessageDialog(this, new JTransparentTextArea("The directory you selected/entered does not exist."));
    }
    try
    {
      this._listFixController.getApplicationConfiguration().write();
    }
    catch (IOException e)
    {
      throw new RuntimeException("Failed to write application configuration", e);
    }
    this.updatePlaylistDirectoryPanel();
  }

  // <editor-fold defaultstate="collapsed" desc="Generated Code">
  // Variables declaration - do not modify//GEN-BEGIN:variables
  private JMenuItem _aboutMenuItem;
  private JButton _addMediaDirButton;
  private JMenuItem _appOptionsMenuItem;
  private JMenuItem _batchRepairWinampMenuItem;
  private JButton _btnDeepRepair;
  private JButton _btnOpenSelected;
  private JButton _btnQuickRepair;
  private JButton _btnRefresh;
  private JButton _btnSetPlaylistsDir;
  private JMenuItem _clearHistoryMenuItem;
  private JMenuItem _closeAllMenuItem;
  private JMenuItem _closeMenuItem;
  private JPanel _docTabPanel;
  private JDocumentTabbedPane<PlaylistEditCtrl> _playlistTabbedPane;
  private JMenuItem _exitMenuItem;
  private JMenuItem _extractPlaylistsMenuItem;
  private JMenu _fileMenu;
  private JPanel _gettingStartedPanel;
  private JMenu _helpMenu;
  private JMenuItem _helpMenuItem;
  private JSplitPane _leftSplitPane;
  private JMenuItem _loadMenuItem;
  private JMenuItem _openPlaylistLocationMenuItem;
  private JList<String> _lstMediaLibraryDirs;
  private JMenuBar _mainMenuBar;
  private JPanel _mediaLibraryButtonPanel;
  private JPanel _mediaLibraryPanel;
  private JScrollPane _mediaLibraryScrollPane;
  private JMenuItem _miBatchRepair;
  private JMenuItem _miClosestMatchRepairOpenPlaylists;
  private JMenuItem _miClosestMatchesSearch;
  private JMenuItem _miDeleteFile;
  private JMenuItem _miExactMatchRepairOpenPlaylists;
  private JMenuItem _miExactMatchesSearch;
  private JMenuItem _miOpenSelectedPlaylists;
  private JMenuItem _miOpenSelectedPlaylistLocation;
  private JMenuItem _miRefreshDirectoryTree;
  private JMenuItem _miRemovePlaylistDirectory;
  private JMenuItem _miReload;
  private JMenuItem _miReloadAll;
  private JMenuItem _miRenameSelectedItem;
  private JButton _newIconButton;
  private JMenuItem _newPlaylistMenuItem;
  private JButton _openIconButton;
  private JPanel _playlistDirectoryPanel;
  private JTree _playlistDirectoryTree;
  private JPanel _playlistPanel;
  private JPopupMenu _playlistTreeRightClickMenu;
  private JPanel _playlistsDirectoryButtonPanel;
  private JButton _refreshMediaDirsButton;
  private JButton _removeMediaDirButton;
  private JMenu _repairMenu;
  private JMenuItem _saveAllMenuItem;
  private JMenuItem _saveAsMenuItem;
  private JMenuItem _saveMenuItem;
  private JPanel _spacerPanel;
  private JSplitPane _splitPane;
  private JPanel _statusPanel;
  private JScrollPane _treeScrollPane;
  private JMenuItem _updateCheckMenuItem;
  private JPanel _verticalPanel;
  private Separator jSeparator1;
  private Separator jSeparator2;
  private Separator jSeparator3;
  private Separator jSeparator4;
  private Separator jSeparator5;
  private JMenu recentMenu;
  private JLabel statusLabel;
  // End of variables declaration//GEN-END:variables
  // </editor-fold>
}
