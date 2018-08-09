package com.sensepost.mallet.swing;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.security.KeyStore;
import java.util.logging.Handler;
import java.util.prefs.Preferences;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JTabbedPane;

import com.mxgraph.examples.swing.editor.EditorActions.ExitAction;
import com.mxgraph.examples.swing.editor.EditorMenuBar;
import com.mxgraph.swing.mxGraphComponent;
import com.sensepost.mallet.InterceptController;

public class InterceptFrame extends JFrame {

	private JCheckBoxMenuItem interceptMenuItem;
	private ConnectionPanel connectionPanel;
	private LogPanel logPanel;

	private JTabbedPane tabbedPane;

	private GraphEditor editor;

	private mxGraphComponent graphComponent;
	private JMenu tlsMenu;
	private JMenuItem serverCertificatesMenu, clientCertificatesMenu;

	private KeyStore serverKeyStore = null;
	private KeyStoreDialog keyStoreDialog = new KeyStoreDialog();

	private FramePersistence persistence = new FramePersistence(
			Preferences.userNodeForPackage(InterceptFrame.class));

	public InterceptFrame(mxGraphComponent graphComponent) {
		setTitle("Mallet");
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

		this.graphComponent = graphComponent;

		tabbedPane = new JTabbedPane(JTabbedPane.TOP);
		getContentPane().add(tabbedPane, BorderLayout.CENTER);

		editor = new GraphEditor("", graphComponent);

		tabbedPane.addTab("Graph Editor", editor);

		connectionPanel = new ConnectionPanel();
		tabbedPane.addTab("Connections", connectionPanel);

		logPanel = new LogPanel();
		tabbedPane.addTab("Log", logPanel);

		JMenuBar menuBar = new EditorMenuBar(editor);
		setJMenuBar(menuBar);

		tlsMenu = new JMenu("TLS");
		menuBar.add(tlsMenu);

		serverCertificatesMenu = new JMenuItem("Server Certificates");
		serverCertificatesMenu.setEnabled(false);
		serverCertificatesMenu.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (serverKeyStore == null)
					return;
				keyStoreDialog.setKeyStore(serverKeyStore);
				keyStoreDialog.setTitle("Server Keys");
				keyStoreDialog.setVisible(true);
			}
		});
		tlsMenu.add(serverCertificatesMenu);

		// clientCertificatesMenu = new JMenuItem("Client Certificates");
		// tlsMenu.add(clientCertificatesMenu);

		interceptMenuItem = new JCheckBoxMenuItem("Intercept");
		interceptMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				connectionPanel.setIntercept(interceptMenuItem.isSelected());
			}
		});
		menuBar.add(interceptMenuItem);

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				new ExitAction().actionPerformed(new ActionEvent(editor, 0,
						"Exit"));
			}
		});

		persistence.apply(this, NORMAL, 800, 600, 0, 0);
		addWindowStateListener(persistence);
		addComponentListener(persistence);
	}

	public void setServerKeyStore(KeyStore serverKeyStore) {
		this.serverKeyStore = serverKeyStore;
		serverCertificatesMenu.setEnabled(serverKeyStore != null);
	}

	public InterceptController getInterceptController() {
		return connectionPanel;
	}

	protected JCheckBoxMenuItem getInterceptMenuItem() {
		return interceptMenuItem;
	}

	public Handler getLogHandler() {
		return logPanel.getHandler();
	}
}
