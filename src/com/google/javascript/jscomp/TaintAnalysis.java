package com.google.javascript.jscomp;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import com.google.javascript.jscomp.ControlFlowGraph.Branch;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.jscomp.graph.LatticeElement;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 *  A simple static taint analysis on variables.
 *  Can currently only mark parameters as tainted by default.
 */
class TaintAnalysis
    extends DataFlowAnalysis<Node, TaintAnalysis.TaintAnalysisLattice> {

  private static class LiveVariableJoinOp implements JoinOp<TaintAnalysisLattice> {
    @Override
    public TaintAnalysisLattice apply(List<TaintAnalysisLattice> in) {
      TaintAnalysisLattice result = new TaintAnalysisLattice(in.get(0));
      for (int i = 1; i < in.size(); i++) {
        result.taintSet.or(in.get(i).taintSet);
      }
      return result;
    }
  }

  /**
   * A lattice that stores the taint status of all local variables at a given point in the program.
   */
  static class TaintAnalysisLattice implements LatticeElement {
    private final BitSet taintSet;

    /** @param numVars Number of all local variables. */
    private TaintAnalysisLattice(int numVars) {
      this.taintSet = new BitSet(numVars);
    }

    private TaintAnalysisLattice(TaintAnalysisLattice other) {
      checkNotNull(other);
      this.taintSet = (BitSet) other.taintSet.clone();
    }

    @Override
    public boolean equals(Object other) {
      checkNotNull(other);
      return (other instanceof TaintAnalysisLattice)
          && this.taintSet.equals(((TaintAnalysisLattice) other).taintSet);
    }

    public boolean isTainted(int index) { return taintSet.get(index); }

    @Override
    public String toString() {
      return taintSet.toString();
    }

    @Override
    public int hashCode() {
      return taintSet.hashCode();
    }
  }

  // The scope of the function that we are analyzing.
  private final Scope jsScope;

  // The scope of the body of the function that we are analyzing.
  private final Scope jsScopeChild;

  // Maps the variable name to it's position
  // in this jsScope were we to combine the function and function body scopes. The Integer
  // represents the equivalent of the variable index property within a scope
  private final Map<String, Integer> scopeVariables;

  // obtain variables in the order in which they appear in the code
  private final List<Var> orderedVars;

  // indicate whether function arguments should be marked as tainted
  private boolean argsTainted;

  private final Map<String, Var> allVarsInFn;
  /**
   * Taint Analysis
   *
   * @param cfg
   * @param jsScope the function scope
   * @param jsScopeChild null or function block scope
   * @param compiler
   * @param scopeCreator Es6 Scope creator
   */
  TaintAnalysis(
      ControlFlowGraph<Node> cfg,
      Scope jsScope,
      @Nullable Scope jsScopeChild,
      boolean argsTainted,
      AbstractCompiler compiler,
      Es6SyntacticScopeCreator scopeCreator) {
    super(cfg, new LiveVariableJoinOp());
    checkState(jsScope.isFunctionScope(), jsScope);

    this.jsScope = jsScope;
    this.jsScopeChild = jsScopeChild;
    this.scopeVariables = new HashMap<>();
    this.argsTainted = argsTainted;
    this.allVarsInFn = new HashMap<>();
    this.orderedVars = new ArrayList<>();
    NodeUtil.getAllVarsDeclaredInFunction(
        allVarsInFn, orderedVars, compiler, scopeCreator, jsScope);
    addScopeVariables();
  }

  /**
   * Parameters belong to the function scope, but variables defined in the function body belong to
   * the function body scope. Assign a unique index to each variable, regardless of which scope it's
   * in.
   */
  private void addScopeVariables() {
    int num = 0;
    for (Var v : orderedVars) {
      scopeVariables.put(v.getName(), num);
      num++;
    }
  }

  public Map<String, Var> getAllVariables() {
    return allVarsInFn;
  }

  public List<Var> getAllVariablesInOrder() {
    return orderedVars;
  }

  public int getVarIndex(String var) {
    // return -1 when variable is not in scope
    if (scopeVariables.containsKey(var)) {
      return scopeVariables.get(var);
    }
    return -1;
  }

  @Override
  boolean isForward() {
    return true;
  }

  @Override
  TaintAnalysisLattice createEntryLattice() {
    TaintAnalysisLattice tainted = new TaintAnalysisLattice(orderedVars.size());

    if(this.argsTainted) {
      Node params = NodeUtil.getFunctionParameters(this.jsScope.getRootNode());
      for (Node c = params.getFirstChild(); c != null; c = c.getNext()) {
        if (c.isName()) {
          tainted.taintSet.set(getVarIndex(c.getString()));
        }
      }
    }
    return tainted;
  }

  @Override
  TaintAnalysisLattice createInitialEstimateLattice() {
    return new TaintAnalysisLattice(orderedVars.size());
  }

  @Override
  TaintAnalysisLattice flowThrough(Node node, TaintAnalysisLattice input) {
    return updateTaintStatus(node, input);
  }

  /**
   * Detects tainted variables
   */
  private TaintAnalysisLattice updateTaintStatus(Node n, TaintAnalysisLattice input) {
    TaintAnalysisLattice result = new TaintAnalysisLattice(input);

    switch (n.getToken()) {
      case SCRIPT:
      case ROOT:
      case FUNCTION:
      case BLOCK:
        return result;

      case WHILE:
      case DO:
      case IF:
      case FOR: {
        TaintAnalysisLattice intermediate = updateTaintStatus(n.getFirstChild(), result);
        result.taintSet.or(intermediate.taintSet);
        return result;
      }

      case FOR_OF:
      case FOR_IN:
        return updateTaintStatus(n.getLastChild(), result);

      case LET:
      case CONST:
      case VAR:
        if (expressionTainted(n.getFirstChild().getFirstChild(), input)) {
          result.taintSet.set(getVarIndex(n.getFirstChild().getString()));
        }

      case NAME: {
        for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
          TaintAnalysisLattice intermediate = updateTaintStatus(c, result);
          result.taintSet.or(intermediate.taintSet);
        }
      }
    }

    return result;
  }

  /**
   * Determines whether the expression is tainted and therefore the resulting variable
   * currently only for simple variable assignments
   *
   * @param n the Node of the expression
   * @param input the current taint state
   * @return true when tainted, false when not
   */
  private boolean expressionTainted(Node n, TaintAnalysisLattice input) {
    if(n != null) {

      if(n.isName()) {
        int index = getVarIndex(n.getString());
        if(index >= 0 && input.taintSet.get(index)){
          return true;
        }
      } else {
        // assume that any kind of interference means the variable is tainted
        // includes a functions return value with a tainted parameter regardless of
        // the actual interference of the parameter with the return value
        boolean tainted = true;
        for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
          tainted &= expressionTainted(c, input);
        }
        return tainted;
      }
    }
    return false;
  }

}
