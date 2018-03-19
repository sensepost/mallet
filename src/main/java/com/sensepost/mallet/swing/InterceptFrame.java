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
import javax.swing.filechooser.FileNameExtensionFilter;

import com.mxgraph.examples.swing.editor.EditorMenuBar;
import com.mxgraph.swing.mxGraphComponent;
import com.sensepost.mallet.InterceptController;
import com.sensepost.mallet.graph.Graph;

public class InterceptFrame extends JFrame {

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

		JMenuBar menuBar = new EditorMenuBar(editor);
		setJMenuBar(menuBar);

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

	public InterceptController getInterceptController() {
		return connectionPanel;
	}

	protected JCheckBoxMenuItem getInterceptMenuItem() {
		return interceptMenuItem;
	}

}
