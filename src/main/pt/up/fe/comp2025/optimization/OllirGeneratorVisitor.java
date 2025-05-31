package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.ast.TypeUtils;

import java.util.*;
import java.util.stream.Collectors;

import static pt.up.fe.comp2025.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are not expressions.
 */
public class OllirGeneratorVisitor extends AJmmVisitor<Void, String> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";
    private final String NL = "\n";
    private final String L_BRACKET = " {\n";
    private final String R_BRACKET = "}\n";


    private final SymbolTable table;

    private final TypeUtils types;
    private final OptUtils ollirTypes;


    private final OllirExprGeneratorVisitor exprVisitor;

    public OllirGeneratorVisitor(SymbolTable table) {
        this.table = table;
        this.types = new TypeUtils(table);
        this.ollirTypes = new OptUtils(types);
        this.exprVisitor = new OllirExprGeneratorVisitor(table);
    }


    @Override
    protected void buildVisitor() {

        addVisit(PROGRAM, this::visitProgram);
        addVisit(CLASS_DECL, this::visitClass);
        addVisit(METHOD_DECL, this::visitMethodDecl);
        addVisit(PARAM, this::visitParam);
        addVisit(RETURN_STMT, this::visitReturn);
        addVisit(ASSIGN_STMT, this::visitAssignStmt);
        addVisit(IMPORT_DECL, this::visitImportDecl);
        addVisit(BINARY_EXPR, this::visitBinaryExpr);
        addVisit(BOOLEAN_EXPR, this::visitBooleanExpr);
        addVisit(INTEGER_LITERAL, this::visitIntegerLiteral);
        addVisit(BOOLEAN_LITERAL, this::visitBooleanLiteral);
        addVisit(VAR_REF_EXPR, this::visitVarRefExpr);
        addVisit(VAR_DECL, this::visitVarDecl);
        addVisit(IF_STMT, this::visitIfStmt);
        addVisit(WHILE_STMT, this::visitWhileStmt);
        addVisit(METHOD_CALL, this::visitMethodCallStmt);
        addVisit(EXPR_STATEMENT, this::visitExprStmt);
        addVisit(ARRAY_ASSIGN_STATEMENT, this::visitArrayAssignStatement);
        addVisit(BLOCK_STATEMENT, this::visitBlockStatement);


        setDefaultVisit(this::defaultVisit);
    }

    private String visitMethodCallStmt(JmmNode node, Void unused) {
        var result = exprVisitor.visit(node);
        return result.getComputation();
    }

    private String visitIfStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        if (node.getNumChildren() == 0) {
            return "";
        }

        OllirExprResult condition = exprVisitor.visit(node.getChild(0));
        code.append(condition.getComputation());

        String labelThen = "then_" + ollirTypes.nextTemp("");
        String labelElse = "else_" + ollirTypes.nextTemp("");
        String labelEnd = "endif_" + ollirTypes.nextTemp("");

        String conditionCode = condition.getCode();
        if (!conditionCode.endsWith(".bool")) {
            String tempVar = ollirTypes.nextTemp();
            code.append(tempVar).append(".bool :=.bool ").append(conditionCode).append(" !=.bool 0.bool;\n");
            conditionCode = tempVar + ".bool";
        }

        code.append("if (").append(conditionCode).append(") goto ").append(labelThen).append(";\n");
        code.append("goto ").append(labelElse).append(";\n");

        code.append(labelThen).append(":\n");
        if (node.getNumChildren() > 1) {
            JmmNode thenNode = node.getChild(1);
            String thenCode = visit(thenNode);
            code.append(thenCode);
        }
        code.append("goto ").append(labelEnd).append(";\n");

        code.append(labelElse).append(":\n");
        if (node.getNumChildren() > 2) {
            JmmNode elseNode = node.getChild(2);
            String elseCode = visit(elseNode);
            code.append(elseCode);
        }

        code.append(labelEnd).append(":\n");

        return code.toString();
    }
    private String visitExprStmt(JmmNode node, Void unused) {
        var result = exprVisitor.visit(node.getChild(0));
        return result.getComputation();
    }

    private String visitBlockStatement(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        for (JmmNode child : node.getChildren()) {
            String childCode = visit(child);
            code.append(childCode);
        }

        return code.toString();
    }

    private String visitWhileStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        String loopLabel = "loop" + ollirTypes.nextTemp("");
        String endLabel = "end" + ollirTypes.nextTemp("");

        code.append("   ").append(loopLabel).append(":\n");

        OllirExprResult condResult = exprVisitor.visit(node.getChild(0));
        code.append("   ").append(condResult.getComputation());

        code.append("   if (").append(condResult.getCode()).append(" ==.bool 0.bool) goto ").append(endLabel).append(";\n");

        if (node.getNumChildren() > 1) {
            String bodyCode = visit(node.getChild(1));
            code.append(bodyCode);
        }

        code.append("   goto ").append(loopLabel).append(";\n");

        code.append("   ").append(endLabel).append(":\n");

        return code.toString();
    }

    private String visitVarDecl(JmmNode node, Void unused) {
        return "";
    }

    private String visitImportDecl(JmmNode node, Void unused) {
        return "";
    }

    private String visitVarRefExpr(JmmNode node, Void unused) {
        return "";
    }

    private String visitBooleanLiteral(JmmNode node, Void unused) {
        return "";
    }

    private String visitIntegerLiteral(JmmNode node, Void unused) {
        return "";
    }

    private String visitBooleanExpr(JmmNode node, Void unused) {
        var result = exprVisitor.visit(node);
        return result.getComputation();
    }

    private String visitBinaryExpr(JmmNode node, Void unused) {
        var result = exprVisitor.visit(node);
        return result.getComputation();
    }


    private String visitAssignStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        var rhs = exprVisitor.visit(node.getChild(1));
        code.append(rhs.getComputation());

        var left = node.getChild(0);
        Type leftType = types.getExprType(left);
        String leftTypeString = ollirTypes.toOllirType(leftType);
        String varName;

        switch (left.getKind()) {
            case "VarRefExpr":
                varName = left.get("name");
                break;
            case "IntegerLiteral":
                varName = "tmp_int_literal_" + left.get("value");
                break;
            default:
                throw new RuntimeException("Unsupported LHS kind in assignment: " + left.getKind());
        }
        
        Type rhsType = types.getExprType(node.getChild(1));

        if (rhsType.isArray() && !leftType.isArray()) {
            String elementTypeName = rhsType.getName();
            String ollirElementType = switch (elementTypeName) {
                case "int" -> "i32";
                case "boolean" -> "bool";
                default -> elementTypeName.toLowerCase();
            };
            leftTypeString = ".array." + ollirElementType;
        }

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
            if (!rhs.getCode().isEmpty()) {
                code.append("putfield(this, ")
                        .append(varName).append(leftTypeString)
                        .append(", ").append(rhs.getCode())
                        .append(")").append(leftTypeString)
                        .append(";\n");
            }
        } else {
            if (!rhs.getCode().isEmpty()) {
                code.append(varName).append(leftTypeString)
                        .append(" :=").append(leftTypeString)
                        .append(" ").append(rhs.getCode())
                        .append(";\n");
            }
        }

        return code.toString();
    }

    private String visitArrayAssignStatement(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        JmmNode arrayNode = node.getChild(0);
        JmmNode indexNode = node.getChild(1);
        JmmNode valueNode = node.getChild(2);

        OllirExprResult arrayExpr = exprVisitor.visit(arrayNode);
        OllirExprResult indexExpr = exprVisitor.visit(indexNode);
        OllirExprResult valueExpr = exprVisitor.visit(valueNode);

        code.append(arrayExpr.getComputation());
        code.append(indexExpr.getComputation());
        code.append(valueExpr.getComputation());

        String varName = arrayExpr.getCode().split("\\.")[0];
        String arrayCode = varName + ".array.i32";

        code.append(arrayCode)
                .append("[")
                .append(indexExpr.getCode())
                .append("]")
                .append(".i32")
                .append(" :=.i32 ")
                .append(valueExpr.getCode())
                .append(";\n");

        return code.toString();
    }


    private String visitReturn(JmmNode node, Void unused) {
        JmmNode methodNode = findParentMethod(node);
        String methodName = methodNode.get("method");
        Type retType = table.getReturnType(methodName);

        StringBuilder code = new StringBuilder();

        if (node.getNumChildren() == 0) {
            code.append("ret").append(ollirTypes.toOllirType(retType)).append(";\n");
            return code.toString();
        }

        JmmNode exprNode = node.getChild(0);
        OllirExprResult expr;

        switch (valueOf(toEnumFormat(exprNode.getKind()))) {
            case INTEGER_LITERAL -> {
                String value = exprNode.get("value") + ".i32";
                expr = new OllirExprResult(value, "");
            }
            case BOOLEAN_LITERAL -> {
                String value = exprNode.get("value") + ".bool";
                expr = new OllirExprResult(value, "");
            }
            default -> {
                expr = exprVisitor.visit(exprNode);
            }
        }


        code.append(expr.getComputation());
        code.append("ret").append(ollirTypes.toOllirType(retType)).append(" ").append(expr.getCode()).append(";\n");

        return code.toString();
    }
    private String toEnumFormat(String kind) {
        return kind.replaceAll("([a-z])([A-Z]+)", "$1_$2").toUpperCase();
    }

    private JmmNode findParentMethod(JmmNode node) {
        JmmNode current = node;
        while (current != null && !current.getKind().equals("MethodDeclaration")) {
            current = current.getParent();
        }
        return current;
    }


    private String visitParam(JmmNode node, Void unused) {

        var typeCode = ollirTypes.toOllirType(node.getChild(0));
        var id = node.get("name");

        String code = id + typeCode;

        return code;
    }


    private String visitMethodDecl(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder(".method ");

        boolean isPublic = node.getBoolean("isPublic", false);
        if (isPublic) {
            code.append("public ");
        }

        var name = node.get("method");
        code.append(name);

        var paramNodes = node.getChildren().stream()
                .filter(child -> child.getKind().equals("Parameter"))
                .collect(Collectors.toList());

        var paramsCode = paramNodes.stream()
                .map(this::visit)
                .collect(Collectors.joining(", "));

        code.append("(").append(paramsCode).append(")");

        Type returnType = table.getReturnType(name);
        if (returnType.isArray()) {
            code.append(".array.i32");
        } else {
            code.append(ollirTypes.toOllirType(returnType));
        }

        code.append(" {\n");

        var stmtsCode = node.getChildren(STMT).stream()
                .map(this::visit)
                .collect(Collectors.joining("\n   ", "   ", ""));

        code.append(stmtsCode);

        if (returnType.getName().equals("void")) {
            code.append("\n   ret.V;\n");
        }

        code.append(R_BRACKET);
        code.append(NL);

        return code.toString();
    }



    private String visitClass(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        code.append(NL);
        code.append(table.getClassName());

        if (table.getSuper() != null) {
            code.append(" extends ").append(table.getSuper());
        }

        code.append(L_BRACKET);
        code.append(NL);

        for (var field : table.getFields()) {
            code.append(".field ");
            code.append("private ");
            code.append(field.getName())
                    .append(ollirTypes.toOllirType(field.getType()))
                    .append(";\n");
        }

        code.append(NL);
        code.append(buildConstructor());
        code.append(NL);

        for (var child : node.getChildren(METHOD_DECL)) {
            var result = visit(child);
            code.append(result);
        }

        code.append(R_BRACKET);
        return code.toString();
    }

    private String buildConstructor() {

        return """
                .construct %s().V {
                    invokespecial(this, "<init>").V;
                }
                """.formatted(table.getClassName());
    }


    private String visitProgram(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        Set<String> processedImports = new HashSet<>();
        Map<String, String> importMap = new HashMap<>();

        for (var child : node.getChildren()) {
            if (child.getKind().equals("ImportDeclaration")) {
                List<String> nameParts = child.getObjectAsList("name", String.class);
                if (!nameParts.isEmpty()) {
                    String fullPath = String.join(".", nameParts);
                    String className = nameParts.get(nameParts.size() - 1);

                    if (!processedImports.contains(fullPath)) {
                        if (importMap.containsKey(className)) {
                            String existing = importMap.get(className);
                            if (fullPath.length() < existing.length()) {
                                importMap.put(className, fullPath);
                            }
                        } else {
                            importMap.put(className, fullPath);
                        }
                        processedImports.add(fullPath);
                    }
                }
            }
        }

        for (String importPath : new TreeSet<>(importMap.values())) {
            code.append("import ").append(importPath).append(";\n");
        }

        node.getChildren().stream()
                .filter(child -> !child.getKind().equals("ImportDeclaration"))
                .map(this::visit)
                .forEach(code::append);

        return code.toString();
    }
    /**
     * Default visitor. Visits every child node and return an empty string.
     *
     * @param node
     * @param unused
     * @return
     */
    private String defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return "";
    }
}
