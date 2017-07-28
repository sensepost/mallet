package com.sensepost.mallet.swing.editors;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JComponent;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class ByteBufEditor implements ObjectEditor {

	private ByteArrayEditor bae = new ByteArrayEditor();
	private EditorController adapter = new EditorController();
	private EditorController controller = null;

	private boolean updating = false;

	private PropertyChangeListener listener = new PropertyChangeListener() {

		@Override
		public void propertyChange(PropertyChangeEvent evt) {
			if (EditorController.OBJECT.equals(evt.getPropertyName())) {
				if (evt.getSource() == controller) {
					updateAdapter(evt.getNewValue());
				} else if (evt.getSource() == adapter) {
					if (!updating) {
						if (controller != null)
							controller.setObject(Unpooled.wrappedBuffer((byte[])evt.getNewValue()));
					}
				}
			} else if (EditorController.READ_ONLY.equals(evt.getPropertyName())) {
				if (evt.getSource() == controller) {
					adapter.setReadOnly(controller.isReadOnly());
				}
			}
		}
	};

	public ByteBufEditor() {
		bae.setEditorController(adapter);
		adapter.addPropertyChangeListener(listener);
	}

	@Override
	public JComponent getEditorComponent() {
		return bae;
	}

	@Override
	public String getEditorName() {
		return "ByteBuf";
	}

	@Override
	public Class<?>[] getSupportedClasses() {
		return new Class<?>[] { ByteBuf.class };
	}

	@Override
	public void setEditorController(EditorController controller) {
		if (this.controller != null)
			controller.removePropertyChangeListener(listener);
		this.controller = controller;
		if (this.controller != null) {
			controller.addPropertyChangeListener(listener);
			updateAdapter(controller.getObject());
		} else {
			updateAdapter(null);
		}
	}

	private void updateAdapter(Object o) {
		updating = true;
		if (o != null && ByteBuf.class.isAssignableFrom(o.getClass())) {
			ByteBuf bb = (ByteBuf) o;
			bb.retain();
			byte[] data = new byte[bb.readableBytes()];
			bb.getBytes(bb.readerIndex(), data);
			adapter.setObject(data);
		} else {
			adapter.setObject(null);
		}
		updating = false;
	}
}
