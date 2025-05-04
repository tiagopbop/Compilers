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
        addVisit(NEW_ARRAY_EXPR, this::visitArrayInit);
        addVisit(BOOLEAN_EXPR, this::visitBooleanExpr);
        addVisit(BOOLEAN_LITERAL, this::visitBooleanLiteral);
        addVisit(METHOD_CALL, this::visitMethodCall);
        addVisit(LENGTH_EXPR, this::visitLengthExpr);
        addVisit(NEW_CLASS_EXPR, this::visitNewClassExpr);


        setDefaultVisit(this::defaultVisit);
    }

    private OllirExprResult visitMethodCall(JmmNode node, Void unused) {
        StringBuilder computation = new StringBuilder();
        String methodName = node.get("name");
        boolean isDotNotation = false;
        JmmNode objectNode = null;

        if (node.getNumChildren() > 0 && !node.getChild(0).getKind().equals("Parameter")) {
            objectNode = node.getChild(0);
            isDotNotation = true;
        }

        ArrayList<OllirExprResult> argsResults = new ArrayList<>();
        int startIdx = isDotNotation ? 1 : 0;

        for (int i = startIdx; i < node.getNumChildren(); i++) {
            OllirExprResult arg = visit(node.getChild(i));
            computation.append(arg.getComputation());
            argsResults.add(arg);
        }

        StringBuilder argsCode = new StringBuilder();
        for (OllirExprResult arg : argsResults) {
            if (!argsCode.isEmpty()) {
                argsCode.append(", ");
            }
            argsCode.append(arg.getCode());
        }

        Type returnType = table.getMethods().contains(methodName)
                ? table.getReturnType(methodName)
                : new Type("void", false);

        String returnTypeStr = ollirTypes.toOllirType(returnType);
        String tempVar = ollirTypes.nextTemp();
        String resultVar = tempVar + returnTypeStr;

        StringBuilder callCode = new StringBuilder();

        if (isDotNotation) {
            String objectName = objectNode.get("name");

            if (table.getImports().contains(objectName)) {
                callCode.append("invokestatic(").append(objectName).append(", \"")
                        .append(methodName).append("\"");
            } else {
                OllirExprResult objResult = visit(objectNode);
                computation.append(objResult.getComputation());
                callCode.append("invokevirtual(").append(objResult.getCode()).append(", \"")
                        .append(methodName).append("\"");
            }

        } else {
            callCode.append("invokestatic(").append(table.getClassName()).append(", \"")
                    .append(methodName).append("\"");
        }

        if (!argsCode.isEmpty()) {
            callCode.append(", ").append(argsCode);
        }

        callCode.append(")").append(returnTypeStr);

        if (returnType.getName().equals("void")) {
            computation.append(callCode).append(";\n");
            return new OllirExprResult("", computation.toString());
        } else {
            computation.append(resultVar).append(" :=").append(returnTypeStr)
                    .append(" ").append(callCode).append(";\n");
            return new OllirExprResult(resultVar, computation.toString());
        }
    }


    private OllirExprResult visitLengthExpr(JmmNode node, Void unused) {
        OllirExprResult arrayExpr = visit(node.getChild(0));

        String arrayCode = arrayExpr.getComputation();

        String varName = arrayExpr.getCode().split("\\.")[0];
        String arrayRef = varName + ".array.i32";

        String temp = ollirTypes.nextTemp();
        String resultCode = temp + ".i32 :=.i32 arraylength(" + arrayRef + ").i32;\n";

        return new OllirExprResult(temp + ".i32", arrayCode + resultCode);
    }

    private OllirExprResult visitBooleanExpr(JmmNode node, Void unused) {
        var op = node.get("operation");

        if ("&&".equals(op)) {
            String labelFalse = "false_" + ollirTypes.nextTemp("label");
            String labelEnd = "end_" + ollirTypes.nextTemp("label");

            var left = visit(node.getChild(0));
            var right = visit(node.getChild(1));

            String resultTemp = ollirTypes.nextTemp() + ".bool";
            StringBuilder computation = new StringBuilder();

            computation.append(left.getComputation());
            computation.append("if (!.bool ").append(left.getCode()).append(") goto ").append(labelFalse).append(";\n");

            computation.append(right.getComputation());
            computation.append("if (!.bool ").append(right.getCode()).append(") goto ").append(labelFalse).append(";\n");

            computation.append(resultTemp).append(" :=.bool 1.bool;\n");
            computation.append("goto ").append(labelEnd).append(";\n");

            computation.append(labelFalse).append(":\n");
            computation.append(resultTemp).append(" :=.bool 0.bool;\n");

            computation.append(labelEnd).append(":\n");

            return new OllirExprResult(resultTemp, computation.toString());
        }

        //other bool
        var left = visit(node.getChild(0));
        var right = visit(node.getChild(1));

        StringBuilder computation = new StringBuilder();
        computation.append(left.getComputation());
        computation.append(right.getComputation());

        String ollirOp = switch(op) {
            case "<" -> "<.i32";
            default -> throw new RuntimeException("Unsupported boolean operation: " + op);
        };

        String resOllirType = ".bool";

        String tempVar = ollirTypes.nextTemp();
        String code = tempVar + resOllirType;

        computation.append(code).append(" :=").append(resOllirType).append(" ")
                .append(left.getCode()).append(" ").append(ollirOp).append(" ").append(right.getCode()).append(";\n");
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
        String value = node.get("value");
        String code = value + ".i32";
        return new OllirExprResult(code);
    }

    private OllirExprResult visitBinExpr(JmmNode node, Void unused) {

        if (node.getNumChildren() < 2) {
            throw new RuntimeException("Binary expression node does not have enough children: " + node);
        }

        var lhs = visit(node.getChild(0));
        var rhs = visit(node.getChild(1));

        StringBuilder computation = new StringBuilder();
        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());

        Type resType = types.getExprType(node);
        String resOllirType = ollirTypes.toOllirType(resType);
        String code = ollirTypes.nextTemp() + resOllirType;

        String op = node.get("operation");
        String ollirOp = switch (op) {
            case "+" -> "+.i32";
            case "-" -> "-.i32";
            case "*" -> "*.i32";
            case "/" -> "/.i32";
            default -> throw new RuntimeException("Unsupported binary operation: " + op);
        };

        computation.append(code).append(" :=").append(resOllirType).append(" ")
                .append(lhs.getCode()).append(" ")
                .append(ollirOp).append(" ")
                .append(rhs.getCode()).append(";\n");

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitArrayAccess(JmmNode node, Void unused) {
        var arrayExpr = visit(node.getChild(0));
        var indexExpr = visit(node.getChild(1));
        StringBuilder computation = new StringBuilder();

        computation.append(arrayExpr.getComputation());
        computation.append(indexExpr.getComputation());

        String varName = arrayExpr.getCode().split("\\.")[0];
        String arrayCode = varName + ".array.i32";

        String tempVar = ollirTypes.nextTemp();
        String resultVar = tempVar + ".i32";

        computation.append(resultVar).append(" :=.i32 ")
                .append(arrayCode)
                .append("[")
                .append(indexExpr.getCode())
                .append("]")
                .append(".i32")
                .append(";\n");

        return new OllirExprResult(resultVar, computation.toString());
    }

    private OllirExprResult visitNewClassExpr(JmmNode node, Void unused) {
        String className = node.get("name");
        StringBuilder code = new StringBuilder();

        String tempVar = ollirTypes.nextTemp();
        String typeStr = "." + className;

        code.append(tempVar).append(typeStr).append(" :=.").append(className)
                .append(" new(").append(className).append(")").append(typeStr).append(";\n");

        code.append("invokespecial(").append(tempVar).append(typeStr)
                .append(", \"<init>\").V;\n");

        return new OllirExprResult(tempVar + typeStr, code.toString());
    }

    private OllirExprResult visitArrayInit(JmmNode node, Void unused) {
        StringBuilder computation = new StringBuilder();

        String arrayType = ".array.i32";

        String tempVar = ollirTypes.nextTemp();
        String resultVar = tempVar + arrayType;

        JmmNode sizeNode = null;
        for (int i = 0; i < node.getNumChildren(); i++) {
            JmmNode child = node.getChild(i);
            if (child.getKind().equals("IntegerLiteral")) {
                sizeNode = child;
                break;
            }
        }

        String sizeCode = "5.i32";
        if (sizeNode != null) {
            sizeCode = sizeNode.get("value") + ".i32";
        }

        computation.append(resultVar)
                .append(" :=").append(arrayType)
                .append(" new(array, ").append(sizeCode).append(")")
                .append(arrayType).append(";\n");

        return new OllirExprResult(resultVar, computation.toString());
    }

    private OllirExprResult visitVarRef(JmmNode node, Void unused) {
        String varName = node.get("name");
        if (table.getImports().contains(varName)) {
            return new OllirExprResult(varName, "");
        }
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
        if (varName.equals("io") || varName.equals("ioPlus")) {
            Type type = new Type("int", false);
            return new OllirExprResult(varName + ".i32", "");
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
