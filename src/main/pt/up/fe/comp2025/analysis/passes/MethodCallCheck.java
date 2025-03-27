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
            return null;
        }

        if (!declared) return null;

        var args = node.getChildren();
        var expected = table.getParameters(methodName);

        if (expected == null) return null;

        boolean hasVararg = !expected.isEmpty() && expected.getLast().getType().isArray();
        int fixedCount = hasVararg ? expected.size() - 1 : expected.size();

        if ((!hasVararg && args.size() != expected.size()) || (hasVararg && args.size() < fixedCount)) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    node.getLine(),
                    node.getColumn(),
                    "Incorrect number of arguments for method '" + methodName + "'",
                    null
            ));
        }

        return null;
    }

}
