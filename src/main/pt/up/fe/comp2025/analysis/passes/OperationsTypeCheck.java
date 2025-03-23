package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;

import java.util.List;


public class OperationsTypeCheck extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        System.out.println("Registering visitor for: " + Kind.BINARY_EXPR);
        addVisit(Kind.BINARY_EXPR, this::visitBinExpr);

        System.out.println("Registering visitor for: " + Kind.ARRAY_ACCESS);
        addVisit(Kind.ARRAY_ACCESS, this::visitArrayAccess);
    }
    private Void visitBinExpr(JmmNode node, SymbolTable table) {
        String op = node.get("operation");

        JmmNode leftOperand = node.getChild(0);
        JmmNode rightOperand = node.getChild(1);

        System.out.println("Checking Binary Operation: " + op);
        System.out.println("Left Operand: " + leftOperand);
        System.out.println("Right Operand: " + rightOperand);

        String leftType = getOperandType(leftOperand, table);
        String rightType = getOperandType(rightOperand, table);

        System.out.println("Left Type: " + leftType);
        System.out.println("Right Type: " + rightType);

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

    private String getOperandType(JmmNode operand, SymbolTable table) {
        if (operand.isInstance(Kind.VAR_REF_EXPR)) {
            String varName = operand.get("name");


            for (String method : table.getMethods()) {
                List<Symbol> localSymbols = table.getLocalVariables(method);
                for (Symbol symbol : localSymbols) {
                    if (symbol.getName().equals(varName)) {
                        return symbol.getType().toString();
                    }
                }
            }

            for (Symbol field : table.getFields()) {
                if (field.getName().equals(varName)) {
                    return field.getType().toString();
                }
            }
        } else if (operand.isInstance(Kind.INTEGER_LITERAL)) {
            String value = operand.get("value");
            if (value.equals("true") || value.equals("false")) {
                return "bool";
            } else {
                try {
                    Integer.parseInt(value);
                    return "int";
                } catch (NumberFormatException e) {
                }
            }
        }
        return null;

    }

    private Void visitArrayAccess(JmmNode node, SymbolTable table) {

        JmmNode arrayExpr = node.getChild(0);
        JmmNode indexExpr = node.getChild(1);

        String arrayType = getOperandType(arrayExpr, table);
        if (!arrayType.endsWith("[]")) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    node.getLine(),
                    node.getColumn(),
                    "Invalid array access: Expected an array but found: " + arrayType,
                    null
            ));
        }

        return null;
    }



}