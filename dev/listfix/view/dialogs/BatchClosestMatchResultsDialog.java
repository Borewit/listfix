/*
 * listFix() - Fix Broken Playlists!
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

package listfix.view.dialogs;

import java.awt.Component;
import java.awt.DisplayMode;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;

import java.io.IOException;

import java.util.EventObject;
import java.util.List;

import javax.swing.AbstractCellEditor;
import javax.swing.AbstractListModel;
import javax.swing.ComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.plaf.basic.BasicComboBoxRenderer;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;

import listfix.model.BatchMatchItem;
import listfix.model.MatchedPlaylistEntry;
import listfix.util.ExStack;
import listfix.view.controls.ClosestMatchesSearchScrollableResultsPanel;
import listfix.view.support.ZebraJTable;

import org.apache.log4j.Logger;

/**
 * This is the results dialog we display when running a closest matches search on multiple files in a playlist (this operation originally worked only on a single entry)..
 * @author jcaron
 */
public class BatchClosestMatchResultsDialog extends javax.swing.JDialog
{
	private static final Logger _logger = Logger.getLogger(BatchClosestMatchResultsDialog.class);

	public BatchClosestMatchResultsDialog(java.awt.Frame parent, List<BatchMatchItem> items)
	{
		super(parent, true);
		_items = items;
		initComponents();

		// get screenwidth using workaround for vmware/linux bug
        int screenWidth;
        DisplayMode dmode = getGraphicsConfiguration().getDevice().getDisplayMode();
        if (dmode != null)
		{
            screenWidth = dmode.getWidth();
		}
        else
		{
            screenWidth = getGraphicsConfiguration().getBounds().width;
		}
        int newWidth = _pnlResults.getTableWidth() + getWidth() - _pnlResults.getWidth() + 2;
        setSize(Math.min(newWidth, screenWidth - 50), getHeight());
	}

	public boolean isAccepted()
	{
		return _isAccepted;
	}
	private boolean _isAccepted;

	public List<BatchMatchItem> getMatches()
	{
		return _items;
	}
	private List<BatchMatchItem> _items;

	/** This method is called from within the constructor to
	 * initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is
	 * always regenerated by the Form Editor.
	 */
	@SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel1 = new javax.swing.JPanel();
        _btnOk = new javax.swing.JButton();
        _btnCancel = new javax.swing.JButton();
        _pnlResults = new ClosestMatchesSearchScrollableResultsPanel(_items);

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Select Closest Matches");

