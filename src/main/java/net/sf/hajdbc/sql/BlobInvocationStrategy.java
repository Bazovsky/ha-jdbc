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
package net.sf.hajdbc.sql;

import java.lang.reflect.InvocationHandler;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import net.sf.hajdbc.Database;
import net.sf.hajdbc.DatabaseCluster;

/**
 * @author Paul Ferraro
 * @param <D> 
 * @param <P> 
 */
public class BlobInvocationStrategy<Z, D extends Database<Z>, P> extends LocatorInvocationStrategy<Z, D, P, Blob>
{
	/**
	 * @param cluster 
	 * @param parent the object that created blobs
	 * @throws SQLException 
	 */
	public BlobInvocationStrategy(DatabaseCluster<Z, D> cluster, P parent, Connection connection) throws SQLException
	{
		super(cluster, parent, Blob.class, connection);
	}

	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.sql.LocatorInvocationStrategy#createInvocationHandler(java.lang.Object, net.sf.hajdbc.sql.SQLProxy, net.sf.hajdbc.sql.Invoker, java.util.Map, boolean)
	 */
	@Override
	protected InvocationHandler createInvocationHandler(P parent, SQLProxy<Z, D, P, SQLException> proxy, Invoker<Z, D, P, Blob, SQLException> invoker, Map<D, Blob> objectMap, boolean updateCopy) throws SQLException
	{
		return new BlobInvocationHandler<Z, D, P>(parent, proxy, invoker, objectMap, updateCopy);
	}
}