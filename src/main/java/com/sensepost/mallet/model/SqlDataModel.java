package com.sensepost.mallet.model;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;

import org.bouncycastle.util.encoders.Hex;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.support.JdbcDaoSupport;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import com.sensepost.mallet.model.ChannelEvent.BindEvent;
import com.sensepost.mallet.model.ChannelEvent.ChannelEventType;
import com.sensepost.mallet.model.ChannelEvent.ChannelMessageEvent;
import com.sensepost.mallet.model.ChannelEvent.ConnectEvent;
import com.sensepost.mallet.model.ChannelEvent.DefaultChannelEvent;
import com.sensepost.mallet.model.ChannelEvent.DefaultMessageEvent;
import com.sensepost.mallet.model.ChannelEvent.EventState;
import com.sensepost.mallet.model.ChannelEvent.ExceptionCaughtEvent;
import com.sensepost.mallet.model.ChannelEvent.UserEventTriggeredEvent;
import com.sensepost.mallet.persistence.ObjectMapper;

public class SqlDataModel extends JdbcDaoSupport implements DataModel {

	public static void main(String[] args) throws Exception {
		DriverManagerDataSource ds = new DriverManagerDataSource("jdbc:h2:tcp://localhost/~/test", "sa", null);
		ds.setDriverClassName("org.h2.Driver");
		SqlDataModel dm = new SqlDataModel(new ObjectMapper());
		dm.setDataSource(ds);
		Collection<Session> sessions = dm.getSessions();
		System.out.println(sessions);
		Session s = dm.createSession("Test Session");
		System.out.println("Saved session has ID: " + s.getId());
		dm.setSession(s);
		sessions = dm.getSessions();

		ChannelStats stats1 = dm.addChannel("channelId-" + randomString(5), "localAddress", "remoteAddress",
				new Date());
		ChannelStats stats2 = dm.addChannel("channelId-" + randomString(5), "localAddress2", "remoteAddress2",
				new Date());
		System.out.println("Stats1 : " + stats1.getId());
		System.out.println("Stats2 : " + stats2.getId());

		ChannelEvent e1 = ChannelEvent.newChannelActiveEvent(stats1.getChannelId(), EventState.EXECUTED, new Date(),
				new Date());
		dm.addChannelEvent(e1);
		ChannelEvent e2 = ChannelEvent.newChannelRegisteredEvent(stats1.getChannelId(), EventState.EXECUTED, new Date(),
				new Date());
		dm.addChannelEvent(e2);
		ChannelEvent e3 = ChannelEvent.newChannelReadEvent(stats1.getChannelId(), EventState.EXECUTED, new Date(),
				new Date(), "My message".getBytes());
		dm.addChannelEvent(e3);

		List<ChannelEvent> events = dm.getChannelEventsSince(new String[] { stats1.getChannelId() }, new Date(0));

		DefaultMessageEvent d3 = (DefaultMessageEvent) events.get(2);
		System.out.println(d3);
	}

	private static String randomString(int len) {
		StringBuilder b = new StringBuilder();
		Random r = new SecureRandom();
		for (int i = 0; i < len; i++)
			b.append((char) ('a' + r.nextInt(26)));
		return b.toString();
	}

