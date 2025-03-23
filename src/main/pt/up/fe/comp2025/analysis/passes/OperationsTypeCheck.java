package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


public class OperationsTypeCheck extends AnalysisVisitor {



    @Override
    public void buildVisitor() {

        System.out.println("Registering visitor for: " + Kind.BINARY_EXPR);
        addVisit(Kind.BINARY_EXPR, this::visitBinExpr);

        System.out.println("Registering visitor for: " + Kind.ARRAY_ACCESS);
        addVisit(Kind.ARRAY_ACCESS, this::visitArrayAccess);

        System.out.println("Initializing array for : " + Kind.ARRAY_INITIALIZATION_EXPR);
        addVisit(Kind.ARRAY_INITIALIZATION_EXPR, this::visitArrayInit);




    }
    private Void visitBinExpr(JmmNode node, SymbolTable table) {
        String op = node.get("operation");

        JmmNode leftOperand = node.getChild(0);
        JmmNode rightOperand = node.getChild(1);

        System.out.println("Checking Binary Operation: " + op);
        System.out.println("Left Operand: " + leftOperand);
        System.out.println("Right Operand: " + rightOperand);

        List<Object> leftType = getOperandType(leftOperand, table);
        String ltype = leftType.get(0).toString();
        String isLarray = leftType.get(1).toString();
        List<Object> rightType = getOperandType(rightOperand, table);
        String rtype = rightType.getFirst().toString();
        String isRarray = rightType.get(1).toString();

        System.out.println("Left Type: " + leftType);
        System.out.println("Right Type: " + rightType);

        if (op.equals("*") || op.equals("/") || op.equals("-") || op.equals("+")) {
            if (leftType == null || rightType == null) {
                System.out.println("ERROR: One or both operand types are null!");
            }
            if (!leftType.equals("int") || !rightType.equals("int")) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        node.getLine(),
                        node.getColumn(),
                        "Operations require both operands to be of type int, but found: " + ltype + ',' + isLarray + " and " + rtype + ',' + isRarray,
                        null
                ));
            }
        }

        return null;
    }

    private List<Object> getOperandType(JmmNode operand, SymbolTable table) {
        List<Object> result = new ArrayList<>();

        if (operand.isInstance(Kind.VAR_REF_EXPR)) {
            String varName = operand.get("name");

            for (String method : table.getMethods()) {
                List<Symbol> localSymbols = table.getLocalVariables(method);
                for (Symbol symbol : localSymbols) {
                    if (symbol.getName().equals(varName)) {
                        result.add(symbol.getType().toString());
                        result.add(symbol.getType().isArray()); //  array flag
                        return result;
                    }
                }
            }

            for (Symbol field : table.getFields()) {
                if (field.getName().equals(varName)) {
                    result.add(field.getType().toString());
                    result.add(field.getType().isArray()); // array flag
                    return result;
                }
            }
        } else if (operand.isInstance(Kind.INTEGER_LITERAL)) {
            String value = operand.get("value");
            if (value.equals("true") || value.equals("false")) {
                result.add("bool");
                result.add(false); // not an array
            } else {
                try {
                    Integer.parseInt(value);
                    result.add("int");
                    result.add(false); // not an array
                } catch (NumberFormatException e) {
                    result.add(null); // undefined or invalid type
                    result.add(false);
                }
            }
        }

        return result;
    }

    private Void visitArrayAccess(JmmNode node, SymbolTable table) {

        JmmNode arrayExpr = node.getChild(0);
        JmmNode indexExpr = node.getChild(1);

        List<Object> arrayType = getOperandType(arrayExpr, table);
        String actualType = arrayType.getFirst().toString();
        String isArray = arrayType.get(1).toString();
        assert arrayType != null;
        if (isArray.equals("false")) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    node.getLine(),
                    node.getColumn(),
                    "Invalid array access: Expected an array but found: " + actualType,
                    null
            ));
        }

        return null;
    }
    private Void visitArrayInit(JmmNode node, SymbolTable table) {

        System.out.println("hahahahhahahahahahahhahahahahhahahahahahhaha!");

        var elements = node.getChildren();

        for (JmmNode element : elements) {
            List<Object> arrayType = getOperandType(element, table);
            String actualType = arrayType.getFirst().toString();
            String isArray = arrayType.get(1).toString();

            if (!Objects.equals(actualType, "int")) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        element.getLine(),
                        element.getColumn(),
                        "Invalid array initialization: all elements must be of type int, but found: " + actualType,
                        null
                ));
            }
        }

        return null;
    }




}