package com.sensepost.mallet;

import io.netty.channel.ChannelHandler.Sharable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import com.sensepost.mallet.swing.InterceptFrame;

import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

@Sharable
public class ScriptHandler extends ChannelHandlerAdapter {

	private ScriptEngineManager sem = new ScriptEngineManager();

	private String language = null;
	private String script = null;
	
	private static String SCRIPT = null;
	
	static {
		try {
			InputStream is = InterceptFrame.class.getResourceAsStream("script.groovy");
			BufferedReader r = new BufferedReader(new InputStreamReader(is));
			StringBuilder b = new StringBuilder();
			String line;
			while ((line = r.readLine()) != null) {
				b.append(line);
			}
			SCRIPT = b.toString();
		} catch (IOException ioe) {
			ioe.printStackTrace();
			SCRIPT = null;
		}
	}
	
	public ScriptHandler() {
		if (SCRIPT != null) {
			language = "groovy";
			script = SCRIPT;
		}
	}
	public void setScript(String language, String script) {
		this.language = language;
		this.script = script;
	}
	
	private Object executeScript(Object object) throws Exception {
		if (language == null || script == null)
			return object;
		ScriptEngine engine = sem.getEngineByName(language);
		if (engine == null)
			return object;
		try {
			Bindings bindings = engine.createBindings();
			bindings.put("object", object);
			engine.eval(script, bindings);
			object = bindings.get("object");
		} catch (Exception e) {
			e.printStackTrace();
			script = null;
			language = null;
		}
		return object;
	}
	
	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		msg = executeScript(msg);
		super.channelRead(ctx, msg);
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		evt = executeScript(evt);
		super.userEventTriggered(ctx, evt);
	}

	@Override
	public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
		msg = executeScript(msg);
		super.write(ctx, msg, promise);
	}
	
}
