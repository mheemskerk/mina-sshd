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
package org.apache.sshd.common.io.mina;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.Set;

import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.service.IoHandler;
import org.apache.mina.core.service.IoService;
import org.apache.mina.core.session.IoSessionConfig;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.apache.sshd.common.FactoryManager;

/**
 */
public class MinaAcceptor extends MinaService implements org.apache.sshd.common.io.IoAcceptor, IoHandler {

    protected volatile IoAcceptor acceptor;
    // Acceptor
    protected int backlog = 50;
    protected boolean reuseAddress = true;
    protected IoSessionConfig sessionConfig;

    public MinaAcceptor(FactoryManager manager, org.apache.sshd.common.io.IoHandler handler) {
        super(manager, handler);
    }

    protected IoAcceptor createAcceptor() {
        NioSocketAcceptor acceptor = new NioSocketAcceptor(getNioWorkers());
        acceptor.setCloseOnDeactivation(false);
        acceptor.setReuseAddress(reuseAddress);
        acceptor.setBacklog(backlog);

        // MINA itself forces our socket receive buffer to 1024 bytes
        // by default, despite what the operating system defaults to.
        // This limits us to about 3 MB/s incoming data transfer.  By
        // forcing back to the operating system default we can get a
        // decent transfer rate again.
        //
        final Socket s = new Socket();
        try {
            try {
                acceptor.getSessionConfig().setReceiveBufferSize(s.getReceiveBufferSize());
            } finally {
                s.close();
            }
        } catch (IOException e) {
            log.warn("cannot adjust SO_RCVBUF back to system default", e);
        }
        if (sessionConfig != null) {
            acceptor.getSessionConfig().setAll(sessionConfig);
        }
        return acceptor;
    }

    protected IoAcceptor getAcceptor() {
        if (acceptor == null) {
            synchronized (this) {
                if (acceptor == null) {
                    acceptor = createAcceptor();
                    acceptor.setHandler(this);
                }
            }
        }
        return acceptor;
    }

    @Override
    protected IoService getIoService() {
        return getAcceptor();
    }

    public void bind(Collection<? extends SocketAddress> addresses) throws IOException {
        getAcceptor().bind(addresses);
    }

    public void bind(SocketAddress address) throws IOException {
        getAcceptor().bind(address);
    }

    public void unbind() {
        getAcceptor().unbind();
    }

    public void unbind(Collection<? extends SocketAddress> addresses) {
        getAcceptor().unbind(addresses);
    }

    public void unbind(SocketAddress address) {
        getAcceptor().unbind(address);
    }

    public Set<SocketAddress> getBoundAddresses() {
        return getAcceptor().getLocalAddresses();
    }

}
