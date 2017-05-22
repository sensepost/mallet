package com.sensepost.mallet.swing.editors;

import java.awt.BorderLayout;
import java.awt.Event;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.swing.AbstractAction;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;

public class ByteArrayEditor extends JPanel implements ObjectEditor {

	private EditorController controller = null;

	private boolean tableUpdating = false;

	private PropertyChangeListener listener = new PropertyChangeListener() {

		@Override
		public void propertyChange(PropertyChangeEvent evt) {
			if (EditorController.OBJECT.equals(evt.getPropertyName())) {
				Object o = evt.getNewValue();
				tableUpdating = true;
				if (o instanceof byte[]) {
					htm.setData((byte[]) o);
				} else {
					htm.setData(null);
				}
				tableUpdating = false;
			}
		}

	};

	private TableModelListener tl = new TableModelListener() {

		@Override
		public void tableChanged(TableModelEvent e) {
			if (tableUpdating)
				return;
			controller.setObject(htm.getData());
		}

	};

	private HexTableModel htm = new HexTableModel(8);
	private JTable table;

	public ByteArrayEditor() {
		setLayout(new BorderLayout(0, 0));

		JScrollPane scrollPane = new JScrollPane();
		add(scrollPane);

		table = new JTable();
		table.setModel(htm);
		scrollPane.setViewportView(table);
		htm.addTableModelListener(tl);
		
        InputMap im = getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, Event.CTRL_MASK), "Save");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_O, Event.CTRL_MASK), "Open");
        getActionMap().put("Save", new AbstractAction() {
			public void actionPerformed(ActionEvent evt) {
                JFileChooser jfc = new JFileChooser();
                jfc.setDialogTitle("Select a file to write the message content to");
                int returnVal = jfc.showSaveDialog(ByteArrayEditor.this);
                if (returnVal == JFileChooser.APPROVE_OPTION) {
                    try {
                        FileOutputStream fos = new FileOutputStream(jfc.getSelectedFile());
                        fos.write(htm.getData());
                        fos.close();
                    } catch (IOException ioe) {
                        JOptionPane.showMessageDialog(ByteArrayEditor.this, "Error writing file: " + ioe.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        });
        getActionMap().put("Open", new AbstractAction() {
			public void actionPerformed(ActionEvent evt) {
                if (controller.isReadOnly()) 
                	return;
                JFileChooser jfc = new JFileChooser();
                jfc.setDialogTitle("Select a file to read the message content from");
                int returnVal = jfc.showOpenDialog(ByteArrayEditor.this);
                if (returnVal == JFileChooser.APPROVE_OPTION) {
                    try {
                        FileInputStream fis = new FileInputStream(jfc.getSelectedFile());
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        byte[] buff = new byte[2048];
                        int got;
                        while ((got = fis.read(buff))>0) {
                            baos.write(buff,0,got);
                        }
                        fis.close();
                        baos.close();
                        htm.setData(baos.toByteArray());
                    } catch (IOException ioe) {
                        JOptionPane.showMessageDialog(ByteArrayEditor.this, "Error writing file: " + ioe.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        });

	}

	@Override
	public JComponent getComponent() {
		return this;
	}

	@Override
	public Class<?>[] getSupportedClasses() {
		return new Class<?>[] { byte[].class };
	}

	@Override
	public void setEditorController(EditorController controller) {
		if (this.controller != null)
			controller.removePropertyChangeListener(listener);
		this.controller = controller;
		tableUpdating = true;
		if (this.controller != null) {
			controller.addPropertyChangeListener(listener);
			Object o = controller.getObject();
			if (o instanceof byte[]) {
				htm.setData((byte[]) o);
			} else {
				htm.setData(null);
			}
		} else {
			htm.setData(null);
		}
		tableUpdating = false;
	}

	public String getName() {
		return "Byte[]";
	}

	private class HexTableModel extends AbstractTableModel {

		private byte[] data;
		private boolean editable = true;

		private int columns = 8;

		public HexTableModel() {
		}

		public HexTableModel(int columns) {
			this.columns = columns;
		}

		public void setData(byte[] data) {
			this.data = data;
			fireTableDataChanged();
		}

		public byte[] getData() {
			if (data == null)
				return null;

			byte[] copy = new byte[data.length];
			System.arraycopy(data, 0, copy, 0, data.length);
			return copy;
		}

		public String getColumnName(int columnIndex) {
			if (columnIndex == 0) {
				return "Position";
			} else if (columnIndex - 1 < columns) {
				return Integer.toHexString(columnIndex - 1).toUpperCase();
			} else {
				return "String";
			}
		}

		public int getColumnCount() {
			return columns + 2;
		}

		public int getRowCount() {
			if (data == null || data.length == 0) {
				return 0;
			}
			if (data.length % columns == 0) {
				return (data.length / columns);
			} else {
				return (data.length / columns) + 1;
			}
		}

		public Object getValueAt(int rowIndex, int columnIndex) {
			if (columnIndex == 0) {
				return String.format("%08X", rowIndex * columns);
			} else if (columnIndex - 1 < columns) {
				int position = rowIndex * columns + columnIndex - 1;
				if (position < data.length) {
					int i = data[position];
					if (i < 0) {
						i = i + 256;
					}
					return String.format("%02X", i);
				} else {
					return "";
				}
			} else {
				int start = rowIndex * columns;
				StringBuffer buff = new StringBuffer();
				for (int i = 0; i < columns; i++) {
					int pos = start + i;
					if (pos >= data.length) {
						return buff.toString();
					}
					if (data[pos] < 32 || data[pos] > 126) {
						buff.append(".");
					} else {
						buff.append((char) data[pos]);
					}
				}
				return buff.toString();
			}
		}

		public boolean isCellEditable(int rowIndex, int columnIndex) {
			if (columnIndex == 0 || columnIndex > columns) {
				return false;
			}
			int position = rowIndex * columns + columnIndex - 1;
			if (position < data.length) {
				return editable;
			}
			return false;
		}

		public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
			int position = rowIndex * columns + columnIndex - 1;
			if (position >= data.length) {
				System.out.println("Out of range");
				return;
			}
			if (aValue instanceof String) {
				try {
					String s = (String) aValue;
					data[position] = new Integer(Integer.parseInt(s.trim(), 16)).byteValue();
					fireTableCellUpdated(rowIndex, columns + 1);
				} catch (NumberFormatException nfe) {
					System.out.println("Number format error : " + nfe);
				}
			} else {
				System.out.println("Value is a " + aValue.getClass().getName());
			}
		}
	}

}
