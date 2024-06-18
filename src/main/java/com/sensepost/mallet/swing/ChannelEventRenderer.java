package com.sensepost.mallet.swing;

import java.awt.Component;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

import com.sensepost.mallet.model.ChannelEvent;
import com.sensepost.mallet.model.ChannelEvent.ChannelEventType;
import com.sensepost.mallet.model.ChannelEvent.ChannelMessageEvent;
import com.sensepost.mallet.model.ChannelEvent.ConnectEvent;
import com.sensepost.mallet.model.ChannelEvent.ExceptionCaughtEvent;
import com.sensepost.mallet.model.ChannelEvent.UserEventTriggeredEvent;

import io.netty.buffer.ByteBuf;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.channel.socket.ChannelOutputShutdownEvent;
import io.netty.util.ReferenceCountUtil;

class ChannelEventRenderer extends DefaultTableCellRenderer {

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
            int row, int column) {
        if (value instanceof ChannelEvent) {
            ChannelEvent evt = (ChannelEvent) value;
            value = "";
            if (evt instanceof ChannelMessageEvent) {
                Object o = ((ChannelMessageEvent) evt).getMessage();
                if (o != null) {
                    value = o.getClass().getName();
                    if (o instanceof ByteBuf)
                        value += " (" + ((ByteBuf) o).readableBytes() + " bytes)";
                    else if (o instanceof byte[]) {
                        value += " (" + ((byte[]) o).length + " bytes)";
                    } else
                        value += " (" + o.toString() + ")";
                }
                ReferenceCountUtil.release(o);
            } else if (evt.type().equals(ChannelEventType.USER_EVENT_TRIGGERED)) {
                Object uevt = ((UserEventTriggeredEvent) evt).userEvent();
                if (uevt != null) {
                    if ((uevt instanceof ChannelInputShutdownEvent))
                        value = "Input Shutdown";
                    else if ((uevt instanceof ChannelOutputShutdownEvent))
                        value = "Output Shutdown";
                    else if ((uevt instanceof ChannelInputShutdownReadComplete))
                        value = "Input Shutdown Read Complete";
                    else
                        value = "UserEvent " + uevt.toString();
                } else
                    value += " UserEvent (null)";
            } else if (evt.type().equals(ChannelEventType.EXCEPTION_CAUGHT)) {
                String cause = ((ExceptionCaughtEvent) evt).cause();
                int cr = cause.indexOf('\n');
                if (cr != -1)
                    cause = cause.substring(0, cr);
                value = cause;
            } else if (evt.type().equals(ChannelEventType.CONNECT)) {
                value = ((ConnectEvent) evt).addresses();
            }
        }
        return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
    }

}