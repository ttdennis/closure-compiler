package com.google.javascript.jscomp;

import com.google.javascript.jscomp.graph.DiGraph;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

public class LoopComplexityDetection extends NodeTraversal.AbstractScopedCallback implements CompilerPass {

  private final AbstractCompiler compiler;
  private List<List<Node>> nestedLoops;
  private List<Node> currentNodeList;
  private FunctionNames functionNames;

  private static final String[] callbackLoopFunctions = {
      "forEach",
      "map",
      "reduce"
  };

  static final DiagnosticType INPUT_DEPENDENT_NESTED_LOOP = DiagnosticType.warning(
      "JSC_INPUT_DEPENDENT_NESTED_LOOP",
      "Input dependent nested loop {0}");

  public LoopComplexityDetection(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.functionNames = compiler.getFunctionNames();
    this.nestedLoops = new ArrayList<>();
  }

  public void process(Node externs, Node root) { NodeTraversal.traverseEs6(this.compiler, root, this); }

  public void visit(NodeTraversal t, Node n, Node parent) {}

  /**
   * Determines whether a node represents a callback type loop
   * @param n
   * @return
   */
  private boolean isCallbackLoop(Node n) {
    if(n.isExprResult()) {
      Node call = n.getFirstChild();
      for (String methodName : callbackLoopFunctions) {
        if (NodeUtil.isObjectCallMethod(call, methodName)) {
          for(Node c = call.getFirstChild(); c != null; c = c.getNext()) {
            if (c.isFunction()) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  /**
   * isLoop determines whether the current node is anything loop like.
   * Keywords for, while, etc. as well as callback based loops (e.g. .forEach())
   * are identified.
   * @param n Node
   * @return Whether
   */
  private boolean isLoop(Node n) {
    // identifies keyword loops
    if(NodeUtil.isLoopStructure(n)) return true;

    if(isCallbackLoop(n)) return true;

    return false;
  }

  /**
   * Returns the code block of a loop for both callbacks and standard loops
   * @param n Node
   * @return Code block node
   */
  private Node getLoopCodeBlock(Node n) {
    // check if this is a standard loop construct
    if (NodeUtil.isLoopStructure(n))
      return NodeUtil.getLoopCodeBlock(n);

    if (isCallbackLoop(n)) {
      Node call = n.getFirstChild();
      for(Node c = call.getFirstChild(); c != null; c = c.getNext()) {
        if (c.isFunction()) {
          return c.getLastChild();
        }
      }
    }

    return null;
  }

  /**
   * Returns the condition expression of a loop or the object a loop callback function is applied to
   * @param n Loop Node
   * @return condition expression or object
   */
  private Node getConditionExpression(Node n) {
    if (NodeUtil.isLoopStructure(n))
      return NodeUtil.getConditionExpression(n);

    if (isCallbackLoop(n)) {
      // EXPR_RESULT <- CALL <- NAME
      return n.getFirstChild().getFirstChild().getFirstChild();
    }

    return null;
  }

  /**
   * identifyNestedLoops traverses all nodes from n to identify loops and collect
   * those that are nested at least inside another loop.
   * @param n Node to start with
   * @param inLoop boolean to identify if already inside loop (for recursion)
   * @return
   */
  private boolean identifyNestedLoops(Node n, boolean inLoop) {
    for(Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      if (isLoop(c)) {
        if (inLoop) {
          currentNodeList.add(c);
          nestedLoops.add(currentNodeList);
          return true;
        }
        currentNodeList = new ArrayList<>();
        currentNodeList.add(c);

        identifyNestedLoops(getLoopCodeBlock(c), true);
      }
      else if(NodeUtil.isControlStructure(c)) {
        Node s = c.getFirstChild();
        while(s != null && !NodeUtil.isControlStructureCodeBlock(c, s)) {
          s = s.getNext();
        }
        identifyNestedLoops(s, inLoop);
      }
    }
    return false;
  }

  /**
   * containsTaintedVar recursively checks whether a supplied loop node in any way depends on a
   * tainted variable
   * @param n Loop node
   * @param taintAnalysis TaintAnalysis with tainted variable information
   * @return if loop is dependent on tainted variable
   */
  private boolean containsTaintedVar(Node n, TaintAnalysis taintAnalysis) {
    checkNotNull(n);
    if (n.getToken() == Token.NAME) {
      int varIndex = taintAnalysis.getVarIndex(n.getString());
      if (varIndex != -1)
        return taintAnalysis.getExitLatticeElement().isTainted(varIndex);
      else
        return false;
    } else {
      for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
        if (containsTaintedVar(c, taintAnalysis)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public void enterScope(NodeTraversal t) {
    super.enterScope(t);

    Scope blockScope = t.getScope();
    Scope functionScope = blockScope.getParent();

    // currently limit loop complexity detection to function scopes
    if (t.inFunctionBlockScope()) {

      TaintAnalysis taintAnalysis = new TaintAnalysis(t.getControlFlowGraph(), functionScope, blockScope,
          true, compiler, new Es6SyntacticScopeCreator(compiler));
      taintAnalysis.analyze();

      // identify all occurring nested loops in function
      identifyNestedLoops(t.getScopeRoot(), false);

      // check all loop condition expressions for tainted variables
      for (int i = 0; i < nestedLoops.size(); i++) {
        boolean isTainted = true;

        for (int j = 0; j < nestedLoops.get(i).size(); j++) {
          Node expr = getConditionExpression(nestedLoops.get(i).get(j));

          if(expr != null)
            isTainted &= containsTaintedVar(expr, taintAnalysis);
        }

        if(isTainted)
          t.report(nestedLoops.get(i).get(0), INPUT_DEPENDENT_NESTED_LOOP, "");
      }
    }
  }

  @Override
  public void exitScope(NodeTraversal t) {
    super.exitScope(t);
  }
}
