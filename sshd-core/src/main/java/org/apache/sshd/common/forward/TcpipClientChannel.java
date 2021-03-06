/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sshd.common.forward;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.apache.sshd.client.channel.AbstractClientChannel;
import org.apache.sshd.client.future.DefaultOpenFuture;
import org.apache.sshd.client.future.OpenFuture;
import org.apache.sshd.common.SshConstants;
import org.apache.sshd.common.SshException;
import org.apache.sshd.common.SshdSocketAddress;
import org.apache.sshd.common.channel.ChannelOutputStream;
import org.apache.sshd.common.future.CloseFuture;
import org.apache.sshd.common.future.DefaultCloseFuture;
import org.apache.sshd.common.future.SshFutureListener;
import org.apache.sshd.common.io.IoCloseFuture;
import org.apache.sshd.common.io.IoSession;
import org.apache.sshd.common.util.Buffer;

/**
 * TODO Add javadoc
 *
 * @author <a href="mailto:dev@mina.apache.org">Apache MINA SSHD Project</a>
 */
public class TcpipClientChannel extends AbstractClientChannel {

    public enum Type {
        Direct,
        Forwarded
    }

    private final Type typeEnum;
    private final IoSession serverSession;
    private final SshdSocketAddress remote;

    public TcpipClientChannel(Type type, IoSession serverSession, SshdSocketAddress remote) {
        super(type == Type.Direct ? "direct-tcpip" : "forwarded-tcpip");
        this.typeEnum = type;
        this.serverSession = serverSession;
        this.remote = remote;
    }


    public OpenFuture getOpenFuture() {
        return openFuture;
    }

    public synchronized OpenFuture open() throws IOException {
        InetSocketAddress src = null, dst = null;
        switch (typeEnum) {
            case Direct:
                src = (InetSocketAddress) serverSession.getRemoteAddress();
                dst = this.remote.toInetSocketAddress();
                break;
            case Forwarded:
                src = (InetSocketAddress) serverSession.getRemoteAddress();
                dst = (InetSocketAddress) serverSession.getLocalAddress();
                break;
        }
        if (closeFuture.isClosed()) {
            throw new SshException("Session has been closed");
        }
        openFuture = new DefaultOpenFuture(lock);
        log.info("Send SSH_MSG_CHANNEL_OPEN on channel {}", id);
        Buffer buffer = session.createBuffer(SshConstants.Message.SSH_MSG_CHANNEL_OPEN, 0);
        buffer.putString(type);
        buffer.putInt(id);
        buffer.putInt(localWindow.getSize());
        buffer.putInt(localWindow.getPacketSize());
        buffer.putString(dst.getAddress().getHostAddress());
        buffer.putInt(dst.getPort());
        buffer.putString(src.getAddress().getHostAddress());
        buffer.putInt(src.getPort());
        writePacket(buffer);
        return openFuture;
    }

    @Override
    protected synchronized void doOpen() throws IOException {
        out = new ChannelOutputStream(this, remoteWindow, log, SshConstants.Message.SSH_MSG_CHANNEL_DATA);
    }

    @Override
    protected synchronized CloseFuture preClose(boolean immediately) {
        final CloseFuture future = new DefaultCloseFuture(null);
        serverSession.close(immediately).addListener(new SshFutureListener<IoCloseFuture>() {
            public void operationComplete(IoCloseFuture f) {
                future.setClosed();
            }
        });
        return future;
    }

    protected synchronized void doWriteData(byte[] data, int off, int len) throws IOException {
        Buffer buf = new Buffer(data, off, len);
        localWindow.consumeAndCheck(len);
        serverSession.write(buf);
    }
}
