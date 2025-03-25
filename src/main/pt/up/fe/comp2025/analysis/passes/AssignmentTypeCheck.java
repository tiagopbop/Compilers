package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;

public class AssignmentTypeCheck extends AnalysisVisitor {

    private TypeUtils typeUtils;

    @Override
    public void buildVisitor() {
        addVisit(Kind.ASSIGN_STMT, this::visitAssignStmt);
    }

    private Void visitAssignStmt(JmmNode node, SymbolTable table) {
        if (typeUtils == null) {
            typeUtils = new TypeUtils(table);
        }

        JmmNode left = node.getChild(0);
        JmmNode right = node.getChild(1);

        Type leftType = typeUtils.getExprType(left);
        Type rightType = typeUtils.getExprType(right);

        String currentClass = table.getClassName();
        String superClass = table.getSuper() == null ? "" : table.getSuper();

        boolean extended = (rightType.getName().equals(currentClass) && leftType.getName().equals(superClass)) || (leftType.getName().equals(currentClass) && rightType.getName().equals(superClass));

        boolean imported = table.getImports().stream().anyMatch(imp -> imp.endsWith(leftType.getName())) && table.getImports().stream().anyMatch(imp -> imp.endsWith(rightType.getName()));

        if ((!leftType.getName().equals(rightType.getName()) || leftType.isArray() != rightType.isArray()) && !imported && !extended) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    node.getLine(),
                    node.getColumn(),
                    "Type mismatch in assignment: expected '" + leftType + "' but found '" + rightType + "'",
                    null
            ));
        }

        return null;
    }

}