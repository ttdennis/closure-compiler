package com.google.javascript.jscomp;

import com.google.javascript.jscomp.graph.DiGraph;
import com.google.javascript.rhino.Node;

public class LoopComplexityDetection extends NodeTraversal.AbstractScopedCallback implements CompilerPass {

    private final AbstractCompiler compiler;
    private Integer loopLevel = 0;

    public LoopComplexityDetection(AbstractCompiler compiler) {
        this.compiler = compiler;
    }

    public void process(Node externs, Node root) {
        NodeTraversal.traverseEs6(this.compiler, root, this);
    }

    public void visit(NodeTraversal t, Node n, Node parent) {
        detectLoops(n);
    }

    private void detectLoops(Node n) {
        switch (n.getToken()) {
            case WHILE:
            case DO:
            case FOR:
            case FOR_IN:
            case FOR_OF:
                this.loopLevel++;
                printLoopLevel();

                Node block = n.getLastChild();
                for (int i = 0; i < block.getChildCount(); i++) {
                    detectLoops(block.getChildAtIndex(i));
                }

                this.loopLevel--;
        }
    }

    private void printLoopLevel() {
        if(this.loopLevel > 1) {
            System.out.printf("Nested loop detected! Level: %d\n", this.loopLevel);
        }
    }

    @Override
    public void enterScope(NodeTraversal t) {
        super.enterScope(t);
    }

    @Override
    public void exitScope(NodeTraversal t) {
        super.exitScope(t);
    }
}
