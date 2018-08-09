package com.sensepost.mallet.swing;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.security.auth.x500.X500Principal;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.table.AbstractTableModel;

import org.jdesktop.swingx.JXTable;

import com.sensepost.mallet.util.ListModelMutator;

public class KeyStoreDialog extends JDialog {

	private final JPanel contentPanel = new JPanel();

	private KeyStore keystore = null;

	private JTable table;
	private CertificateTableModel tableModel = new CertificateTableModel();

	/*
	 * There is no notification mechanism for changes to a keystore
	 * 
	 * So we simply poll periodically to see if it has changed.
	 */
	private Timer updateTimer;

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		try {
			KeyStoreDialog dialog = new KeyStoreDialog();
			KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
			File ks = new File("keystore.jks");
			keystore.load(new FileInputStream(ks), "password".toCharArray());
			dialog.setKeyStore(keystore);
			dialog.setVisible(true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public KeyStoreDialog() {
		this(null);
	}

	/**
	 * Create the dialog.
	 */
	public KeyStoreDialog(Frame owner) {
		super(owner);
		setBounds(100, 100, 450, 300);
		getContentPane().setLayout(new BorderLayout());
		contentPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
		getContentPane().add(contentPanel, BorderLayout.CENTER);
		
		JPanel buttonPane = new JPanel();
		buttonPane.setLayout(new FlowLayout(FlowLayout.RIGHT));
		getContentPane().add(buttonPane, BorderLayout.SOUTH);
		JButton okButton = new JButton("OK");
		okButton.setActionCommand("OK");
		buttonPane.add(okButton);
		getRootPane().setDefaultButton(okButton);
		
		contentPanel.setLayout(new BorderLayout(0, 0));
		JScrollPane scrollPane = new JScrollPane();
		contentPanel.add(scrollPane, BorderLayout.CENTER);

		table = new JXTable(tableModel);
		table.setAutoCreateRowSorter(true);
		table.setDefaultRenderer(Date.class, new DateRenderer(false));
		scrollPane.setViewportView(table);

		JPanel buttonPanel = new JPanel();
		contentPanel.add(buttonPanel, BorderLayout.EAST);
		GridBagLayout gbl_buttonPanel = new GridBagLayout();
		gbl_buttonPanel.columnWidths = new int[] { 81, 0 };
		gbl_buttonPanel.rowHeights = new int[] { 25, 25, 0 };
		gbl_buttonPanel.columnWeights = new double[] { 0.0, Double.MIN_VALUE };
		gbl_buttonPanel.rowWeights = new double[] { 0.0, 0.0, Double.MIN_VALUE };
		buttonPanel.setLayout(gbl_buttonPanel);

		JButton deleteButton = new JButton("Delete");
		deleteButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (keystore == null)
					return;
				int[] rows = table.getSelectedRows();
				if (rows != null && rows.length > 0) {
					synchronized (KeyStoreDialog.this.keystore) {
						try {
							for (int i = 0; i < rows.length; i++) {
								int rowIndex = table
										.convertRowIndexToModel(rows[i]);
								String alias = tableModel.getAlias(rowIndex);
								KeyStoreDialog.this.keystore.deleteEntry(alias);
							}
						} catch (Exception ex) {
							ex.printStackTrace();
						}
					}
					updateTableData();
				}
			}
		});

