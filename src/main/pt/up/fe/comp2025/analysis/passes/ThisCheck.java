package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.ast.Kind;

public class ThisCheck extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(Kind.THIS_EXPR, this::visitThisExpr);
    }

    private Void visitThisExpr(JmmNode node, SymbolTable table) {
        JmmNode current = node;

        while (current != null && !current.getKind().equals("MethodDeclaration") && !current.getKind().equals("MainMethodDeclaration")) {
            current = current.getParent();
        }

        if (current == null || current.getKind().equals("MainMethodDeclaration")) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    node.getLine(),
                    node.getColumn(),
                    "'this' cannot be used in a static method",
                    null
            ));
        }

        return null;
    }
}
