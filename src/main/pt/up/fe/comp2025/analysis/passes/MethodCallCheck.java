package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.ast.Kind;

public class MethodCallCheck extends AnalysisVisitor {
    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_CALL, this::visitMethodCall);
    }

    private Void visitMethodCall(JmmNode node, SymbolTable table) {
        String methodName = node.get("name");

        boolean declared = table.getMethods().contains(methodName);
        boolean assumed = !table.getImports().isEmpty() || table.getSuper() != null;

        if (!declared && !assumed) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    node.getLine(),
                    node.getColumn(),
                    "Call to undeclared method: '" + methodName + "'",
                    null
            ));
        }

        return null;
    }

}