		// JButton importButton = new JButton("Import");
		// buttonPanel.add(importButton);

//		JButton viewButton = new JButton("View");
//		viewButton.addActionListener(new ActionListener() {
//
//			@Override
//			public void actionPerformed(ActionEvent e) {
//				if (keystore == null)
//					return;
//			}
//		});
//		GridBagConstraints gbc_viewButton = new GridBagConstraints();
//		gbc_viewButton.fill = GridBagConstraints.HORIZONTAL;
//		gbc_viewButton.anchor = GridBagConstraints.WEST;
//		gbc_viewButton.insets = new Insets(0, 0, 5, 0);
//		gbc_viewButton.gridx = 0;
//		gbc_viewButton.gridy = 0;
//		buttonPanel.add(viewButton, gbc_viewButton);
//		GridBagConstraints gbc_deleteButton = new GridBagConstraints();
//		gbc_deleteButton.fill = GridBagConstraints.HORIZONTAL;
//		gbc_deleteButton.anchor = GridBagConstraints.WEST;
//		gbc_deleteButton.gridx = 0;
//		gbc_deleteButton.gridy = 1;
//		buttonPanel.add(deleteButton, gbc_deleteButton);

		updateTimer = new Timer(2000, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				updateTableData();
			}
		});
		updateTimer.setInitialDelay(0);
		updateTimer.setRepeats(true);

		setModal(true);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
	}

	public void setKeyStore(KeyStore keystore) {
		updateTimer.stop();
		this.keystore = keystore;
		if (keystore != null)
			updateTimer.start();
	}

	private void updateTableData() {
		List<String> aliases = new ArrayList<>();
		Map<String, X509Certificate> certs = new HashMap<>();
		Map<String, Date> updated = new HashMap<>();
		try {
			synchronized (keystore) {
				Enumeration<String> en = keystore.aliases();
				while (en.hasMoreElements()) {
					String alias = en.nextElement();
					Certificate cert = keystore.getCertificate(alias);
					if (cert instanceof X509Certificate) {
						aliases.add(alias);
						certs.put(alias, (X509Certificate) cert);
						updated.put(alias, keystore.getCreationDate(alias));
					}
				}
			}
			Collections.sort(aliases);
			tableModel.update(aliases, certs, updated);
		} catch (KeyStoreException e) {
			e.printStackTrace();
			updateTimer.stop();
		}
	}

	private static class CertificateTableModel extends AbstractTableModel {

		private static String[] columnNames = new String[] { "Subject",
				"Updated" };
		private static Class<?>[] columnClasses = new Class<?>[] {
				X500Principal.class, Date.class };

		private DefaultListModel<String> aliases = new DefaultListModel<>();
		private Map<String, X509Certificate> certs = new HashMap<>();
		private Map<String, Date> updated = new HashMap<>();

		private ListModelMutator<String> mutator = new ListModelMutator<>(
				aliases);

		private ListDataListener listener = new ListDataListener() {

			@Override
			public void intervalAdded(ListDataEvent e) {
				fireTableRowsInserted(e.getIndex0(), e.getIndex1());
			}

			@Override
			public void intervalRemoved(ListDataEvent e) {
				fireTableRowsDeleted(e.getIndex0(), e.getIndex1());
			}

			@Override
			public void contentsChanged(ListDataEvent e) {
				fireTableDataChanged();
			}

		};

		public CertificateTableModel() {
			aliases.addListDataListener(listener);
		}

		public void update(List<String> aliases,
				Map<String, X509Certificate> certs, Map<String, Date> updated) {
			// Make sure that all possible dependent data is available for the
			// intermediate states
			this.updated.putAll(updated);
			this.certs.putAll(certs);
			mutator.mutate(aliases);
			this.updated = updated;
			this.certs = certs;
		}

		public String getAlias(int rowIndex) {
			return aliases.get(rowIndex);
		}

		@Override
		public int getRowCount() {
			return aliases.size();
		}

		@Override
		public int getColumnCount() {
			return columnNames.length;
		}

		@Override
		public String getColumnName(int columnIndex) {
			return columnNames[columnIndex];
		}

		@Override
		public Class<?> getColumnClass(int columnIndex) {
			return columnClasses[columnIndex];
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			String alias = aliases.get(rowIndex);
			X509Certificate cert = certs.get(alias);
			switch (columnIndex) {
			case 0:
				return cert.getSubjectX500Principal();
			case 1:
				return updated.get(alias);
			}
			return null;
		}

	}

}
