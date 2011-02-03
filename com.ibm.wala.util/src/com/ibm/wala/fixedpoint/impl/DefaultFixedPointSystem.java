/*******************************************************************************
 * Copyright (c) 2002 - 2006 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.wala.fixedpoint.impl;

import java.util.Iterator;
import java.util.Set;

import com.ibm.wala.fixpoint.AbstractStatement;
import com.ibm.wala.fixpoint.IFixedPointStatement;
import com.ibm.wala.fixpoint.IFixedPointSystem;
import com.ibm.wala.fixpoint.IVariable;
import com.ibm.wala.fixpoint.UnaryStatement;
import com.ibm.wala.util.collections.EmptyIterator;
import com.ibm.wala.util.collections.Filter;
import com.ibm.wala.util.collections.FilterIterator;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.debug.UnimplementedError;
import com.ibm.wala.util.graph.GraphIntegrity;
import com.ibm.wala.util.graph.INodeWithNumber;
import com.ibm.wala.util.graph.NumberedGraph;
import com.ibm.wala.util.graph.impl.SparseNumberedGraph;
import com.ibm.wala.util.graph.traverse.Topological;

/**
 * Default implementation of a dataflow graph
 */
public class DefaultFixedPointSystem<T extends IVariable> implements IFixedPointSystem<T>  {
  static final boolean DEBUG = false;

  /**
   * A graph which defines the underlying system of statements and variables
   */
  private final NumberedGraph<INodeWithNumber> graph;

  /**
   * We maintain a hash set of equations in order to check for equality with
   * equals() ... the NumberedGraph does not support this. TODO: use a custom
   * NumberedNodeManager to save space
   */
  final private Set<GeneralStatement> equations = HashSetFactory.make();

  /**
   * We maintain a hash set of variables in order to check for equality with
   * equals() ... the NumberedGraph does not support this. TODO: use a custom
   * NumberedNodeManager to save space
   */
  final private Set<IVariable> variables = HashSetFactory.make();

  /**
   * @param expectedOut number of expected out edges in the "usual" case
   * for constraints .. used to tune graph representation
   */
  public DefaultFixedPointSystem(int expectedOut) {
    super();
    graph = new SparseNumberedGraph<INodeWithNumber>(expectedOut);
  }
  
  /**
   * default constructor ... tuned for one use for each def in
   * dataflow graph.
   */
  public DefaultFixedPointSystem() {
    this(1);
  }
  
  @Override
  public boolean equals(Object obj) {
    return graph.equals(obj);
  }

  @Override
  public int hashCode() {
    return graph.hashCode();
  }

  @Override
  public String toString() {
    return graph.toString();
  }

  public void removeStatement(IFixedPointStatement s) {
    graph.removeNodeAndEdges(s);
  }

  @SuppressWarnings("unchecked")
  public Iterator<AbstractStatement> getStatements() {
    return new FilterIterator(graph.iterator(), new Filter() {
      public boolean accepts(Object x) {
        return x instanceof AbstractStatement;
      }
    });
  }

  public void addStatement(IFixedPointStatement statement) throws IllegalArgumentException, UnimplementedError {
    if (statement == null) {
      throw new IllegalArgumentException("statement == null");
    }
    if (statement instanceof UnaryStatement) {
      addStatement((UnaryStatement) statement);
    } else if (statement instanceof NullaryStatement) {
      addStatement((NullaryStatement) statement);
    } else if (statement instanceof GeneralStatement) {
      addStatement((GeneralStatement) statement);
    } else {
      Assertions.UNREACHABLE("unexpected: " + statement.getClass());
    }
  }

  public void addStatement(GeneralStatement s) {
    if (s == null) {
      throw new IllegalArgumentException("s is null");
    }
    IVariable[] rhs = s.getRHS();
    IVariable lhs = s.getLHS();

    equations.add(s);
    graph.addNode(s);
    if (lhs != null) {
      variables.add(lhs);
      graph.addNode(lhs);
      graph.addEdge(s, lhs);
    }
    for (int i = 0; i < rhs.length; i++) {
      IVariable v = rhs[i];
      IVariable variable = v;
      if (variable != null) {
        variables.add(variable);
        graph.addNode(variable);
        graph.addEdge(variable, s);
      }
    }

    if (DEBUG) {
      checkGraph();
    }
  }

  public void addStatement(UnaryStatement s) {
    if (s == null) {
      throw new IllegalArgumentException("s is null");
    }
    IVariable lhs = s.getLHS();
    IVariable rhs = s.getRightHandSide();

    graph.addNode(s);
    if (lhs != null) {
      variables.add(lhs);
      graph.addNode(lhs);
      graph.addEdge(s, lhs);
    }
    variables.add(rhs);
    graph.addNode(rhs);
    graph.addEdge(rhs, s);

    if (DEBUG) {
      checkGraph();
    }
  }

  public void addStatement(NullaryStatement s) {
    if (s == null) {
      throw new IllegalArgumentException("s is null");
    }
    IVariable lhs = s.getLHS();

    graph.addNode(s);
    if (lhs != null) {
      variables.add(lhs);
      graph.addNode(lhs);
      graph.addEdge(s, lhs);
    }

    if (DEBUG) {
      checkGraph();
    }
  }

  public void addVariable(IVariable v) {
    variables.add(v);
    graph.addNode(v);
    if (DEBUG) {
      checkGraph();
    }
  }

  public AbstractStatement getStep(int number) {
    return (AbstractStatement) graph.getNode(number);
  }

  public void reorder() {
    if (DEBUG) {
      checkGraph();
    }

    Iterator<INodeWithNumber> order = Topological.makeTopologicalIter(graph);
    int number = 0;
    while (order.hasNext()) {
      Object elt = order.next();
      if (elt instanceof IVariable) {
        IVariable v = (IVariable) elt;
        v.setOrderNumber(number++);
      }
    }
  }

  /**
   * check that this graph is well-formed
   */
  private void checkGraph() {
    try {
      GraphIntegrity.check(graph);
    } catch (Throwable e) {
      e.printStackTrace();
      Assertions.UNREACHABLE();
    }
  }

  public Iterator getStatementsThatUse(IVariable v) {
    return (graph.containsNode(v) ? graph.getSuccNodes(v) : EmptyIterator.instance());
  }

  public Iterator getStatementsThatDef(IVariable v) {
    return (graph.containsNode(v) ? graph.getPredNodes(v) : EmptyIterator.instance());
  }

  public IVariable getVariable(int n) {
    return (IVariable) graph.getNode(n);
  }

  public int getNumberOfStatementsThatUse(IVariable v) {
    return (graph.containsNode(v) ? graph.getSuccNodeCount(v) : 0);
  }

  public int getNumberOfStatementsThatDef(IVariable v) {
    return (graph.containsNode(v) ? graph.getPredNodeCount(v) : 0);
  }

  @SuppressWarnings("unchecked")
  public Iterator<IVariable> getVariables() {
    return new FilterIterator(graph.iterator(), new Filter() {
      public boolean accepts(Object x) {
        return x instanceof IVariable;
      }
    });
  }

  public int getNumberOfNodes() {
    return graph.getNumberOfNodes();
  }

  public Iterator<? extends INodeWithNumber> getPredNodes(INodeWithNumber n) {
    return graph.getPredNodes(n);
  }


  public int getPredNodeCount(INodeWithNumber n) {
    return graph.getPredNodeCount(n);
  }

  public boolean containsStatement(IFixedPointStatement s) {
    return equations.contains(s);
  }

  public boolean containsVariable(IVariable v) {
    return variables.contains(v);
  }

}