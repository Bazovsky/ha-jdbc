/*
 * HA-JDBC: High-Availability JDBC
 * Copyright 2004-2009 Paul Ferraro
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.sf.hajdbc.state.distributed;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import net.sf.hajdbc.Database;
import net.sf.hajdbc.DatabaseCluster;
import net.sf.hajdbc.distributed.CommandDispatcher;
import net.sf.hajdbc.distributed.CommandDispatcherFactory;
import net.sf.hajdbc.distributed.Member;
import net.sf.hajdbc.distributed.MembershipListener;
import net.sf.hajdbc.distributed.Remote;
import net.sf.hajdbc.distributed.Stateful;
import net.sf.hajdbc.durability.InvocationEvent;
import net.sf.hajdbc.durability.InvokerEvent;
import net.sf.hajdbc.state.DatabaseEvent;
import net.sf.hajdbc.state.StateManager;

/**
 * @author paul
 *
 */
public class DistributedStateManager<Z, D extends Database<Z>> implements StateManager, StateCommandContext<Z, D>, MembershipListener, Stateful
{
	private final DatabaseCluster<Z, D> cluster;
	private final StateManager stateManager;
	private final CommandDispatcher<StateCommandContext<Z, D>> dispatcher;
	private final ConcurrentMap<Member, Map<InvocationEvent, Map<D, InvokerEvent>>> remoteInvokerMap = new ConcurrentHashMap<Member, Map<InvocationEvent, Map<D, InvokerEvent>>>();
	
	public DistributedStateManager(DatabaseCluster<Z, D> cluster, CommandDispatcherFactory dispatcherFactory) throws Exception
	{
		this.cluster = cluster;
		this.stateManager = cluster.getStateManager();
		StateCommandContext<Z, D> context = this;
		this.dispatcher = dispatcherFactory.createCommandDispatcher(cluster.getId(), context, this, this);
	}

	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.state.StateManager#isMembershipEmpty()
	 */
	@Override
	public boolean isMembershipEmpty()
	{
		return false;
	}

	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.state.StateManager#getActiveDatabases()
	 */
	@Override
	public Set<String> getActiveDatabases()
	{
		return this.stateManager.getActiveDatabases();
	}

	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.state.StateManager#setActiveDatabases(java.util.Set)
	 */
	@Override
	public void setActiveDatabases(Set<String> databases)
	{
		this.stateManager.setActiveDatabases(databases);
	}

	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.DatabaseClusterListener#activated(net.sf.hajdbc.state.DatabaseEvent)
	 */
	@Override
	public void activated(DatabaseEvent event)
	{
		this.dispatcher.executeAll(new ActivationCommand<Z, D>(event));
	}

	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.DatabaseClusterListener#deactivated(net.sf.hajdbc.state.DatabaseEvent)
	 */
	@Override
	public void deactivated(DatabaseEvent event)
	{
		this.dispatcher.executeAll(new DeactivationCommand<Z, D>(event));
	}

	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.durability.DurabilityListener#afterInvocation(net.sf.hajdbc.durability.InvocationEvent)
	 */
	@Override
	public void afterInvocation(InvocationEvent event)
	{
		this.dispatcher.executeAll(new PostInvocationCommand<Z, D>(this.getRemoteDescriptor(event)));
		
		this.stateManager.afterInvocation(event);
	}

	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.durability.DurabilityListener#afterInvoker(net.sf.hajdbc.durability.InvokerEvent)
	 */
	@Override
	public void afterInvoker(InvokerEvent event)
	{
		this.dispatcher.executeAll(new InvokerCommand<Z, D>(this.getRemoteDescriptor(event)));
		
		this.stateManager.afterInvoker(event);
	}

	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.durability.DurabilityListener#beforeInvocation(net.sf.hajdbc.durability.InvocationEvent)
	 */
	@Override
	public void beforeInvocation(InvocationEvent event)
	{
		this.stateManager.beforeInvocation(event);
		
		this.dispatcher.executeAll(new PreInvocationCommand<Z, D>(this.getRemoteDescriptor(event)));
	}

	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.durability.DurabilityListener#beforeInvoker(net.sf.hajdbc.durability.InvokerEvent)
	 */
	@Override
	public void beforeInvoker(InvokerEvent event)
	{
		this.stateManager.beforeInvoker(event);
		
		this.dispatcher.executeAll(new InvokerCommand<Z, D>(this.getRemoteDescriptor(event)));
	}

