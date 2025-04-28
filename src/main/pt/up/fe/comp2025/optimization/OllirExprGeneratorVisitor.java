package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp2025.ast.TypeUtils;

import java.util.ArrayList;

import static pt.up.fe.comp2025.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are expressions.
 */
public class OllirExprGeneratorVisitor extends PreorderJmmVisitor<Void, OllirExprResult> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";

    private final SymbolTable table;

    private final TypeUtils types;
    private final OptUtils ollirTypes;


    public OllirExprGeneratorVisitor(SymbolTable table) {
        this.table = table;
        this.types = new TypeUtils(table);
        this.ollirTypes = new OptUtils(types);
    }


    @Override
    protected void buildVisitor() {
        addVisit(VAR_REF_EXPR, this::visitVarRef);
        addVisit(BINARY_EXPR, this::visitBinExpr);
        addVisit(ARRAY_ACCESS, this::visitArrayAccess);
        addVisit(INTEGER_LITERAL, this::visitInteger);
        addVisit(ARRAY_INITIALIZATION_EXPR, this::visitArrayInit);
        addVisit(BOOLEAN_EXPR, this::visitBooleanExpr);
        addVisit(BOOLEAN_LITERAL, this::visitBooleanLiteral);
        addVisit(METHOD_CALL, this::visitMethodCall);

        setDefaultVisit(this::defaultVisit);
    }

    private OllirExprResult visitMethodCall(JmmNode node, Void unused) {
        StringBuilder computation = new StringBuilder();

        String fullMethodName = node.get("name");
        boolean isDotNotation = fullMethodName.contains(".");

        String objectName = null;
        String methodName = fullMethodName;

        if (isDotNotation) {
            String[] parts = fullMethodName.split("\\.", 2);
            objectName = parts[0];
            methodName = parts[1];
        }

        StringBuilder argsComputation = new StringBuilder();
        StringBuilder argsCode = new StringBuilder();

        for (int i = 0; i < node.getNumChildren(); i++) {
            OllirExprResult arg = visit(node.getChild(i));
            argsComputation.append(arg.getComputation());

            if (i > 0) {
                argsCode.append(", ");
            }
            argsCode.append(arg.getCode());
        }

        computation.append(argsComputation);

        if (isDotNotation) {
            computation.append("invokestatic(")
                    .append(objectName)
                    .append(", \"")
                    .append(methodName)
                    .append("\")(")
                    .append(argsCode)
                    .append(").V;\n");
        } else {
            String returnTypeStr = ".V"; // Default to void
            if (table.getMethods().contains(methodName)) {
                Type returnType = table.getReturnType(methodName);
                returnTypeStr = ollirTypes.toOllirType(returnType);
            } else {
                returnTypeStr = ".i32";
            }

            String tempVar = ollirTypes.nextTemp();
            String resultVar = tempVar + returnTypeStr;

            if (!returnTypeStr.equals(".V")) {
                computation.append(resultVar).append(" :=").append(returnTypeStr).append(" ");
            }

            computation.append("invokestatic(")
                    .append(table.getClassName())
                    .append(", \"")
                    .append(methodName)
                    .append("\")(")
                    .append(argsCode)
                    .append(")")
                    .append(returnTypeStr)
                    .append(";\n");

            if (returnTypeStr.equals(".V")) {
                return new OllirExprResult("", computation.toString());
            } else {
                return new OllirExprResult(resultVar, computation.toString());
            }
        }

        return new OllirExprResult("", computation.toString());
    }

    private OllirExprResult visitBooleanExpr(JmmNode node, Void unused) {
        var left = visit(node.getChild(0));
        var right = visit(node.getChild(1));

        StringBuilder computation = new StringBuilder();

        computation.append(left.getComputation());
        computation.append(right.getComputation());

        String op = node.get("operation");
        String ollirOp = switch(op) {
            case "&&" -> "&&.bool";
            case "<" -> "<.i32";
            default -> throw new RuntimeException("Unsupported boolean operation: " + op);
        };

        Type resType = types.getExprType(node);
        String resOllirType = ollirTypes.toOllirType(resType);

        String tempVar = ollirTypes.nextTemp();
        String code = tempVar + resOllirType;

        computation.append(tempVar).append(resOllirType)
                .append(" :=").append(resOllirType).append(" ")
                .append(left.getCode()).append(" ")
                .append(ollirOp).append(" ")
                .append(right.getCode()).append(";\n");

        return new OllirExprResult(code, computation.toString());
    }

    private OllirExprResult visitBooleanLiteral(JmmNode node, Void unused) {
        String value = node.get("value");
        String ollirValue = value.equals("true") ? "1" : "0";
        return new OllirExprResult(ollirValue + ".bool");
    }


    private OllirExprResult visitInteger(JmmNode node, Void unused) {
        var intType = TypeUtils.newIntType();
        String ollirIntType = ollirTypes.toOllirType(intType);
        String code = node.get("value") + ollirIntType;
        return new OllirExprResult(code);
    }


    private OllirExprResult visitBinExpr(JmmNode node, Void unused) {

        var lhs = visit(node.getChild(0));
        var rhs = visit(node.getChild(1));

        StringBuilder computation = new StringBuilder();

        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());

        Type resType = types.getExprType(node);
        String resOllirType = ollirTypes.toOllirType(resType);
        String code = ollirTypes.nextTemp() + resOllirType;

        computation.append(code).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE)
                .append(lhs.getCode()).append(SPACE);

        String op = node.get("operation");
        String ollirOp = switch(op) {
            case "+" -> "+.i32";
            case "-" -> "-.i32";
            case "*" -> "*.i32";
            case "/" -> "/.i32";
            default -> throw new RuntimeException("Unsupported binary operation: " + op);
        };
        computation.append(ollirOp).append(SPACE)
                .append(rhs.getCode()).append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitArrayAccess(JmmNode node, Void unused) {

        var arrayExpr = visit(node.getChild(0));
        var indexExpr = visit(node.getChild(1));
        StringBuilder computation = new StringBuilder();
        computation.append(arrayExpr.getComputation());
        computation.append(indexExpr.getComputation());

        Type arrayType = types.getExprType(node.getChild(0));

        String elementOllirType = ollirTypes.toOllirType(arrayType);
        String tempVar = ollirTypes.nextTemp() + elementOllirType;

        computation.append(tempVar).append(SPACE)
                .append(ASSIGN).append(elementOllirType).append(SPACE)
                .append(arrayExpr.getCode()).append(SPACE)
                .append("[").append(indexExpr.getCode()).append("]").append(END_STMT);

        return new OllirExprResult(tempVar, computation);
    }
    private OllirExprResult visitArrayInit(JmmNode node, Void unused) {

        System.out.println("Visiting array initialization");
        StringBuilder computation = new StringBuilder();

        String arrayType = ollirTypes.toOllirType(types.getExprType(node));
        String arrayVar = ollirTypes.nextTemp() + arrayType;

        computation.append(arrayVar).append(SPACE)
                .append(ASSIGN).append(arrayType).append(SPACE)
                .append("new ").append(arrayType).append(SPACE);

        int size = node.getChildren().size();
        computation.append(size).append(END_STMT);

        StringBuilder initComputation = new StringBuilder();
        for (int i = 0; i < size; i++) {
            JmmNode element = node.getChild(i);
            String elementCode = visit(element).getCode();

            initComputation.append("store ").append(elementCode)
                    .append(" to ").append(arrayVar).append("[").append(i).append("]").append(END_STMT);
        }

        computation.append(initComputation);

        return new OllirExprResult(arrayVar, computation);
    }



    private OllirExprResult visitVarRef(JmmNode node, Void unused) {
        String varName = node.get("name");
        Type varType = types.getExprType(node);
        String ollirType = ollirTypes.toOllirType(varType);

        JmmNode current = node;
        String currentMethod = null;
        while (current != null && currentMethod == null) {
            if (current.getKind().equals("MethodDeclaration")) {
                currentMethod = current.get("method");
            }
            current = current.getParent();
        }

        boolean isLocal = false;
        if (currentMethod != null) {
            isLocal = table.getLocalVariables(currentMethod).stream()
                    .anyMatch(symbol -> symbol.getName().equals(varName)) ||
                    table.getParameters(currentMethod).stream()
                            .anyMatch(symbol -> symbol.getName().equals(varName));
        }

        boolean isField = !isLocal && table.getFields().stream()
                .anyMatch(field -> field.getName().equals(varName));

        if (isField) {
            String tempVar = ollirTypes.nextTemp();
            StringBuilder computation = new StringBuilder();

            computation.append(tempVar).append(ollirType)
                    .append(" :=").append(ollirType).append(" ")
                    .append("getfield(this, ")
                    .append(varName).append(ollirType)
                    .append(")").append(ollirType)
                    .append(";\n");

            return new OllirExprResult(tempVar + ollirType, computation.toString());
        }

        return new OllirExprResult(varName + ollirType);
    }

    /**
     * Default visitor. Visits every child node and return an empty result.
     *
     * @param node
     * @param unused
     * @return
     */
    private OllirExprResult defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return OllirExprResult.EMPTY;
    }

}
