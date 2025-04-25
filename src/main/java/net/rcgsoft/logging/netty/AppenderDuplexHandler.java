/*********************************************************************
* Copyright (c) 2025 Jon Huang
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/

package net.rcgsoft.logging.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.ReferenceCountUtil;

/**
 * Duplex handler for both inbound and outbound {@link Channel} events.
 * 
 * @author Jon Huang
 *
 */
final class AppenderDuplexHandler extends ChannelDuplexHandler {
	@Override
	public final void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		ReferenceCountUtil.release(msg);
	}

	@Override
	public final void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		NettyTcpSocketManager.getLogger().error(cause.getMessage(), cause);
	}

	@Override
	public final void userEventTriggered(ChannelHandlerContext ctx, Object event) throws Exception {
		if (event instanceof IdleStateEvent evt && evt.state() == IdleState.WRITER_IDLE) {
			// Just close the connection if the idle event is triggered
			NettyTcpSocketManager.getLogger().debug("Closing socket due to writer inactivity.");
			ctx.close();
		}
	}
}