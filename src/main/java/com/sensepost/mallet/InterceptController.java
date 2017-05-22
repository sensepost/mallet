package com.sensepost.mallet;

import com.sensepost.mallet.events.ChannelEvent;

public interface InterceptController {
	
	void addChannelEvent(ChannelEvent evt) throws Exception;
	
}
