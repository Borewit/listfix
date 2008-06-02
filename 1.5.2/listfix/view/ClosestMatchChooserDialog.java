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

package listfix.view;

/**
 *
 * @author  jcaron
 */
import java.awt.Point;
import java.util.Vector;
import listfix.model.*;

public class ClosestMatchChooserDialog extends javax.swing.JDialog 
{
	private static final long serialVersionUID = -5374761780814261291L;
	private Vector<MatchedPlaylistEntry> matchData;
    private int choice = -1;
    public static final int OK = 0;
    public static final int CANCEL = 1;
    private int resultCode = 1;
    
    /** Creates new form ClosestMatchChooserDialog */
    public ClosestMatchChooserDialog(java.awt.Frame parent, Vector<MatchedPlaylistEntry> matches, boolean modal) 
    {
        super(parent, modal);
        matchData = matches;
        initComponents();
        jTable1.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
    }
    
    public int getResultCode()
    {
        return resultCode;
    }
    
    public void setResultCode(int i)
    {
        resultCode = i;
    }
    
    public int getChoice()
    {
        return choice;
    }
    
    public void setChoice(int i)
    {
        choice = i;
    }
    
    public void center()
  {
      Point parentLocation = this.getParent().getLocationOnScreen();
      double x = parentLocation.getX();
      double y = parentLocation.getY();
      int width = this.getParent().getWidth();
      int height = this.getParent().getHeight();
      
      this.setLocation((int)x + (width - this.getWidth())/2, (int)y + (height - this.getHeight())/2);
  }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    private void initComponents() {//GEN-BEGIN:initComponents
        jPanel1 = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        jTable1 = new javax.swing.JTable();
        jPanel2 = new javax.swing.JPanel();
        jButton1 = new javax.swing.JButton();
        jButton2 = new javax.swing.JButton();
        jPanel3 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        
        getContentPane().setLayout(new java.awt.GridLayout(1, 0));
        
        setTitle("Closest Filename Matches:");
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                closeDialog(evt);
            }
        });
        
        jPanel1.setLayout(new java.awt.BorderLayout());
        
        jScrollPane1.setPreferredSize(new java.awt.Dimension(453, 200));
        jTable1.setFont(new java.awt.Font("Verdana", 0, 9));
        jTable1.setModel(new MatchedFileTableModel(matchData));
        jTable1.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                jTable1MousePressed(evt);
            }
        });
        
        jScrollPane1.setViewportView(jTable1);
        
        jPanel1.add(jScrollPane1, java.awt.BorderLayout.CENTER);
        
        jButton1.setFont(new java.awt.Font("Verdana", 0, 9));
        jButton1.setText("OK");
        jButton1.setEnabled(false);
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });
        
        jPanel2.add(jButton1);
        
        jButton2.setFont(new java.awt.Font("Verdana", 0, 9));
        jButton2.setText("Cancel");
        jButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton2ActionPerformed(evt);
            }
        });
        
        jPanel2.add(jButton2);
        
        jPanel1.add(jPanel2, java.awt.BorderLayout.SOUTH);
        
        jPanel3.setLayout(new java.awt.GridBagLayout());
        java.awt.GridBagConstraints gridBagConstraints1;
        
        jLabel1.setFont(new java.awt.Font("Verdana", 0, 9));
        jLabel1.setText("Choose a replacement from the list below");
        gridBagConstraints1 = new java.awt.GridBagConstraints();
        jPanel3.add(jLabel1, gridBagConstraints1);
        
        jLabel2.setFont(new java.awt.Font("Verdana", 0, 9));
        jLabel2.setText("and click ok, or click cancel to quit the operation.");
        gridBagConstraints1 = new java.awt.GridBagConstraints();
        gridBagConstraints1.gridx = 0;
        gridBagConstraints1.gridy = 1;
        jPanel3.add(jLabel2, gridBagConstraints1);
        
        jPanel1.add(jPanel3, java.awt.BorderLayout.NORTH);
        
        getContentPane().add(jPanel1);
        
        pack();
    }//GEN-END:initComponents

    private void jTable1MousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jTable1MousePressed
        if (jTable1.getSelectedRowCount() > 0)
        {
            jButton1.setEnabled(true);
        }
    }//GEN-LAST:event_jTable1MousePressed

    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton2ActionPerformed
        setVisible(false);        
        dispose();
        setResultCode( CANCEL );
    }//GEN-LAST:event_jButton2ActionPerformed

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        setVisible(false);        
        setChoice(jTable1.getSelectedRow());
        dispose();
        setResultCode( OK );
    }//GEN-LAST:event_jButton1ActionPerformed

    /** Closes the dialog */
    private void closeDialog(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_closeDialog
        setVisible(false);
        dispose();
    }//GEN-LAST:event_closeDialog

    /**
    * @param args the command line arguments
    */
    public static void main(String args[]) {
        new ClosestMatchChooserDialog(new javax.swing.JFrame(), null, true).setVisible(true);
    }


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTable jTable1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    // End of variables declaration//GEN-END:variables

}