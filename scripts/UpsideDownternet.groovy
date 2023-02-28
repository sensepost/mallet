import java.awt.geom.*;
import java.awt.image.*;

import javax.imageio.ImageIO;

import io.netty.buffer.*;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;

return new ChannelDuplexHandler() {
            public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {
                if (FullHttpRequest.class.isAssignableFrom(msg.getClass())) {
                    msg.headers().set("if-modified-since", "-1");
                    msg.headers().get("if-range");
                    msg.headers().get("range");
                    msg.headers().remove("If-None-Match");
                    msg.headers().remove("etag");
                }
                ctx.fireChannelRead(msg);
            }

            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                if (FullHttpResponse.class.isAssignableFrom(msg.getClass())) {
                    FullHttpResponse resp = (FullHttpResponse) msg;
                    String ct = resp.headers().get(HttpHeaderNames.CONTENT_TYPE);
                    String image = null;

                    System.out.println("CT = " + ct);
                    if ("image/jpeg".equals(ct)) {
                        image = "jpg";
                    } else if ("image/png".equals(ct)) {
                        image = "png";
                    }
                    if (image != null) {
                        // msg = msg.copy();
                        ByteBuf bb = resp.content();
                        if (bb != null && bb.readableBytes() > 0) {
                            byte[] bytes = new byte[bb.readableBytes()];
                            bb.readBytes(bytes);
                            try {
                                ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                                BufferedImage bi = ImageIO.read(bais);

                                BufferedImage flipped = new BufferedImage(bi.getWidth(),bi.getHeight(),bi.getType());
                                AffineTransform tran = AffineTransform.getTranslateInstance(0, bi.getHeight());
                                AffineTransform flip = AffineTransform.getScaleInstance(1d, -1d);
                                tran.concatenate(flip);
                                java.awt.Graphics2D g = flipped.createGraphics();
                                g.setTransform(tran);
                                g.drawImage(bi, 0, 0, null);
                                g.dispose();

                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                ImageIO.write(flipped, image, baos);
                                ByteBuf newContent = Unpooled.copiedBuffer(baos.toByteArray());
                                resp = resp.replace(newContent);
                                resp.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, newContent.readableBytes());
                                msg = resp;
                                System.out.println("Flipped a " + image + "!");
                            } catch (Exception e) {
                                e.printStackTrace();
                                msg = resp.replace(Unpooled.wrappedBuffer(bytes));
                            }
                        }
                    }
                }
                ctx.write(msg, promise);
            }
        };

