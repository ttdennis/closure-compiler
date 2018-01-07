package com.google.javascript.jscomp;

import com.google.javascript.jscomp.parsing.parser.FeatureSet;

import java.util.ArrayList;
import java.util.List;

import static com.google.javascript.jscomp.parsing.parser.FeatureSet.ES8_MODULES;

class LoopComplexityRunner extends CommandLineRunner {
  LoopComplexityRunner(String[] args) {
    super(args);
  }

  @Override
  protected CompilerOptions createOptions() {
    CompilerOptions options = super.createOptions();
    Compiler compiler = this.getCompiler();
    LoopComplexityDetection loopComplexityDetection = new LoopComplexityDetection(compiler);
    options.addCustomPass(CustomPassExecutionTime.BEFORE_OPTIMIZATIONS, loopComplexityDetection);
    return options;
  }

  public static void main(String[] args) {
    LoopComplexityRunner runner = new LoopComplexityRunner(args);
    if (runner.shouldRunCompiler()) {
      runner.run();
    }
    if (runner.hasErrors()) {
      System.exit(-1);
    }
  }
}
