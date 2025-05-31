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
        addVisit(PARENTHESIS_EXPR, this::visitParenthesisExpr);

        setDefaultVisit(this::defaultVisit);
    }

    private OllirExprResult visitMethodCall(JmmNode node, Void unused) {
        StringBuilder computation = new StringBuilder();
        String methodName = node.get("name");
        boolean isDotNotation = false;
        JmmNode objectNode = null;

        if (node.getNumChildren() > 0) {
            JmmNode firstChild = node.getChild(0);
            if (firstChild != null &&
                    !firstChild.getKind().equals("Parameter") &&
                    !firstChild.getKind().equals("IntegerLiteral") &&
                    !firstChild.getKind().equals("BooleanLiteral")) {
                objectNode = firstChild;
                isDotNotation = true;
            }
        }

        ArrayList<OllirExprResult> argsResults = new ArrayList<>();
        int startIdx = isDotNotation ? 1 : 0;

        for (int i = startIdx; i < node.getNumChildren(); i++) {
            JmmNode childNode = node.getChild(i);
            if (childNode != null) {
                OllirExprResult arg = processComplexArgument(childNode);
                computation.append(arg.getComputation());
                argsResults.add(arg);
            }
        }

        StringBuilder argsCode = new StringBuilder();
        for (OllirExprResult arg : argsResults) {
            if (!argsCode.isEmpty()) {
                argsCode.append(", ");
            }
            argsCode.append(arg.getCode());
        }

        Type returnType = determineMethodReturnType(methodName, objectNode, node);
        String returnTypeStr = ollirTypes.toOllirType(returnType);

        StringBuilder callCode = new StringBuilder();

        if (isDotNotation && objectNode != null) {
            if (objectNode.getKind().equals("VarRefExpr")) {
                String objectName = objectNode.get("name");

                if (isImportedClass(objectName)) {
                    callCode.append("invokestatic(").append(objectName).append(", \"")
                            .append(methodName).append("\"");
                } else if ("this".equals(objectName)) {
                    callCode.append("invokevirtual(this, \"")
                            .append(methodName).append("\"");
                } else {
                    OllirExprResult objResult = visit(objectNode);
                    computation.append(objResult.getComputation());
                    callCode.append("invokevirtual(").append(objResult.getCode()).append(", \"")
                            .append(methodName).append("\"");
                }
            } else {
                OllirExprResult objResult = visit(objectNode);
                computation.append(objResult.getComputation());
                callCode.append("invokevirtual(").append(objResult.getCode()).append(", \"")
                        .append(methodName).append("\"");
            }
        } else {
            callCode.append("invokevirtual(this, \"")
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
            String tempVar = ollirTypes.nextTemp();
            String resultVar = tempVar + returnTypeStr;
            computation.append(resultVar).append(" :=").append(returnTypeStr)
                    .append(" ").append(callCode).append(";\n");
            return new OllirExprResult(resultVar, computation.toString());
        }
    }

    private OllirExprResult processComplexArgument(JmmNode argNode) {
        if (argNode == null) {
            return OllirExprResult.EMPTY;
        }

        switch (argNode.getKind()) {
            case "BinaryExpr":
                return visitBinExpr(argNode, null);

            case "MethodCall":
                return visitMethodCall(argNode, null);

            case "NewClassExpr":
                return visitNewClassExpr(argNode, null);

            case "ParenthesisExpr":
                return processComplexArgument(argNode.getChild(0));

            case "IntegerLiteral":
            case "BooleanLiteral":
            case "VarRefExpr":
            case "ArrayAccess":
            case "LengthExpr":
                return visit(argNode);

            default:
                return visit(argNode);
        }
    }

    private Type determineMethodReturnType(String methodName, JmmNode objectNode, JmmNode callNode) {
        if (table.getMethods().contains(methodName)) {
            return table.getReturnType(methodName);
        }
        if (objectNode == null || (objectNode.getKind().equals("VarRefExpr") && "this".equals(objectNode.get("name")))) {
            JmmNode parent = callNode.getParent();
            if (parent != null) {
                if (parent.getKind().equals("AssignStmt") || parent.getKind().equals("VarDeclaration")) {
                    try {
                        Type targetType = types.getExprType(callNode);
                        if (targetType != null && !targetType.getName().equals("void")) {
                            return targetType;
                        }
                    } catch (Exception e) {
                    }
                }

                if (parent.getKind().equals("BinaryExpr") || parent.getKind().equals("BooleanExpr")) {
                    return new Type("int", false);
                }

                if (parent.getKind().equals("ReturnStmt")) {
                    JmmNode methodNode = findParentMethod(callNode);
                    if (methodNode != null) {
                        String parentMethodName = methodNode.get("method");
                        if (table.getMethods().contains(parentMethodName)) {
                            return table.getReturnType(parentMethodName);
                        }
                    }
                }
            }
        }
        JmmNode parent = callNode.getParent();
        if (parent != null && (parent.getKind().equals("BinaryExpr") ||
                parent.getKind().equals("IntegerLiteral") ||
                parent.getKind().contains("Assign"))) {
            return new Type("int", false);
        }
        return new Type("void", false);
    }

    private JmmNode findParentMethod(JmmNode node) {
        JmmNode current = node;
        while (current != null) {
            if (current.getKind().equals("MethodDeclaration") || current.getKind().equals("MainMethodDeclaration")) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private boolean isImportedClass(String className) {
        if (table.getImports().contains(className)) {
            return true;
        }

        for (String importName : table.getImports()) {
            if (importName.contains(".")) {
                String lastPart = importName.substring(importName.lastIndexOf('.') + 1);
                if (lastPart.equals(className)) {
                    return true;
                }
            }
        }

        return false;
    }

    private OllirExprResult visitParenthesisExpr(JmmNode node, Void unused) {
        return visit(node.getChild(0));
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

        var left = visit(node.getChild(0));
        var right = visit(node.getChild(1));

        StringBuilder computation = new StringBuilder();
        computation.append(left.getComputation());
        computation.append(right.getComputation());

        String ollirOp = switch(op) {
            case "<" -> "<.i32";
            case ">" -> ">.i32";
            case "<=" -> "<=.i32";
            case ">=" -> ">=.i32";
            case "==" -> "==.i32";
            case "!=" -> "!=.i32";
            default -> throw new RuntimeException("Unsupported boolean operation: " + op);
        };

        String resOllirType = ".bool";
        String tempVar = ollirTypes.nextTemp();
        String code = tempVar + resOllirType;

        computation.append(code).append(" :=").append(resOllirType).append(" ")
                .append(left.getCode()).append(" ").append(ollirOp).append(" ").append(right.getCode()).append(";\n");

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
        StringBuilder computation = new StringBuilder();

        String tempVar = ollirTypes.nextTemp();
        String typeStr = "." + className;

        computation.append(tempVar).append(typeStr).append(" :=.").append(className)
                .append(" new(").append(className).append(")").append(typeStr).append(";\n");

        computation.append("invokespecial(").append(tempVar).append(typeStr)
                .append(", \"<init>\").V;\n");

        return new OllirExprResult(tempVar + typeStr, computation.toString());
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

        if (varName.equals("io") || varName.equals("ioPlus")) {
            return new OllirExprResult(varName, "");
        }

        if (varName.equals("this")) {
            return new OllirExprResult("this", "");
        }

        Type varType = types.getExprType(node);
        String ollirType = ollirTypes.toOllirType(varType);

        JmmNode current = node;
        String currentMethod = null;
        while (current != null && currentMethod == null) {
            if (current.getKind().equals("MethodDeclaration") || current.getKind().equals("MainMethodDeclaration")) {
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
        } else {
            return new OllirExprResult(varName + ollirType);
        }
    }

    /**
     * Default visitor. Visits every child node and return an empty result.
     */
    private OllirExprResult defaultVisit(JmmNode node, Void unused) {
        for (var child : node.getChildren()) {
            visit(child);
        }
        return OllirExprResult.EMPTY;
    }
}