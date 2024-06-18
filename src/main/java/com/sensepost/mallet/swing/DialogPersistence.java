package com.sensepost.mallet.swing;

import java.awt.Dialog;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowStateListener;
import java.util.prefs.Preferences;

public class DialogPersistence extends ComponentAdapter {

	private Preferences prefs;

	public DialogPersistence(Preferences prefs) {
		this.prefs = prefs;
	}

	@Override
	public void componentResized(ComponentEvent e) {
		Dialog dialog = (Dialog) e.getSource();
		String size = dialog.getWidth() + "x" + dialog.getHeight();
		prefs.put("size", size);
	}

	@Override
	public void componentMoved(ComponentEvent e) {
		Dialog dialog = (Dialog) e.getSource();
		String position = dialog.getX() + "," + dialog.getY();
		prefs.put("position", position);
	}

	public void apply(Dialog dialog, int width, int height, int x, int y) {
		String size = prefs.get("size", null);
		if (size != null) {
			try {
				String[] split = size.split("x");
				if (split.length == 2) {
					int tmp_width = Integer.parseInt(split[0]);
					int tmp_height = Integer.parseInt(split[1]);
					width = tmp_width;
					height = tmp_height;
				}
			} catch (NumberFormatException nfe) {
				// ignore
			}
		}
		dialog.setSize(width, height);
		String position = prefs.get("position", null);
		if (position != null) {
			try {
				String[] split = position.split(",");
				if (split.length == 2) {
					int tmp_x = Integer.parseInt(split[0]);
					int tmp_y = Integer.parseInt(split[1]);
					x = tmp_x;
					y = tmp_y;
				}
			} catch (NumberFormatException nfe) {
				// ignore
			}
		}
		dialog.setLocation(x, y);
	}

}
