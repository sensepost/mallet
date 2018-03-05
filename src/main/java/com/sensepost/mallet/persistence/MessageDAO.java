package com.sensepost.mallet.persistence;

import java.net.SocketAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

public class MessageDAO {

	private Map<String, Blob> blobMap = new HashMap<>();
	private List<ChannelRecord> channels = new ArrayList<>();
	private List<Integer> channelLink = new ArrayList<>();

	private DataSource ds;
	private ObjectMapper mapper;
	
	public MessageDAO(DataSource ds, ObjectMapper mapper) {
		this.ds = ds;
		this.mapper = mapper;
	}
	
	public String writeObject(Object o) {
		Blob b = new Blob();
		b.blob = mapper.convertToByte(o);
		b.type = o.getClass();
		String key = hash(b.blob);
		
		// Temporary implementation, should store to a DB
		synchronized(blobMap) {
			blobMap.put(key, b);
		}

		return key;
	}

	public Object readObject(String key) {
		synchronized(blobMap) {
			Blob b = blobMap.get(key);
			return mapper.convertToObject(b.type, b.blob);
		}
	}
	
	private final static String HEX = "0123456789ABCDEF";

	private String hash(byte[] blob) {
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(blob);
			char[] hex = new char[hash.length*2];
		    for (int i=0; i<hash.length; i++) {
		    	hex[i*2] = HEX.charAt((hash[i] >> 4) & 0x0F);
		    	hex[i*2+1] = HEX.charAt(hash[i] & 0x0F);
		    }
		    return new String(hex);
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}
	
	private static class Blob {
		private Class<?> type;
		private byte[] blob;
	}
	
	public int addChannel(SocketAddress src, SocketAddress dst, long openTime) {
		ChannelRecord cr = new ChannelRecord();
		cr.src = src;
		cr.dst = dst;
		cr.openTime = openTime;
		synchronized (channels) {
			int i = channels.size();
			channels.add(cr);
			return i;
		}
	}
	
	public SocketAddress getChannelSource(int channel) {
		synchronized (channels) {
			return channels.get(channel).src;
		}
	}
	
	public SocketAddress getChannelDestination(int channel) {
		synchronized (channels) {
			return channels.get(channel).dst;
		}
	}
	
	public long getChannelOpenTime(int channel) {
		synchronized (channels) {
			return channels.get(channel).openTime;
		}
	}
	
	public long getChannelCloseTime(int channel) {
		synchronized (channels) {
			return channels.get(channel).closeTime;
		}
	}
	
	public void linkChannels(int c1, int c2) {
		synchronized(channelLink) {
			channelLink.add(c1, c2);
			channelLink.add(c2, c1);
		}
	}
	
	public int getLinkedChannel(int channel) {
		synchronized(channelLink) {
			int c2 = channelLink.get(channel);
			return (channelLink.get(c2) == channel) ? c2 : -1;
		}
	}
	
	private static class ChannelRecord {
		SocketAddress src, dst;
		long openTime = -1, closeTime = -1;
	}
}
