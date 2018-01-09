package com.google.javascript.jscomp;

import com.google.javascript.jscomp.graph.DiGraph;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.ArrayList;
import java.util.List;

public class LoopComplexityDetection extends NodeTraversal.AbstractScopedCallback implements CompilerPass {

  private final AbstractCompiler compiler;
  private Integer loopLevel = 0;
  private List<Node> nestedLoops;

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
          return true;
        }
        if (identifyNestedLoops(NodeUtil.getLoopCodeBlock(c), true)) {
          nestedLoops.add(c);
        }
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

  @Override
  public void enterScope(NodeTraversal t) {
    super.enterScope(t);

    Scope blockScope = t.getScope();
    Scope functionScope = blockScope.getParent();

    // currently limit loop complexity detection to function scopes
    if (t.inFunctionBlockScope()) {

      // identify all occurring nested loops in function
      identifyNestedLoops(t.getScopeRoot(), false);
      if (nestedLoops != null) {
        System.out.println("Nested loop(s) detected:");
        for (int i = 0; i < nestedLoops.size(); i++) {
          System.out.println("\tNested loop in line " + nestedLoops.get(i).getLineno());
          System.out.println("\t" + compiler.toSource(nestedLoops.get(i)));
        }
      }

      TaintAnalysis taintAnalysis = new TaintAnalysis(t.getControlFlowGraph(), functionScope, blockScope,
          true, compiler, new Es6SyntacticScopeCreator(compiler));
      taintAnalysis.analyze();
    }
  }

  @Override
  public void exitScope(NodeTraversal t) {
    super.exitScope(t);
  }
}
