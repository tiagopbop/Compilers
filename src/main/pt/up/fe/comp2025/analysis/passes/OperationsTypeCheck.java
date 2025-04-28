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

        addVisit(Kind.BINARY_EXPR, this::visitBinExpr);

        addVisit(Kind.WHILE_STMT, this::visitWhileStmt);

        addVisit(Kind.IF_STMT, this::visitIfStmt);

    }
    private Void visitBinExpr(JmmNode node, SymbolTable table) {
        String op = node.get("operation");

        if (node.getNumChildren() < 2) return null;

        JmmNode leftOperand = node.getChild(0);
        JmmNode rightOperand = node.getChild(1);

        List<Object> leftType = getOperandType(leftOperand, table);
        String leftTypeName = leftType.get(0).toString();
        String isLeftArray = leftType.get(1).toString();

        List<Object> rightType = getOperandType(rightOperand, table);
        String rightTypeName = rightType.getFirst().toString();
        String isRightArray = rightType.get(1).toString();

        if (op.equals("*") || op.equals("/") || op.equals("-") || op.equals("+")) {
            if (!leftTypeName.equals("int") || !rightTypeName.equals("int")) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        node.getLine(),
                        node.getColumn(),
                        "Operations require both operands to be of type int, but found: " + leftTypeName + " and " + rightTypeName,
                        null
                ));
            } else if (!isLeftArray.equals("false") || !isRightArray.equals("false")) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        node.getLine(),
                        node.getColumn(),
                        "Operations require both operands to not be an array, but found at least one",
                        null
                ));
            }
        }

        return null;
    }

    private Void visitWhileStmt(JmmNode node, SymbolTable table) {
        if (node.getNumChildren() == 0) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    node.getLine(),
                    node.getColumn(),
                    "While statement is missing condition or body",
                    null
            ));
            return null;
        }

        if (node.getNumChildren() > 0) {
            JmmNode condition = node.getChild(0);

            List<Object> conditionType = getOperandType(condition, table);
            if (conditionType.isEmpty()) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        condition.getLine(),
                        condition.getColumn(),
                        "Could not determine type of while condition",
                        null
                ));
                return null;
            }

            String type = conditionType.get(0).toString();
            String isArray = conditionType.size() > 1 ? conditionType.get(1).toString() : "false";

            if (!type.equals("boolean") || isArray.equals("true")) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        condition.getLine(),
                        condition.getColumn(),
                        "Condition in 'while' must be a boolean, but found: " + type + (isArray.equals("true") ? " array" : " non array"),
                        null
                ));
            }
        }

        return null;
    }

    private Void visitIfStmt(JmmNode node, SymbolTable table) {
        if (node.getNumChildren() == 0) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    node.getLine(),
                    node.getColumn(),
                    "If statement is missing condition",
                    null
            ));
            return null;
        }

        JmmNode condition = node.getChild(0);

        List<Object> conditionType = getOperandType(condition, table);

        if (conditionType.isEmpty()) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    condition.getLine(),
                    condition.getColumn(),
                    "Could not determine type of if condition",
                    null
            ));
            return null;
        }

        String type = conditionType.get(0).toString();
        String isArray = conditionType.size() > 1 ? conditionType.get(1).toString() : "false";

        if (!type.equals("boolean") || isArray.equals("true")) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    condition.getLine(),
                    condition.getColumn(),
                    "Condition in 'If' must be a boolean, but found: " + type + (isArray.equals("true") ? " array" : " non array"),
                    null
            ));
        }

        return null;
    }

    private List<Object> getOperandType(JmmNode operand, SymbolTable table) {
        List<Object> result = new ArrayList<>();

        if (operand.isInstance(Kind.PARENTHESIS_EXPR)) {
            return getOperandType(operand.getChild(0), table);

        } else if (operand.isInstance(Kind.VAR_REF_EXPR)) {
            String varName = operand.get("name");

            for (String method : table.getMethods()) {
                for (Symbol symbol : table.getLocalVariables(method)) {
                    if (symbol.getName().equals(varName)) {
                        result.add(symbol.getType().getName());
                        result.add(symbol.getType().isArray()); //  array flag
                        return result;
                    }
                }
            }

            for (Symbol field : table.getFields()) {
                if (field.getName().equals(varName)) {
                    result.add(field.getType().getName());
                    result.add(field.getType().isArray()); // array flag
                    return result;
                }
            }

        } else if (operand.isInstance(Kind.BINARY_EXPR)) {

            String op = operand.get("operation");

            if (op.equals("+") ||  op.equals("-") ||  op.equals("*") ||  op.equals("/")) {
                result.add("int");
                result.add(false);
            } else {
                result.add("boolean");
                result.add(false);
            }

        } else if (operand.isInstance(Kind.BOOLEAN_EXPR)) {

            List<Object> left = getOperandType(operand.getChild(0), table);
            List<Object> right = getOperandType(operand.getChild(1), table);

            if (left.isEmpty() || right.isEmpty()) return result;

            result.add("boolean");
            result.add(false);

        } else if (operand.isInstance(Kind.INTEGER_LITERAL)) {
            result.add("int");
            result.add(false);
        } else if (operand.isInstance(Kind.BOOLEAN_LITERAL)) {
            result.add("boolean");
            result.add(false);
        }

        return result;
    }

}