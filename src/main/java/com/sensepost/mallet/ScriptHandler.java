package com.sensepost.mallet;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelInitializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

@Sharable
public class ScriptHandler extends ChannelInitializer<Channel> {

	private String script = null, language = null, fileName = null;

	public ScriptHandler(String script, String language) throws Exception {
		this.script = script;
		this.language = language;
	}

	public ScriptHandler(String filename) throws FileNotFoundException,
			ScriptException {
		this.fileName = filename;
	}

	private ChannelHandler getChannelHandler() throws ScriptException {
		ScriptEngineManager sem = new ScriptEngineManager();
		ScriptEngine engine;

		Object scriptResult;

		if (fileName != null) {
			InputStream is = null;
			try {
				File f = new File(fileName);
				if (f.exists())
					is = new FileInputStream(f);
				else
					is = getClass().getClassLoader().getResourceAsStream(fileName);
				if (is == null)
					throw new FileNotFoundException(fileName);

				String extension = fileName.substring(fileName.lastIndexOf('.'));
				engine = sem.getEngineByExtension(extension);
				scriptResult = engine.eval(new InputStreamReader(is));
			} catch (IOException ioe) {
				throw new ScriptException(ioe);
			} finally {
				if (is != null)
					try {
						is.close();
					} catch (IOException e) {
					}
			}
		} else if (script != null && language != null) {
			engine = sem.getEngineByName(language);
			try {
				scriptResult = engine.eval(script);
			} catch (Exception e) {
				e.printStackTrace();
				throw e;
			}
		} else {
			throw new NullPointerException("filename or script and language were null");
		}
		return getScriptHandler(engine, scriptResult);
	}
	
	private ChannelHandler getScriptHandler(ScriptEngine engine, Object script) {
		if (script instanceof ChannelDuplexHandler)
			return (ChannelDuplexHandler) script;
		if (script instanceof ChannelHandler) {
			return (ChannelHandler) script;
		} else
			throw new ClassCastException(
					"Script does not implement ChannelHandler");
	}

	@Override
	protected void initChannel(Channel ch) throws Exception {
		String name = ch.pipeline().context(this).name();
		ch.pipeline().addAfter(name, null, getChannelHandler());
	}

}
