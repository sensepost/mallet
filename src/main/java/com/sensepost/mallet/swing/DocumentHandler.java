package com.sensepost.mallet.swing;

import java.util.logging.ErrorManager;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;

public class DocumentHandler extends Handler {

	private PlainDocument doc;
	private int max;

	/** Creates a new instance of DocumentHandler */
	public DocumentHandler() {
		this(Integer.MAX_VALUE);
	}

	public DocumentHandler(int limit) {
		max = limit;
		doc = new PlainDocument();
	}

	public Document getDocument() {
		return doc;
	}

	public void close() {
	}

	public void flush() {
	}

	public void publish(LogRecord record) {
		if (!isLoggable(record)) {
			return;
		}
		final StringBuilder msg = new StringBuilder();
		try {
			Formatter formatter = getFormatter();
			msg.append(formatter != null ? formatter.format(record) : record.getMessage());
			msg.append("\n");
		} catch (Exception ex) {
			// We don't want to throw an exception here, but we
			// report the exception to any registered ErrorManager.
			reportError(null, ex, ErrorManager.FORMAT_FAILURE);
			return;
		}
		Runnable r = new Runnable() {
			public void run() {
				try {
					makeSpace(msg.length());
					doc.insertString(doc.getLength(), msg.toString(), null);
				} catch (Exception ex) {
					// We don't want to throw an exception here, but we
					// report the exception to any registered ErrorManager.
					reportError(null, ex, ErrorManager.WRITE_FAILURE);
				}
			}
		};
		if (SwingUtilities.isEventDispatchThread()) {
			r.run();
		} else {
			SwingUtilities.invokeLater(r);
		}
	}

	private void makeSpace(int count) {
		int length = doc.getLength();
		if (length + count < max)
			return;
		try {
			if (count > max) {
				doc.remove(0, length);
			} else {
				int min = length + count - max;
				String remove = doc.getText(min, Math.min(500, length - min));
				int cr = remove.indexOf("\n");
				if (cr < 0) {
					min = min + remove.length();
				} else {
					min = Math.min(min + cr + 1, length);
				}
				doc.remove(0, min);
			}
		} catch (BadLocationException ble) {
			ble.printStackTrace();
		}
	}
}
