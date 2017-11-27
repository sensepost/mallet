package com.sensepost.mallet.swing;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.swing.view.mxICellEditor;
import com.mxgraph.view.mxGraph;
import com.sensepost.mallet.InterceptController;
import com.sensepost.mallet.graph.Graph;

import io.netty.util.ReferenceCountUtil;

public class InterceptFrame extends JFrame implements InterceptController {

	private JCheckBoxMenuItem interceptMenuItem;
	private ConnectionPanel connectionPanel;
	private JTabbedPane tabbedPane;

	private GraphEditor editor;
	
	private mxGraphComponent graphComponent;

	private Graph graph;
	private JMenuItem loadMenuItem;
	private File currentDir = new File(".");

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

		JMenuBar menuBar = new JMenuBar();
		setJMenuBar(menuBar);

		JMenu mnFile = new JMenu("File");
		menuBar.add(mnFile);

		JMenuItem mntmExit = new JMenuItem("Exit");
		mntmExit.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (graph != null)
					try {
						graph.shutdownServers();
					} catch (Exception ex) {
						JOptionPane.showMessageDialog(InterceptFrame.this, ex.getLocalizedMessage(), "Error",
								JOptionPane.ERROR_MESSAGE);
					}
				System.exit(0);
			}
		});

		loadMenuItem = new JMenuItem("Load");
		loadMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				try {
					JFileChooser jfc = new JFileChooser(currentDir);
					FileNameExtensionFilter filter = new FileNameExtensionFilter("Graph files", "mxe");
					jfc.setFileFilter(filter);
					int r = jfc.showOpenDialog(InterceptFrame.this);
					if (r != JFileChooser.APPROVE_OPTION)
						return;
					File f = jfc.getSelectedFile();
					if (f == null)
						return;
					currentDir = jfc.getCurrentDirectory();
					graph.loadGraph(f);
					InterceptFrame.this.graphComponent.setEnabled(true);
					try {
						graph.startServers();
					} catch (Exception ex) {
						ex.printStackTrace();
						JOptionPane.showMessageDialog(InterceptFrame.this, ex.getLocalizedMessage(), "Error",
								JOptionPane.ERROR_MESSAGE);
					}

				} catch (IOException ex) {
					JOptionPane.showMessageDialog(InterceptFrame.this, ex.getLocalizedMessage(), "Error",
							JOptionPane.ERROR_MESSAGE);
				}
			}
		});
		mnFile.add(loadMenuItem);
		mnFile.add(mntmExit);

		interceptMenuItem = new JCheckBoxMenuItem("Intercept");
		interceptMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				connectionPanel.setIntercept(interceptMenuItem.isSelected());
			}
		});
		menuBar.add(interceptMenuItem);

	}

	public void setGraph(final Graph graph) {
		if (graph == null)
			throw new NullPointerException("graph");
		this.graph = graph;
		this.graphComponent.setGraph(graph.getGraph());
	}

	@Override
	public void addChannelEvent(final ChannelEvent evt) throws Exception {
		if (evt instanceof ChannelReadEvent) {
			Object o = ((ChannelReadEvent) evt).getMessage();
			ReferenceCountUtil.retain(o);
		}
		SwingUtilities.invokeAndWait(new Runnable() {
			public void run() {
				try {
					connectionPanel.addChannelEvent(evt);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	protected JCheckBoxMenuItem getInterceptMenuItem() {
		return interceptMenuItem;
	}

}