        jPanel1.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));

        _btnOk.setText("OK");
        _btnOk.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                onBtnOkActionPerformed(evt);
            }
        });
        jPanel1.add(_btnOk);

        _btnCancel.setText("Cancel");
        _btnCancel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                onBtnCancelActionPerformed(evt);
            }
        });
        jPanel1.add(_btnCancel);

        getContentPane().add(jPanel1, java.awt.BorderLayout.SOUTH);
        getContentPane().add(_pnlResults, java.awt.BorderLayout.CENTER);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void onBtnOkActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_onBtnOkActionPerformed
    {//GEN-HEADEREND:event_onBtnOkActionPerformed
		_isAccepted = true;
		if (_pnlResults.getSelectedRow() > -1 && _pnlResults.getSelectedColumn() == 3)
		{
			TableCellEditor cellEditor = _pnlResults.getCellEditor(_pnlResults.getSelectedRow(), _pnlResults.getSelectedColumn());
			cellEditor.stopCellEditing();
		}
		setVisible(false);
    }//GEN-LAST:event_onBtnOkActionPerformed

    private void onBtnCancelActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_onBtnCancelActionPerformed
    {//GEN-HEADEREND:event_onBtnCancelActionPerformed
		_isAccepted = false;
		setVisible(false);
    }//GEN-LAST:event_onBtnCancelActionPerformed
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton _btnCancel;
    private javax.swing.JButton _btnOk;
    private listfix.view.controls.ClosestMatchesSearchScrollableResultsPanel _pnlResults;
    private javax.swing.JPanel jPanel1;
    // End of variables declaration//GEN-END:variables

	private ZebraJTable createTable()
	{
		return new ZebraJTable()
		{
			@Override
			public String getToolTipText(MouseEvent event)
			{
				Point point = event.getPoint();
				int rawRowIx = rowAtPoint(point);
				int rawColIx = columnAtPoint(point);
				if (rawRowIx >= 0 && rawColIx >= 0)
				{
					int rowIx = convertRowIndexToModel(rawRowIx);
					int colIx = convertColumnIndexToModel(rawColIx);
					if (rowIx >= 0 && rowIx < _items.size() && (colIx == 1 || colIx == 2 || colIx == 3))
					{
						BatchMatchItem item = _items.get(rowIx);
						if (colIx == 1)
						{
							return item.getEntry().getPath();
						}
						else
						{
							MatchedPlaylistEntry match = item.getSelectedMatch();
							if (match != null)
							{
								return match.getPlaylistFile().getPath();
							}
						}
					}
				}
				return super.getToolTipText(event);
			}
		};
	}
	
	private class ButtonRenderer implements TableCellRenderer
	{
		JButton button = new JButton();

		@Override
		public Component getTableCellRendererComponent(JTable table,
			Object value,
			boolean isSelected,
			boolean hasFocus,
			int row, int column)
		{
			button.setText("PLAY");
			return button;
		}
	}

	private class ButtonEditor extends AbstractCellEditor implements TableCellEditor, ActionListener
	{
		JTable table;
		JButton button = new JButton();
		int clickCountToStart = 1;

		public ButtonEditor(JTable table)
		{
			this.table = table;
			button.addActionListener(this);
		}

		@Override
		public void actionPerformed(ActionEvent e)
		{
			int row = table.getEditingRow();
			int rowIx = table.getRowSorter().convertRowIndexToModel(row);
			BatchMatchItem item = _items.get(rowIx);
			MatchedPlaylistEntry match = item.getSelectedMatch();		

			try
			{
				match.getPlaylistFile().play();
			}
			catch (IOException ex)
			{
				_logger.warn(ExStack.toString(ex));
			}
			catch (InterruptedException ex)
			{
				_logger.warn(ExStack.toString(ex));
			}
		}

		@Override
		public Component getTableCellEditorComponent(JTable table,
			Object value,
			boolean isSelected,
			int row, int column)
		{
			button.setText("PLAY");			
			return button;
		}

		@Override
		public Object getCellEditorValue()
		{
			return button.getText();
		}

		@Override
		public boolean isCellEditable(EventObject anEvent)
		{
			if (anEvent instanceof MouseEvent)
			{
				return ((MouseEvent) anEvent).getClickCount() >= clickCountToStart;
			}
			return true;
		}

		@Override
		public boolean shouldSelectCell(EventObject anEvent)
		{
			return true;
		}

		@Override
		public boolean stopCellEditing()
		{
			return super.stopCellEditing();
		}

		@Override
		public void cancelCellEditing()
		{
			super.cancelCellEditing();
		}
	}

	private class MatchTableModel extends AbstractTableModel
	{
		@Override
		public int getRowCount()
		{
			return _items.size();
		}

		@Override
		public int getColumnCount()
		{
			return 5;
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex)
		{
			final BatchMatchItem item = _items.get(rowIndex);
			switch (columnIndex)
			{
				case 0:
					return rowIndex + 1;

				case 1:
					return item.getEntry().getFileName();

				case 2:
					return "";
				case 3:
					MatchedPlaylistEntry match = item.getSelectedMatch();
					if (match != null)
					{
						return match.getPlaylistFile().getFileName();
					}
					else
					{
						return "< skip >";
					}

				default:
					return null;
			}
		}

		@Override
		public String getColumnName(int column)
		{
			switch (column)
			{
				case 0:
					return "#";
				case 1:
					return "Original Name";
				case 2:
					return "Preview";
				case 3:
					return "Matched Name";
				default:
					return null;
			}
		}

		@Override
		public Class<?> getColumnClass(int columnIndex)
		{
			return columnIndex == 0 ? Integer.class : Object.class;
		}

		@Override
		public boolean isCellEditable(int rowIndex, int columnIndex)
		{
			//return super.isCellEditable(rowIndex, columnIndex);
			return columnIndex == 2 || columnIndex == 3;
		}

		@Override
		public void setValueAt(Object aValue, int rowIndex, int columnIndex)
		{
			if (columnIndex == 3)
			{
				int ix = (Integer) aValue;
				//Log.write("set %d to %d", rowIndex, ix);
				BatchMatchItem item = _items.get(rowIndex);
				item.setSelectedIx(ix);
			}
		}
	}

	private class MatchEditor extends AbstractCellEditor implements TableCellEditor
	{
		public MatchEditor()
		{
			_model = new MatchComboBoxModel();
			_combo = new JComboBox(_model);
			_combo.setRenderer(new MyComboBoxRenderer());
			_combo.setMaximumRowCount(25);
			_combo.setFocusable(false);
		}
		JComboBox _combo;
		MatchComboBoxModel _model;

		@Override
		public Object getCellEditorValue()
		{
			return _combo.getSelectedIndex() - 1;
		}

		@Override
		public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column)
		{
			BatchMatchItem item = _items.get(row);
			_model.setMatches(item.getMatches());
			_combo.setSelectedIndex(item.getSelectedIx() + 1);
			return _combo;
		}

		private class MyComboBoxRenderer extends BasicComboBoxRenderer
		{
			public Component getListCellRendererComponent(JList list, Object value,
				int index, boolean isSelected, boolean cellHasFocus)
			{
				JComponent comp = (JComponent) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

				if (isSelected)
				{
					if (index > 0)
					{
						list.setToolTipText(((MatchedPlaylistEntry)((MatchComboBoxModel)list.getModel())._matches.get(index - 1)).getPlaylistFile().getPath());
					}
				}
				
				return comp;
			}
		}
	}

	private static class MatchComboBoxModel extends AbstractListModel implements ComboBoxModel
	{
		List<MatchedPlaylistEntry> _matches;
		Object _selected;

		public void setMatches(List<MatchedPlaylistEntry> matches)
		{
			_matches = matches;
			_selected = null;
			fireContentsChanged(this, 0, _matches.size());
		}

		@Override
		public int getSize()
		{
			return _matches != null ? _matches.size() + 1 : 0;
		}

		@Override
		public Object getElementAt(int index)
		{
			if (_matches != null)
			{
				if (index > 0)
				{
					MatchedPlaylistEntry match = _matches.get(index - 1);
					return Integer.toString(match.getScore()) + ": " + match.getPlaylistFile().getFileName();
				}
				else
				{
					return "< skip >";
				}
			}
			else
			{
				return null;
			}
		}

		@Override
		public void setSelectedItem(Object anItem)
		{
			_selected = anItem;
		}

		@Override
		public Object getSelectedItem()
		{
			return _selected;
		}
	}
}
