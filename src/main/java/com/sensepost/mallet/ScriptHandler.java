package com.sensepost.mallet;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import com.sensepost.mallet.graph.Graph;
import com.sensepost.mallet.graph.GraphNodeAware;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;

/**
 * The ScriptHandler is a <code>ChannelInitializer</code> that instantiates a
 * JSR-223 script implementing a <code>ChannelHandler</code> as its replacement
 * in the pipeline.
 * 
 * @author rogan
 *
 */
@Sharable
public class ScriptHandler extends ChannelInitializer<Channel> implements GraphNodeAware {

	private String script = null, language = null, fileName = null;

	private Graph graph = null;
	private Object node = null;

	public ScriptHandler(String script, String language) throws Exception {
		this.script = script;
		this.language = language;
	}

	public ScriptHandler(String filename) throws FileNotFoundException, ScriptException {
		this.fileName = filename;
	}

	@Override
	public void setGraphNode(Graph graph, Object node) {
		this.graph = graph;
		this.node = node;
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
				if (engine == null)
					throw new RuntimeException("No engine for extension: '" + extension + "'");
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
			if (engine == null)
				throw new RuntimeException("No engine for language: '" + language + "'");
			scriptResult = engine.eval(script);
		} else {
			throw new NullPointerException("filename or script and language were null");
		}
		return getScriptHandler(engine, scriptResult);
	}

	private ChannelHandler getScriptHandler(ScriptEngine engine, Object script) {
		if (script instanceof ChannelHandler) {
			return (ChannelHandler) script;
		} else
			throw new ClassCastException("Script does not implement ChannelHandler");
	}

	@Override
	protected void initChannel(Channel ch) throws Exception {
		String name = ch.pipeline().context(this).name();
		ch.pipeline().addAfter(name, null, getChannelHandler());
	}

	/**
	 * This method can only ever be called as a result of an exception in the compilation of the script.
	 * If that happens, propagate the exception upstream so it can handled by the last resort handlers
	 */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    	if (graph != null && node != null)
    		graph.addGraphException(ctx, cause);
		ctx.fireExceptionCaught(cause);
		if (ctx.channel().isOpen())
			ctx.close();
    }

}
