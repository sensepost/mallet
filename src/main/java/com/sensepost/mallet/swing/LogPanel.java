package com.sensepost.mallet.swing;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.logging.Handler;
import java.util.logging.Level;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;

public class LogPanel extends JPanel {

	private DocumentHandler handler = new DocumentHandler(100 * 1024);

	private JTextArea logTextArea;

	public LogPanel() {
		setLayout(new BorderLayout(0, 0));

		logTextArea = new JTextArea(handler.getDocument());
		logTextArea.setFont(new Font("Courier 10 Pitch", Font.PLAIN, 12));
		logTextArea.setEditable(false);

		JPanel panel = new JPanel();
		add(panel, BorderLayout.NORTH);

		final JCheckBox enableCheckBox = new JCheckBox("Enable");
		panel.add(enableCheckBox);
		enableCheckBox.setSelected(false);

		enableCheckBox.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				updateLogLevel(enableCheckBox.isSelected());
			}
		});
		updateLogLevel(enableCheckBox.isSelected());

		final JCheckBox scrollCheckBox = new JCheckBox("Scroll automatically");
		scrollCheckBox.setSelected(true);
		panel.add(scrollCheckBox);
		scrollCheckBox.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				updateScrollPolicy(scrollCheckBox.isSelected());
			}
		});
		updateScrollPolicy(scrollCheckBox.isSelected());

		JScrollPane scrollPane = new JScrollPane();
		scrollPane.setViewportView(logTextArea);
		add(scrollPane, BorderLayout.CENTER);

		JButton clearButton = new JButton("Clear");
		clearButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				try {
					logTextArea.getDocument().remove(0,
							logTextArea.getDocument().getLength());
				} catch (BadLocationException ble) {
					ble.printStackTrace();
				}
			}
		});
		add(clearButton, BorderLayout.SOUTH);

	}

	public Handler getHandler() {
		return handler;
	}

	private void updateLogLevel(boolean enabled) {
		handler.setLevel(enabled ? Level.ALL : Level.OFF);
	}

	private void updateScrollPolicy(boolean scroll) {
		DefaultCaret caret = (DefaultCaret) logTextArea.getCaret();
		caret.setUpdatePolicy(scroll ? DefaultCaret.ALWAYS_UPDATE : DefaultCaret.NEVER_UPDATE);
	}
}
