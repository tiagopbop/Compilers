package pt.up.fe.comp2025.ast;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.symboltable.JmmSymbolTable;

/**
 * Utility methods regarding types.
 */
public class TypeUtils {


    private final JmmSymbolTable table;

    public TypeUtils(SymbolTable table) {
        this.table = (JmmSymbolTable) table;
    }

    public static Type newIntType() {
        return new Type("int", false);
    }

    public static Type convertType(JmmNode typeNode) {

        var name = typeNode.get("name");
        var isArray = typeNode.getKind().equals("ArrayType");

        return new Type(name, isArray);
    }


    /**
     * Gets the {@link Type} of an arbitrary expression.
     *
     * @param expr
     * @return
     */
    public Type getExprType(JmmNode expr) {

        if (Kind.ARRAY_ACCESS.check(expr)) {
            if (!getExprType(expr.getChild(0)).isArray()) {
                throw new SemanticException("Cannot access element of non-array type: " + getExprType(expr.getChild(0)).getName());
            }
            return new Type(getExprType(expr.getChild(0)).getName(), false);

        } else if (Kind.ARRAY_INITIALIZATION_EXPR.check(expr)) {
            if (expr.getNumChildren() == 0) {
                throw new SemanticException("Array initialization cannot be empty.");
            }

            return new Type(getExprType(expr.getChild(0)).getName(), true);

        }else if (Kind.VAR_REF_EXPR.check(expr)) {
            String varName = expr.get("name");

            for (String method : table.getMethods()) {
                for (var param : table.getParameters(method)) {
                    if (param.getName().equals(varName)) {
                        return param.getType();
                    }
                }
                for (var local : table.getLocalVariables(method)) {
                    if (local.getName().equals(varName)) {
                        return local.getType();
                    }
                }
            }

            for (var field : table.getFields()) {
                if (field.getName().equals(varName)) {
                    return field.getType();
                }
            }

            for (String imp : table.getImports()) {
                String lastPart = imp.contains(".") ? imp.substring(imp.lastIndexOf('.') + 1) : imp;
                if (lastPart.equals(varName)) {
                    return new Type(varName, false);
                }
            }
            throw new SemanticException("Unknown variable or class reference: " + varName);
        }
        else if (Kind.BINARY_EXPR.check(expr)) {
            String op = expr.get("operation");

            if (op.equals("+") || op.equals("-") || op.equals("*") || op.equals("/")) {
                System.out.println("HAHAHHAHAHAHHA");
                return new Type("int", false);
            } else {
                System.out.println("BAAHHAHAHAHAHAHA");
                return new Type("boolean", false);

            }
        }

        else if (Kind.METHOD_CALL.check(expr)) {
            String methodName = expr.get("name");
            if (!table.getMethods().contains(methodName)) {
                throw new SemanticException("Method '" + methodName + "' not declared.");
            }

            return table.getReturnType(methodName);

        } else if (Kind.NEW_CLASS_EXPR.check(expr)) {
            return new Type(expr.get("name"), false);

        } else if (Kind.NEW_ARRAY_EXPR.check(expr)) {
            return new Type("int", true);

        } else if (Kind.BOOLEAN_LITERAL.check(expr)) {
            System.out.println("CAAHHAHAHAHAHAHA");

            return new Type("boolean", false);

        }
        else if (Kind.BOOLEAN_EXPR.check(expr)) {
            System.out.println("CAAHHAHAHAHAHAHA");

            return new Type("boolean", false);

        }
        else if (Kind.INTEGER_LITERAL.check(expr)) {
            return new Type("int", false);

        } else if (Kind.THIS_EXPR.check(expr)) {
            return new Type(table.getClassName(), false);

        } else if (Kind.PARENTHESIS_EXPR.check(expr)) {
            return getExprType(expr.getChild(0));

        }

        System.out.println("Unknown expression kind in getExprType: " + expr.getKind());
        return new Type("int", false);
    }


    public class SemanticException extends RuntimeException {
        public SemanticException(String message) {
            super(message);
        }
    }




}
