package com.sensepost.mallet.swing;

import java.awt.Frame;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowStateListener;
import java.util.prefs.Preferences;

public class FramePersistence extends ComponentAdapter implements WindowStateListener {

	private Preferences prefs;

	public FramePersistence(Preferences prefs) {
		this.prefs = prefs;
	}

	@Override
	public void windowStateChanged(WindowEvent e) {
		int state = e.getNewState() & ~Frame.ICONIFIED;
		prefs.putInt("state", state);
		System.out.println("SC : " + state);
	}

	@Override
	public void componentResized(ComponentEvent e) {
		Frame frame = (Frame) e.getSource();
		String size = frame.getWidth() + "x" + frame.getHeight();
		prefs.put("size", size);
	}

	@Override
	public void componentMoved(ComponentEvent e) {
		Frame frame = (Frame) e.getSource();
		String position = frame.getX() + "," + frame.getY();
		prefs.put("position", position);
	}

	public void apply(Frame frame, int state, int width, int height, int x, int y) {
		frame.setExtendedState(prefs.getInt("state", state));
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
		frame.setSize(width, height);
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
		frame.setLocation(x, y);
	}

}
