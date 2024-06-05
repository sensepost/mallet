package com.sensepost.mallet.swing;

import javax.swing.ListModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.table.AbstractTableModel;

public class ListTableModelAdapter<T> extends AbstractTableModel {

    private static final long serialVersionUID = 1L;

    public interface RowModel<T> {
        public int getColumnCount();
        public Class<?> getColumnClass(int column);
        public String getColumnName(int column);
        public Object getValueAt(T item, int columnIndex);
    }
    
    private ListModel<T> listModel = null;
    private RowModel<T> rowModel;
    
    private ListDataListener listener = new Listener();
    
    public ListTableModelAdapter(RowModel<T> rowModel, ListModel<T> listModel) {
        this.rowModel = rowModel;
        setListModel(listModel);
    }
    
    public void setListModel(ListModel<T> listModel) {
        if (this.listModel != null)
            this.listModel.removeListDataListener(listener);
        this.listModel = listModel;
        if (this.listModel != null)
            this.listModel.addListDataListener(listener);
        fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return listModel == null ? 0 : listModel.getSize();
    }

    public T getElementAt(int rowIndex) {
        return listModel == null ? null : listModel.getElementAt(rowIndex);
    }
    
    @Override
    public int getColumnCount() {
        return rowModel.getColumnCount();
    }

    @Override
    public String getColumnName(int columnIndex) {
        return rowModel.getColumnName(columnIndex);
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return rowModel.getColumnClass(columnIndex);
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (listModel == null || rowIndex > listModel.getSize())
            return null;
        T item = getElementAt(rowIndex);
        return rowModel.getValueAt(item, columnIndex);
    }

    private class Listener implements ListDataListener {
        @Override
        public void intervalAdded(ListDataEvent e) {
            fireTableRowsInserted(e.getIndex0(), e.getIndex1());
        }
    
        @Override
        public void intervalRemoved(ListDataEvent e) {
            fireTableRowsDeleted(e.getIndex0(), e.getIndex1());
        }
    
        @Override
        public void contentsChanged(ListDataEvent e) {
            fireTableRowsUpdated(e.getIndex0(), e.getIndex1());
        }
    }
}