package com.google.javascript.jscomp;

import com.google.javascript.jscomp.parsing.parser.FeatureSet;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.javascript.jscomp.parsing.parser.FeatureSet.ES8_MODULES;

class LoopComplexityRunner extends CommandLineRunner {
  LoopComplexityRunner(String[] args) {
    super(args);
  }

  @Override
  protected CompilerOptions createOptions() {
    CompilerOptions options = new CompilerOptions();
    Compiler compiler = this.getCompiler();
    options.setOutputJs(CompilerOptions.OutputJs.NONE);
    LoopComplexityDetection loopComplexityDetection = new LoopComplexityDetection(compiler);
    options.addCustomPass(CustomPassExecutionTime.BEFORE_OPTIMIZATIONS, loopComplexityDetection);
    return options;
  }

  public static void main(String[] args) {
    // turn of PhaseOptimizer logger to omit warnings that it is
    // disabled
    Logger pl = Logger.getLogger(PhaseOptimizer.class.getName());
    pl.setLevel(Level.OFF);

    LoopComplexityRunner runner = new LoopComplexityRunner(args);
    if (runner.shouldRunCompiler()) {
      runner.run();
    }
    if (runner.hasErrors()) {
      System.exit(-1);
    }
  }
}
