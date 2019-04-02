package com.sensepost.mallet.model;

import java.util.Date;

public class ChannelStats extends BaseEntity {

	private String channelId;
	private String localAddress, remoteAddress;
	private Date openTime, closeTime = null;
	private long bytesRead = 0, bytesWritten = 0;
	private int events = 0, pendingEvents = 0;

	private String linkedChannel = null;

	public ChannelStats() {
	}

	public String getChannelId() {
		return channelId;
	}

	public void setChannelId(String channelId) {
		this.channelId = channelId;
	}

	public String getLocalAddress() {
		return localAddress;
	}

	public void setLocalAddress(String localAddress) {
		this.localAddress = localAddress;
	}

	public String getRemoteAddress() {
		return remoteAddress;
	}

	public void setRemoteAddress(String remoteAddress) {
		this.remoteAddress = remoteAddress;
	}

	public Date getOpenTime() {
		return openTime;
	}

	public void setOpenTime(Date openTime) {
		this.openTime = openTime;
	}

	public Date getCloseTime() {
		return closeTime;
	}

	public void setCloseTime(Date closeTime) {
		this.closeTime = closeTime;
	}

	public long getBytesRead() {
		return bytesRead;
	}

	public void setBytesRead(long bytesRead) {
		this.bytesRead = bytesRead;
	}

	public long getBytesWritten() {
		return bytesWritten;
	}

	public void setBytesWritten(long bytesWritten) {
		this.bytesWritten = bytesWritten;
	}

	public int getEvents() {
		return events;
	}

	public void setEvents(int events) {
		this.events = events;
	}

	public int getPendingEvents() {
		return pendingEvents;
	}

	public void setPendingEvents(int pendingEvents) {
		this.pendingEvents = pendingEvents;
	}

	public String getLinkedChannel() {
		return linkedChannel;
	}

	public void setLinkedChannel(String linkedChannel) {
		this.linkedChannel = linkedChannel;
	}

	@Override
	public String toString() {
		return new StringBuilder("ChannelStat: (").append(getChannelId()).append(", opened ").append(getOpenTime())
				.append(", closed ").append(getCloseTime()).toString();
	}
}