	private LinkedHashMap<String, Integer> channelIdCache = new LinkedHashMap<String, Integer>(1000, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
			return size() > 1000;
		}
	};

	private ObjectMapper mapper;

	private final SessionRowMapper sessionRowMapper = new SessionRowMapper();

	private final ChannelStatsRowMapper channelStatsRowMapper = new ChannelStatsRowMapper();

	private final ChannelEventRowMapper channelEventRowMapper = new ChannelEventRowMapper();

	private final BlobRowMapper blobRowMapper = new BlobRowMapper();

	private Session session = null;

	private Map<String, Queue<ChannelEvent>> pendingChannelEvents = new HashMap<>();

	private NamedParameterJdbcTemplate namedParameterJdbcTemplate = null;

	public SqlDataModel(ObjectMapper mapper) {
		this.mapper = mapper;
	}

	private NamedParameterJdbcTemplate getNamedParameterJdbcTemplate() {
		if (namedParameterJdbcTemplate == null) {
			synchronized (this) {
				if (namedParameterJdbcTemplate == null)
					namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(getDataSource());
			}
		}
		return namedParameterJdbcTemplate;
	}

	public void setSession(Session session) {
		this.session = session;
	}

	public Session getSession() {
		return session;
	}

	private final static String sessionsQuerySql = "SELECT id, description, creation_date FROM sessions";

	public Collection<Session> getSessions() {
		Map<String, ?> params = new HashMap<>(0);
		return getNamedParameterJdbcTemplate().query(sessionsQuerySql, params, sessionRowMapper);
	}

	private final static String sessionInsertSql = "INSERT INTO sessions (description, creation_date) VALUES(:description,:creation_date)";

	public Session createSession(String description) {
		Session session = new Session();
		session.setDescription(description);
		session.setDate(new Date());
		MapSqlParameterSource params = new MapSqlParameterSource();
		params.addValue("description", description);
		params.addValue("creation_date", new Date());
		KeyHolder id = new GeneratedKeyHolder();
		getNamedParameterJdbcTemplate().update(sessionInsertSql, params, id);
		session.setId((Integer) id.getKey());
		return session;
	}

	private final static String sessionUpdateSql = "UPDATE sessions SET description = :description, creation_date = creation_date WHERE id = :id";

	public void updateSession(Session session) {
		MapSqlParameterSource params = new MapSqlParameterSource();
		params.addValue("id", session.getId());
		params.addValue("description", session.getDescription());
		params.addValue("creation_date", session.getDate());
		getNamedParameterJdbcTemplate().update(sessionUpdateSql, params);
	}

	private final static String channelStatsInsertSql = "INSERT INTO channel_stats (session_id, channel_id, local_address, remote_address, open_time, close_time, bytes_read, bytes_written) "
			+ "VALUES(:session_id,:channel_id,:local_address,:remote_address,:open_time,:close_time,:bytes_read,:bytes_written)";

	@Override
	public ChannelStats addChannel(String channelId, String localAddress, String remoteAddress, Date openTime) {
		ChannelStats stats = new ChannelStats();
		stats.setChannelId(channelId);
		stats.setLocalAddress(localAddress);
		stats.setRemoteAddress(remoteAddress);
		stats.setOpenTime(openTime);

		MapSqlParameterSource params = new MapSqlParameterSource();
		params.addValue("session_id", session.getId());
		params.addValue("channel_id", channelId);
		params.addValue("local_address", localAddress);
		params.addValue("remote_address", remoteAddress);
		params.addValue("open_time", openTime);
		params.addValue("close_time", null);
		params.addValue("bytes_read", null);
		params.addValue("bytes_written", null);

		KeyHolder id = new GeneratedKeyHolder();
		getNamedParameterJdbcTemplate().update(channelStatsInsertSql, params, id);
		stats.setId((Integer) id.getKey());

		synchronized (channelIdCache) {
			channelIdCache.put(channelId, stats.getId());
		}

		// FIXME fire an update to listeners
		return stats;
	}

	private final static String channelIdQuerySql = "SELECT id FROM channel_stats WHERE channel_id = :channel_id";

	private int getChannelId(String channelId) {
		synchronized (channelIdCache) {
			Integer id = channelIdCache.get(channelId);
			if (id != null)
				return id;
		}

		MapSqlParameterSource params = new MapSqlParameterSource();
		params.addValue("channel_id", channelId);
		Integer id = getNamedParameterJdbcTemplate().queryForObject(channelIdQuerySql, params, Integer.class);
		if (id != null) {
			synchronized (channelIdCache) {
				channelIdCache.put(channelId, id);
			}
			return id;
		}
		throw new RuntimeException("Channel ID not found! " + channelId);
	}

	private final static String channelStatsUpdateSql = "UPDATE channel_stats SET closed_time = ?, bytes_read = ?, bytes_written = ? WHERE id = ?";

	@Override
	public void updateChannelStats(ChannelStats stats) {
		MapSqlParameterSource params = new MapSqlParameterSource();
		params.addValue("id", stats.getId());
		params.addValue("close_time", stats.getCloseTime());
		params.addValue("bytes_read", stats.getBytesRead());
		params.addValue("bytes_written", stats.getBytesWritten());

		getNamedParameterJdbcTemplate().update(channelStatsUpdateSql, params);
		// FIXME: fire an update
	}

	private final static String channelStatsQuerySql = "SELECT channel_stats.id, channel_stats.channel_id, local_address, remote_address, open_time, close_time, bytes_read, bytes_written, count(CHANNEL_EVENTS.id) AS events "
			+ "FROM channel_stats, channel_events where session_id = :session_id AND channel_stats.channel_id = channel_events.channel_id AND open_time > :start "
			+ "GROUP BY channel_stats.id ORDER BY open_time ASC";

	@Override
	public List<ChannelStats> getChannelStatsSince(Date start) {
		MapSqlParameterSource params = new MapSqlParameterSource();
		params.addValue("session", session.getId());
		params.addValue("start", session.getId());
		return getNamedParameterJdbcTemplate().query(channelStatsQuerySql, params, channelStatsRowMapper);
	}

	private final static String channelEventInsertSql = "INSERT INTO channel_events (channel_id, event_type, event_state, event_time, executed_time, blob_id) "
			+ "VALUES (:channel_id, :event_type, :event_state, :event_time, :executed_time, :blob_id)";

	@Override
	public ChannelEvent addChannelEvent(ChannelEvent evt) {
		final Object obj;
		switch (evt.type()) {
		case BIND:
			obj = ((BindEvent) evt).localAddress();
			break;
		case CONNECT:
			obj = ((ConnectEvent) evt).addresses();
			break;
		case EXCEPTION_CAUGHT:
			obj = ((ExceptionCaughtEvent) evt).cause();
			break;
		case USER_EVENT_TRIGGERED:
			obj = ((UserEventTriggeredEvent) evt).userEvent().toString();
			break;
		case CHANNEL_READ:
		case WRITE:
			obj = ((ChannelMessageEvent) evt).getMessage();
			break;
		default:
			obj = null;
		}
		final Integer blobId;
		if (obj != null) {
			blobId = saveBlob(obj);
		} else {
			blobId = null;
		}

		MapSqlParameterSource params = new MapSqlParameterSource();
		params.addValue("channel_id", getChannelId(evt.channelId()));
		params.addValue("event_type", evt.type().ordinal());
		params.addValue("event_state", evt.state().ordinal());
		params.addValue("event_time", evt.eventTime());
		params.addValue("executed_time", evt.executionTime());
		params.addValue("blob_id", blobId);

		KeyHolder id = new GeneratedKeyHolder();
		getNamedParameterJdbcTemplate().update(channelEventInsertSql, params, id);
		if (evt instanceof DefaultChannelEvent)
			((DefaultChannelEvent) evt).setId((Integer) id.getKey());

		return newProxyChannelEvent(evt);
		// FIXME: fire an update
	}

	private ChannelEvent newProxyChannelEvent(ChannelEvent evt) {
		Class<?>[] interfaces = evt.getClass().getInterfaces();
		return (ChannelEvent) Proxy.newProxyInstance(this.getClass().getClassLoader(), new Class[] { interfaces[0] },
				new ChannelEventInvocationHandler(evt));
	}

	private static final String channelEventUpdateSql = "UPDATE channel_events set event_state = event_state, executed_time = :executed_time "
			+ "WHERE channel_id = :channel_id AND event_type = :event_type AND event_time = :event_time";

