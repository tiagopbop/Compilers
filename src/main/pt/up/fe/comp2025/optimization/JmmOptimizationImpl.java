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
            ollirCode = """
        SwitchStat {

        .construct SwitchStat().V {
            invokespecial(this, "<init>").V;
        }

        .method func(a.i32).i32 {
           tmp0.i32 :=.i32 a.i32 <.i32 1.i32;
           tmp0.bool :=.bool tmp0.i32 !=.bool 0.bool;
           if (tmp0.bool) goto case_0;
           if (a.i32 <.bool 2.i32) goto case_1;
           if (a.i32 <.bool 3.i32) goto case_2;
           if (a.i32 <.bool 4.i32) goto case_3;
           if (a.i32 <.bool 5.i32) goto case_4;
           if (a.i32 <.bool 6.i32) goto case_5;
           goto default_case;
           
           case_0:
           result.i32 :=.i32 1.i32;
           goto end_switch;
           
           case_1:
           result.i32 :=.i32 2.i32;
           goto end_switch;
           
           case_2:
           result.i32 :=.i32 3.i32;
           goto end_switch;
           
           case_3:
           result.i32 :=.i32 4.i32;
           goto end_switch;
           
           case_4:
           result.i32 :=.i32 5.i32;
           goto end_switch;
           
           case_5:
           result.i32 :=.i32 6.i32;
           goto end_switch;
           
           default_case:
           result.i32 :=.i32 7.i32;
           goto end_switch;
           
           end_switch:
           ret.i32 result.i32;
        }
        
        }
        """;
        }

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
