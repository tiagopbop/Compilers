package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp2025.CompilerConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class JmmOptimizationImpl implements JmmOptimization {

    @Override
    public OllirResult toOllir(JmmSemanticsResult semanticsResult) {
        var visitor = new OllirGeneratorVisitor(semanticsResult.getSymbolTable());
        var ollirCode = visitor.visit(semanticsResult.getRootNode());

        var result = new OllirResult(semanticsResult, ollirCode, Collections.emptyList());
        result.getConfig().putAll(semanticsResult.getConfig());
        return result;
    }

    @Override
    public JmmSemanticsResult optimize(JmmSemanticsResult semanticsResult) {
        if (!CompilerConfig.getOptimize(semanticsResult.getConfig())) {
            return semanticsResult;
        }

        List<Report> reports = new ArrayList<>();
        JmmNode rootNode = semanticsResult.getRootNode();

        boolean globalChanged = true;
        int maxPasses = 10; // prevent infinite loops
        int passCount = 0;

        while (globalChanged && passCount < maxPasses) {
            globalChanged = false;
            passCount++;
            ConstantPropagationVisitor propVisitor = new ConstantPropagationVisitor();
            boolean propChanged = propVisitor.visit(rootNode);
            globalChanged |= propChanged;
            ConstantFoldingVisitor folder = new ConstantFoldingVisitor();
            boolean foldChanged = folder.visit(rootNode);
            globalChanged |= foldChanged;
            DeadCodeEliminationVisitor dceVisitor = new DeadCodeEliminationVisitor();
            boolean dceChanged = dceVisitor.visit(rootNode);
            globalChanged |= dceChanged;
        }
        return new JmmSemanticsResult(semanticsResult, reports);
    }

    @Override
    public OllirResult optimize(OllirResult ollirResult) {
        int maxRegs = CompilerConfig.getRegisterAllocation(ollirResult.getConfig());

        if (maxRegs < 0) {
            return ollirResult;
        }

        new RegisterAllocation(ollirResult, maxRegs).allocateRegisters();
        return ollirResult;
    }


}