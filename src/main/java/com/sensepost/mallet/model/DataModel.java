package com.sensepost.mallet.model;

import java.util.Date;
import java.util.List;

public interface DataModel {

	/**
	 * Inform the data model that there is a new Channel to be tracked.
	 * 
	 * @param channelId     the channel identifier, normally
	 *                      channel.id().asLongText()
	 * @param localAddress  a String representation of the local SocketAddress
	 * @param remoteAddress a String representation of the remote SocketAddress
	 * @param openTime      the time the Channel was opened
	 */
	ChannelStats addChannel(String channelId, String localAddress, String remoteAddress, Date openTime);

	/**
	 * Called to update listeners regarding activity on a particular Channel.
	 * 
	 * @param channelStats the identifier of the channel
	 */
	void updateChannelStats(ChannelStats channelStats);

	/**
	 * Called to inform the data model that there has been a new ChannelEvent on a
	 * previously tracked Channel
	 * 
	 * @param evt
	 */
	ChannelEvent addChannelEvent(ChannelEvent evt);

	/**
	 * Called to inform the data model that there has been an update to an existing
	 * ChannelEvent
	 * 
	 * @param evt
	 */
	void updateChannelEvent(ChannelEvent evt);

	/**
	 * Called to inform the data model that the events on two separate Channels
	 * should be considered related.
	 * 
	 * @param channel1 one channel identifier
	 * @param channel2 the other channel identifier
	 */
	void linkChannel(String channel1, String channel2);

	/**
	 * Called to obtain a list of ChannelStats that have been updated since the
	 * indicated time. Callers should keep track of the time of the last call, to
	 * avoid getting the same data returned repeatedly.
	 * 
	 * @param start the time of the last call, or -1 for all data
	 * @return a list of ChannelStats objects representing data added to the model
	 *         since the indicated time.
	 */
	List<ChannelStats> getChannelStatsSince(Date start);

	/**
	 * Call to obtain a list of ChannelEvent's on a particular Channel, or all
	 * Channels if channelId is null Callers should keep track of the time of the
	 * last call, to avoid getting the same data returned repeatedly.
	 * 
	 * @param channelIds the channelId to return events for, or null for all
	 *                   Channels
	 * @param start      the time to start looking from, or -1 for all events on the
	 *                   specified Channel
	 * @return a list of ChannelEvents for the specified Channel since the indicated
	 *         time
	 */
	List<ChannelEvent> getChannelEventsSince(String[] channelIds, Date start);

}
