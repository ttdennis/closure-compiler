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

  public LoopComplexityDetection(AbstractCompiler compiler) {

    this.compiler = compiler;
    nestedLoops = new ArrayList<>();
  }

  public void process(Node externs, Node root) { NodeTraversal.traverseEs6(this.compiler, root, this); }

  public void visit(NodeTraversal t, Node n, Node parent) {}

  private boolean identifyNestedLoops(Node n, boolean inLoop) {
    for(Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      if (NodeUtil.isLoopStructure(c)) {
        if (inLoop) {
          currentNodeList.add(c);
          nestedLoops.add(currentNodeList);
          return true;
        }
        currentNodeList = new ArrayList<>();
        currentNodeList.add(c);

        identifyNestedLoops(NodeUtil.getLoopCodeBlock(c), true);
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
        System.out.println("Nested Loop in line " + nestedLoops.get(i).get(0).getLineno());

        boolean isTainted = true;
        for (int j = 0; j < nestedLoops.get(i).size(); j++) {
          Node expr = NodeUtil.getConditionExpression(nestedLoops.get(i).get(j));
          // get rhs of expression
          Node rhs = expr.getLastChild() != null ? expr.getLastChild() : expr;

          isTainted &= containsTaintedVar(rhs, taintAnalysis);
        }

        if(isTainted) {
          System.out.println("\tLoop is parameter dependent");
          System.out.println("\t" + compiler.toSource(nestedLoops.get(i).get(0)));
        }
      }
    }
  }

  @Override
  public void exitScope(NodeTraversal t) {
    super.exitScope(t);
  }
}
