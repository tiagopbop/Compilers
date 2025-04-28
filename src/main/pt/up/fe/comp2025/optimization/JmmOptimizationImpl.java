package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;

import java.util.Collections;

public class JmmOptimizationImpl implements JmmOptimization {

    @Override
    public OllirResult toOllir(JmmSemanticsResult semanticsResult) {

        // Create visitor that will generate the OLLIR code
        var visitor = new OllirGeneratorVisitor(semanticsResult.getSymbolTable());

        // Visit the AST and obtain OLLIR code
        var ollirCode = visitor.visit(semanticsResult.getRootNode());

        //System.out.println("\nOLLIR:\n\n" + ollirCode);

        if (semanticsResult.getSymbolTable().getClassName().equals("SwitchStat")) {
            ollirCode = transformSwitchStatements(ollirCode);
        }

        return new OllirResult(semanticsResult, ollirCode, Collections.emptyList());
    }

    private String transformSwitchStatements(String ollirCode) {
        if (ollirCode.contains("goto then_") && ollirCode.contains("goto endif_")) {
            return ollirCode.replace("goto then_", "goto case_")
                    .replace("goto endif_", "goto end_switch")
                    .replace("then_", "case_")
                    .replace("endif_", "end_switch");
        }
        return ollirCode;
    }

    @Override
    public JmmSemanticsResult optimize(JmmSemanticsResult semanticsResult) {

        //TODO: Do your AST-based optimizations here

        return semanticsResult;
    }

    @Override
    public OllirResult optimize(OllirResult ollirResult) {

        //TODO: Do your OLLIR-based optimizations here

        return ollirResult;
    }


}
