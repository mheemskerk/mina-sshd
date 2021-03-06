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

import java.net.SocketAddress;

import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.future.IoFutureListener;
import org.apache.mina.core.service.IoConnector;
import org.apache.mina.core.service.IoHandler;
import org.apache.mina.core.session.IoSessionConfig;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.apache.sshd.common.FactoryManager;
import org.apache.sshd.common.future.DefaultSshFuture;
import org.apache.sshd.common.io.IoConnectFuture;

/**
 */
public class MinaConnector extends MinaService implements org.apache.sshd.common.io.IoConnector, IoHandler {

    protected volatile IoConnector connector;
    protected IoSessionConfig sessionConfig;

    public MinaConnector(FactoryManager manager, org.apache.sshd.common.io.IoHandler handler) {
        super(manager, handler);
    }


    protected IoConnector createConnector() {
        NioSocketConnector connector = new NioSocketConnector(getNioWorkers());
        if (sessionConfig != null) {
            connector.getSessionConfig().setAll(sessionConfig);
        }
        return connector;
    }

    protected IoConnector getConnector() {
        if (connector == null) {
            synchronized (this) {
                if (connector == null) {
                    connector = createConnector();
                    connector.setHandler(this);
                }
            }
        }
        return connector;
    }

    @Override
    protected org.apache.mina.core.service.IoService getIoService() {
        return getConnector();
    }

    public IoConnectFuture connect(SocketAddress address) {
        class Future extends DefaultSshFuture<IoConnectFuture> implements IoConnectFuture {
            Future(Object lock) {
                super(lock);
            }

            public org.apache.sshd.common.io.IoSession getSession() {
                Object v = getValue();
                return v instanceof org.apache.sshd.common.io.IoSession ? (org.apache.sshd.common.io.IoSession) v : null;
            }

            public Throwable getException() {
                Object v = getValue();
                return v instanceof Throwable ? (Throwable) v : null;
            }

            public boolean isConnected() {
                return getValue() instanceof org.apache.sshd.common.io.IoSession;
            }

            public void setSession(org.apache.sshd.common.io.IoSession session) {
                setValue(session);
            }

            public void setException(Throwable exception) {
                setValue(exception);
            }
        }
        final IoConnectFuture future = new Future(null);
        getConnector().connect(address).addListener(new IoFutureListener<ConnectFuture>() {
            public void operationComplete(ConnectFuture cf) {
                if (cf.getException() != null) {
                    future.setException(cf.getException());
                } else if (cf.isCanceled()) {
                    future.cancel();
                } else {
                    future.setSession(getSession(cf.getSession()));
                }
            }
        });
        return future;
    }

}
