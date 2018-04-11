package com.sensepost.mallet.swing;

import java.awt.Component;
import java.awt.FontMetrics;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

public class DateRenderer extends DefaultTableCellRenderer {

	private static final long serialVersionUID = 4146923038862167831L;
	private static SimpleDateFormat[] formats = new SimpleDateFormat[] { new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.S"),
			new SimpleDateFormat("yyyy/MM/dd HH:mm:ss"), new SimpleDateFormat("MM/dd HH:mm:ss"),
			new SimpleDateFormat("HH:mm:ss"), new SimpleDateFormat(":mm:ss") };

	private boolean relative = false;

	public DateRenderer() {
		this(true);
	}

	public DateRenderer(boolean relative) {
		this.relative = relative;
	}

	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
			int row, int column) {
		super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
		if (value instanceof Date) {
			Date date = (Date) value;
			FontMetrics fm = getFontMetrics(getFont());
			int i = 0;
			String text;
			int textWidth;
			int targetWidth = table.getColumnModel().getColumn(column).getWidth() - 4;
			if (!relative || row == 0) {
				do {
					text = formats[i++].format(date);
					textWidth = fm.stringWidth(text);
				} while (textWidth > targetWidth && i < formats.length);
			} else {
				Date base = (Date) table.getValueAt(0, column);
				if (date != null && base != null) {
					float r = date.getTime() - base.getTime();
					text = String.format("+%.3f", r/1000) + "s";
				} else {
					text = "";
				}
			}
			setText(text);
		}
		return this;
	}

}
