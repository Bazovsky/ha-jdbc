/*
 * HA-JDBC: High-Availability JDBC
 * Copyright (C) 2012  Paul Ferraro
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
package net.sf.hajdbc.invocation;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import net.sf.hajdbc.Database;
import net.sf.hajdbc.DatabaseCluster;
import net.sf.hajdbc.ExceptionFactory;
import net.sf.hajdbc.Messages;
import net.sf.hajdbc.dialect.Dialect;
import net.sf.hajdbc.logging.Level;
import net.sf.hajdbc.logging.Logger;
import net.sf.hajdbc.logging.LoggerFactory;
import net.sf.hajdbc.sql.SQLProxy;
import net.sf.hajdbc.state.StateManager;

/**
 * @author Paul Ferraro
 */
public class InvokeOnAnyInvocationStrategy extends InvokeOnNextInvocationStrategy
{
	private static Logger logger = LoggerFactory.getLogger(InvokeOnExistingInvocationStrategy.class);

	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.invocation.InvocationStrategy#invoke(net.sf.hajdbc.sql.SQLProxy, net.sf.hajdbc.invocation.Invoker)
	 */
	@Override
	public <Z, D extends Database<Z>, T, R, E extends Exception> SortedMap<D, R> invoke(SQLProxy<Z, D, T, E> proxy, Invoker<Z, D, T, R, E> invoker) throws E
	{
		DatabaseCluster<Z, D> cluster = proxy.getDatabaseCluster();
		Dialect dialect = cluster.getDialect();
		StateManager stateManager = cluster.getStateManager();
		ExceptionFactory<E> exceptionFactory = proxy.getExceptionFactory();

		for (Map.Entry<D, T> entry: proxy.entries())
		{
			D database = entry.getKey();
			
			try
			{
				R result = invoker.invoke(database, entry.getValue());
				
				SortedMap<D, R> resultMap = new TreeMap<D, R>();
				resultMap.put(database, result);
				return resultMap;
			}
			catch (Exception e)
			{
				E exception = exceptionFactory.createException(e);
				
				if (exceptionFactory.indicatesFailure(exception, dialect) && (cluster.getBalancer().size() > 1))
				{
					if (cluster.deactivate(database, stateManager))
					{
						logger.log(Level.ERROR, exception, Messages.DATABASE_DEACTIVATED.getMessage(), database, cluster);
					}
				}
				else
				{
					throw exception;
				}
			}
		}
		
		return super.invoke(proxy, invoker);
	}
}