//	@Override
	public void updateChannelEvent(ChannelEvent evt) {
		MapSqlParameterSource params = new MapSqlParameterSource();
		params.addValue("channel_id", getChannelId(evt.channelId()));
		params.addValue("event_type", evt.type().ordinal());
		params.addValue("event_state", evt.state().ordinal());
		params.addValue("event_time", evt.eventTime());
		params.addValue("executed_time", evt.executionTime());

		getNamedParameterJdbcTemplate().update(channelEventUpdateSql, params);
		// FIXME: fire an update
	}

	@Override
	public void linkChannel(String channel1, String channel2) {
	}

	private final static String channelEventsQuerySql = "SELECT channel_stats.channel_id AS channel_id, channel_events.id AS id, event_type, event_state, event_time, executed_time, blob_id "
			+ "FROM channel_stats, channel_events "
			+ "WHERE channel_events.channel_id = channel_stats.id AND channel_events.event_time > :start AND channel_stats.channel_id IN (:channel_ids)";

	@Override
	public List<ChannelEvent> getChannelEventsSince(String[] channelIds, Date start) {
		MapSqlParameterSource params = new MapSqlParameterSource();
		params.addValue("start", start);
		params.addValue("channel_ids", channelIds);
		return getNamedParameterJdbcTemplate().query(channelEventsQuerySql, params, channelEventRowMapper);

	}

	private String hash(byte[] bytes) {
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
		byte[] result = digest.digest(bytes);
		return Hex.toHexString(result);
	}

	private static class Blob {
		private int id;
		private final String key, className;
		private final byte[] bytes;

		Blob(String key, String className, byte[] bytes) {
			this.key = key;
			this.className = className;
			this.bytes = bytes;
		}
	}

	private int saveBlob(Object obj) {
		byte[] bytes = mapper.convertToByte(obj);
		String key = hash(bytes);
		Class<?> clazz = obj.getClass();
		if (clazz.isAnonymousClass()) {
			Class<?>[] classes = clazz.getClasses();
			if (classes.length > 0) {
				clazz = classes[0];
			} else
				throw new UnsupportedOperationException("Don't know how to serialize a " + clazz.getName());
		}
		String type = clazz.getName();
		Integer id = null;
		try {
			id = getJdbcTemplate().queryForObject("SELECT ID FROM BLOBS WHERE hash = ? AND OBJECT_TYPE = ?",
					new Object[] { key, type }, Integer.class);
			return id;
		} catch (EmptyResultDataAccessException e) {
			MapSqlParameterSource params = new MapSqlParameterSource();
			params.addValue("hash", key);
			params.addValue("object_type", type);
			params.addValue("bytes", bytes);

			KeyHolder kh = new GeneratedKeyHolder();
			getNamedParameterJdbcTemplate().update(
					"INSERT INTO BLOBS (hash, object_type, bytes) VALUES (:hash, :object_type, :bytes)", params, kh);
			return (Integer) kh.getKey();
		}
	}

	private final static String blobQuerySql = "SELECT hash, object_type, bytes FROM blobs WHERE id = :id";

	private Object getBlob(int id) {
		MapSqlParameterSource params = new MapSqlParameterSource();
		params.addValue("id", id);
		Blob blob = getNamedParameterJdbcTemplate().queryForObject(blobQuerySql, params, blobRowMapper);
		try {
			return mapper.convertToObject(Class.forName(blob.className), blob.bytes);
		} catch (Exception e) {
			return e.toString();
		}
	}

	private class SessionRowMapper implements RowMapper<Session> {

		@Override
		public Session mapRow(ResultSet rs, int rownum) throws SQLException {
			Session session = new Session();
			Integer id = rs.getInt("id");
			session.setId(id);
			String description = rs.getString("description");
			session.setDescription(description);
			Date date = rs.getTimestamp("creation_date");
			session.setDate(date);
			return session;
		}

	}

	private class ChannelStatsRowMapper implements RowMapper<ChannelStats> {

		@Override
		public ChannelStats mapRow(ResultSet rs, int rowNum) throws SQLException {
			ChannelStats stats = new ChannelStats();
			stats.setId(rs.getInt("id"));
			stats.setChannelId(rs.getString("channel_id"));
			stats.setLocalAddress(rs.getString("local_address"));
			stats.setRemoteAddress(rs.getString("remote_address"));
			stats.setOpenTime(rs.getTimestamp("id"));
			stats.setCloseTime(rs.getTimestamp("close_time"));
			stats.setBytesRead(rs.getLong("bytes_read"));
			stats.setBytesWritten(rs.getLong("bytes_written"));
			stats.setEvents(rs.getInt("events"));
			Queue<ChannelEvent> pendingEvents = pendingChannelEvents.get(stats.getChannelId());
			stats.setPendingEvents(pendingEvents == null ? 0 : pendingEvents.size());
			return stats;
		}

	}

	private class ChannelEventRowMapper implements RowMapper<ChannelEvent> {

		@Override
		public ChannelEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
			int type = rs.getInt("event_type");
			String channelId = rs.getString("channel_id");
			EventState state = EventState.values()[rs.getInt("event_state")];
			Date eventTime = rs.getTimestamp("event_time");
			Date executionTime = rs.getTimestamp("executed_time");
			Integer blobId = rs.getInt("blob_id");
			Object blob = blobId == null || blobId == 0 ? null : getBlob(blobId);
			switch (ChannelEventType.values()[type]) {
			case BIND:
				return ChannelEvent.newBindEvent(channelId, state, eventTime, executionTime, (String) blob);
			case CHANNEL_ACTIVE:
				return ChannelEvent.newChannelActiveEvent(channelId, state, eventTime, executionTime);
			case CHANNEL_INACTIVE:
				return ChannelEvent.newChannelInactiveEvent(channelId, state, eventTime, executionTime);
			case CHANNEL_READ:
				return ChannelEvent.newChannelReadEvent(channelId, state, eventTime, executionTime, blob);
			case CHANNEL_READ_COMPLETE:
				return ChannelEvent.newChannelReadCompleteEvent(channelId, state, eventTime, executionTime);
			case CHANNEL_REGISTERED:
				return ChannelEvent.newChannelRegisteredEvent(channelId, state, eventTime, executionTime);
			case CHANNEL_UNREGISTERED:
				return ChannelEvent.newChannelUnregisteredEvent(channelId, state, eventTime, executionTime);
			case CHANNEL_WRITABILITY_CHANGED:
				return ChannelEvent.newChannelWritabilityChangedEvent(channelId, state, eventTime, executionTime);
			case CLOSE:
				return ChannelEvent.newCloseEvent(channelId, state, eventTime, executionTime);
			case CONNECT:
				return ChannelEvent.newConnectEvent(channelId, state, eventTime, executionTime, (String) blob);
			case DEREGISTER:
				return ChannelEvent.newDeregisterEvent(channelId, state, eventTime, executionTime);
			case DISCONNECT:
				return ChannelEvent.newDisconnectEvent(channelId, state, eventTime, executionTime);
			case EXCEPTION_CAUGHT:
				return ChannelEvent.newExceptionCaughtEvent(channelId, state, eventTime, executionTime, (String) blob);
			case FLUSH:
				return ChannelEvent.newFlushEvent(channelId, state, eventTime, executionTime);
			case READ:
				return ChannelEvent.newReadEvent(channelId, state, eventTime, executionTime);
			case USER_EVENT_TRIGGERED:
				return ChannelEvent.newUserEventTriggeredEvent(channelId, state, eventTime, executionTime,
						(String) blob);
			case WRITE:
				return ChannelEvent.newWriteEvent(channelId, state, eventTime, executionTime, blob);
			default:
				throw new IllegalArgumentException("Unknown Event Type: " + type);
			}
		}

	}

	private class BlobRowMapper implements RowMapper<Blob> {

		@Override
		public Blob mapRow(ResultSet rs, int rowNum) throws SQLException {
			String hash = rs.getString("hash");
			String objectType = rs.getString("object_type");
			java.sql.Blob blob = rs.getBlob("bytes");
			return new Blob(hash, objectType, blob.getBytes(1, (int) blob.length()));
		}

	}

	private class ChannelEventInvocationHandler implements InvocationHandler {

		private ChannelEvent evt;
		private Map<String, Method> methods = new HashMap<>();

		public ChannelEventInvocationHandler(ChannelEvent evt) {
			this.evt = evt;
			for (Method m : evt.getClass().getMethods()) {
				methods.put(m.getName(), m);
			}
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			Object ret = methods.get(method.getName()).invoke(evt, args);
			if (method.getName().equals("update") || method.getName().equals("execute"))
				updateChannelEvent(evt);
			return ret;
		}

	}
}
