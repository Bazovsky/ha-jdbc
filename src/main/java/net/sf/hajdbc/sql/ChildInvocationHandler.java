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
package net.sf.hajdbc.sql;

import java.util.Map;

import net.sf.hajdbc.Database;
import net.sf.hajdbc.invocation.Invoker;

/**
 * @author Paul Ferraro
 * @param <D> 
 * @param <P> 
 * @param <T> 
 */
public abstract class ChildInvocationHandler<Z, D extends Database<Z>, P, T, E extends Exception> extends AbstractChildInvocationHandler<Z, D, P, E, T, E>
{
	protected ChildInvocationHandler(P parent, SQLProxy<Z, D, P, E> proxy, Invoker<Z, D, P, T, E> invoker, Class<T> proxyClass, Class<E> exceptionClass, Map<D, T> objects)
	{
		super(parent, proxy, invoker, proxyClass, exceptionClass, objects);
	}
}
