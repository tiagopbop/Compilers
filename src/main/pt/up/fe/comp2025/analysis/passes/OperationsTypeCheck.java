package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;


public class OperationsTypeCheck extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        System.out.println("Registering visitor for: " + Kind.BINARY_EXPR);
        addVisit(Kind.BINARY_EXPR, this::visitBinExpr);
    }

    private Void visitBinExpr(JmmNode node, SymbolTable table) {
        String op = node.get("operation");

        JmmNode leftOperand = node.getChild(0);
        JmmNode rightOperand = node.getChild(1);

        System.out.println("Checking Binary Operation: " + op);
        System.out.println("Left Operand: " + leftOperand);
        System.out.println("Right Operand: " + rightOperand);

        String leftType = leftOperand.isInstance(Kind.VAR_REF_EXPR) ? leftOperand.get("name") : leftOperand.get("value");
        String rightType = rightOperand.isInstance(Kind.VAR_REF_EXPR) ? rightOperand.get("name") : rightOperand.get("value");

        System.out.println("Left Type: " + leftType);
        System.out.println("Right Type: " + rightType);

        // Check if operation is multiplication (*)
        if (op.equals("*") || op.equals("/") || op.equals("-") || op.equals("+")) {
            if (leftType == null || rightType == null) {
                System.out.println("ERROR: One or both operand types are null!");
            }
            if (!leftType.equals("int") || !rightType.equals("int")) {
                addReport(Report.newError(
                        Stage.SEMANTIC,  // Use this instead of ReportType.ERROR
                        node.getLine(),
                        node.getColumn(),
                        "Operations require both operands to be of type int, but found: " + leftType + " and " + rightType,
                        null
                ));
            }
        }

        return null;
    }
}