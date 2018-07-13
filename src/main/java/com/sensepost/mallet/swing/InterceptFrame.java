package com.sensepost.mallet.swing;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.logging.Handler;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JMenuBar;
import javax.swing.JTabbedPane;

import com.mxgraph.examples.swing.editor.EditorActions.ExitAction;
import com.mxgraph.examples.swing.editor.EditorMenuBar;
import com.mxgraph.swing.mxGraphComponent;
import com.sensepost.mallet.InterceptController;
import com.sensepost.mallet.graph.Graph;

public class InterceptFrame extends JFrame {

	private JCheckBoxMenuItem interceptMenuItem;
	private ConnectionPanel connectionPanel;
	private LogPanel logPanel;

	private JTabbedPane tabbedPane;

	private GraphEditor editor;
	
	private mxGraphComponent graphComponent;

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

		addWindowListener(new WindowAdapter() {
			@Override public void windowClosing(WindowEvent e) {
				new ExitAction().actionPerformed(new ActionEvent(editor, 0, "Exit"));
			}
		});

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
		this.graphComponent.setGraph(graph.getGraph());
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
