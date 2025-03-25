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

public class ArraysCheck extends AnalysisVisitor {

    @Override
    public void buildVisitor() {

        addVisit(Kind.ARRAY_ACCESS, this::visitArrayAccess);

        addVisit(Kind.ARRAY_INITIALIZATION_EXPR, this::visitArrayInit);


    }

    private Void visitArrayAccess(JmmNode node, SymbolTable table) {

        JmmNode arrayExpr = node.getChild(0);
        JmmNode indexExpr = node.getChild(1);

        List<Object> arrayType = getOperandType(arrayExpr, table);
        String actualType = arrayType.getFirst().toString();
        String isArray = arrayType.get(1).toString();

        List<Object> indexType = getOperandType(indexExpr, table);
        String indexActualType = indexType.getFirst().toString();
        String indexIsArray = indexType.get(1).toString();

        if (isArray.equals("false")) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    node.getLine(),
                    node.getColumn(),
                    "Invalid array access: Expected an array but found: " + actualType,
                    null
            ));
        }

        if (!indexActualType.equals("int") || indexIsArray.equals("true")) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    indexExpr.getLine(),
                    indexExpr.getColumn(),
                    "Invalid array index: Expected type 'int', but found: " + indexActualType,
                    null
            ));
        }

        return null;
    }
    private Void visitArrayInit(JmmNode node, SymbolTable table) {

        var elements = node.getChildren();

        for (JmmNode element : elements) {
            List<Object> arrayType = getOperandType(element, table);

            if (arrayType.isEmpty() || arrayType.getFirst() == null) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        element.getLine(),
                        element.getColumn(),
                        "Invalid array initialization: could not determine element type.",
                        null
                ));
                continue;
            }

            String actualType = arrayType.getFirst().toString();

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

    private List<Object> getOperandType(JmmNode operand, SymbolTable table) {
        List<Object> result = new ArrayList<>();

        if (operand.isInstance(Kind.VAR_REF_EXPR)) {
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
