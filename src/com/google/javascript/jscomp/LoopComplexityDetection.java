package com.google.javascript.jscomp;

import com.google.javascript.jscomp.graph.DiGraph;
import com.google.javascript.rhino.Node;

public class LoopComplexityDetection extends NodeTraversal.AbstractScopedCallback implements CompilerPass {

    private final AbstractCompiler compiler;

    public LoopComplexityDetection(AbstractCompiler compiler) {
        this.compiler = compiler;
    }

    public void process(Node externs, Node root) {
        NodeTraversal.traverseEs6(this.compiler, root, this);
    }

    public void visit(NodeTraversal t, Node n, Node parent) {

    }

    @Override
    public void enterScope(NodeTraversal t) {
        super.enterScope(t);

        LoopComplexity loopComplexity = new LoopComplexity(t.getControlFlowGraph());
        loopComplexity.analyze();
    }

    @Override
    public void exitScope(NodeTraversal t) {
        super.exitScope(t);
    }
}
