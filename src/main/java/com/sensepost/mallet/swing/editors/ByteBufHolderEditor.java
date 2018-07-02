package com.sensepost.mallet.swing.editors;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.Unpooled;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JComponent;

public class ByteBufHolderEditor implements ObjectEditor {

	private ByteArrayEditor bae = new ByteArrayEditor();
	private EditorController adapter = new EditorController();
	private EditorController controller = null;

	private boolean updating = false;

	private ByteBufHolder bbh = null;
	
	private PropertyChangeListener listener = new PropertyChangeListener() {

		@Override
		public void propertyChange(PropertyChangeEvent evt) {
			if (EditorController.OBJECT.equals(evt.getPropertyName())) {
				if (evt.getSource() == controller) {
					updateAdapter(evt.getNewValue());
				} else if (evt.getSource() == adapter) {
					if (!updating) {
						if (controller != null) {
							ByteBuf content = Unpooled.wrappedBuffer((byte[])evt.getNewValue());
							ByteBufHolder replacement = bbh.replace(content);
							controller.setObject(replacement);
							bbh = null;
						}
					}
				}
			} else if (EditorController.READ_ONLY.equals(evt.getPropertyName())) {
				if (evt.getSource() == controller) {
					adapter.setReadOnly(controller.isReadOnly());
				}
			}
		}
	};

	public ByteBufHolderEditor() {
		bae.setEditorController(adapter);
		adapter.addPropertyChangeListener(listener);
	}

	@Override
	public JComponent getEditorComponent() {
		return bae;
	}

	@Override
	public String getEditorName() {
		return "ByteBufHolder";
	}

	@Override
	public Class<?>[] getSupportedClasses() {
		return new Class<?>[] { ByteBufHolder.class };
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
		if (o != null && ByteBufHolder.class.isAssignableFrom(o.getClass())) {
			bbh = (ByteBufHolder) o;
			ByteBuf bb = bbh.content();
			byte[] data = new byte[bb.readableBytes()];
			bb.getBytes(bb.readerIndex(), data);
			adapter.setObject(data);
		} else {
			adapter.setObject(null);
		}
		updating = false;
	}
}
