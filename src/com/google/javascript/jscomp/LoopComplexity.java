/*
 * Copyright 2017 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

class LoopComplexity extends DataFlowAnalysis<Node, LoopComplexity.LoopComplexityLattice> {

    private static class LoopComplexityJoinOp implements JoinOp<LoopComplexityLattice> {
        @Override
        public LoopComplexityLattice apply(List<LoopComplexityLattice> in) {
            return in.get(0);
        }
    }

    LoopComplexity(ControlFlowGraph<Node> targetCfg) {
        super(targetCfg, new LoopComplexityJoinOp());
    }

    @Override
    boolean isForward() {
        return true;
    }

    @Override
    LoopComplexityLattice flowThrough(Node node, LoopComplexityLattice input) {
        return input;
    }

    @Override
    LoopComplexityLattice createInitialEstimateLattice() {
        return new LoopComplexityLattice(30);
    }

    @Override
    LoopComplexityLattice createEntryLattice() {
        return new LoopComplexityLattice(30);
    }

    static class LoopComplexityLattice implements LatticeElement {

        private final BitSet loopDependencies;

        private LoopComplexityLattice(int num) {
            this.loopDependencies = new BitSet(num);
        }

        @Override
        public boolean equals(Object other) {
            return (other instanceof LoopComplexityLattice) 
                && this.loopDependencies.equals(((LoopComplexityLattice) other).loopDependencies);
        }

        public boolean loopDependsOnVariable(int index) {
            return loopDependencies.get(index);
        }

        @Override
        public String toString() {
          return loopDependencies.toString();
        }

        @Override
        public int hashCode() {
          return loopDependencies.hashCode();
        }
    }

}
