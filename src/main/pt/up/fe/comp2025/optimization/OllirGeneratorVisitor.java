package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.ast.TypeUtils;

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

        String labelThen = "then_" + ollirTypes.nextTemp("label");
        String labelElse = "else_" + ollirTypes.nextTemp("label");
        String labelEnd = "endif_" + ollirTypes.nextTemp("label");

        String conditionCode = condition.getCode();
        if (!conditionCode.endsWith(".bool")) {
            String tempVar = ollirTypes.nextTemp();
            code.append("   ").append(tempVar).append(".bool :=.bool ").append(conditionCode).append(" !=.bool 0.bool;\n");
            conditionCode = tempVar + ".bool";
        }

        code.append("   if (").append(conditionCode).append(") goto ").append(labelThen).append(";\n");

        code.append("   goto ").append(labelElse).append(";\n");

        code.append("   ").append(labelThen).append(":\n");

        if (node.getNumChildren() > 1) {
            code.append(visit(node.getChild(1)));
        }

        code.append("   goto ").append(labelEnd).append(";\n");

        code.append("   ").append(labelElse).append(":\n");

        if (node.getNumChildren() > 2) {
            code.append(visit(node.getChild(2)));
        }

        code.append("   ").append(labelEnd).append(":\n");

        return code.toString();
    }
    private String visitExprStmt(JmmNode node, Void unused) {
        var result = exprVisitor.visit(node.getChild(0));
        return result.getComputation();
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
        var rhs = exprVisitor.visit(node.getChild(1));
        StringBuilder code = new StringBuilder();

        code.append(rhs.getComputation());

        var left = node.getChild(0);
        Type thisType = types.getExprType(left);
        String typeString = ollirTypes.toOllirType(thisType);
        String varName = left.get("name");

        if (thisType.isArray()) {
            if (rhs.getCode().isEmpty()) {
                code.append(varName).append(typeString)
                        .append(" :=").append(typeString)
                        .append(" new(array, 10).array.i32;\n");
            } else {
                code.append(varName).append(typeString)
                        .append(" :=").append(typeString)
                        .append(" ").append(rhs.getCode()).append(";\n");
            }
        } else {
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
                code.append("putfield(this, ")
                        .append(varName).append(typeString)
                        .append(", ")
                        .append(rhs.getCode())
                        .append(")").append(typeString)
                        .append(";\n");
            } else {
                code.append(varName).append(typeString)
                        .append(" :=")
                        .append(typeString)
                        .append(" ")
                        .append(rhs.getCode())
                        .append(";\n");
            }
        }

        return code.toString();
    }


    private String visitReturn(JmmNode node, Void unused) {
        JmmNode methodNode = findParentMethod(node);
        String methodName = methodNode.get("method");
        Type retType = table.getReturnType(methodName);

        StringBuilder code = new StringBuilder();

        var expr = node.getNumChildren() > 0 ? exprVisitor.visit(node.getChild(0)) : OllirExprResult.EMPTY;

        code.append(expr.getComputation());

        if (retType.isArray()) {
            code.append("ret.array.i32 ");
        } else {
            code.append("ret");
            code.append(ollirTypes.toOllirType(retType));
            code.append(" ");
        }

        code.append(expr.getCode());
        code.append(";\n");

        return code.toString();
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

        // Method name
        var name = node.get("method");
        code.append(name);

        // Parameters
        var paramNodes = node.getChildren().stream()
                .filter(child -> child.getKind().equals("Parameter"))
                .collect(Collectors.toList());

        var paramsCode = paramNodes.stream()
                .map(this::visit)
                .collect(Collectors.joining(", "));

        code.append("(").append(paramsCode).append(")");

        // Return type
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

        // Add ret.V if method returns void
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

            // Field name and type
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

        node.getChildren().stream()
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
