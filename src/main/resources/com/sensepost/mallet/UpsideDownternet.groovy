import java.awt.*;
import java.awt.image.*;
import java.awt.geom.*;
import javax.imageio.ImageIO;
import io.netty.handler.codec.http.*;
import io.netty.buffer.*;
import io.netty.channel.*;

return new ChannelDuplexHandler() {
	public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {
		if (FullHttpRequest.class.isAssignableFrom(msg.getClass())) {
			msg.headers().set("if-modified-since", "-1");
			msg.headers().get("if-range");
			msg.headers().get("range");
		}
		ctx.fireChannelRead(msg);
	}
	
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
	    if (FullHttpResponse.class.isAssignableFrom(msg.getClass())) {
			ct = msg.headers().get(HttpHeaderNames.CONTENT_TYPE);
			if ("image/jpeg".equals(ct)) {
				msg = msg.copy();
				bb = msg.content();
				if (bb != null && bb.readableBytes() > 0) {
					bytes = new byte[bb.readableBytes()];
					bb.readBytes(bytes);
					bais = new ByteArrayInputStream(bytes);
					bi = ImageIO.read(bais);
					
					flipped = new BufferedImage(bi.getWidth(),bi.getHeight(),bi.getType());
					tran = AffineTransform.getTranslateInstance(0, bi.getHeight());
					flip = AffineTransform.getScaleInstance(1d, -1d);
					tran.concatenate(flip);
					g = flipped.createGraphics();
					g.setTransform(tran);
					g.drawImage(bi, 0, 0, null);
					g.dispose();
					
					baos = new ByteArrayOutputStream();
					ImageIO.write(flipped, "jpg", baos);
					newcontent = Unpooled.wrappedBuffer(baos.toByteArray());
					msg.replace(newcontent);
					msg.headers().setHeader("Content-Length", newContent.readableBytes());
					System.out.println("Flipped an image!");
				} else {
					System.out.println("No content!");
				}
			} else {
				System.out.println("Not a jpg: " + ct);
			}
		}
        ctx.write(msg, promise);
    }
};

