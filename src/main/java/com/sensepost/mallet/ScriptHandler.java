package com.sensepost.mallet;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import javax.script.Bindings;
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
 * JSR-223 script implementing a {@link io.netty.channel.ChannelHandler} as its
 * replacement in the pipeline.
 * 
 * A typical script would return an instance of a class that implements
 * ChannelHandler. In most cases, the easiest approach is to extend either
 * {@link io.netty.channel.ChannelInboundHandlerAdapter} or
 * {@link io.netty.channel.ChannelDuplexHandler}
 * 
 * Extending {@link io.netty.channel.ChannelInboundHandlerAdapter} to intercept
 * READ events:
 * 
 * <pre>
 * {@code
 * import io.netty.channel.*;
 * 
 * return new ChannelInboundHandlerAdapter() {
 * 		public void channelRead(ChannelHandlerContext ctx, Object msg) {
 * 			// do something with the msg
 * 			ctx.fireChannelRead(msg);  // pass it up the pipeline 
 * 		}
 * };
 * 
 * }<pre>
 * 
 * Extending {@link io.netty.channel.ChannelDuplexHandler} to intercept WRITE
 * events:
 * 
 * <pre>
 * {@code
 * import io.netty.channel.*;
 * 
 * return new ChannelDuplexHandler() {
 * 		public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
 * 			// do something with the msg
 * 			ctx.write(msg, promise);  // pass it down the pipeline 
 * 		}
 * };
 * 
 * }<pre>
 * 
 * @author rogan
 *
 */
@Sharable
public class ScriptHandler extends ChannelInitializer<Channel> implements GraphNodeAware {

	private String script = null, language = null, fileName = null;

	private Graph graph = null;
	private Object node = null;

	/**
	 * Creates a ScriptHandler instance using the provided script, interpreted as
	 * the specified language
	 * 
	 * @param script
	 * @param language
	 */
	public ScriptHandler(String script, String language) {
		this.script = script;
		if (script == null)
			throw new NullPointerException("script");
		this.language = language;
		if (language == null)
			throw new NullPointerException("language");
	}

	/**
	 * Creates a ScriptHandler instance from the provided filename, interpreted
	 * using the language matching the file extension.
	 * 
	 * @param filename
	 */
	public ScriptHandler(String filename) {
		this.fileName = filename;
		if (filename == null)
			throw new NullPointerException("filename");
	}

	@Override
	public void setGraphNode(Graph graph, Object node) {
		this.graph = graph;
		this.node = node;
	}

	private ChannelHandler getChannelHandler(Bindings bindings) throws ScriptException {
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
					is = getClass().getResourceAsStream(fileName);
				if (is == null)
					throw new FileNotFoundException(fileName);

				String extension = fileName.substring(fileName.lastIndexOf('.')+1);
				engine = sem.getEngineByExtension(extension);
				if (engine == null)
					throw new RuntimeException("No engine for extension: '" + extension + "'");
				scriptResult = engine.eval(new InputStreamReader(is), bindings);
			} catch (IOException ioe) {
				ScriptException e = new ScriptException("Error compiling script");
				e.initCause(ioe);
				throw e;
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
			scriptResult = engine.eval(script, bindings);
		} else {
			throw new NullPointerException("filename or script and language were null");
		}
		if (scriptResult == null)
			scriptResult = engine.eval("_", bindings); // special case for Jython
		if (scriptResult == null)
			throw new RuntimeException("Could not get a ChannelHandler from the script");
		return getScriptHandler(engine, scriptResult);
	}

	private ChannelHandler getScriptHandler(ScriptEngine engine, Object script) {
		if (ChannelHandler.class.isAssignableFrom(script.getClass())) {
			return (ChannelHandler) script;
		} else
			throw new ClassCastException("Script does not implement ChannelHandler");
	}

	@Override
	protected void initChannel(Channel ch) throws Exception {
		String name = ch.pipeline().context(this).name();
		Bindings bindings = ch.attr(ChannelAttributes.SCRIPT_CONTEXT).get();
		ch.pipeline().addAfter(name, null, getChannelHandler(bindings));
	}

	/**
	 * This method can only ever be called as a result of an exception in the
	 * compilation of the script. If that happens, propagate the exception upstream
	 * so it can handled by the last resort handlers
	 */
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		if (graph != null && node != null)
			graph.addGraphException(node, cause);
		ctx.fireExceptionCaught(cause);
	}

}