	private RemoteInvocationDescriptor getRemoteDescriptor(InvocationEvent event)
	{
		return new RemoteInvocationDescriptorImpl(event, this.dispatcher.getLocal());
	}
	
	private RemoteInvokerDescriptor getRemoteDescriptor(InvokerEvent event)
	{
		return new RemoteInvokerDescriptorImpl(event, this.dispatcher.getLocal());
	}
	
	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.Lifecycle#start()
	 */
	@Override
	public void start() throws Exception
	{
		this.stateManager.start();
		this.dispatcher.start();
	}

	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.Lifecycle#stop()
	 */
	@Override
	public void stop()
	{
		this.dispatcher.stop();
		this.stateManager.stop();
	}

	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.state.distributed.StateCommandContext#getDatabaseCluster()
	 */
	@Override
	public DatabaseCluster<Z, D> getDatabaseCluster()
	{
		return this.cluster;
	}

	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.state.distributed.StateCommandContext#getLocalStateManager()
	 */
	@Override
	public StateManager getLocalStateManager()
	{
		return this.stateManager;
	}

	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.state.distributed.StateCommandContext#getRemoteInvokers(net.sf.hajdbc.distributed.Remote)
	 */
	@Override
	public Map<InvocationEvent, Map<D, InvokerEvent>> getRemoteInvokers(Remote remote)
	{
		return this.remoteInvokerMap.get(remote.getMember());
	}

	/**
	 * {@inheritDoc}
	 * @see org.jgroups.MessageListener#getState()
	 */
	@Override
	public byte[] getState()
	{
		Set<String> databases = this.stateManager.getActiveDatabases();
		
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutput output = new DataOutputStream(bytes);
		
		try
		{
			output.writeInt(databases.size());
			
			for (String database: databases)
			{
				output.writeUTF(database);
			}
		
			return bytes.toByteArray();
		}
		catch (IOException e)
		{
			throw new IllegalStateException(e);
		}
	}

	/**
	 * {@inheritDoc}
	 * @see org.jgroups.MessageListener#setState(byte[])
	 */
	@Override
	public void setState(byte[] bytes)
	{
		if (bytes != null)
		{
			Set<String> databases = new TreeSet<String>();
			
			DataInput input = new DataInputStream(new ByteArrayInputStream(bytes));
			
			try
			{
				int size = input.readInt();
				
				for (int i = 0; i < size; ++i)
				{
					databases.add(input.readUTF());
				}
				
				this.stateManager.setActiveDatabases(databases);
			}
			catch (IOException e)
			{
				throw new IllegalStateException(e);
			}
		}
	}

	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.distributed.MembershipListener#added(org.jgroups.Address)
	 */
	@Override
	public void added(Member member)
	{
		this.remoteInvokerMap.putIfAbsent(member, new HashMap<InvocationEvent, Map<D, InvokerEvent>>());
	}

	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.distributed.MembershipListener#removed(org.jgroups.Address)
	 */
	@Override
	public void removed(Member member)
	{
		Map<InvocationEvent, Map<D, InvokerEvent>> invokers = this.remoteInvokerMap.remove(member);
		
		if (invokers != null)
		{
			
		}
	}
	
	private static class RemoteDescriptor implements Remote, Serializable
	{
		private static final long serialVersionUID = 3717630867671175936L;
		
		private final Member member;
		
		RemoteDescriptor(Member member)
		{
			this.member = member;
		}

		@Override
		public Member getMember()
		{
			return this.member;
		}
	}
	
	private static class RemoteInvocationDescriptorImpl extends RemoteDescriptor implements RemoteInvocationDescriptor
	{
		private static final long serialVersionUID = 7782082258670023082L;
		
		private final InvocationEvent event;
		
		RemoteInvocationDescriptorImpl(InvocationEvent event, Member member)
		{
			super(member);
			
			this.event = event;
		}
		
		@Override
		public InvocationEvent getEvent()
		{
			return this.event;
		}
	}
	
	private static class RemoteInvokerDescriptorImpl extends RemoteDescriptor implements RemoteInvokerDescriptor
	{
		private static final long serialVersionUID = 6991831573393882786L;
		
		private final InvokerEvent event;
		
		RemoteInvokerDescriptorImpl(InvokerEvent event, Member member)
		{
			super(member);
			
			this.event = event;
		}
		
		@Override
		public InvokerEvent getEvent()
		{
			return this.event;
		}
	}
}
